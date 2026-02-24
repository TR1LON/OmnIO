package com.trilon.omnio.content.conduit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.trilon.omnio.Constants;
import com.trilon.omnio.api.conduit.ConduitSlot;
import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.api.conduit.IConduitType;
import com.trilon.omnio.content.conduit.network.ConduitNetworkManager;
import com.trilon.omnio.content.conduit.network.ConduitTypeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block entity for {@link OmniConduitBlock}.
 * Holds multiple conduit types in a single block ("bundle"), each with its own
 * per-direction connection container tracking status, transfer mode, priority,
 * redstone control, and filter slots.
 *
 * <p>Key data structures:</p>
 * <ul>
 *   <li>{@code conduitSlots} — ordered set of {@link ConduitSlot}s present in this bundle</li>
 *   <li>{@code connections} — per-slot map of {@link ConnectionContainer}</li>
 * </ul>
 */
public class OmniConduitBlockEntity extends BlockEntity implements MenuProvider {

    // TODO: Replace with actual registered BlockEntityType from OmnIOBlockEntities
    private static BlockEntityType<OmniConduitBlockEntity> TYPE;

    /**
     * Ordered set of conduit slots present in this bundle.
     * Each slot is a {@link ConduitSlot} combining conduit ID + color channel.
     * LinkedHashSet preserves insertion order and provides O(1) contains().
     */
    private final Set<ConduitSlot> conduitSlots = new LinkedHashSet<>();

    /**
     * Per-slot connection data. Key = {@link ConduitSlot}.
     */
    private final Map<ConduitSlot, ConnectionContainer> connections = new LinkedHashMap<>();

    /**
     * Cached VoxelShape, rebuilt when connections change.
     */
    private VoxelShape cachedShape = null;

    public OmniConduitBlockEntity(BlockPos pos, BlockState state) {
        super(getBlockEntityType(), pos, state);
    }

    /**
     * Set the registered BlockEntityType. Called during mod init from the registry.
     */
    public static void setType(BlockEntityType<OmniConduitBlockEntity> type) {
        TYPE = type;
    }

    public static BlockEntityType<OmniConduitBlockEntity> getBlockEntityType() {
        if (TYPE == null) {
            throw new IllegalStateException("OmniConduitBlockEntity type not yet registered");
        }
        return TYPE;
    }

    /**
     * Called when the block entity is placed into a level (chunk load, world load).
     * Registers all conduit nodes with the network manager to rebuild the graph.
     * Uses setLevel() instead of onLoad() for vanilla compatibility (onLoad is NeoForge-patched).
     */
    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel && !conduitSlots.isEmpty()) {
            ConduitNetworkManager.get(serverLevel).onBlockEntityLoaded(this);
        }
    }

    // ---- Conduit Management ----

    /**
     * @return unmodifiable view of the conduit slots in this bundle
     */
    public Set<ConduitSlot> getConduitSlots() {
        return Collections.unmodifiableSet(conduitSlots);
    }

    /**
     * @return conduit slots sorted in a deterministic order (by conduitId then channel).
     *         This ensures consistent visual positioning across all bundles regardless
     *         of the order conduits were added.
     */
    public List<ConduitSlot> getSortedSlots() {
        List<ConduitSlot> sorted = new ArrayList<>(conduitSlots);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * @return the number of conduits in this bundle
     */
    public int getConduitCount() {
        return conduitSlots.size();
    }

    /**
     * @param slot the conduit slot to check (conduitId + channel)
     * @return true if this bundle contains the exact slot
     */
    public boolean hasConduit(ConduitSlot slot) {
        return conduitSlots.contains(slot);
    }

    /**
     * Check if this bundle contains any conduit with the given conduit ID
     * (regardless of channel).
     *
     * @param conduitId the conduit ID to check
     * @return true if any slot in this bundle uses the given conduit ID
     */
    public boolean hasConduitType(ResourceLocation conduitId) {
        for (ConduitSlot slot : conduitSlots) {
            if (slot.conduitId().equals(conduitId)) return true;
        }
        return false;
    }

    /**
     * Add a conduit to this bundle.
     *
     * @param slot the conduit slot (conduitId + channel) to add
     * @return true if the conduit was added, false if the bundle is full or already contains it
     */
    public boolean addConduit(ConduitSlot slot) {
        if (conduitSlots.size() >= Constants.MAX_CONDUITS_PER_BUNDLE || conduitSlots.contains(slot)) {
            return false;
        }

        // Reject adding a conduit of the same type-category but different tier
        String conduitType = extractConduitType(slot.conduitId());
        for (ConduitSlot existing : conduitSlots) {
            if (extractConduitType(existing.conduitId()).equals(conduitType)
                    && existing.channel() == slot.channel()) {
                return false; // Same base type + same channel already present (different tier) — use break-and-replace
            }
        }

        conduitSlots.add(slot);
        ConnectionContainer container = new ConnectionContainer();
        connections.put(slot, container);

        // Evaluate connections to neighbors for the new conduit
        if (level != null && !level.isClientSide()) {
            evaluateConnections(slot);
            // Notify the network manager to create/merge networks
            if (level instanceof ServerLevel serverLevel) {
                ConduitNetworkManager.get(serverLevel).onConduitAdded(getBlockPos(), slot, container);
            }
        }

        invalidateShape();
        setChanged();
        syncToClient();
        return true;
    }

    /**
     * Convenience overload: add a conduit with default channel (white/0).
     */
    public boolean addConduit(ResourceLocation conduitId) {
        return addConduit(new ConduitSlot(conduitId));
    }

    /**
     * Extract the conduit type category from a conduit ID.
     * E.g., "omnio:energy_conduit_basic" → "energy_conduit",
     *        "omnio:fluid_conduit_advanced" → "fluid_conduit",
     *        "omnio:redstone_conduit" → "redstone_conduit"
     * Strips the last underscore-separated tier suffix if it matches a known tier.
     */
    private static String extractConduitType(ResourceLocation conduitId) {
        String path = conduitId.getPath();
        // Known tier suffixes to strip
        int lastUnderscore = path.lastIndexOf('_');
        if (lastUnderscore > 0) {
            String suffix = path.substring(lastUnderscore + 1);
            if (suffix.equals("basic") || suffix.equals("advanced") || suffix.equals("elite") || suffix.equals("ultimate") || suffix.equals("creative")) {
                return path.substring(0, lastUnderscore);
            }
        }
        return path;
    }

    /**
     * Remove a conduit from this bundle.
     *
     * @param slot the conduit slot to remove
     * @return true if the conduit was removed
     */
    public boolean removeConduit(ConduitSlot slot) {
        if (!conduitSlots.remove(slot)) {
            return false;
        }
        connections.remove(slot);

        // Disconnect neighbor bundles' connections for this conduit slot
        if (level != null && !level.isClientSide()) {
            disconnectNeighborsFor(slot);
        }

        // Notify the network manager to handle potential splits
        if (level instanceof ServerLevel serverLevel) {
            ConduitNetworkManager.get(serverLevel).onConduitRemoved(getBlockPos(), slot);
        }

        invalidateShape();
        setChanged();
        syncToClient();

        // If no conduits remain, remove the block entirely
        if (conduitSlots.isEmpty() && level != null) {
            level.removeBlock(getBlockPos(), false);
        }

        return true;
    }

    /**
     * @param slot the conduit slot to query
     * @return the connection container for the given slot, or null if not present
     */
    @Nullable
    public ConnectionContainer getConnectionContainer(ConduitSlot slot) {
        return connections.get(slot);
    }

    // ---- Connection Evaluation ----

    /**
     * Evaluate all 6 neighbor positions for a specific conduit slot to determine
     * connection status (conduit-to-conduit, conduit-to-block, or disconnected).
     */
    private void evaluateConnections(ConduitSlot slot) {
        if (level == null) return;

        ConnectionContainer container = connections.get(slot);
        if (container == null) return;

        for (Direction dir : Direction.values()) {
            evaluateConnection(slot, container, dir);
        }
    }

    /**
     * Evaluate a single direction for a specific conduit slot.
     *
     * @return true if the connection status changed (caller should notify network manager)
     */
    private boolean evaluateConnection(ConduitSlot slot, ConnectionContainer container, Direction dir) {
        if (level == null) return false;

        BlockPos neighborPos = getBlockPos().relative(dir);
        BlockEntity neighborBE = level.getBlockEntity(neighborPos);

        if (neighborBE instanceof OmniConduitBlockEntity neighborBundle) {
            // Check if neighbor has the same conduit slot (same type + same channel)
            if (neighborBundle.hasConduit(slot)) {
                ConnectionStatus oldStatus = container.getStatus(dir);
                // Don't override player-disabled connections
                if (oldStatus == ConnectionStatus.DISABLED) {
                    return false;
                }
                container.setConfig(dir, ConnectionConfig.conduitConnection());

                // Also set the reverse connection on the neighbor
                ConnectionContainer neighborContainer = neighborBundle.getConnectionContainer(slot);
                if (neighborContainer != null) {
                    Direction opposite = dir.getOpposite();
                    if (neighborContainer.getStatus(opposite) == ConnectionStatus.DISCONNECTED) {
                        neighborContainer.setConfig(opposite, ConnectionConfig.conduitConnection());
                        neighborBundle.invalidateShape();
                        neighborBundle.setChanged();
                        neighborBundle.syncToClient();
                    }
                }
                return oldStatus != ConnectionStatus.CONNECTED_CONDUIT;
            }
        }

        // Check if neighbor block has a compatible capability for this conduit type
        IConduitType<?> conduitType = ConduitTypeRegistry.getOrStub(slot.conduitId());
        if (conduitType.canConnectToBlock(level, getBlockPos(), dir)) {
            // Only upgrade DISCONNECTED to CONNECTED_BLOCK; don't overwrite DISABLED
            ConnectionStatus currentStatus = container.getStatus(dir);
            if (currentStatus == ConnectionStatus.DISCONNECTED) {
                container.setConfig(dir, ConnectionConfig.blockConnection());
                return true; // Changed from DISCONNECTED to CONNECTED_BLOCK
            }
            return false; // Already connected or disabled — no change
        }

        // No valid connection found — ensure any stale connection is cleared
        ConnectionStatus currentStatus = container.getStatus(dir);
        if (currentStatus != ConnectionStatus.DISCONNECTED && currentStatus != ConnectionStatus.DISABLED) {
            container.disconnect(dir);
            return true; // Changed to DISCONNECTED
        }
        return false; // Already disconnected or disabled — no change
    }

    /**
     * Called when a neighboring block changes.
     * Re-evaluates connections for all conduit slots on the affected side.
     */
    public void onNeighborChanged(Direction direction, BlockState neighborState, BlockPos neighborPos) {
        boolean anyChanged = false;
        for (ConduitSlot slot : conduitSlots) {
            ConnectionContainer container = connections.get(slot);
            if (container != null) {
                boolean changed = evaluateConnection(slot, container, direction);
                if (changed) {
                    anyChanged = true;
                    // Only notify network manager if connection actually changed — avoids unnecessary BFS
                    if (level instanceof ServerLevel serverLevel) {
                        ConduitNetworkManager.get(serverLevel).onConnectionsChanged(getBlockPos(), slot, container);
                    }
                }
            }
        }
        if (anyChanged) {
            invalidateShape();
            setChanged();
            syncToClient();
        }
    }

    /**
     * Called when this block is being removed. Notify all neighbor bundles
     * to disconnect their connections to this position.
     */
    public void onBlockRemoved() {
        if (level == null || level.isClientSide()) return;

        // Disconnect neighbors for all conduit slots in this bundle
        for (ConduitSlot slot : conduitSlots) {
            disconnectNeighborsFor(slot);
        }

        // Notify network manager that all conduit nodes at this position are removed
        if (level instanceof ServerLevel serverLevel) {
            ConduitNetworkManager manager = ConduitNetworkManager.get(serverLevel);
            for (ConduitSlot slot : conduitSlots) {
                manager.onConduitRemoved(getBlockPos(), slot);
            }
        }
    }

    /**
     * Notify all 6 neighboring bundles to disconnect their connection for a specific conduit slot.
     * Called when removing a single conduit or when the block is destroyed.
     */
    private void disconnectNeighborsFor(ConduitSlot slot) {
        if (level == null) return;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = getBlockPos().relative(dir);
            BlockEntity neighborBE = level.getBlockEntity(neighborPos);
            if (neighborBE instanceof OmniConduitBlockEntity neighborBundle) {
                ConnectionContainer neighborContainer = neighborBundle.getConnectionContainer(slot);
                if (neighborContainer != null) {
                    neighborContainer.disconnect(dir.getOpposite());
                    neighborBundle.invalidateShape();
                    neighborBundle.setChanged();
                    neighborBundle.syncToClient();
                    // Sync the neighbor's node cache so graph state stays consistent
                    if (level instanceof ServerLevel serverLevel) {
                        ConduitNetworkManager.get(serverLevel)
                                .onConnectionsChanged(neighborPos, slot, neighborContainer);
                    }
                }
            }
        }
    }

    // ---- Shape ----

    /**
     * @return the dynamic VoxelShape for this bundle, based on active connections
     */
    public VoxelShape getDynamicShape() {
        if (cachedShape == null) {
            cachedShape = rebuildShape();
        }
        return cachedShape;
    }

    private void invalidateShape() {
        cachedShape = null;
    }

    private VoxelShape rebuildShape() {
        return ConduitShape.buildCombinedShape(getSortedSlots(), connections);
    }

    // ---- NBT Serialization ----

    private static final String TAG_CONDUITS = "Conduits";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_CONNECTION = "Connection";

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveConduitData(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadConduitData(tag, registries);
    }

    private void saveConduitData(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag conduitList = new ListTag();
        for (ConduitSlot slot : conduitSlots) {
            CompoundTag entry = new CompoundTag();
            entry.put(TAG_SLOT, slot.save());
            ConnectionContainer container = connections.get(slot);
            if (container != null) {
                entry.put(TAG_CONNECTION, container.save(registries));
            }
            conduitList.add(entry);
        }
        tag.put(TAG_CONDUITS, conduitList);
    }

    private void loadConduitData(CompoundTag tag, HolderLookup.Provider registries) {
        conduitSlots.clear();
        connections.clear();

        if (tag.contains(TAG_CONDUITS)) {
            ListTag conduitList = tag.getList(TAG_CONDUITS, Tag.TAG_COMPOUND);
            for (int i = 0; i < conduitList.size(); i++) {
                CompoundTag entry = conduitList.getCompound(i);
                ConduitSlot slot;
                if (entry.contains(TAG_SLOT)) {
                    // New format: slot compound with conduitId + channel
                    slot = ConduitSlot.load(entry.getCompound(TAG_SLOT));
                } else if (entry.contains("Id")) {
                    // Legacy format: just a conduit ID string, default channel 0
                    ResourceLocation conduitId = ResourceLocation.parse(entry.getString("Id"));
                    slot = new ConduitSlot(conduitId);
                } else {
                    continue; // Malformed entry, skip
                }
                conduitSlots.add(slot);

                if (entry.contains(TAG_CONNECTION)) {
                    connections.put(slot, ConnectionContainer.load(entry.getCompound(TAG_CONNECTION), registries));
                } else {
                    connections.put(slot, new ConnectionContainer());
                }
            }
        }

        invalidateShape();
    }

    // ---- Config Change Notification (called from ConduitMenu) ----

    /**
     * Called after the GUI changes a config value (transfer mode, priority, redstone, channel).
     * Re-syncs the node's cached config in the network manager so the ticker reads
     * the latest values, then notifies the client for visual updates.
     */
    public void notifyConfigChanged(ConduitSlot slot) {
        ConnectionContainer container = connections.get(slot);
        if (container == null) return;

        if (level instanceof ServerLevel serverLevel) {
            ConduitNetworkManager.get(serverLevel).onConnectionsChanged(getBlockPos(), slot, container);
        }
        invalidateShape();
        setChanged();
        syncToClient();
    }

    // ---- MenuProvider ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.omnio.conduit");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ConduitMenu(containerId, playerInv, this);
    }

    // ---- Client Sync ----

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveConduitData(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
