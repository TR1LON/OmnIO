package com.trilon.omnio.api.conduit;

import com.trilon.omnio.api.conduit.network.IConduitNetwork;
import net.minecraft.server.level.ServerLevel;

/**
 * Handles the per-tick transfer logic for a specific conduit type.
 * Each conduit type provides a ticker that the network saved data
 * invokes on its configured tick schedule.
 */
public interface IConduitTicker {

    /**
     * Perform one tick of resource transfer for the given network.
     * The ticker should:
     * 1. Iterate extract connections and pull resources from adjacent blocks
     * 2. Store in the network context buffer
     * 3. Iterate insert connections (sorted by priority) and push resources
     *
     * @param level   the server level
     * @param network the conduit network to tick
     */
    void tick(ServerLevel level, IConduitNetwork network);
}
