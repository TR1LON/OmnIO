package com.trilon.omnio.content.conduit.type.redstone;

import java.util.Collection;
import java.util.Map;

import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.api.conduit.IConduitNode;
import com.trilon.omnio.api.conduit.IConduitTicker;
import com.trilon.omnio.api.conduit.IConnectionConfig;
import com.trilon.omnio.api.conduit.network.IConduitNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Redstone conduit ticker implementing channel-based signal propagation.
 *
 * <p>Algorithm per tick:</p>
 * <ol>
 *   <li>Clear all 16 channel signal strengths to 0</li>
 *   <li>For each EXTRACT/BOTH endpoint, read the redstone signal from the
 *       adjacent block and update the configured channel with the max signal</li>
 *   <li>For each INSERT/BOTH endpoint, output the channel's signal strength
 *       to the adjacent block by triggering a neighbor update</li>
 * </ol>
 *
 * <p>Channel selection uses {@link IConnectionConfig#getChannel()},
 * which maps to DyeColor ordinals (0-15).</p>
 */
public class RedstoneConduitTicker implements IConduitTicker {

    @Override
    public void tick(ServerLevel level, IConduitNetwork network) {
        if (!(network.getContext() instanceof RedstoneConduitNetworkContext ctx)) {
            return;
        }

        Collection<? extends IConduitNode> endpoints = network.getBlockEndpoints();
        if (endpoints.isEmpty()) return;

        // Phase 1: Clear signals from previous tick
        ctx.clearSignals();

        // Phase 2: Read signals from EXTRACT endpoints
        for (IConduitNode node : endpoints) {
            if (!isNodeTicking(node)) continue;

            for (Map.Entry<Direction, IConnectionConfig> entry : node.getConnections().entrySet()) {
                Direction dir = entry.getKey();
                IConnectionConfig config = entry.getValue();

                if (config.getStatus() != ConnectionStatus.CONNECTED_BLOCK) continue;
                if (!canExtract(config)) continue;

                BlockPos targetPos = node.getPos().relative(dir);
                int channel = config.getChannel();

                // Read the redstone signal from the neighbor block on the access face
                int signal = level.getSignal(targetPos, dir);
                // Also check direct signal for strong-power sources
                int directSignal = level.getDirectSignal(targetPos, dir);
                int maxSignal = Math.max(signal, directSignal);

                if (maxSignal > 0) {
                    ctx.updateSignal(channel, maxSignal);
                }
            }
        }

        // Phase 3: Output signals to INSERT endpoints
        if (!ctx.hasAnySignal()) return;

        boolean needsUpdate = false;

        for (IConduitNode node : endpoints) {
            if (!isNodeTicking(node)) continue;

            for (Map.Entry<Direction, IConnectionConfig> entry : node.getConnections().entrySet()) {
                IConnectionConfig config = entry.getValue();

                if (config.getStatus() != ConnectionStatus.CONNECTED_BLOCK) continue;
                if (!canInsert(config)) continue;

                int channel = config.getChannel();
                int signalStrength = ctx.getSignal(channel);

                if (signalStrength > 0) {
                    // Mark that we need to trigger block updates
                    // The actual signal output happens through the conduit block's
                    // getSignal/getDirectSignal methods, which read from the network context
                    needsUpdate = true;
                }
            }
        }

        // Trigger neighbor updates for all conduit positions that have INSERT connections
        // so adjacent blocks re-read our signal output
        if (needsUpdate) {
            for (IConduitNode node : endpoints) {
                if (!isNodeTicking(node)) continue;

                for (Map.Entry<Direction, IConnectionConfig> entry : node.getConnections().entrySet()) {
                    Direction dir = entry.getKey();
                    IConnectionConfig config = entry.getValue();

                    if (config.getStatus() != ConnectionStatus.CONNECTED_BLOCK) continue;
                    if (!canInsert(config)) continue;

                    int channel = config.getChannel();
                    if (ctx.getSignal(channel) > 0) {
                        BlockPos targetPos = node.getPos().relative(dir);
                        // Notify the target block that the conduit's signal has changed
                        level.neighborChanged(targetPos, level.getBlockState(node.getPos()).getBlock(), node.getPos());
                    }
                }
            }
        }
    }

    // ---- Helper methods ----

    private static boolean canExtract(IConnectionConfig config) {
        IConnectionConfig.TransferMode mode = config.getTransferMode();
        return mode == IConnectionConfig.TransferMode.EXTRACT || mode == IConnectionConfig.TransferMode.BOTH;
    }

    private static boolean canInsert(IConnectionConfig config) {
        IConnectionConfig.TransferMode mode = config.getTransferMode();
        return mode == IConnectionConfig.TransferMode.INSERT || mode == IConnectionConfig.TransferMode.BOTH;
    }

    private static boolean isNodeTicking(IConduitNode node) {
        if (node instanceof com.trilon.omnio.content.conduit.network.ConduitNodeImpl impl) {
            return impl.isTicking();
        }
        return true;
    }
}
