package com.trilon.omnio.content.conduit.type.fluid;

import com.trilon.omnio.api.conduit.IConduitTicker;
import com.trilon.omnio.api.conduit.IConduitType;
import com.trilon.omnio.api.conduit.network.IConduitNetworkContext;
import com.trilon.omnio.api.transfer.IFluidTransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Conduit type implementation for fluid conduits.
 * Each tier creates its own {@code FluidConduitType} instance with
 * tier-specific transfer rates and buffer capacities.
 *
 * <p>Connections to blocks are determined by the platform-specific
 * {@link IFluidTransferHelper} (checks for IFluidHandler on NeoForge,
 * FluidStorage on Fabric).</p>
 */
public class FluidConduitType implements IConduitType<FluidConduitTier> {

    private final ResourceLocation id;
    private final FluidConduitTier tier;
    private final FluidConduitTicker ticker;
    private final IFluidTransferHelper transferHelper;

    public FluidConduitType(ResourceLocation id, FluidConduitTier tier, IFluidTransferHelper transferHelper) {
        this.id = id;
        this.tier = tier;
        this.transferHelper = transferHelper;
        this.ticker = new FluidConduitTicker(tier, transferHelper);
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
        return 2; // Fluid conduits tick every 2 game ticks (10 operations/second)
    }

    @Override
    public boolean canConnectToBlock(Level level, BlockPos conduitPos, Direction direction) {
        BlockPos neighborPos = conduitPos.relative(direction);
        Direction accessFace = direction.getOpposite();
        return transferHelper.hasHandler(level, neighborPos, accessFace);
    }

    @Override
    public boolean canConnectConduits(FluidConduitTier selfTier, FluidConduitTier otherTier) {
        return selfTier == otherTier; // Same tier only
    }

    @Override
    public IConduitNetworkContext<?> createNetworkContext() {
        return new FluidConduitNetworkContext(tier.getCapacity());
    }

    @Override
    public void recalculateContext(IConduitNetworkContext<?> context, int nodeCount) {
        if (context instanceof FluidConduitNetworkContext ctx) {
            ctx.setCapacity(calculateNetworkCapacity(nodeCount));
        }
    }

    public FluidConduitTier getTier() {
        return tier;
    }

    public IFluidTransferHelper getTransferHelper() {
        return transferHelper;
    }

    /**
     * Calculate the network buffer capacity for a given number of nodes.
     * Each node contributes the tier's base capacity to the pool.
     */
    public int calculateNetworkCapacity(int nodeCount) {
        int baseCap = tier.getCapacity();
        if (baseCap == Integer.MAX_VALUE || nodeCount <= 0) return baseCap;
        // Guard against int overflow
        long result = (long) baseCap * nodeCount;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    @Override
    public String toString() {
        return "FluidConduitType[" + id + ", tier=" + tier.name() + "]";
    }
}
