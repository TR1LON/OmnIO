package com.trilon.omnio.content.conduit.type.energy;

import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.api.conduit.IConduitNode;
import com.trilon.omnio.api.conduit.IConduitTicker;
import com.trilon.omnio.api.conduit.IConnectionConfig;
import com.trilon.omnio.api.conduit.network.IConduitNetwork;
import com.trilon.omnio.api.transfer.ITransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * Energy conduit ticker implementing pool-and-distribute transfer.
 *
 * <p>Algorithm per tick:</p>
 * <ol>
 *   <li>Iterate all block-endpoint nodes with EXTRACT or BOTH transfer mode</li>
 *   <li>Extract energy from adjacent blocks into the network's energy buffer</li>
 *   <li>Iterate all block-endpoint nodes with INSERT or BOTH transfer mode,
 *       sorted by priority (highest first)</li>
 *   <li>Push energy from the buffer into adjacent blocks</li>
 * </ol>
 *
 * <p>Transfer rates are capped by the tier's per-connection transfer rate.
 * Redstone control is respected per-connection.</p>
 */
public class EnergyConduitTicker implements IConduitTicker {

    private final EnergyConduitTier tier;
    private final ITransferHelper<Long> transferHelper;

    public EnergyConduitTicker(EnergyConduitTier tier, ITransferHelper<Long> transferHelper) {
        this.tier = tier;
        this.transferHelper = transferHelper;
    }

    @Override
    public void tick(ServerLevel level, IConduitNetwork network) {
        if (!(network.getContext() instanceof EnergyConduitNetworkContext ctx)) {
            return;
        }

        Collection<? extends IConduitNode> endpoints = network.getBlockEndpoints();
        if (endpoints.isEmpty()) return;

        long transferRate = tier.getTransferRate();

        // Collect all extract and insert endpoints grouped by channel
        Map<Integer, List<ExtractTarget>> extractByChannel = new HashMap<>();
        Map<Integer, List<InsertTarget>> insertByChannel = new HashMap<>();

        for (IConduitNode node : endpoints) {
            if (!isNodeTicking(node)) continue;

            for (Map.Entry<Direction, IConnectionConfig> entry : node.getConnections().entrySet()) {
                Direction dir = entry.getKey();
                IConnectionConfig config = entry.getValue();

                if (config.getStatus() != ConnectionStatus.CONNECTED_BLOCK) continue;
                if (!isRedstoneActive(level, node.getPos(), config)) continue;

                BlockPos targetPos = node.getPos().relative(dir);
                Direction accessFace = dir.getOpposite();
                if (!transferHelper.hasHandler(level, targetPos, accessFace)) continue;

                int channel = config.getChannel();
                if (canExtract(config)) {
                    extractByChannel.computeIfAbsent(channel, k -> new ArrayList<>())
                            .add(new ExtractTarget(node.getPos(), targetPos, accessFace));
                }
                if (canInsert(config)) {
                    insertByChannel.computeIfAbsent(channel, k -> new ArrayList<>())
                            .add(new InsertTarget(targetPos, accessFace, config.getPriority()));
                }
            }
        }

        // Process each channel independently through the shared buffer
        for (Map.Entry<Integer, List<ExtractTarget>> chEntry : extractByChannel.entrySet()) {
            int channel = chEntry.getKey();
            List<ExtractTarget> extractors = chEntry.getValue();
            List<InsertTarget> inserters = insertByChannel.getOrDefault(channel, List.of());
            if (inserters.isEmpty()) continue;

            // Sort inserters by priority (highest first)
            inserters.sort(Comparator.comparingInt(InsertTarget::priority).reversed());

            // Phase 1: Extract into buffer
            long extracted = 0;
            for (ExtractTarget src : extractors) {
                long space = ctx.getCapacity() - ctx.getStoredEnergy();
                if (space <= 0) break;
                long maxExtract = Math.min(transferRate, space);
                long got = transferHelper.extract(level, src.targetPos, src.accessFace, maxExtract, false);
                if (got > 0) {
                    ctx.addEnergy(got);
                    extracted += got;
                }
            }

            // Phase 2: Insert from buffer
            if (extracted <= 0 && ctx.getStoredEnergy() <= 0) continue;
            for (InsertTarget target : inserters) {
                if (ctx.getStoredEnergy() <= 0) break;
                long maxInsert = Math.min(transferRate, ctx.getStoredEnergy());
                Long remainder = transferHelper.insert(level, target.targetPos, target.accessFace, maxInsert, false);
                long inserted = maxInsert - remainder;
                if (inserted > 0) {
                    ctx.removeEnergy(inserted);
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

    /**
     * Check if a connection is active based on its redstone control mode.
     */
    private static boolean isRedstoneActive(ServerLevel level, BlockPos conduitPos, IConnectionConfig config) {
        return switch (config.getRedstoneMode()) {
            case ALWAYS_ACTIVE -> true;
            case NEVER_ACTIVE -> false;
            case ACTIVE_WITH_SIGNAL -> level.hasNeighborSignal(conduitPos);
            case ACTIVE_WITHOUT_SIGNAL -> !level.hasNeighborSignal(conduitPos);
        };
    }

    /**
     * Check if a node is in a loaded chunk and should participate in ticking.
     */
    private static boolean isNodeTicking(IConduitNode node) {
        if (node instanceof com.trilon.omnio.content.conduit.network.ConduitNodeImpl impl) {
            return impl.isTicking();
        }
        return true; // Default to ticking if type is unknown
    }

    /**
     * Internal record for tracking insertion targets with their priority.
     */
    private record InsertTarget(BlockPos targetPos, Direction accessFace, int priority) {
    }

    private record ExtractTarget(BlockPos conduitPos, BlockPos targetPos, Direction accessFace) {
    }
}
