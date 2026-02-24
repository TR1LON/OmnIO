package com.trilon.omnio.content.conduit.network;

import com.trilon.omnio.api.conduit.IConduitTicker;
import com.trilon.omnio.api.conduit.IConduitType;
import com.trilon.omnio.api.conduit.network.IConduitNetwork;
import com.trilon.omnio.api.tier.ITier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Temporary stub conduit type used by the network manager before the
 * conduit type registry is fully wired in Phase 5+.
 * Returns a no-op ticker and allows all connections.
 *
 * <p>This will be replaced with actual IConduitType lookups from
 * the registry once energy/fluid/item conduit types are implemented.</p>
 */
class StubConduitType implements IConduitType<ITier> {

    private final ResourceLocation id;

    private static final IConduitTicker NO_OP_TICKER = (level, network) -> {
        // No-op: actual transfer logic will be implemented per conduit type
    };

    StubConduitType(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public IConduitTicker getTicker() {
        return NO_OP_TICKER;
    }

    @Override
    public int getTickRate() {
        return 20; // Default: tick once per second
    }

    @Override
    public boolean canConnectToBlock(Level level, BlockPos conduitPos, Direction direction) {
        return false; // Stub: no block connections until types are implemented
    }

    @Override
    public boolean canConnectConduits(ITier selfTier, ITier otherTier) {
        return true; // Stub: allow all conduit-to-conduit connections
    }
}
