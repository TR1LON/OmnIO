package com.trilon.omnio.content.conduit.type.redstone;

import com.trilon.omnio.api.tier.BaseTier;
import com.trilon.omnio.api.tier.ITier;

/**
 * Tier definition for redstone conduits.
 * Redstone conduits have only a single tier — they propagate signals
 * with no loss or transformation.
 */
public enum RedstoneConduitTier implements ITier {

    STANDARD(BaseTier.BASIC);

    private final BaseTier baseTier;

    RedstoneConduitTier(BaseTier baseTier) {
        this.baseTier = baseTier;
    }

    @Override
    public BaseTier getBaseTier() {
        return baseTier;
    }
}
