package com.trilon.omnio.content.conduit.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.trilon.omnio.Constants;
import com.trilon.omnio.api.conduit.ConduitSlot;
import com.trilon.omnio.api.conduit.IConduitType;
import com.trilon.omnio.api.conduit.network.IConduitNetworkContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persists conduit network context state (energy buffers, fluid locks, etc.)
 * across world saves / reloads using Minecraft's {@link SavedData} API.
 *
 * <h3>What is persisted:</h3>
 * <ul>
 *   <li>For each {@link ConduitSlot}, a list of networks</li>
 *   <li>Each network stores a representative {@link BlockPos} and a serialized
 *       {@link ConduitNetworkContext}</li>
 * </ul>
 *
 * <h3>What is NOT persisted:</h3>
 * <ul>
 *   <li>Network topology (node positions, graph edges) — rebuilt from block entities on chunk load</li>
 *   <li>Transient per-tick state (e.g., redstone signal strengths)</li>
 * </ul>
 *
 * <h3>Restore flow:</h3>
 * <ol>
 *   <li>Level loads → SavedData is loaded from disk → contexts stored in {@link #pendingContexts}</li>
 *   <li>Chunks load → block entities load → {@link ConduitNetworkManager#onBlockEntityLoaded} rebuilds graph</li>
 *   <li>When a network is rebuilt, the manager calls {@link #tryApplyPendingContext} to match
 *       saved contexts to rebuilt networks via representative position</li>
 * </ol>
 */
public class ConduitNetworkSavedData extends SavedData {

    /** The key used to store/retrieve this SavedData from the level's DataStorage. */
    public static final String DATA_KEY = "omnio_conduit_networks";

    // NBT structure constants
    private static final String TAG_SLOTS = "Slots";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_NETWORKS = "Networks";
    private static final String TAG_NODE_POSITIONS = "NodePositions";
    private static final String TAG_CONTEXT = "Context";

    /**
     * Pending contexts loaded from disk but not yet applied to rebuilt networks.
     * Key = ConduitSlot, Value = list of (node positions set → context NBT) pairs.
     * Entries are removed as networks are rebuilt and matched.
     */
    private final Map<ConduitSlot, List<PendingNetwork>> pendingContexts = new HashMap<>();

    /**
     * Reference to the network manager for this level.
     * Set during initialization; may be null during initial load before manager is ready.
     */
    private ConduitNetworkManager manager;

    /**
     * Holds a saved network's node positions and serialized context.
     * Matching works by checking if any saved position appears in a rebuilt network.
     */
    private record PendingNetwork(Set<BlockPos> nodePositions, CompoundTag contextTag) {}

    public ConduitNetworkSavedData() {
        // Empty — will be populated via load() or from live network data
    }

    /**
     * Associate this SavedData with a network manager.
     * Must be called after creation, before contexts can be applied.
     */
    public void setManager(ConduitNetworkManager manager) {
        this.manager = manager;
    }

    // ========================================================================
    // Save — serialize live network contexts to NBT
    // ========================================================================

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        if (manager == null) {
            return root;
        }

        ListTag slotList = new ListTag();

        // Iterate all conduit slots that have networks
        for (Map.Entry<ConduitSlot, Set<ConduitNetwork>> entry : manager.getNetworksBySlot().entrySet()) {
            ConduitSlot slot = entry.getKey();
            Set<ConduitNetwork> networks = entry.getValue();

            if (networks == null || networks.isEmpty()) continue;

            CompoundTag slotTag = new CompoundTag();
            slotTag.put(TAG_SLOT, slot.save());

            ListTag networkList = new ListTag();
            for (ConduitNetwork network : networks) {
                if (network.isEmpty()) continue;

                CompoundTag netTag = new CompoundTag();

                // Save ALL node positions for robust matching during restore
                long[] posArray = network.getAllNodes().stream()
                        .mapToLong(n -> n.getPos().asLong())
                        .toArray();
                netTag.putLongArray(TAG_NODE_POSITIONS, posArray);

                // Save context
                IConduitNetworkContext<?> ctx = network.getContext();
                if (ctx instanceof ConduitNetworkContext netCtx) {
                    netTag.put(TAG_CONTEXT, netCtx.saveToTag());
                }

                networkList.add(netTag);
            }

            if (!networkList.isEmpty()) {
                slotTag.put(TAG_NETWORKS, networkList);
                slotList.add(slotTag);
            }
        }

        root.put(TAG_SLOTS, slotList);
        return root;
    }

    // ========================================================================
    // Load — deserialize saved contexts into pending staging map
    // ========================================================================

    /**
     * Load saved network contexts from disk into the pending staging map.
     * Contexts will be applied later as networks are rebuilt from block entities.
     */
    public static ConduitNetworkSavedData load(CompoundTag root, HolderLookup.Provider registries) {
        ConduitNetworkSavedData data = new ConduitNetworkSavedData();

        if (!root.contains(TAG_SLOTS)) return data;

        ListTag slotList = root.getList(TAG_SLOTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < slotList.size(); i++) {
            CompoundTag slotTag = slotList.getCompound(i);

            if (!slotTag.contains(TAG_SLOT)) continue;

            ConduitSlot slot;
            try {
                slot = ConduitSlot.load(slotTag.getCompound(TAG_SLOT));
            } catch (Exception e) {
                Constants.LOG.warn("Failed to load conduit slot from saved data, skipping: {}", e.getMessage());
                continue;
            }

            // Check if the conduit type is still registered
            if (!ConduitTypeRegistry.isRegistered(slot.conduitId())) {
                Constants.LOG.warn("Conduit type {} not registered (mod removed?), discarding saved network data",
                        slot.conduitId());
                continue;
            }

            if (!slotTag.contains(TAG_NETWORKS)) continue;

            ListTag networkList = slotTag.getList(TAG_NETWORKS, Tag.TAG_COMPOUND);
            List<PendingNetwork> pendingList = new ArrayList<>();

            for (int j = 0; j < networkList.size(); j++) {
                CompoundTag netTag = networkList.getCompound(j);

                Set<BlockPos> nodePositions = new HashSet<>();
                if (netTag.contains(TAG_NODE_POSITIONS)) {
                    long[] posArray = netTag.getLongArray(TAG_NODE_POSITIONS);
                    for (long encoded : posArray) {
                        nodePositions.add(BlockPos.of(encoded));
                    }
                }

                if (nodePositions.isEmpty()) continue;

                CompoundTag contextTag = netTag.contains(TAG_CONTEXT)
                        ? netTag.getCompound(TAG_CONTEXT)
                        : new CompoundTag();

                pendingList.add(new PendingNetwork(nodePositions, contextTag));
            }

            if (!pendingList.isEmpty()) {
                data.pendingContexts.put(slot, pendingList);
                Constants.LOG.debug("Loaded {} pending network contexts for slot {}",
                        pendingList.size(), slot);
            }
        }

        return data;
    }

    // ========================================================================
    // Context matching — apply saved contexts to rebuilt networks
    // ========================================================================

    /**
     * Try to apply a saved context to a rebuilt network.
     * Called by the network manager after a network is formed or merged during
     * chunk/block-entity load.
     *
     * <p>Matches by checking if any of the network's node positions matches any
     * saved node position for this slot.</p>
     *
     * @param slot    the conduit slot
     * @param network the newly rebuilt network
     * @return true if a saved context was found and applied
     */
    public boolean tryApplyPendingContext(ConduitSlot slot, ConduitNetwork network) {
        List<PendingNetwork> pendingList = pendingContexts.get(slot);
        if (pendingList == null || pendingList.isEmpty()) return false;

        // Check if any saved node position exists in this rebuilt network
        for (int i = 0; i < pendingList.size(); i++) {
            PendingNetwork pending = pendingList.get(i);
            boolean matches = false;
            for (BlockPos savedPos : pending.nodePositions) {
                if (network.containsNode(savedPos)) {
                    matches = true;
                    break;
                }
            }
            if (matches) {
                // Match found — apply the saved context
                applyContext(slot, network, pending.contextTag);
                pendingList.remove(i);

                // Also consume any other pending entries that share a position with this network
                // (can happen if two saved networks got merged during rebuild)
                pendingList.removeIf(p -> {
                    for (BlockPos pos : p.nodePositions) {
                        if (network.containsNode(pos)) return true;
                    }
                    return false;
                });

                if (pendingList.isEmpty()) {
                    pendingContexts.remove(slot);
                }

                Constants.LOG.debug("Applied saved context to network {} for slot {}", network.getId(), slot);
                return true;
            }
        }

        return false;
    }

    /**
     * Apply a saved context tag to a rebuilt network by creating a fresh context
     * from the conduit type's factory and loading the saved data into it.
     */
    private void applyContext(ConduitSlot slot, ConduitNetwork network, CompoundTag contextTag) {
        if (contextTag.isEmpty()) return;

        IConduitType<?> type = ConduitTypeRegistry.getOrStub(slot.conduitId());
        IConduitNetworkContext<?> freshCtx = type.createNetworkContext();
        if (freshCtx instanceof ConduitNetworkContext netCtx) {
            netCtx.loadFromTag(contextTag);
            network.setContext(netCtx);
        }
    }

    /**
     * @return true if there are pending contexts waiting to be applied
     */
    public boolean hasPendingContexts() {
        return !pendingContexts.isEmpty();
    }

    /**
     * @return the number of pending context entries across all slots
     */
    public int getPendingContextCount() {
        int count = 0;
        for (List<PendingNetwork> list : pendingContexts.values()) {
            count += list.size();
        }
        return count;
    }

    // ========================================================================
    // Factory for MC's SavedData API
    // ========================================================================

    /**
     * Creates the SavedData factory for use with
     * {@code level.getDataStorage().computeIfAbsent(factory, DATA_KEY)}.
     */
    public static SavedData.Factory<ConduitNetworkSavedData> factory() {
        return new SavedData.Factory<>(
                ConduitNetworkSavedData::new,
                ConduitNetworkSavedData::load,
                null // no DataFixTypes needed for custom mod data
        );
    }
}
