package com.trilon.omnio.content.conduit.type.fluid;

import com.trilon.omnio.api.transfer.FluidResource;
import com.trilon.omnio.api.transfer.IFluidTransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * No-op implementation of fluid transfer.
 * Used as a fallback when no platform-specific fluid API is available.
 */
public class NoOpFluidTransferHelper implements IFluidTransferHelper {

    public static final NoOpFluidTransferHelper INSTANCE = new NoOpFluidTransferHelper();

    private NoOpFluidTransferHelper() {
    }

    @Override
    public FluidResource extract(Level level, BlockPos pos, Direction direction,
                                  @Nullable Fluid filter, int maxAmount, boolean simulate) {
        return FluidResource.EMPTY;
    }

    @Override
    public int insert(Level level, BlockPos pos, Direction direction,
                      Fluid fluid, int amount, boolean simulate) {
        return 0;
    }

    @Override
    public boolean hasHandler(Level level, BlockPos pos, Direction direction) {
        return false;
    }
}
