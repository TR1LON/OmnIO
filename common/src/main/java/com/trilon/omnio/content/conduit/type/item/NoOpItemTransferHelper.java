package com.trilon.omnio.content.conduit.type.item;

import com.trilon.omnio.api.transfer.IItemTransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * No-op implementation of item transfer.
 * Used as a fallback when no platform-specific item API is available.
 */
public class NoOpItemTransferHelper implements IItemTransferHelper {

    public static final NoOpItemTransferHelper INSTANCE = new NoOpItemTransferHelper();

    private NoOpItemTransferHelper() {
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, Direction direction, int maxCount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insert(Level level, BlockPos pos, Direction direction, ItemStack stack, boolean simulate) {
        return stack; // Nothing inserted, entire stack is remainder
    }

    @Override
    public boolean hasHandler(Level level, BlockPos pos, Direction direction) {
        return false;
    }
}
