package com.trilon.omnio.content.conduit;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Item used to place or add a conduit into a bundle block.
 * Each conduit variant (type + tier) has its own ConduitItem instance.
 *
 * <p>Placement behavior:</p>
 * <ul>
 *   <li>If targeting an empty space → place a new {@link OmniConduitBlock} and add this conduit</li>
 *   <li>If targeting an existing {@link OmniConduitBlock} → add this conduit to the bundle</li>
 * </ul>
 */
public class ConduitItem extends Item {

    private final String conduitId;
    private final Block conduitBlock;

    /**
     * @param conduitId    the registry ID of the conduit variant (e.g., "omnio:energy_basic")
     * @param conduitBlock the OmniConduitBlock instance to place
     * @param properties   item properties
     */
    public ConduitItem(String conduitId, Block conduitBlock, Properties properties) {
        super(properties);
        this.conduitId = conduitId;
        this.conduitBlock = conduitBlock;
    }

    /**
     * @return the conduit variant ID this item places
     */
    public String getConduitId() {
        return conduitId;
    }

    /**
     * Attempt to place or add a conduit when the player uses this item on a block.
     * If the target is an existing conduit bundle, add to it.
     * Otherwise, place a new bundle block.
     */
    public InteractionResult placeConduit(Level level, BlockPos pos, BlockState existingState, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // If the target is already a conduit bundle, try to add to it
        if (existingState.getBlock() instanceof OmniConduitBlock) {
            var be = level.getBlockEntity(pos);
            if (be instanceof OmniConduitBlockEntity conduitBE) {
                if (conduitBE.addConduit(conduitId)) {
                    stack.shrink(1);
                    return InteractionResult.CONSUME;
                }
                return InteractionResult.FAIL; // Bundle full or already has this conduit
            }
        }

        // Place a new conduit bundle block
        BlockState newState = conduitBlock.defaultBlockState();
        if (level.setBlock(pos, newState, Block.UPDATE_ALL)) {
            var be = level.getBlockEntity(pos);
            if (be instanceof OmniConduitBlockEntity conduitBE) {
                conduitBE.addConduit(conduitId);
                stack.shrink(1);
                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.FAIL;
    }
}
