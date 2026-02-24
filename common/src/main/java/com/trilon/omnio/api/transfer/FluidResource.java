package com.trilon.omnio.api.transfer;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Lightweight, vanilla-safe fluid identity + amount pair.
 * Used in the common module instead of platform-specific FluidStack / FluidVariant.
 *
 * <p>Intentionally omits NBT / DataComponent data for simplicity.
 * This covers the vast majority of fluid transfer scenarios.  If component-aware
 * fluid handling is needed later, this record can be extended.</p>
 *
 * @param fluid  the fluid type (vanilla {@link Fluid})
 * @param amount the amount in platform-neutral millibuckets (mB).
 *               NeoForge uses mB natively; Fabric helpers convert from droplets.
 */
public record FluidResource(Fluid fluid, int amount) {

    /** Canonical empty resource. */
    public static final FluidResource EMPTY = new FluidResource(Fluids.EMPTY, 0);

    /**
     * @return {@code true} if this resource represents no fluid
     */
    public boolean isEmpty() {
        return fluid == Fluids.EMPTY || amount <= 0;
    }

    /**
     * Create a copy with a different amount.
     */
    public FluidResource withAmount(int newAmount) {
        return new FluidResource(fluid, newAmount);
    }

    @Override
    public String toString() {
        if (isEmpty()) return "FluidResource[EMPTY]";
        return "FluidResource[" + fluid + " x" + amount + " mB]";
    }
}
