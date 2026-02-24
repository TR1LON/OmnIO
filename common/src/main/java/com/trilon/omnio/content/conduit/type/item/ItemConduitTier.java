package com.trilon.omnio.content.conduit.type.item;

import com.trilon.omnio.api.tier.BaseTier;
import com.trilon.omnio.api.tier.ITier;

/**
 * Tier definitions for item conduits.
 * Stack size = max items moved per extraction operation.
 * Speed = ticks between each extraction cycle (lower = faster).
 */
public enum ItemConduitTier implements ITier {

    BASIC(BaseTier.BASIC, 1, 20),
    ADVANCED(BaseTier.ADVANCED, 8, 15),
    ELITE(BaseTier.ELITE, 32, 10),
    ULTIMATE(BaseTier.ULTIMATE, 64, 5),
    CREATIVE(BaseTier.CREATIVE, 64, 1);

    private final BaseTier baseTier;
    private final int baseStackSize;
    private final int baseSpeed;

    ItemConduitTier(BaseTier baseTier, int baseStackSize, int baseSpeed) {
        this.baseTier = baseTier;
        this.baseStackSize = baseStackSize;
        this.baseSpeed = baseSpeed;
    }

    @Override
    public BaseTier getBaseTier() {
        return baseTier;
    }

    /**
     * @return the max items extracted per operation
     */
    public int getStackSize() {
        return baseStackSize;
    }

    /**
     * @return the number of ticks between extraction cycles
     */
    public int getSpeed() {
        return baseSpeed;
    }
}
