package com.trilon.omnio.fabric.transfer;

import com.trilon.omnio.api.transfer.IItemTransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fabric implementation of item transfer.
 *
 * <p>Note: Fabric uses the Fabric Transfer API v2 for item handling via
 * {@code ItemStorage.SIDED}. For now, this delegates to a no-op implementation.
 * When the Fabric Transfer API dependency is added, this class will bridge
 * to the native Fabric item storage API.</p>
 *
 * <p>TODO: Integrate with Fabric Transfer API v2 when dependency is added
 * to fabric/build.gradle.</p>
 */
public class FabricItemTransferHelper implements IItemTransferHelper {

    public static final FabricItemTransferHelper INSTANCE = new FabricItemTransferHelper();

    private FabricItemTransferHelper() {
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, Direction direction, int maxCount, boolean simulate) {
        // TODO: Bridge to Fabric Transfer API v2 when dependency is available
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insert(Level level, BlockPos pos, Direction direction, ItemStack stack, boolean simulate) {
        // TODO: Bridge to Fabric Transfer API v2 when dependency is available
        return stack; // Nothing inserted
    }

    @Override
    public boolean hasHandler(Level level, BlockPos pos, Direction direction) {
        // TODO: Bridge to Fabric Transfer API v2 when dependency is available
        return false;
    }
}
