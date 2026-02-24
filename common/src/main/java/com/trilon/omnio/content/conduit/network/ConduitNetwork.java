package com.trilon.omnio.content.conduit.network;

import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.api.conduit.IConduitNode;
import com.trilon.omnio.api.conduit.IConduitType;
import com.trilon.omnio.api.conduit.IConnectionConfig;
import com.trilon.omnio.api.conduit.network.IConduitNetwork;
import com.trilon.omnio.api.conduit.network.IConduitNetworkContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

/**
 * Concrete implementation of {@link IConduitNetwork}.
 * Represents a connected subgraph of conduit nodes of the same conduit type.
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Tracks all nodes in the graph and their neighbor relationships</li>
 *   <li>Maintains cached sets of ticking nodes and block endpoints</li>
 *   <li>Holds a per-network mutable {@link IConduitNetworkContext}</li>
 *   <li>Supports merge (absorb another network) and provides data for split</li>
 * </ul>
 *
 * <p>The network does NOT own the graph mutation logic (add/remove/BFS) —
 * that belongs to {@link ConduitNetworkManager}. The network is a data holder
 * that the manager creates, populates, and tears down.</p>
 */
public class ConduitNetwork implements IConduitNetwork {

    private static long nextId = 0;

    private final long id;
    private final IConduitType<?> type;

    /**
     * All nodes in this network, keyed by position for O(1) lookup.
     */
    private final Map<BlockPos, ConduitNodeImpl> nodes = new LinkedHashMap<>();

    /**
     * Per-network mutable context (energy buffer, locked fluid, etc.).
     * Created by the conduit type or defaulting to {@link ConduitNetworkContext}.
     */
    private IConduitNetworkContext<?> context;

    /**
     * Cached: nodes that are in loaded chunks and should be ticked.
     * Rebuilt lazily when dirty.
     */
    private List<ConduitNodeImpl> tickingNodesCache;

    /**
     * Cached: nodes with at least one CONNECTED_BLOCK connection.
     * Rebuilt lazily when dirty.
     */
    private List<ConduitNodeImpl> blockEndpointsCache;

    /**
     * If true, the ticking/endpoint caches need rebuilding.
     */
    private boolean cachesDirty = true;

    public ConduitNetwork(IConduitType<?> type) {
        this.id = nextId++;
        this.type = type;
        this.context = new ConduitNetworkContext();
    }

    // ---- IConduitNetwork implementation ----

    @Override
    public IConduitType<?> getType() {
        return type;
    }

    @Override
    public Collection<ConduitNodeImpl> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    @Override
    public Collection<ConduitNodeImpl> getTickingNodes() {
        rebuildCachesIfDirty();
        return Collections.unmodifiableList(tickingNodesCache);
    }

    @Override
    public Collection<ConduitNodeImpl> getBlockEndpoints() {
        rebuildCachesIfDirty();
        return Collections.unmodifiableList(blockEndpointsCache);
    }

    @Override
    public IConduitNetworkContext<?> getContext() {
        return context;
    }

    @Override
    public int size() {
        return nodes.size();
    }

    // ---- Network identity ----

    /**
     * @return unique network ID for this session (not persisted)
     */
    public long getId() {
        return id;
    }

    // ---- Node management (called by ConduitNetworkManager) ----

    /**
     * Add a node to this network. Updates the node's network reference.
     * Does NOT wire up graph neighbor edges — that's the manager's job.
     */
    public void addNode(ConduitNodeImpl node) {
        nodes.put(node.getPos(), node);
        node.setNetwork(this);
        invalidateCaches();
    }

    /**
     * Remove a node from this network. Clears the node's network reference.
     * Does NOT clean up graph neighbor edges — that's the manager's job.
     */
    public void removeNode(ConduitNodeImpl node) {
        nodes.remove(node.getPos());
        node.setNetwork(null);
        invalidateCaches();
    }

    /**
     * @param pos the block position to look up
     * @return the node at that position, or null if not in this network
     */
    public ConduitNodeImpl getNode(BlockPos pos) {
        return nodes.get(pos);
    }

    /**
     * @param pos the block position to check
     * @return true if this network contains a node at the given position
     */
    public boolean containsNode(BlockPos pos) {
        return nodes.containsKey(pos);
    }

    /**
     * @return true if this network has no nodes
     */
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    // ---- Merge ----

    /**
     * Absorb all nodes from another network into this one.
     * The other network will be empty after this call.
     * Merges the network contexts as well.
     *
     * @param other the network to absorb
     */
    @SuppressWarnings("unchecked")
    public void mergeFrom(ConduitNetwork other) {
        for (ConduitNodeImpl node : other.nodes.values()) {
            node.setNetwork(this);
            nodes.put(node.getPos(), node);
        }
        other.nodes.clear();

        // Merge contexts
        if (context instanceof ConduitNetworkContext thisCtx && other.context instanceof ConduitNetworkContext otherCtx) {
            thisCtx.mergeFrom(otherCtx);
        }

        invalidateCaches();
        other.invalidateCaches();
    }

    // ---- Context ----

    /**
     * Set a custom network context (e.g., EnergyConduitNetworkContext).
     */
    public void setContext(IConduitNetworkContext<?> context) {
        this.context = context;
    }

    // ---- Caches ----

    /**
     * Mark caches as dirty. Called when nodes are added/removed or connections change.
     */
    public void invalidateCaches() {
        cachesDirty = true;
    }

    private void rebuildCachesIfDirty() {
        if (!cachesDirty) return;

        // Single pass over all nodes to populate both caches
        List<ConduitNodeImpl> ticking = new ArrayList<>();
        List<ConduitNodeImpl> endpoints = new ArrayList<>();
        for (ConduitNodeImpl node : nodes.values()) {
            if (node.isTicking()) ticking.add(node);
            if (node.isBlockEndpoint()) endpoints.add(node);
        }
        tickingNodesCache = ticking;
        blockEndpointsCache = endpoints;

        cachesDirty = false;
    }

    // ---- Graph traversal helpers ----

    /**
     * Perform a BFS from a starting node, returning all reachable nodes
     * within this network's node set via conduit-to-conduit edges.
     *
     * @param start the node to start from
     * @return set of all reachable positions (including start)
     */
    public Set<BlockPos> bfsReachable(ConduitNodeImpl start) {
        Set<BlockPos> visited = new LinkedHashSet<>();
        Queue<ConduitNodeImpl> queue = new ArrayDeque<>();

        visited.add(start.getPos());
        queue.add(start);

        while (!queue.isEmpty()) {
            ConduitNodeImpl current = queue.poll();
            for (Map.Entry<Direction, ConduitNodeImpl> entry : current.getNeighborNodes().entrySet()) {
                ConduitNodeImpl neighbor = entry.getValue();
                if (neighbor != null && nodes.containsKey(neighbor.getPos()) && visited.add(neighbor.getPos())) {
                    queue.add(neighbor);
                }
            }
        }

        return visited;
    }

    @Override
    public String toString() {
        return "ConduitNetwork[id=" + id + ", type=" + type.getId() + ", nodes=" + nodes.size() + "]";
    }
}
