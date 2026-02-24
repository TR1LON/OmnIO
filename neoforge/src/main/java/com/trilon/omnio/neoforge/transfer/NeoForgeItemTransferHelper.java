package com.trilon.omnio.neoforge.transfer;

import com.trilon.omnio.api.transfer.IItemTransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge implementation of item transfer using {@link IItemHandler} capability.
 * Bridges OmnIO's platform-neutral {@link IItemTransferHelper} to NeoForge's item API.
 */
public class NeoForgeItemTransferHelper implements IItemTransferHelper {

    public static final NeoForgeItemTransferHelper INSTANCE = new NeoForgeItemTransferHelper();

    private NeoForgeItemTransferHelper() {
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, Direction direction, int maxCount, boolean simulate) {
        IItemHandler handler = getHandler(level, pos, direction);
        if (handler == null) return ItemStack.EMPTY;

        // Scan slots for the first extractable stack
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (inSlot.isEmpty()) continue;

            ItemStack extracted = handler.extractItem(slot, maxCount, simulate);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insert(Level level, BlockPos pos, Direction direction, ItemStack stack, boolean simulate) {
        IItemHandler handler = getHandler(level, pos, direction);
        if (handler == null) return stack;

        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
        }

        return remaining;
    }

    @Override
    public boolean hasHandler(Level level, BlockPos pos, Direction direction) {
        return getHandler(level, pos, direction) != null;
    }

    @Nullable
    private static IItemHandler getHandler(Level level, BlockPos pos, Direction direction) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
    }
}
