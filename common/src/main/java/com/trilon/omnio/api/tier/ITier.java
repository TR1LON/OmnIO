package com.trilon.omnio.api.tier;

/**
 * Marker interface for conduit-type-specific tier enums.
 * Each conduit type (energy, fluid, item) defines its own enum
 * implementing this interface with type-specific stats.
 */
public interface ITier {

    /**
     * @return the base tier level that this specific tier maps to
     */
    BaseTier getBaseTier();
}
