package com.trilon.omnio.content.conduit;

import com.trilon.omnio.api.conduit.ConduitSlot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/**
 * Item used to place or add a conduit into a bundle block.
 * Each conduit variant (type + tier) has its own ConduitItem instance.
 *
 * <p>Placement behavior:</p>
 * <ul>
 *   <li>If targeting an existing {@link OmniConduitBlock} → add this conduit to the bundle</li>
 *   <li>If targeting any other block → place a new {@link OmniConduitBlock} on the clicked face</li>
 * </ul>
 */
public class ConduitItem extends Item {

    private static final String TAG_CHANNEL = "Channel";

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

    /**
     * Get the color channel stored on this item stack.
     * Defaults to {@link ConduitSlot#DEFAULT_CHANNEL} (white) if not set.
     *
     * @param stack the item stack to read from
     * @return the channel index (0–15)
     */
    public static int getChannel(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains(TAG_CHANNEL)) {
                return Math.clamp(tag.getInt(TAG_CHANNEL), 0, ConduitSlot.MAX_CHANNEL);
            }
        }
        return ConduitSlot.DEFAULT_CHANNEL;
    }

    /**
     * Set the color channel on this item stack.
     *
     * @param stack   the item stack to modify
     * @param channel the channel index (0–15)
     */
    public static void setChannel(ItemStack stack, int channel) {
        int clamped = Math.clamp(channel, 0, ConduitSlot.MAX_CHANNEL);
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> {
            CompoundTag tag = data.copyTag();
            tag.putInt(TAG_CHANNEL, clamped);
            return CustomData.of(tag);
        });
    }

    /**
     * Build a {@link ConduitSlot} for this item, reading the channel from the stack.
     *
     * @param stack the item stack
     * @return the conduit slot (conduitId + channel)
     */
    public ConduitSlot getSlot(ItemStack stack) {
        return new ConduitSlot(conduitId, getChannel(stack));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        ItemStack stack = context.getItemInHand();

        // If the target is already a conduit bundle, try to add this conduit to it
        if (clickedState.getBlock() instanceof OmniConduitBlock) {
            InteractionResult addResult = placeConduit(level, clickedPos, clickedState, stack);
            if (addResult.consumesAction()) return addResult;

            // Could not add to this bundle — extend to the adjacent block
            BlockPos placePos = clickedPos.relative(context.getClickedFace());
            BlockState placeState = level.getBlockState(placePos);

            if (placeState.getBlock() instanceof OmniConduitBlock || placeState.canBeReplaced()) {
                return placeConduit(level, placePos, placeState, stack);
            }
            return InteractionResult.FAIL;
        }

        // Otherwise, try to place a new bundle at the adjacent position
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

        ConduitSlot slot = getSlot(stack);

        // If the target is already a conduit bundle, try to add to it
        if (existingState.getBlock() instanceof OmniConduitBlock) {
            var be = level.getBlockEntity(pos);
            if (be instanceof OmniConduitBlockEntity conduitBE) {
                if (conduitBE.addConduit(slot)) {
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
                conduitBE.addConduit(slot);
                stack.shrink(1);
                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.FAIL;
    }
}
