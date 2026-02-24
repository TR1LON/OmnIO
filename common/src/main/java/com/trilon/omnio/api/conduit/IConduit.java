package com.trilon.omnio.api.conduit;

import com.trilon.omnio.api.tier.ITier;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents an individual conduit instance within a bundle block.
 * Each conduit has a type (energy, fluid, etc.) and a tier (basic, advanced, etc.).
 *
 * @param <T> the tier enum type for this conduit
 */
public interface IConduit<T extends ITier> {

    /**
     * @return the conduit type this conduit belongs to
     */
    IConduitType<T> getType();

    /**
     * @return the tier of this specific conduit instance
     */
    T getTier();

    /**
     * @return the registry ID that uniquely identifies this conduit variant (type + tier)
     */
    ResourceLocation getId();

    /**
     * Checks if this conduit can replace another conduit in the same bundle slot.
     * Typically used for tier upgrades: a higher-tier conduit can replace a lower-tier one.
     *
     * @param other the conduit that would be replaced
     * @return true if this conduit can replace the other
     */
    default boolean canReplace(IConduit<?> other) {
        return false;
    }
}
