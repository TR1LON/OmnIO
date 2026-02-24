package com.trilon.omnio.content.conduit.type.energy;

import com.trilon.omnio.api.transfer.ITransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * No-op implementation of energy transfer.
 * Used as a fallback when no platform-specific energy API is available.
 * This ensures the energy conduit type can be registered even before
 * platform helpers are wired in.
 *
 * <p>This will be replaced by NeoForge/Fabric-specific implementations
 * that bridge to IEnergyStorage / EnergyStorage respectively.</p>
 */
public class NoOpEnergyTransferHelper implements ITransferHelper<Long> {

    public static final NoOpEnergyTransferHelper INSTANCE = new NoOpEnergyTransferHelper();

    private NoOpEnergyTransferHelper() {
    }

    @Override
    public Long extract(Level level, BlockPos pos, Direction direction, Long maxAmount, boolean simulate) {
        return 0L;
    }

    @Override
    public Long insert(Level level, BlockPos pos, Direction direction, Long resource, boolean simulate) {
        return resource; // Entire amount is "remainder" (nothing inserted)
    }

    @Override
    public boolean hasHandler(Level level, BlockPos pos, Direction direction) {
        return false;
    }
}
