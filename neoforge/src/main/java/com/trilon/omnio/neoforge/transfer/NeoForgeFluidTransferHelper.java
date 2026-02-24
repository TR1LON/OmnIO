package com.trilon.omnio.neoforge.transfer;

import com.trilon.omnio.api.transfer.FluidResource;
import com.trilon.omnio.api.transfer.IFluidTransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge implementation of fluid transfer using {@link IFluidHandler} capability.
 * Bridges OmnIO's platform-neutral {@link IFluidTransferHelper} to NeoForge's fluid API.
 *
 * <p>NeoForge uses millibuckets (mB) natively, so no unit conversion is needed.</p>
 */
public class NeoForgeFluidTransferHelper implements IFluidTransferHelper {

    public static final NeoForgeFluidTransferHelper INSTANCE = new NeoForgeFluidTransferHelper();

    private NeoForgeFluidTransferHelper() {
    }

    @Override
    public FluidResource extract(Level level, BlockPos pos, Direction direction,
                                  @Nullable Fluid filter, int maxAmount, boolean simulate) {
        IFluidHandler handler = getHandler(level, pos, direction);
        if (handler == null) return FluidResource.EMPTY;

        IFluidHandler.FluidAction action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;

        FluidStack drained;
        if (filter != null) {
            // Drain a specific fluid type
            drained = handler.drain(new FluidStack(filter, maxAmount), action);
        } else {
            // Drain any fluid
            drained = handler.drain(maxAmount, action);
        }

        if (drained.isEmpty()) return FluidResource.EMPTY;
        return new FluidResource(drained.getFluid(), drained.getAmount());
    }

    @Override
    public int insert(Level level, BlockPos pos, Direction direction,
                      Fluid fluid, int amount, boolean simulate) {
        IFluidHandler handler = getHandler(level, pos, direction);
        if (handler == null) return 0;

        IFluidHandler.FluidAction action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        FluidStack toFill = new FluidStack(fluid, amount);
        return handler.fill(toFill, action);
    }

    @Override
    public boolean hasHandler(Level level, BlockPos pos, Direction direction) {
        return getHandler(level, pos, direction) != null;
    }

    @Nullable
    private static IFluidHandler getHandler(Level level, BlockPos pos, Direction direction) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, direction);
    }
}
