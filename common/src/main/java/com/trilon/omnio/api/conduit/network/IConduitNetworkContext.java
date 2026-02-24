package com.trilon.omnio.api.conduit.network;

/**
 * Per-network mutable state for a specific conduit type.
 * Each network maintains its own context instance that tracks
 * shared resources (energy buffer, locked fluid, signal strengths, etc.).
 *
 * <p>Context implementations must support merging (when two networks join)
 * and splitting (when a network breaks into parts).</p>
 *
 * @param <T> the self-type for fluent merge/split APIs
 */
public interface IConduitNetworkContext<T extends IConduitNetworkContext<T>> {

    /**
     * Merge another context into this one. Called when two networks join.
     * This context should absorb all state from the other.
     *
     * @param other the context to merge into this one
     */
    void mergeFrom(T other);

    /**
     * Create a new context representing a fraction of this network's state.
     * Called when a network splits; the fraction represents the proportion
     * of nodes going to the new network.
     *
     * @param fraction the proportion of the network being split off (0.0 to 1.0)
     * @return a new context with a proportional share of this context's state
     */
    T split(double fraction);

    /**
     * @return true if this context has been modified since last save
     */
    boolean isDirty();

    /**
     * Mark this context as clean (just saved).
     */
    void clearDirty();
}
