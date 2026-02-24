package com.trilon.omnio.content.conduit.type.fluid;

import org.jetbrains.annotations.Nullable;

import com.trilon.omnio.Constants;
import com.trilon.omnio.content.conduit.network.ConduitNetworkContext;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Per-network fluid buffer for fluid conduit networks.
 * Stores a shared fluid pool that the ticker extracts into and inserts from.
 *
 * <p>Fluid locking: the first fluid that enters the network "locks" it to
 * that fluid type. The lock persists until the buffer is empty AND the lock
 * is explicitly cleared (e.g., via GUI). While locked, only the locked fluid
 * type can enter the network.</p>
 *
 * <p>When networks merge, if both have stored fluid of the same type, amounts
 * are summed. If different fluids, the primary network's fluid takes priority
 * and the secondary's stored fluid is lost (with a capacity merge).
 * When networks split, the buffer is divided proportionally.</p>
 */
public class FluidConduitNetworkContext extends ConduitNetworkContext {

    private Fluid storedFluid;
    private int storedAmount;
    private int capacity;

    /**
     * If non-null, only this fluid type may enter the network.
     * Set automatically when the first fluid enters, or manually by the player.
     * Cleared manually or when buffer empties and {@code manualLock} is false.
     */
    @Nullable
    private Fluid lockedFluid;

    /**
     * If true, the fluid lock was set manually by the player and persists
     * even when the buffer empties.
     */
    private boolean manualLock;

    public FluidConduitNetworkContext(int capacity) {
        this.capacity = capacity;
        this.storedFluid = Fluids.EMPTY;
        this.storedAmount = 0;
        this.lockedFluid = null;
        this.manualLock = false;
    }

    public FluidConduitNetworkContext(int capacity, Fluid storedFluid, int storedAmount,
                                      @Nullable Fluid lockedFluid, boolean manualLock) {
        this.capacity = capacity;
        this.storedFluid = storedFluid;
        this.storedAmount = Math.min(storedAmount, capacity);
        this.lockedFluid = lockedFluid;
        this.manualLock = manualLock;
    }

    // ---- Getters ----

    public Fluid getStoredFluid() {
        return storedFluid;
    }

    public int getStoredAmount() {
        return storedAmount;
    }

    public int getCapacity() {
        return capacity;
    }

    @Nullable
    public Fluid getLockedFluid() {
        return lockedFluid;
    }

    public boolean isManualLock() {
        return manualLock;
    }

    /**
     * @return the fluid type that this network will accept.
     *         Returns the locked fluid if set, or the currently stored fluid,
     *         or null if the network is empty and unlocked (accepts any).
     */
    @Nullable
    public Fluid getAcceptedFluid() {
        if (lockedFluid != null) return lockedFluid;
        if (storedFluid != Fluids.EMPTY) return storedFluid;
        return null; // Accepts any
    }

    // ---- Capacity ----

    public void setCapacity(int capacity) {
        this.capacity = capacity;
        if (storedAmount > capacity) {
            storedAmount = capacity;
        }
        markDirty();
    }

    // ---- Fluid operations ----

    /**
     * Add fluid to the network buffer.
     *
     * @param fluid  the fluid type
     * @param amount the amount in mB (must be positive)
     * @return the amount actually accepted
     */
    public int addFluid(Fluid fluid, int amount) {
        if (amount <= 0 || fluid == Fluids.EMPTY) return 0;

        // Check fluid compatibility
        Fluid accepted = getAcceptedFluid();
        if (accepted != null && accepted != fluid) return 0;

        // If buffer is empty, set the stored fluid type
        if (storedFluid == Fluids.EMPTY) {
            storedFluid = fluid;
            // Auto-lock to this fluid type
            if (lockedFluid == null) {
                lockedFluid = fluid;
            }
        }

        int space = capacity - storedAmount;
        int added = Math.min(amount, space);
        storedAmount += added;
        if (added > 0) markDirty();
        return added;
    }

    /**
     * Remove fluid from the network buffer.
     *
     * @param amount the amount in mB (must be positive)
     * @return the amount actually removed
     */
    public int removeFluid(int amount) {
        if (amount <= 0) return 0;
        int removed = Math.min(amount, storedAmount);
        storedAmount -= removed;

        // If buffer is now empty, clear the stored fluid type
        if (storedAmount <= 0) {
            storedAmount = 0;
            storedFluid = Fluids.EMPTY;
            // Clear auto-lock if not manually locked
            if (!manualLock) {
                lockedFluid = null;
            }
        }

        if (removed > 0) markDirty();
        return removed;
    }

    /**
     * Set a manual fluid lock.
     */
    public void setManualLock(@Nullable Fluid fluid) {
        this.lockedFluid = fluid;
        this.manualLock = fluid != null;
        markDirty();
    }

    /**
     * Clear the fluid lock (both manual and auto).
     */
    public void clearLock() {
        this.lockedFluid = null;
        this.manualLock = false;
        markDirty();
    }

    // ---- Merge / Split ----

    @Override
    public void mergeFrom(ConduitNetworkContext other) {
        if (other instanceof FluidConduitNetworkContext fluidOther) {
            this.capacity += fluidOther.capacity;

            if (this.storedFluid == Fluids.EMPTY && fluidOther.storedFluid != Fluids.EMPTY) {
                // This is empty, take the other's fluid
                this.storedFluid = fluidOther.storedFluid;
                this.storedAmount = Math.min(fluidOther.storedAmount, this.capacity);
            } else if (this.storedFluid == fluidOther.storedFluid) {
                // Same fluid, combine amounts
                this.storedAmount = (int) Math.min(
                        (long) this.storedAmount + fluidOther.storedAmount, this.capacity);
            }
            // Different fluids: the other's fluid is lost (primary takes priority)
            if (this.storedFluid != Fluids.EMPTY && fluidOther.storedFluid != Fluids.EMPTY
                    && this.storedFluid != fluidOther.storedFluid && fluidOther.storedAmount > 0) {
                Constants.LOG.warn("Fluid network merge: {} mB of {} voided (primary contains {})",
                        fluidOther.storedAmount, fluidOther.storedFluid, this.storedFluid);
            }

            // Merge locks: if either has a manual lock, prefer the non-empty lock
            if (fluidOther.manualLock && !this.manualLock) {
                this.lockedFluid = fluidOther.lockedFluid;
                this.manualLock = true;
            } else if (this.lockedFluid == null && fluidOther.lockedFluid != null) {
                this.lockedFluid = fluidOther.lockedFluid;
            }
        }
        markDirty();
    }

    @Override
    public ConduitNetworkContext split(double fraction) {
        int splitCapacity = Math.max(1, (int) (this.capacity * fraction));
        int splitAmount = (int) (this.storedAmount * fraction);

        this.capacity -= splitCapacity;
        this.storedAmount -= splitAmount;

        if (this.capacity <= 0) this.capacity = 1;
        if (this.storedAmount < 0) this.storedAmount = 0;
        if (this.storedAmount > this.capacity) this.storedAmount = this.capacity;

        // Both halves keep the same fluid type and lock
        Fluid splitFluid = splitAmount > 0 ? this.storedFluid : Fluids.EMPTY;

        // If this half is now empty, clear its fluid
        if (this.storedAmount <= 0) {
            this.storedFluid = Fluids.EMPTY;
            if (!manualLock) {
                this.lockedFluid = null;
            }
        }

        markDirty();
        return new FluidConduitNetworkContext(splitCapacity, splitFluid, splitAmount,
                this.lockedFluid, this.manualLock);
    }

    @Override
    public String toString() {
        String fluidName = storedFluid == Fluids.EMPTY ? "EMPTY" : storedFluid.toString();
        return "FluidContext[" + fluidName + " " + storedAmount + "/" + capacity + " mB]";
    }
}
