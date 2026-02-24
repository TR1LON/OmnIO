package com.trilon.omnio.content.conduit.type.fluid;

import com.trilon.omnio.api.tier.BaseTier;
import com.trilon.omnio.api.tier.ITier;

/**
 * Tier definitions for fluid conduits.
 * Capacity = internal buffer size in mB.
 * Transfer rate = max mB moved per connection per tick.
 */
public enum FluidConduitTier implements ITier {

    BASIC(BaseTier.BASIC, 2_000, 500),
    ADVANCED(BaseTier.ADVANCED, 8_000, 2_000),
    ELITE(BaseTier.ELITE, 32_000, 8_000),
    ULTIMATE(BaseTier.ULTIMATE, 128_000, 32_000),
    CREATIVE(BaseTier.CREATIVE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final BaseTier baseTier;
    private final int baseCapacity;
    private final int baseTransferRate;

    FluidConduitTier(BaseTier baseTier, int baseCapacity, int baseTransferRate) {
        this.baseTier = baseTier;
        this.baseCapacity = baseCapacity;
        this.baseTransferRate = baseTransferRate;
    }

    @Override
    public BaseTier getBaseTier() {
        return baseTier;
    }

    /**
     * @return the base buffer capacity in mB
     */
    public int getCapacity() {
        return baseCapacity;
    }

    /**
     * @return the base transfer rate in mB/tick
     */
    public int getTransferRate() {
        return baseTransferRate;
    }
}
