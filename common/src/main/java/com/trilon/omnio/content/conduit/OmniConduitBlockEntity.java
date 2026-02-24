package com.trilon.omnio.content.conduit;

import com.trilon.omnio.Constants;
import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.api.conduit.IConduit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
     * Ordered list of conduit IDs present in this bundle.
     * Each ID is a ResourceLocation string like "omnio:energy_basic", "omnio:fluid_advanced", etc.
     */
    private final List<String> conduitIds = new ArrayList<>();

    /**
     * Per-conduit connection data. Key = conduit ID string.
     */
    private final Map<String, ConnectionContainer> connections = new LinkedHashMap<>();

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

    // ---- Conduit Management ----

    /**
     * @return unmodifiable view of the conduit IDs in this bundle
     */
    public List<String> getConduitIds() {
        return Collections.unmodifiableList(conduitIds);
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
    public boolean hasConduit(String conduitId) {
        return conduitIds.contains(conduitId);
    }

    /**
     * Add a conduit to this bundle.
     *
     * @param conduitId the ID of the conduit to add (e.g., "omnio:energy_basic")
     * @return true if the conduit was added, false if the bundle is full or already contains it
     */
    public boolean addConduit(String conduitId) {
        if (conduitIds.size() >= Constants.MAX_CONDUITS_PER_BUNDLE || conduitIds.contains(conduitId)) {
            return false;
        }

        conduitIds.add(conduitId);
        connections.put(conduitId, new ConnectionContainer());

        // Evaluate connections to neighbors for the new conduit
        if (level != null && !level.isClientSide()) {
            evaluateConnections(conduitId);
        }

        invalidateShape();
        setChanged();
        syncToClient();
        return true;
    }

    /**
     * Remove a conduit from this bundle.
     *
     * @param conduitId the ID of the conduit to remove
     * @return true if the conduit was removed
     */
    public boolean removeConduit(String conduitId) {
        if (!conduitIds.remove(conduitId)) {
            return false;
        }
        connections.remove(conduitId);

        // TODO: Notify the conduit's network that this node was removed

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
    public ConnectionContainer getConnectionContainer(String conduitId) {
        return connections.get(conduitId);
    }

    // ---- Connection Evaluation ----

    /**
     * Evaluate all 6 neighbor positions for a specific conduit type to determine
     * connection status (conduit-to-conduit, conduit-to-block, or disconnected).
     */
    private void evaluateConnections(String conduitId) {
        if (level == null) return;

        ConnectionContainer container = connections.get(conduitId);
        if (container == null) return;

        for (Direction dir : Direction.values()) {
            evaluateConnection(conduitId, container, dir);
        }
    }

    /**
     * Evaluate a single direction for a specific conduit.
     */
    private void evaluateConnection(String conduitId, ConnectionContainer container, Direction dir) {
        if (level == null) return;

        BlockPos neighborPos = getBlockPos().relative(dir);
        BlockEntity neighborBE = level.getBlockEntity(neighborPos);

        if (neighborBE instanceof OmniConduitBlockEntity neighborBundle) {
            // Check if neighbor has the same conduit type → conduit-to-conduit
            if (neighborBundle.hasConduit(conduitId)) {
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
                return;
            }
        }

        // TODO: Check if neighbor block has a compatible capability for this conduit type
        // (IEnergyStorage for energy, IFluidHandler for fluid, IItemHandler for items)
        // If so, set CONNECTED_BLOCK. For now, leave as DISCONNECTED.
    }

    /**
     * Called when a neighboring block changes.
     * Re-evaluates connections for all conduits on the affected side.
     */
    public void onNeighborChanged(Direction direction, BlockState neighborState, BlockPos neighborPos) {
        for (String conduitId : conduitIds) {
            ConnectionContainer container = connections.get(conduitId);
            if (container != null) {
                evaluateConnection(conduitId, container, direction);
            }
        }
        invalidateShape();
        setChanged();
        syncToClient();
    }

    /**
     * Called when this block is being removed. Notify all neighbor bundles
     * to disconnect their connections to this position.
     */
    public void onBlockRemoved() {
        if (level == null || level.isClientSide()) return;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = getBlockPos().relative(dir);
            BlockEntity neighborBE = level.getBlockEntity(neighborPos);
            if (neighborBE instanceof OmniConduitBlockEntity neighborBundle) {
                Direction opposite = dir.getOpposite();
                for (String conduitId : conduitIds) {
                    ConnectionContainer neighborContainer = neighborBundle.getConnectionContainer(conduitId);
                    if (neighborContainer != null) {
                        neighborContainer.disconnect(opposite);
                    }
                }
                neighborBundle.invalidateShape();
                neighborBundle.setChanged();
                neighborBundle.syncToClient();
            }
        }

        // TODO: Notify networks that all conduit nodes at this position are removed
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
        saveConduitData(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadConduitData(tag);
    }

    private void saveConduitData(CompoundTag tag) {
        ListTag conduitList = new ListTag();
        for (String conduitId : conduitIds) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_CONDUIT_ID, conduitId);
            ConnectionContainer container = connections.get(conduitId);
            if (container != null) {
                entry.put(TAG_CONNECTION, container.save());
            }
            conduitList.add(entry);
        }
        tag.put(TAG_CONDUITS, conduitList);
    }

    private void loadConduitData(CompoundTag tag) {
        conduitIds.clear();
        connections.clear();

        if (tag.contains(TAG_CONDUITS)) {
            ListTag conduitList = tag.getList(TAG_CONDUITS, Tag.TAG_COMPOUND);
            for (int i = 0; i < conduitList.size(); i++) {
                CompoundTag entry = conduitList.getCompound(i);
                String conduitId = entry.getString(TAG_CONDUIT_ID);
                conduitIds.add(conduitId);

                if (entry.contains(TAG_CONNECTION)) {
                    connections.put(conduitId, ConnectionContainer.load(entry.getCompound(TAG_CONNECTION)));
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
        saveConduitData(tag);
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
