package com.trilon.omnio.fabric.transfer;

import com.trilon.omnio.api.transfer.FluidResource;
import com.trilon.omnio.api.transfer.IFluidTransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric implementation of fluid transfer.
 *
 * <p>Note: Fabric uses the Fabric Transfer API v2 for fluid handling,
 * which operates in "droplets" (1 bucket = 81000 droplets) rather than
 * NeoForge's millibuckets (1 bucket = 1000 mB). For now, this delegates
 * to a no-op implementation. When the Fabric Transfer API dependency is
 * added, this class will bridge to {@code FluidStorage.SIDED} and
 * perform the droplet ↔ mB unit conversion.</p>
 *
 * <p>TODO: Integrate with Fabric Transfer API v2 when dependency is added
 * to fabric/build.gradle.</p>
 */
public class FabricFluidTransferHelper implements IFluidTransferHelper {

    public static final FabricFluidTransferHelper INSTANCE = new FabricFluidTransferHelper();

    private FabricFluidTransferHelper() {
    }

    @Override
    public FluidResource extract(Level level, BlockPos pos, Direction direction,
                                  @Nullable Fluid filter, int maxAmount, boolean simulate) {
        // TODO: Bridge to Fabric Transfer API v2 when dependency is available
        return FluidResource.EMPTY;
    }

    @Override
    public int insert(Level level, BlockPos pos, Direction direction,
                      Fluid fluid, int amount, boolean simulate) {
        // TODO: Bridge to Fabric Transfer API v2 when dependency is available
        return 0;
    }

    @Override
    public boolean hasHandler(Level level, BlockPos pos, Direction direction) {
        // TODO: Bridge to Fabric Transfer API v2 when dependency is available
        return false;
    }
}
