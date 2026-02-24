package com.trilon.omnio.content.conduit.type.item;

import com.trilon.omnio.api.conduit.IConduitTicker;
import com.trilon.omnio.api.conduit.IConduitType;
import com.trilon.omnio.api.conduit.network.IConduitNetworkContext;
import com.trilon.omnio.api.transfer.IItemTransferHelper;
import com.trilon.omnio.content.conduit.network.ConduitNetworkContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Conduit type implementation for item conduits.
 *
 * <p>Item conduits use an instant pass-through model: items are extracted
 * from source inventories and immediately inserted into target inventories
 * each tick cycle. There is no persistent buffer — the network context is
 * just a plain {@link ConduitNetworkContext} with no extra state.</p>
 *
 * <p>Round-robin extraction and priority-sorted insertion are handled
 * by {@link ItemConduitTicker}.</p>
 */
public class ItemConduitType implements IConduitType<ItemConduitTier> {

    private final ResourceLocation id;
    private final ItemConduitTier tier;
    private final ItemConduitTicker ticker;
    private final IItemTransferHelper transferHelper;

    public ItemConduitType(ResourceLocation id, ItemConduitTier tier, IItemTransferHelper transferHelper) {
        this.id = id;
        this.tier = tier;
        this.transferHelper = transferHelper;
        this.ticker = new ItemConduitTicker(tier, transferHelper);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public IConduitTicker getTicker() {
        return ticker;
    }

    @Override
    public int getTickRate() {
        return tier.getSpeed();
    }

    @Override
    public boolean canConnectToBlock(Level level, BlockPos conduitPos, Direction direction) {
        BlockPos neighborPos = conduitPos.relative(direction);
        Direction accessFace = direction.getOpposite();
        return transferHelper.hasHandler(level, neighborPos, accessFace);
    }

    @Override
    public boolean canConnectConduits(ItemConduitTier selfTier, ItemConduitTier otherTier) {
        return selfTier == otherTier;
    }

    public ItemConduitTier getTier() {
        return tier;
    }

    public IItemTransferHelper getTransferHelper() {
        return transferHelper;
    }

    /**
     * Item conduits don't need per-network state — items are passed through instantly.
     */
    @Override
    public IConduitNetworkContext<?> createNetworkContext() {
        return new ConduitNetworkContext();
    }

    @Override
    public String toString() {
        return "ItemConduitType[" + id + ", tier=" + tier.name() + "]";
    }
}
