package com.trilon.omnio.api.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Platform-neutral abstraction for item transfer operations.
 * Each platform (NeoForge, Fabric) provides an implementation that bridges
 * to the native item capability / transfer API.
 *
 * <p>{@link ItemStack} is a vanilla class, so it can be used directly in common.</p>
 */
public interface IItemTransferHelper {

    /**
     * Extract items from the block at the given position.
     * Pulls the first available stack (up to {@code maxCount} items) from any slot.
     *
     * @param level     the level
     * @param pos       the position of the target block
     * @param direction the face of the target to access
     * @param maxCount  the maximum number of items to extract
     * @param simulate  if true, don't actually extract — just return what would be extracted
     * @return the ItemStack actually extracted (or that would be extracted if simulating).
     *         Returns {@link ItemStack#EMPTY} if nothing could be extracted.
     */
    ItemStack extract(Level level, BlockPos pos, Direction direction, int maxCount, boolean simulate);

    /**
     * Insert an item stack into the block at the given position.
     *
     * @param level     the level
     * @param pos       the position of the target block
     * @param direction the face of the target to access
     * @param stack     the item stack to insert
     * @param simulate  if true, don't actually insert — just return the remainder
     * @return the remainder that could NOT be inserted.
     *         Returns {@link ItemStack#EMPTY} if everything was accepted.
     */
    ItemStack insert(Level level, BlockPos pos, Direction direction, ItemStack stack, boolean simulate);

    /**
     * Check if the block at the given position has a compatible item handler on the given face.
     *
     * @param level     the level
     * @param pos       the position of the target block
     * @param direction the direction to check
     * @return true if the block exposes a compatible item handler
     */
    boolean hasHandler(Level level, BlockPos pos, Direction direction);
}
