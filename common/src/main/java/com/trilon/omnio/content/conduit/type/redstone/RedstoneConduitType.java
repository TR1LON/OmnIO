package com.trilon.omnio.content.conduit.type.redstone;

import com.trilon.omnio.api.conduit.IConduitTicker;
import com.trilon.omnio.api.conduit.IConduitType;
import com.trilon.omnio.api.conduit.network.IConduitNetworkContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Conduit type implementation for redstone conduits.
 *
 * <p>Redstone conduits propagate vanilla redstone signals through a network
 * using 16 dye-color channels. Each EXTRACT endpoint reads the signal from
 * an adjacent block on its configured channel, and each INSERT endpoint
 * outputs the strongest signal on its configured channel to adjacent blocks.</p>
 *
 * <p>There is only a single tier — no upgrades needed for redstone.</p>
 */
public class RedstoneConduitType implements IConduitType<RedstoneConduitTier> {

    private final ResourceLocation id;
    private final RedstoneConduitTicker ticker;

    public RedstoneConduitType(ResourceLocation id) {
        this.id = id;
        this.ticker = new RedstoneConduitTicker();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public IConduitTicker getTicker() {
        return ticker;
    }

    @Override
    public int getTickRate() {
        return 2; // Redstone ticks every 2 game ticks (matches vanilla repeater behavior)
    }

    /**
     * Redstone conduits can connect to any non-air solid block that could
     * potentially produce or consume a redstone signal.
     */
    @Override
    public boolean canConnectToBlock(Level level, BlockPos conduitPos, Direction direction) {
        BlockPos neighborPos = conduitPos.relative(direction);
        BlockState state = level.getBlockState(neighborPos);
        // Connect to any solid block or known redstone components
        return !state.isAir() && (state.isRedstoneConductor(level, neighborPos)
                || state.isSignalSource()
                || state.hasAnalogOutputSignal());
    }

    /**
     * All redstone conduits are the same tier — always connectable.
     */
    @Override
    public boolean canConnectConduits(RedstoneConduitTier selfTier, RedstoneConduitTier otherTier) {
        return true;
    }

    @Override
    public IConduitNetworkContext<?> createNetworkContext() {
        return new RedstoneConduitNetworkContext();
    }

    @Override
    public String toString() {
        return "RedstoneConduitType[" + id + "]";
    }
}
