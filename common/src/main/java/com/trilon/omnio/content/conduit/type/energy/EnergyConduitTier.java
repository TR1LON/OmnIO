package com.trilon.omnio.content.conduit.type.energy;

import com.trilon.omnio.api.tier.BaseTier;
import com.trilon.omnio.api.tier.ITier;

/**
 * Tier definitions for energy conduits.
 * Capacity = internal buffer size of the network per conduit node.
 * Transfer rate = max FE moved per connection per tick.
 */
public enum EnergyConduitTier implements ITier {

    BASIC(BaseTier.BASIC, 8_000L, 2_000L),
    ADVANCED(BaseTier.ADVANCED, 128_000L, 16_000L),
    ELITE(BaseTier.ELITE, 1_024_000L, 128_000L),
    ULTIMATE(BaseTier.ULTIMATE, 8_192_000L, 1_024_000L),
    CREATIVE(BaseTier.CREATIVE, Long.MAX_VALUE, Long.MAX_VALUE);

    private final BaseTier baseTier;
    private final long baseCapacity;
    private final long baseTransferRate;

    EnergyConduitTier(BaseTier baseTier, long baseCapacity, long baseTransferRate) {
        this.baseTier = baseTier;
        this.baseCapacity = baseCapacity;
        this.baseTransferRate = baseTransferRate;
    }

    @Override
    public BaseTier getBaseTier() {
        return baseTier;
    }

    /**
     * @return the base buffer capacity in FE (configurable at runtime)
     */
    public long getCapacity() {
        // TODO: return config-overridden value when TierConfig is implemented
        return baseCapacity;
    }

    /**
     * @return the base transfer rate in FE/tick (configurable at runtime)
     */
    public long getTransferRate() {
        // TODO: return config-overridden value when TierConfig is implemented
        return baseTransferRate;
    }
}
