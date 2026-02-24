package com.trilon.omnio.neoforge.transfer;

import com.trilon.omnio.api.transfer.ITransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * NeoForge implementation of energy transfer using the {@link IEnergyStorage} capability.
 * Bridges OmnIO's platform-neutral {@link ITransferHelper} to NeoForge's energy API.
 */
public class NeoForgeEnergyTransferHelper implements ITransferHelper<Long> {

    public static final NeoForgeEnergyTransferHelper INSTANCE = new NeoForgeEnergyTransferHelper();

    private NeoForgeEnergyTransferHelper() {
    }

    @Override
    public Long extract(Level level, BlockPos pos, Direction direction, Long maxAmount, boolean simulate) {
        IEnergyStorage storage = getStorage(level, pos, direction);
        if (storage == null || !storage.canExtract()) return 0L;

        // IEnergyStorage uses int, so clamp
        int maxInt = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        int extracted = storage.extractEnergy(maxInt, simulate);
        return (long) extracted;
    }

    @Override
    public Long insert(Level level, BlockPos pos, Direction direction, Long resource, boolean simulate) {
        IEnergyStorage storage = getStorage(level, pos, direction);
        if (storage == null || !storage.canReceive()) return resource; // Nothing inserted

        int maxInt = (int) Math.min(resource, Integer.MAX_VALUE);
        int accepted = storage.receiveEnergy(maxInt, simulate);
        return resource - accepted; // Return remainder
    }

    @Override
    public boolean hasHandler(Level level, BlockPos pos, Direction direction) {
        return getStorage(level, pos, direction) != null;
    }

    private static IEnergyStorage getStorage(Level level, BlockPos pos, Direction direction) {
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, direction);
    }
}
