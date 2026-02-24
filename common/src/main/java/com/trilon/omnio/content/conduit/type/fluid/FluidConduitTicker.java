package com.trilon.omnio.content.conduit.type.fluid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.api.conduit.IConduitNode;
import com.trilon.omnio.api.conduit.IConduitTicker;
import com.trilon.omnio.api.conduit.IConnectionConfig;
import com.trilon.omnio.api.conduit.network.IConduitNetwork;
import com.trilon.omnio.api.transfer.FluidResource;
import com.trilon.omnio.api.transfer.IFluidTransferHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;

/**
 * Fluid conduit ticker implementing pool-and-distribute transfer.
 *
 * <p>Algorithm per tick:</p>
 * <ol>
 *   <li>Iterate all block-endpoint nodes with EXTRACT or BOTH transfer mode</li>
 *   <li>Extract fluid from adjacent blocks into the network's fluid buffer</li>
 *   <li>Iterate all block-endpoint nodes with INSERT or BOTH transfer mode,
 *       sorted by priority (highest first)</li>
 *   <li>Push fluid from the buffer into adjacent blocks</li>
 * </ol>
 *
 * <p>Fluid locking is enforced: a network only accepts one fluid type at a time.
 * Transfer rates are capped by the tier's per-connection transfer rate.
 * Redstone control is respected per-connection.</p>
 */
public class FluidConduitTicker implements IConduitTicker {

    private final FluidConduitTier tier;
    private final IFluidTransferHelper transferHelper;

    public FluidConduitTicker(FluidConduitTier tier, IFluidTransferHelper transferHelper) {
        this.tier = tier;
        this.transferHelper = transferHelper;
    }

    @Override
    public void tick(ServerLevel level, IConduitNetwork network) {
        if (!(network.getContext() instanceof FluidConduitNetworkContext ctx)) {
            return;
        }

        Collection<? extends IConduitNode> endpoints = network.getBlockEndpoints();
        if (endpoints.isEmpty()) return;

        int transferRate = tier.getTransferRate();

        // Phase 1: Extract from block endpoints into the network buffer
        extractLoop:
        for (IConduitNode node : endpoints) {
            if (!isNodeTicking(node)) continue;

            for (Map.Entry<Direction, IConnectionConfig> entry : node.getConnections().entrySet()) {
                Direction dir = entry.getKey();
                IConnectionConfig config = entry.getValue();

                if (config.getStatus() != ConnectionStatus.CONNECTED_BLOCK) continue;
                if (!canExtract(config)) continue;
                if (!isRedstoneActive(level, node.getPos(), config)) continue;

                BlockPos targetPos = node.getPos().relative(dir);
                Direction accessFace = dir.getOpposite();

                if (!transferHelper.hasHandler(level, targetPos, accessFace)) continue;

                int space = ctx.getCapacity() - ctx.getStoredAmount();
                if (space <= 0) break extractLoop; // Buffer full

                int maxExtract = Math.min(transferRate, space);

                // Filter by locked/stored fluid to maintain single-fluid invariant
                Fluid filter = ctx.getAcceptedFluid();

                FluidResource extracted = transferHelper.extract(level, targetPos, accessFace,
                        filter, maxExtract, false);

                if (!extracted.isEmpty()) {
                    ctx.addFluid(extracted.fluid(), extracted.amount());
                }
            }
        }

        // Phase 2: Insert from network buffer into block endpoints, priority-sorted
        if (ctx.getStoredAmount() <= 0) return;

        List<InsertTarget> insertTargets = collectInsertTargets(level, endpoints);
        if (insertTargets.isEmpty()) return;

        insertTargets.sort(Comparator.comparingInt(InsertTarget::priority).reversed());

        Fluid fluidToInsert = ctx.getStoredFluid();

        for (InsertTarget target : insertTargets) {
            if (ctx.getStoredAmount() <= 0) break;

            int maxInsert = Math.min(transferRate, ctx.getStoredAmount());
            int accepted = transferHelper.insert(level, target.targetPos, target.accessFace,
                    fluidToInsert, maxInsert, false);
            if (accepted > 0) {
                ctx.removeFluid(accepted);
            }
        }
    }

    private List<InsertTarget> collectInsertTargets(ServerLevel level,
                                                     Collection<? extends IConduitNode> endpoints) {
        List<InsertTarget> targets = new ArrayList<>();

        for (IConduitNode node : endpoints) {
            if (!isNodeTicking(node)) continue;

            for (Map.Entry<Direction, IConnectionConfig> entry : node.getConnections().entrySet()) {
                Direction dir = entry.getKey();
                IConnectionConfig config = entry.getValue();

                if (config.getStatus() != ConnectionStatus.CONNECTED_BLOCK) continue;
                if (!canInsert(config)) continue;
                if (!isRedstoneActive(level, node.getPos(), config)) continue;

                BlockPos targetPos = node.getPos().relative(dir);
                Direction accessFace = dir.getOpposite();

                if (!transferHelper.hasHandler(level, targetPos, accessFace)) continue;

                targets.add(new InsertTarget(targetPos, accessFace, config.getPriority()));
            }
        }

        return targets;
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

    private static boolean isRedstoneActive(ServerLevel level, BlockPos conduitPos, IConnectionConfig config) {
        return switch (config.getRedstoneMode()) {
            case ALWAYS_ACTIVE -> true;
            case NEVER_ACTIVE -> false;
            case ACTIVE_WITH_SIGNAL -> level.hasNeighborSignal(conduitPos);
            case ACTIVE_WITHOUT_SIGNAL -> !level.hasNeighborSignal(conduitPos);
        };
    }

    private static boolean isNodeTicking(IConduitNode node) {
        if (node instanceof com.trilon.omnio.content.conduit.network.ConduitNodeImpl impl) {
            return impl.isTicking();
        }
        return true;
    }

    private record InsertTarget(BlockPos targetPos, Direction accessFace, int priority) {
    }
}
