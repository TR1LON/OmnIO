package com.trilon.omnio.content.conduit.type.energy;

import com.trilon.omnio.content.conduit.network.ConduitNetworkContext;

/**
 * Per-network energy buffer for energy conduit networks.
 * Stores a shared energy pool that the ticker extracts into and inserts from.
 *
 * <p>When networks merge, buffers are summed. When networks split,
 * the buffer is divided proportionally based on node count.</p>
 */
public class EnergyConduitNetworkContext extends ConduitNetworkContext {

    private long storedEnergy;
    private long capacity;

    public EnergyConduitNetworkContext(long capacity) {
        this.capacity = capacity;
        this.storedEnergy = 0;
    }

    public EnergyConduitNetworkContext(long capacity, long storedEnergy) {
        this.capacity = capacity;
        this.storedEnergy = Math.min(storedEnergy, capacity);
    }

    /**
     * @return the current energy stored in the network buffer
     */
    public long getStoredEnergy() {
        return storedEnergy;
    }

    /**
     * @return the maximum energy this network can buffer
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Set the buffer capacity. Clamps stored energy if it exceeds the new capacity.
     */
    public void setCapacity(long capacity) {
        this.capacity = capacity;
        if (storedEnergy > capacity) {
            storedEnergy = capacity;
        }
        markDirty();
    }

    /**
     * Add energy to the network buffer.
     *
     * @param amount the amount to add (must be non-negative)
     * @return the amount actually accepted
     */
    public long addEnergy(long amount) {
        if (amount <= 0) return 0;
        long space = capacity - storedEnergy;
        long accepted = Math.min(amount, space);
        storedEnergy += accepted;
        if (accepted > 0) markDirty();
        return accepted;
    }

    /**
     * Remove energy from the network buffer.
     *
     * @param amount the amount to remove (must be non-negative)
     * @return the amount actually removed
     */
    public long removeEnergy(long amount) {
        if (amount <= 0) return 0;
        long removed = Math.min(amount, storedEnergy);
        storedEnergy -= removed;
        if (removed > 0) markDirty();
        return removed;
    }

    // ---- Merge / Split ----

    @Override
    public void mergeFrom(ConduitNetworkContext other) {
        if (other instanceof EnergyConduitNetworkContext energyOther) {
            this.capacity += energyOther.capacity;
            this.storedEnergy = Math.min(this.storedEnergy + energyOther.storedEnergy, this.capacity);
        }
        markDirty();
    }

    @Override
    public ConduitNetworkContext split(double fraction) {
        long splitCapacity = Math.max(1, (long) (this.capacity * fraction));
        long splitEnergy = (long) (this.storedEnergy * fraction);

        this.capacity -= splitCapacity;
        this.storedEnergy -= splitEnergy;

        // Clamp: after split, capacity should never be zero or negative
        if (this.capacity <= 0) this.capacity = 1;
        if (this.storedEnergy < 0) this.storedEnergy = 0;
        if (this.storedEnergy > this.capacity) this.storedEnergy = this.capacity;

        markDirty();
        return new EnergyConduitNetworkContext(splitCapacity, splitEnergy);
    }

    @Override
    public String toString() {
        return "EnergyContext[" + storedEnergy + "/" + capacity + " FE]";
    }
}
