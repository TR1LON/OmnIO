package com.trilon.omnio.content.conduit;

import com.trilon.omnio.Constants;
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
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The block entity for {@link OmniConduitBlock}.
 * Holds multiple conduit types in a single block ("bundle"), each with its own
 * per-direction connection container tracking status, transfer mode, priority,
 * redstone control, and filter slots.
 *
 * <p>Key data structures:</p>
 * <ul>
 *   <li>{@code conduitIds} — ordered list of conduit resource IDs present in this bundle</li>
 *   <li>{@code connections} — per-conduit-ID map of {@link ConnectionContainer}</li>
 * </ul>
 */
public class OmniConduitBlockEntity extends BlockEntity {

    // TODO: Replace with actual registered BlockEntityType from OmnIOBlockEntities
    private static BlockEntityType<OmniConduitBlockEntity> TYPE;

    /**
     * Ordered set of conduit IDs present in this bundle.
     * Each ID is a ResourceLocation like "omnio:energy_conduit_basic".
     * LinkedHashSet preserves insertion order and provides O(1) contains().
     */
    private final Set<ResourceLocation> conduitIds = new LinkedHashSet<>();

    /**
     * Per-conduit connection data. Key = conduit ResourceLocation ID.
     */
    private final Map<ResourceLocation, ConnectionContainer> connections = new LinkedHashMap<>();

    /**
     * Cached VoxelShape, rebuilt when connections change.
     */
    private VoxelShape cachedShape = null;

    public OmniConduitBlockEntity(BlockPos pos, BlockState state) {
        super(getType(), pos, state);
    }

    /**
     * Set the registered BlockEntityType. Called during mod init from the registry.
     */
    public static void setType(BlockEntityType<OmniConduitBlockEntity> type) {
        TYPE = type;
    }

    public static BlockEntityType<OmniConduitBlockEntity> getType() {
        if (TYPE == null) {
            throw new IllegalStateException("OmniConduitBlockEntity type not yet registered");
        }
        return TYPE;
    }

    /**
     * Called when the block entity is loaded into the world (chunk load, world load).
     * Registers all conduit nodes with the network manager to rebuild the graph.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel && !conduitIds.isEmpty()) {
            ConduitNetworkManager.get(serverLevel).onBlockEntityLoaded(this);
        }
    }

    // ---- Conduit Management ----

    /**
     * @return unmodifiable view of the conduit IDs in this bundle
     */
    public Set<ResourceLocation> getConduitIds() {
        return Collections.unmodifiableSet(conduitIds);
    }

    /**
     * @return the number of conduits in this bundle
     */
    public int getConduitCount() {
        return conduitIds.size();
    }

    /**
     * @param conduitId the conduit ID to check
     * @return true if this bundle contains the given conduit
     */
    public boolean hasConduit(ResourceLocation conduitId) {
        return conduitIds.contains(conduitId);
    }

    /**
     * Convenience overload accepting a string ID.
     */
    public boolean hasConduit(String conduitId) {
        return hasConduit(ResourceLocation.parse(conduitId));
    }

    /**
     * Add a conduit to this bundle.
     *
     * @param conduitId the ID of the conduit to add (e.g., "omnio:energy_conduit_basic")
     * @return true if the conduit was added, false if the bundle is full or already contains it
     */
    public boolean addConduit(ResourceLocation conduitId) {
        if (conduitIds.size() >= Constants.MAX_CONDUITS_PER_BUNDLE || conduitIds.contains(conduitId)) {
            return false;
        }

        // Reject adding a conduit of the same type-category but different tier
        String conduitType = extractConduitType(conduitId);
        for (ResourceLocation existing : conduitIds) {
            if (extractConduitType(existing).equals(conduitType)) {
                return false; // Same type already present (different tier) — use break-and-replace
            }
        }

        conduitIds.add(conduitId);
        ConnectionContainer container = new ConnectionContainer();
        connections.put(conduitId, container);

        // Evaluate connections to neighbors for the new conduit
        if (level != null && !level.isClientSide()) {
            evaluateConnections(conduitId);
            // Notify the network manager to create/merge networks
            if (level instanceof ServerLevel serverLevel) {
                ConduitNetworkManager.get(serverLevel).onConduitAdded(getBlockPos(), conduitId, container);
            }
        }

        invalidateShape();
        setChanged();
        syncToClient();
        return true;
    }

    /**
     * Convenience overload accepting a string ID.
     */
    public boolean addConduit(String conduitId) {
        return addConduit(ResourceLocation.parse(conduitId));
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
            if (suffix.equals("basic") || suffix.equals("advanced") || suffix.equals("elite") || suffix.equals("ultimate")) {
                return path.substring(0, lastUnderscore);
            }
        }
        return path;
    }

    /**
     * Remove a conduit from this bundle.
     *
     * @param conduitId the ID of the conduit to remove
     * @return true if the conduit was removed
     */
    public boolean removeConduit(ResourceLocation conduitId) {
        if (!conduitIds.remove(conduitId)) {
            return false;
        }
        connections.remove(conduitId);

        // Disconnect neighbor bundles' connections for this conduit type
        if (level != null && !level.isClientSide()) {
            disconnectNeighborsFor(conduitId);
        }

        // Notify the network manager to handle potential splits
        if (level instanceof ServerLevel serverLevel) {
            ConduitNetworkManager.get(serverLevel).onConduitRemoved(getBlockPos(), conduitId);
        }

        invalidateShape();
        setChanged();
        syncToClient();

        // If no conduits remain, remove the block entirely
        if (conduitIds.isEmpty() && level != null) {
            level.removeBlock(getBlockPos(), false);
        }

        return true;
    }

    /**
     * @param conduitId the conduit to query
     * @return the connection container for the given conduit, or null if not present
     */
    @Nullable
    public ConnectionContainer getConnectionContainer(ResourceLocation conduitId) {
        return connections.get(conduitId);
    }

    // ---- Connection Evaluation ----

    /**
     * Evaluate all 6 neighbor positions for a specific conduit type to determine
     * connection status (conduit-to-conduit, conduit-to-block, or disconnected).
     */
    private void evaluateConnections(ResourceLocation conduitId) {
        if (level == null) return;

        ConnectionContainer container = connections.get(conduitId);
        if (container == null) return;

        for (Direction dir : Direction.values()) {
            evaluateConnection(conduitId, container, dir);
        }
    }

    /**
     * Evaluate a single direction for a specific conduit.
     *
     * @return true if the connection status changed (caller should notify network manager)
     */
    private boolean evaluateConnection(ResourceLocation conduitId, ConnectionContainer container, Direction dir) {
        if (level == null) return false;

        BlockPos neighborPos = getBlockPos().relative(dir);
        BlockEntity neighborBE = level.getBlockEntity(neighborPos);

        if (neighborBE instanceof OmniConduitBlockEntity neighborBundle) {
            // Check if neighbor has the same conduit type → conduit-to-conduit
            if (neighborBundle.hasConduit(conduitId)) {
                ConnectionStatus oldStatus = container.getStatus(dir);
                container.setConfig(dir, ConnectionConfig.conduitConnection());

                // Also set the reverse connection on the neighbor
                ConnectionContainer neighborContainer = neighborBundle.getConnectionContainer(conduitId);
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
        IConduitType<?> conduitType = ConduitTypeRegistry.getOrStub(conduitId);
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
     * Re-evaluates connections for all conduits on the affected side.
     */
    public void onNeighborChanged(Direction direction, BlockState neighborState, BlockPos neighborPos) {
        boolean anyChanged = false;
        for (ResourceLocation conduitId : conduitIds) {
            ConnectionContainer container = connections.get(conduitId);
            if (container != null) {
                boolean changed = evaluateConnection(conduitId, container, direction);
                if (changed) {
                    anyChanged = true;
                    // Only notify network manager if connection actually changed — avoids unnecessary BFS
                    if (level instanceof ServerLevel serverLevel) {
                        ConduitNetworkManager.get(serverLevel).onConnectionsChanged(getBlockPos(), conduitId, container);
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

        // Disconnect neighbors for all conduit types in this bundle
        for (ResourceLocation conduitId : conduitIds) {
            disconnectNeighborsFor(conduitId);
        }

        // Notify network manager that all conduit nodes at this position are removed
        if (level instanceof ServerLevel serverLevel) {
            ConduitNetworkManager manager = ConduitNetworkManager.get(serverLevel);
            for (ResourceLocation conduitId : conduitIds) {
                manager.onConduitRemoved(getBlockPos(), conduitId);
            }
        }
    }

    /**
     * Notify all 6 neighboring bundles to disconnect their connection for a specific conduit type.
     * Called when removing a single conduit or when the block is destroyed.
     */
    private void disconnectNeighborsFor(ResourceLocation conduitId) {
        if (level == null) return;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = getBlockPos().relative(dir);
            BlockEntity neighborBE = level.getBlockEntity(neighborPos);
            if (neighborBE instanceof OmniConduitBlockEntity neighborBundle) {
                ConnectionContainer neighborContainer = neighborBundle.getConnectionContainer(conduitId);
                if (neighborContainer != null) {
                    neighborContainer.disconnect(dir.getOpposite());
                    neighborBundle.invalidateShape();
                    neighborBundle.setChanged();
                    neighborBundle.syncToClient();
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
        Set<Direction> connectedDirs = EnumSet.noneOf(Direction.class);
        for (ConnectionContainer container : connections.values()) {
            for (Direction dir : Direction.values()) {
                if (container.isConnected(dir)) {
                    connectedDirs.add(dir);
                }
            }
        }
        return OmniConduitBlock.buildShape(connectedDirs);
    }

    // ---- NBT Serialization ----

    private static final String TAG_CONDUITS = "Conduits";
    private static final String TAG_CONDUIT_ID = "Id";
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
        for (ResourceLocation conduitId : conduitIds) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_CONDUIT_ID, conduitId.toString());
            ConnectionContainer container = connections.get(conduitId);
            if (container != null) {
                entry.put(TAG_CONNECTION, container.save(registries));
            }
            conduitList.add(entry);
        }
        tag.put(TAG_CONDUITS, conduitList);
    }

    private void loadConduitData(CompoundTag tag, HolderLookup.Provider registries) {
        conduitIds.clear();
        connections.clear();

        if (tag.contains(TAG_CONDUITS)) {
            ListTag conduitList = tag.getList(TAG_CONDUITS, Tag.TAG_COMPOUND);
            for (int i = 0; i < conduitList.size(); i++) {
                CompoundTag entry = conduitList.getCompound(i);
                ResourceLocation conduitId = ResourceLocation.parse(entry.getString(TAG_CONDUIT_ID));
                conduitIds.add(conduitId);

                if (entry.contains(TAG_CONNECTION)) {
                    connections.put(conduitId, ConnectionContainer.load(entry.getCompound(TAG_CONNECTION), registries));
                } else {
                    connections.put(conduitId, new ConnectionContainer());
                }
            }
        }

        invalidateShape();
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
