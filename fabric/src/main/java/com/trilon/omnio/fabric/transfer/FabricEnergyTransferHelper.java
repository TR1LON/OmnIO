package com.trilon.omnio.fabric.transfer;

import com.trilon.omnio.api.transfer.ITransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Fabric implementation of energy transfer.
 *
 * <p>Note: Fabric does not have a built-in energy API in the core Fabric API.
 * Energy handling on Fabric typically uses Team Reborn Energy (tech-reborn)
 * or other community standards. For now, this delegates to the no-op helper.
 * When a Fabric energy API dependency is added, this class will bridge to it.</p>
 *
 * <p>TODO: Integrate with Team Reborn Energy or Fabric Transfer API v2 for energy
 * once the dependency is added to fabric/build.gradle.</p>
 */
public class FabricEnergyTransferHelper implements ITransferHelper<Long> {

    public static final FabricEnergyTransferHelper INSTANCE = new FabricEnergyTransferHelper();

    private FabricEnergyTransferHelper() {
    }

    @Override
    public Long extract(Level level, BlockPos pos, Direction direction, Long maxAmount, boolean simulate) {
        // TODO: Bridge to Fabric energy API when dependency is available
        return 0L;
    }

    @Override
    public Long insert(Level level, BlockPos pos, Direction direction, Long resource, boolean simulate) {
        // TODO: Bridge to Fabric energy API when dependency is available
        return resource; // Nothing inserted
    }

    @Override
    public boolean hasHandler(Level level, BlockPos pos, Direction direction) {
        // TODO: Bridge to Fabric energy API when dependency is available
        return false;
    }
}
