package com.trilon.omnio.content.conduit.type.energy;

import com.trilon.omnio.Constants;
import com.trilon.omnio.api.conduit.IConduitTicker;
import com.trilon.omnio.api.conduit.IConduitType;
import com.trilon.omnio.api.transfer.ITransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Conduit type implementation for energy conduits.
 * Each tier creates its own {@code EnergyConduitType} instance with
 * tier-specific transfer rates and capacities.
 *
 * <p>Connections to blocks are determined by the platform-specific
 * {@link ITransferHelper} (checks for IEnergyStorage on NeoForge,
 * EnergyStorage on Fabric).</p>
 */
public class EnergyConduitType implements IConduitType<EnergyConduitTier> {

    private final ResourceLocation id;
    private final EnergyConduitTier tier;
    private final EnergyConduitTicker ticker;
    private final ITransferHelper<Long> transferHelper;

    public EnergyConduitType(ResourceLocation id, EnergyConduitTier tier, ITransferHelper<Long> transferHelper) {
        this.id = id;
        this.tier = tier;
        this.transferHelper = transferHelper;
        this.ticker = new EnergyConduitTicker(tier, transferHelper);
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
        return 1; // Energy conduits tick every game tick for smooth transfer
    }

    /**
     * Checks if the neighboring block exposes an energy handler
     * (IEnergyStorage on NeoForge, EnergyStorage on Fabric).
     */
    @Override
    public boolean canConnectToBlock(Level level, BlockPos conduitPos, Direction direction) {
        BlockPos neighborPos = conduitPos.relative(direction);
        Direction accessFace = direction.getOpposite();
        return transferHelper.hasHandler(level, neighborPos, accessFace);
    }

    /**
     * Energy conduits of the same tier can always connect to each other.
     * Different tiers cannot connect (must break-and-replace to upgrade).
     */
    @Override
    public boolean canConnectConduits(EnergyConduitTier selfTier, EnergyConduitTier otherTier) {
        return selfTier == otherTier;
    }

    /**
     * @return the tier for this energy conduit type
     */
    public EnergyConduitTier getTier() {
        return tier;
    }

    /**
     * @return the transfer helper used for energy operations
     */
    public ITransferHelper<Long> getTransferHelper() {
        return transferHelper;
    }

    /**
     * Calculate the network buffer capacity for a given number of nodes.
     * Each node contributes the tier's base capacity to the pool.
     */
    public long calculateNetworkCapacity(int nodeCount) {
        long baseCap = tier.getCapacity();
        if (baseCap == Long.MAX_VALUE || nodeCount <= 0) return baseCap;
        // Guard against long overflow: if multiplication would exceed Long.MAX_VALUE, cap it
        if (nodeCount > Long.MAX_VALUE / baseCap) return Long.MAX_VALUE;
        return baseCap * nodeCount;
    }

    @Override
    public String toString() {
        return "EnergyConduitType[" + id + ", tier=" + tier.name() + "]";
    }
}
