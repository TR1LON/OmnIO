package com.trilon.omnio.content.conduit.network;

import com.trilon.omnio.api.conduit.network.IConduitNetworkContext;

import net.minecraft.nbt.CompoundTag;

/**
 * Default implementation of {@link IConduitNetworkContext}.
 * Provides a no-op context for conduit types that don't need per-network state
 * (e.g., basic item conduits), and serves as a base class for type-specific
 * contexts (energy buffer, locked fluid, etc.).
 *
 * <p>Subclasses should override {@link #mergeFrom(ConduitNetworkContext)} and
 * {@link #split(double)} to handle their specific state.</p>
 */
public class ConduitNetworkContext implements IConduitNetworkContext<ConduitNetworkContext> {

    private boolean dirty = false;

    @Override
    public void mergeFrom(ConduitNetworkContext other) {
        // Default: nothing to merge. Subclasses override for energy pools, etc.
        markDirty();
    }

    @Override
    public ConduitNetworkContext split(double fraction) {
        // Default: return a fresh empty context. Subclasses override to split state.
        ConduitNetworkContext newContext = new ConduitNetworkContext();
        markDirty();
        return newContext;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void clearDirty() {
        dirty = false;
    }

    /**
     * Mark this context as modified since last save.
     */
    protected void markDirty() {
        dirty = true;
    }

    // ---- NBT Persistence ----

    /**
     * Serialize this context's state to an NBT compound tag.
     * Subclasses should override to save their specific fields.
     *
     * @return the serialized state (empty tag for base context)
     */
    public CompoundTag saveToTag() {
        return new CompoundTag();
    }

    /**
     * Deserialize this context's state from an NBT compound tag.
     * Subclasses should override to load their specific fields.
     *
     * @param tag the tag to read from
     */
    public void loadFromTag(CompoundTag tag) {
        // Default: nothing to load
    }
}
