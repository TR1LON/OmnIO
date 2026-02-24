package com.trilon.omnio.content.conduit.type.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.api.conduit.IConduitNode;
import com.trilon.omnio.api.conduit.IConduitTicker;
import com.trilon.omnio.api.conduit.IConnectionConfig;
import com.trilon.omnio.api.conduit.network.IConduitNetwork;
import com.trilon.omnio.api.transfer.IItemTransferHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Item conduit ticker implementing round-robin extraction with priority insertion.
 *
 * <p>Algorithm per tick:</p>
 * <ol>
 *   <li>Collect all EXTRACT/BOTH endpoints as extraction sources</li>
 *   <li>For each source, extract up to stackSize items</li>
 *   <li>Immediately distribute extracted items to INSERT/BOTH endpoints,
 *       sorted by priority (highest first)</li>
 *   <li>Round-robin across extraction sources: each tick starts from where
 *       the previous tick left off</li>
 * </ol>
 *
 * <p>Items are passed through instantly — there is no persistent buffer.
 * Any items that cannot be inserted are "voided" (remain at the source
 * because extraction is simulated first, then committed only if insertion succeeds).</p>
 */
public class ItemConduitTicker implements IConduitTicker {

    private final ItemConduitTier tier;
    private final IItemTransferHelper transferHelper;

    /**
     * Per-network round-robin index. Keyed by network identity hash to avoid
     * holding strong references to dead networks.
     */
    private final Map<Long, Integer> rrIndices = new HashMap<>();

    public ItemConduitTicker(ItemConduitTier tier, IItemTransferHelper transferHelper) {
        this.tier = tier;
        this.transferHelper = transferHelper;
    }

    @Override
    public void tick(ServerLevel level, IConduitNetwork network) {
        Collection<? extends IConduitNode> endpoints = network.getBlockEndpoints();
        if (endpoints.isEmpty()) return;

        int stackSize = tier.getStackSize();

        // Collect extraction and insertion targets
        List<TransferTarget> extractTargets = new ArrayList<>();
        List<TransferTarget> insertTargets = new ArrayList<>();

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

                TransferTarget target = new TransferTarget(targetPos, accessFace, config.getPriority());
                IConnectionConfig.TransferMode mode = config.getTransferMode();
                if (mode == IConnectionConfig.TransferMode.EXTRACT || mode == IConnectionConfig.TransferMode.BOTH) {
                    extractTargets.add(target);
                }
                if (mode == IConnectionConfig.TransferMode.INSERT || mode == IConnectionConfig.TransferMode.BOTH) {
                    insertTargets.add(target);
                }
            }
        }

        if (extractTargets.isEmpty() || insertTargets.isEmpty()) return;

        // Sort insertion targets by priority (highest first)
        insertTargets.sort(Comparator.comparingInt(TransferTarget::priority).reversed());

        // Round-robin starting index (use network's stable ID as key)
        long networkKey = network.getId();
        int rrIndex = rrIndices.getOrDefault(networkKey, 0) % extractTargets.size();

        // Extract from each source and distribute immediately
        int sourcesProcessed = 0;
        int totalSources = extractTargets.size();

        while (sourcesProcessed < totalSources) {
            int sourceIdx = (rrIndex + sourcesProcessed) % totalSources;
            TransferTarget source = extractTargets.get(sourceIdx);
            sourcesProcessed++;

            // Simulate extraction to see what's available
            ItemStack simulated = transferHelper.extract(level, source.targetPos, source.accessFace,
                    stackSize, true);
            if (simulated.isEmpty()) continue;

            int remainingToExtract = simulated.getCount();

            for (TransferTarget insertTarget : insertTargets) {
                if (remainingToExtract <= 0) break;
                // Don't insert into the same block we're extracting from
                if (insertTarget.targetPos.equals(source.targetPos)) continue;

                // Simulate insertion to see how much the target can accept
                ItemStack testStack = simulated.copyWithCount(remainingToExtract);
                ItemStack simLeftover = transferHelper.insert(level, insertTarget.targetPos,
                        insertTarget.accessFace, testStack, true);
                int canAccept = remainingToExtract - simLeftover.getCount();
                if (canAccept <= 0) continue;

                // Actually extract the transferable amount from the source
                ItemStack extracted = transferHelper.extract(level, source.targetPos, source.accessFace,
                        canAccept, false);
                if (extracted.isEmpty()) break; // Source was drained externally

                // Actually insert the extracted items into the target
                ItemStack leftover = transferHelper.insert(level, insertTarget.targetPos,
                        insertTarget.accessFace, extracted, false);
                int transferred = extracted.getCount() - leftover.getCount();
                remainingToExtract -= transferred;

                // If insertion rejected items we already extracted, return them to source
                if (!leftover.isEmpty()) {
                    transferHelper.insert(level, source.targetPos, source.accessFace, leftover, false);
                }
            }
        }

        // Advance round-robin index for next tick
        rrIndices.put(networkKey, (rrIndex + 1) % totalSources);
    }

    // ---- Helper methods ----

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

    private record TransferTarget(BlockPos targetPos, Direction accessFace, int priority) {
    }
}
