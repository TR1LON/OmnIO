package com.trilon.omnio.content.conduit;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Item used to place or add a conduit into a bundle block.
 * Each conduit variant (type + tier) has its own ConduitItem instance.
 *
 * <p>Placement behavior:</p>
 * <ul>
 *   <li>If targeting an existing {@link OmniConduitBlock} → add this conduit to the bundle</li>
 *   <li>If targeting an empty space → place a new {@link OmniConduitBlock} and add this conduit</li>
 * </ul>
 */
public class ConduitItem extends Item {

    private final ResourceLocation conduitId;
    private final Block conduitBlock;

    /**
     * @param conduitId    the registry ID of the conduit variant (e.g., "omnio:energy_basic")
     * @param conduitBlock the OmniConduitBlock instance to place
     * @param properties   item properties
     */
    public ConduitItem(String conduitId, Block conduitBlock, Properties properties) {
        super(properties);
        this.conduitId = ResourceLocation.parse(conduitId);
        this.conduitBlock = conduitBlock;
    }

    /**
     * @return the conduit variant ID this item places
     */
    public ResourceLocation getConduitId() {
        return conduitId;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        ItemStack stack = context.getItemInHand();

        // If the target is already a conduit bundle, try to add to it
        if (clickedState.getBlock() instanceof OmniConduitBlock) {
            return placeConduit(level, clickedPos, clickedState, stack);
        }

        // Otherwise, try to place at the adjacent position
        BlockPos placePos = clickedPos.relative(context.getClickedFace());
        BlockState placeState = level.getBlockState(placePos);

        // If the adjacent position is also a bundle, add to it
        if (placeState.getBlock() instanceof OmniConduitBlock) {
            return placeConduit(level, placePos, placeState, stack);
        }

        // If the adjacent position is replaceable, place a new bundle
        if (placeState.canBeReplaced()) {
            return placeConduit(level, placePos, placeState, stack);
        }

        return InteractionResult.FAIL;
    }

    /**
     * Attempt to place or add a conduit at the given position.
     * If the target is an existing conduit bundle, add to it.
     * Otherwise, place a new bundle block.
     */
    private InteractionResult placeConduit(Level level, BlockPos pos, BlockState existingState, ItemStack stack) {
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

        // Place a new conduit bundle block, respecting waterlogging
        boolean waterlogged = level.getFluidState(pos).getType() == Fluids.WATER;
        BlockState newState = conduitBlock.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, waterlogged);
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
