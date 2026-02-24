package com.trilon.omnio.content.conduit.network;

import com.trilon.omnio.Constants;
import com.trilon.omnio.api.conduit.IConduitType;
import com.trilon.omnio.content.conduit.ConnectionContainer;
import com.trilon.omnio.content.conduit.OmniConduitBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all conduit networks in a single server level.
 * Handles the full lifecycle: node creation, network formation via merge,
 * network splitting on node removal, ticking, and cleanup.
 *
 * <p>One instance per {@link ServerLevel}, obtained via {@link #get(ServerLevel)}.</p>
 *
 * <h3>Network formation rules:</h3>
 * <ul>
 *   <li>Each conduit ID gets its own independent set of networks</li>
 *   <li>When a conduit is placed, check all 6 neighbors for matching conduit IDs</li>
 *   <li>If neighbor(s) exist, join/merge their networks into one</li>
 *   <li>If no neighbors, create a new singleton network</li>
 * </ul>
 *
 * <h3>Split detection:</h3>
 * <ul>
 *   <li>When a conduit is removed, BFS from each remaining neighbor</li>
 *   <li>If all neighbors reach each other → network intact</li>
 *   <li>If not → split into separate networks, redistributing context</li>
 * </ul>
 */
public class ConduitNetworkManager {

    /**
     * Per-level manager instances. Cleared on server stop.
     * Uses ConcurrentHashMap for thread-safety between tick and server-stop events.
     * Note: unlike WeakHashMap, entries are NOT automatically GC'd — explicit
     * cleanup via {@link #clearAll()} on server stop is required.
     */
    private static final Map<ServerLevel, ConduitNetworkManager> INSTANCES = new ConcurrentHashMap<>();

    /**
     * All nodes in this level, keyed by (conduitId, blockPos).
     * Primary index for O(1) node lookup.
     */
    private final Map<ResourceLocation, Map<BlockPos, ConduitNodeImpl>> nodesByConduit = new HashMap<>();

    /**
     * All active networks in this level, keyed by (conduitId, networkId).
     */
    private final Map<ResourceLocation, Set<ConduitNetwork>> networksByConduit = new HashMap<>();

    /**
     * The server level this manager is associated with.
     */
    private final ServerLevel level;

    /**
     * Tick counter per conduit ID, for rate-limiting ticker invocations.
     */
    private final Map<ResourceLocation, Integer> tickCounters = new HashMap<>();

    private ConduitNetworkManager(ServerLevel level) {
        this.level = level;
    }

    /**
     * Get or create the network manager for a server level.
     */
    public static ConduitNetworkManager get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, ConduitNetworkManager::new);
    }

    /**
     * Clear all manager instances. Called on server stop.
     */
    public static void clearAll() {
        INSTANCES.clear();
    }

    // ========================================================================
    // Node placement — called when a conduit is added to a bundle
    // ========================================================================

    /**
     * Called when a conduit is added to a block entity.
     * Creates a node, evaluates neighbor connections, and merges/creates networks.
     *
     * @param pos       the position of the conduit bundle
     * @param conduitId the conduit variant ID
     * @param container the connection container for this conduit at this position
     */
    public void onConduitAdded(BlockPos pos, ResourceLocation conduitId, ConnectionContainer container) {
        Map<BlockPos, ConduitNodeImpl> nodesMap = nodesByConduit.computeIfAbsent(conduitId, k -> new HashMap<>());

        // Don't re-add if already tracked (e.g., chunk reload)
        if (nodesMap.containsKey(pos)) {
            // Just refresh the connection data
            ConduitNodeImpl existing = nodesMap.get(pos);
            existing.syncFromContainer(container);
            if (existing.getNetwork() != null) {
                existing.getNetwork().invalidateCaches();
            }
            return;
        }

        // Create the node
        ConduitNodeImpl node = new ConduitNodeImpl(pos);
        node.syncFromContainer(container);
        nodesMap.put(pos, node);

        // Find neighbor nodes and wire up graph edges
        Set<ConduitNetwork> neighborNetworks = new LinkedHashSet<>();
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            ConduitNodeImpl neighborNode = nodesMap.get(neighborPos);
            if (neighborNode != null) {
                // Wire bidirectional graph edges
                node.setNeighbor(dir, neighborNode);
                neighborNode.setNeighbor(dir.getOpposite(), node);

                if (neighborNode.getNetwork() != null) {
                    neighborNetworks.add(neighborNode.getNetwork());
                }
            }
        }

        if (neighborNetworks.isEmpty()) {
            // No neighbors — create a new singleton network
            ConduitNetwork network = createNetwork(conduitId);
            network.addNode(node);
            Constants.LOG.debug("Created new network {} for {} at {}", network.getId(), conduitId, pos.toShortString());
        } else {
            // Merge all neighboring networks into one, then add the new node
            Iterator<ConduitNetwork> it = neighborNetworks.iterator();
            ConduitNetwork primary = it.next();
            while (it.hasNext()) {
                ConduitNetwork other = it.next();
                if (other != primary) {
                    Constants.LOG.debug("Merging network {} into {} for {}", other.getId(), primary.getId(), conduitId);
                    primary.mergeFrom(other);
                    removeNetwork(conduitId, other);
                }
            }
            primary.addNode(node);
        }
    }

    // ========================================================================
    // Node removal — called when a conduit is removed from a bundle
    // ========================================================================

    /**
     * Called when a conduit is removed from a block entity.
     * Removes the node, disconnects graph edges, and handles potential network splits.
     *
     * @param pos       the position of the conduit bundle
     * @param conduitId the conduit variant ID
     */
    public void onConduitRemoved(BlockPos pos, ResourceLocation conduitId) {
        Map<BlockPos, ConduitNodeImpl> nodesMap = nodesByConduit.get(conduitId);
        if (nodesMap == null) return;

        ConduitNodeImpl node = nodesMap.remove(pos);
        if (node == null) return;

        ConduitNetwork network = node.getNetwork();

        // Collect neighbors before disconnecting
        List<ConduitNodeImpl> formerNeighbors = new ArrayList<>();
        for (Map.Entry<Direction, ConduitNodeImpl> entry : node.getNeighborNodes().entrySet()) {
            ConduitNodeImpl neighbor = entry.getValue();
            if (neighbor != null) {
                formerNeighbors.add(neighbor);
                // Remove bidirectional edge
                neighbor.setNeighbor(entry.getKey().getOpposite(), null);
            }
        }
        node.clearNeighbors();

        if (network != null) {
            network.removeNode(node);

            if (network.isEmpty()) {
                // Network is now empty, clean it up
                removeNetwork(conduitId, network);
                Constants.LOG.debug("Removed empty network {} for {}", network.getId(), conduitId);
            } else if (formerNeighbors.size() >= 2) {
                // Potential split: check if all former neighbors can still reach each other
                checkAndSplitNetwork(conduitId, network, formerNeighbors);
            }
            // If only 0 or 1 former neighbor, no split possible
        }
    }

    /**
     * Check if a network needs to be split after a node was removed.
     * Uses BFS from the first former neighbor; if not all former neighbors
     * are reachable, splits into separate networks.
     */
    private void checkAndSplitNetwork(ResourceLocation conduitId, ConduitNetwork network, List<ConduitNodeImpl> formerNeighbors) {
        // BFS from the first remaining neighbor
        ConduitNodeImpl seed = formerNeighbors.get(0);
        Set<BlockPos> reachable = network.bfsReachable(seed);

        // Check if all former neighbors are in the reachable set
        boolean needsSplit = false;
        for (int i = 1; i < formerNeighbors.size(); i++) {
            if (!reachable.contains(formerNeighbors.get(i).getPos())) {
                needsSplit = true;
                break;
            }
        }

        if (!needsSplit) return;

        // Perform the split: BFS from each unreachable neighbor to form new networks
        Set<BlockPos> assigned = new LinkedHashSet<>(reachable);

        // The seed's reachable set stays in the original network — remove everything else
        List<ConduitNodeImpl> toRemoveFromOriginal = new ArrayList<>();
        for (ConduitNodeImpl n : network.getAllNodes()) {
            if (!reachable.contains(n.getPos())) {
                toRemoveFromOriginal.add(n);
            }
        }
        for (ConduitNodeImpl n : toRemoveFromOriginal) {
            network.removeNode(n);
        }

        // Now BFS from each unassigned former neighbor to create new split networks
        for (int i = 1; i < formerNeighbors.size(); i++) {
            ConduitNodeImpl neighbor = formerNeighbors.get(i);
            if (assigned.contains(neighbor.getPos())) continue;

            // BFS from this neighbor among the removed nodes
            ConduitNetwork splitNetwork = createNetwork(conduitId);
            Set<BlockPos> splitReachable = bfsAmong(neighbor, toRemoveFromOriginal);

            double fraction = (double) splitReachable.size() / (reachable.size() + toRemoveFromOriginal.size());

            for (ConduitNodeImpl removed : toRemoveFromOriginal) {
                if (splitReachable.contains(removed.getPos())) {
                    splitNetwork.addNode(removed);
                    assigned.add(removed.getPos());
                }
            }

            // Split the context proportionally
            if (network.getContext() instanceof ConduitNetworkContext ctx) {
                splitNetwork.setContext(ctx.split(fraction));
            }

            Constants.LOG.debug("Split off network {} ({} nodes) from {} for {}",
                    splitNetwork.getId(), splitNetwork.size(), network.getId(), conduitId);
        }
    }

    /**
     * BFS from a starting node, only visiting positions present in the given node list.
     */
    private Set<BlockPos> bfsAmong(ConduitNodeImpl start, List<ConduitNodeImpl> candidates) {
        Set<BlockPos> candidatePositions = new HashSet<>();
        Map<BlockPos, ConduitNodeImpl> candidateMap = new HashMap<>();
        for (ConduitNodeImpl n : candidates) {
            candidatePositions.add(n.getPos());
            candidateMap.put(n.getPos(), n);
        }

        Set<BlockPos> visited = new LinkedHashSet<>();
        Queue<ConduitNodeImpl> queue = new ArrayDeque<>();

        if (candidatePositions.contains(start.getPos())) {
            visited.add(start.getPos());
            queue.add(start);
        }

        while (!queue.isEmpty()) {
            ConduitNodeImpl current = queue.poll();
            for (ConduitNodeImpl neighbor : current.getNeighborNodes().values()) {
                if (neighbor != null && candidatePositions.contains(neighbor.getPos()) && visited.add(neighbor.getPos())) {
                    queue.add(neighbor);
                }
            }
        }

        return visited;
    }

    // ========================================================================
    // Connection updates — called when block entity connections change
    // ========================================================================

    /**
     * Called when a conduit's connections are re-evaluated (e.g., neighbor block changed).
     * Updates the node's cached connection data, refreshes graph edges, and handles
     * any topology changes (merges if new edges bridge separate networks, splits if
     * removed edges disconnect parts of a network).
     */
    public void onConnectionsChanged(BlockPos pos, ResourceLocation conduitId, ConnectionContainer container) {
        Map<BlockPos, ConduitNodeImpl> nodesMap = nodesByConduit.get(conduitId);
        if (nodesMap == null) return;

        ConduitNodeImpl node = nodesMap.get(pos);
        if (node == null) return;

        node.syncFromContainer(container);

        // Track topology changes as we refresh edges
        Set<ConduitNetwork> networksToMerge = new LinkedHashSet<>();
        boolean edgeRemoved = false;

        if (node.getNetwork() != null) {
            networksToMerge.add(node.getNetwork());
        }

        Set<Direction> conduitDirs = node.getConduitConnectedDirections();

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            ConduitNodeImpl neighborNode = nodesMap.get(neighborPos);

            if (neighborNode != null && conduitDirs.contains(dir)) {
                // Edge should exist
                node.setNeighbor(dir, neighborNode);
                neighborNode.setNeighbor(dir.getOpposite(), node);

                // If the neighbor is in a different network, we need to merge
                if (neighborNode.getNetwork() != null) {
                    networksToMerge.add(neighborNode.getNetwork());
                }
            } else {
                // Edge should NOT exist
                ConduitNodeImpl oldNeighbor = node.getNeighborNodes().get(dir);
                if (oldNeighbor != null) {
                    edgeRemoved = true;
                }
                node.setNeighbor(dir, null);
                if (neighborNode != null) {
                    neighborNode.setNeighbor(dir.getOpposite(), null);
                }
            }
        }

        // Handle merges: if edges now bridge multiple networks, merge them
        if (networksToMerge.size() > 1) {
            Iterator<ConduitNetwork> it = networksToMerge.iterator();
            ConduitNetwork primary = it.next();
            while (it.hasNext()) {
                ConduitNetwork other = it.next();
                if (other != primary) {
                    Constants.LOG.debug("Merging network {} into {} for {} (connection change)",
                            other.getId(), primary.getId(), conduitId);
                    primary.mergeFrom(other);
                    removeNetwork(conduitId, other);
                }
            }
        }

        // Handle splits: if edges were removed, check if network is still connected
        ConduitNetwork network = node.getNetwork();
        if (edgeRemoved && network != null && network.size() > 1) {
            Set<BlockPos> reachable = network.bfsReachable(node);
            if (reachable.size() < network.size()) {
                // Network is disconnected — split off the unreachable nodes
                List<ConduitNodeImpl> unreachable = new ArrayList<>();
                for (ConduitNodeImpl n : network.getAllNodes()) {
                    if (!reachable.contains(n.getPos())) {
                        unreachable.add(n);
                    }
                }
                for (ConduitNodeImpl n : unreachable) {
                    network.removeNode(n);
                }

                // BFS among unreachable to form new networks (may be >1 fragment)
                Set<BlockPos> assigned = new LinkedHashSet<>();
                for (ConduitNodeImpl orphan : unreachable) {
                    if (assigned.contains(orphan.getPos())) continue;
                    ConduitNetwork splitNetwork = createNetwork(conduitId);
                    Set<BlockPos> fragment = bfsAmong(orphan, unreachable);
                    double fraction = (double) fragment.size() / (reachable.size() + unreachable.size());
                    for (ConduitNodeImpl n : unreachable) {
                        if (fragment.contains(n.getPos())) {
                            splitNetwork.addNode(n);
                            assigned.add(n.getPos());
                        }
                    }
                    if (network.getContext() instanceof ConduitNetworkContext ctx) {
                        splitNetwork.setContext(ctx.split(fraction));
                    }
                    Constants.LOG.debug("Split off network {} ({} nodes) from {} for {} (connection change)",
                            splitNetwork.getId(), splitNetwork.size(), network.getId(), conduitId);
                }
            }
        }

        if (network != null) {
            network.invalidateCaches();
        }
    }

    // ========================================================================
    // Ticking — called once per server tick
    // ========================================================================

    /**
     * Tick all conduit networks in this level.
     * Respects each conduit type's tick rate.
     * Called from the platform-specific server tick event.
     */
    public void tickAllNetworks() {
        for (Map.Entry<ResourceLocation, Set<ConduitNetwork>> entry : networksByConduit.entrySet()) {
            ResourceLocation conduitId = entry.getKey();
            Set<ConduitNetwork> networks = entry.getValue();

            if (networks.isEmpty()) continue;

            // Get any network to access the conduit type (all share the same type)
            ConduitNetwork anyNetwork = networks.iterator().next();
            IConduitType<?> type = anyNetwork.getType();
            int tickRate = type.getTickRate();

            // Rate-limit based on tick rate
            int counter = tickCounters.getOrDefault(conduitId, 0) + 1;
            if (counter >= tickRate) {
                counter = 0;
                for (ConduitNetwork network : networks) {
                    if (!network.isEmpty()) {
                        type.getTicker().tick(level, network);
                    }
                }
            }
            tickCounters.put(conduitId, counter);
        }
    }

    // ========================================================================
    // Chunk load/unload — mark nodes as ticking or non-ticking
    // ========================================================================

    /**
     * Called when a chunk is loaded. Marks all conduit nodes in that chunk as ticking.
     */
    public void onChunkLoaded(int chunkX, int chunkZ) {
        updateTickingStateForChunk(chunkX, chunkZ, true);
    }

    /**
     * Called when a chunk is unloaded. Marks all conduit nodes in that chunk as non-ticking.
     */
    public void onChunkUnloaded(int chunkX, int chunkZ) {
        updateTickingStateForChunk(chunkX, chunkZ, false);
    }

    private void updateTickingStateForChunk(int chunkX, int chunkZ, boolean ticking) {
        for (Map<BlockPos, ConduitNodeImpl> nodesMap : nodesByConduit.values()) {
            for (ConduitNodeImpl node : nodesMap.values()) {
                BlockPos pos = node.getPos();
                if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ) {
                    node.setTicking(ticking);
                    if (node.getNetwork() != null) {
                        node.getNetwork().invalidateCaches();
                    }
                }
            }
        }
    }

    // ========================================================================
    // Block entity integration — rebuild nodes from a loaded block entity
    // ========================================================================

    /**
     * Called when a block entity is loaded (chunk load, world load).
     * Ensures all conduits in the bundle are tracked in the network manager.
     * This is the primary mechanism for rebuilding the network graph after a restart.
     */
    public void onBlockEntityLoaded(OmniConduitBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        for (ResourceLocation conduitId : blockEntity.getConduitIds()) {
            ConnectionContainer container = blockEntity.getConnectionContainer(conduitId);
            if (container != null) {
                onConduitAdded(pos, conduitId, container);
            }
        }
    }

    /**
     * Called when a block entity is unloaded or destroyed.
     * Removes all conduit nodes associated with that block entity.
     */
    public void onBlockEntityRemoved(OmniConduitBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        for (ResourceLocation conduitId : blockEntity.getConduitIds()) {
            onConduitRemoved(pos, conduitId);
        }
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private ConduitNetwork createNetwork(ResourceLocation conduitId) {
        // TODO: When conduit type registry is wired, look up the IConduitType from conduitId
        // For now, create with a stub type
        ConduitNetwork network = new ConduitNetwork(new StubConduitType(conduitId));
        networksByConduit.computeIfAbsent(conduitId, k -> new LinkedHashSet<>()).add(network);
        return network;
    }

    private void removeNetwork(ResourceLocation conduitId, ConduitNetwork network) {
        Set<ConduitNetwork> networks = networksByConduit.get(conduitId);
        if (networks != null) {
            networks.remove(network);
            if (networks.isEmpty()) {
                networksByConduit.remove(conduitId);
            }
        }
    }

    // ---- Queries ----

    /**
     * @param conduitId the conduit type to query
     * @return all networks for the given conduit type in this level
     */
    public Collection<ConduitNetwork> getNetworks(ResourceLocation conduitId) {
        Set<ConduitNetwork> nets = networksByConduit.get(conduitId);
        return nets != null ? Collections.unmodifiableSet(nets) : Collections.emptySet();
    }

    /**
     * @return the total number of tracked networks across all conduit types
     */
    public int getTotalNetworkCount() {
        int count = 0;
        for (Set<ConduitNetwork> nets : networksByConduit.values()) {
            count += nets.size();
        }
        return count;
    }

    /**
     * @return the total number of tracked nodes across all conduit types
     */
    public int getTotalNodeCount() {
        int count = 0;
        for (Map<BlockPos, ConduitNodeImpl> nodesMap : nodesByConduit.values()) {
            count += nodesMap.size();
        }
        return count;
    }
}
