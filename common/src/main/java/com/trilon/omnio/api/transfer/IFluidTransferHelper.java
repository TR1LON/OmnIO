package com.trilon.omnio.api.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-neutral abstraction for fluid transfer operations.
 * Each platform (NeoForge, Fabric) provides an implementation that bridges
 * to the native fluid capability / transfer API.
 *
 * <p>Amounts are in millibuckets (mB). NeoForge uses mB natively;
 * Fabric implementations should convert from/to droplets (81 000 droplets = 1000 mB).</p>
 */
public interface IFluidTransferHelper {

    /**
     * Extract fluid from the block at the given position.
     *
     * @param level     the level
     * @param pos       the position of the target block
     * @param direction the face of the target to access
     * @param filter    if non-null, only extract this fluid type; if null, extract any
     * @param maxAmount the maximum amount to extract in mB
     * @param simulate  if true, don't actually extract — just return what would be extracted
     * @return the fluid actually extracted (or that would be extracted if simulating)
     */
    FluidResource extract(Level level, BlockPos pos, Direction direction,
                          @Nullable Fluid filter, int maxAmount, boolean simulate);

    /**
     * Insert fluid into the block at the given position.
     *
     * @param level     the level
     * @param pos       the position of the target block
     * @param direction the face of the target to access
     * @param fluid     the fluid type to insert
     * @param amount    the amount to insert in mB
     * @param simulate  if true, don't actually insert — just return how much would be accepted
     * @return the amount actually accepted (NOT the remainder)
     */
    int insert(Level level, BlockPos pos, Direction direction,
               Fluid fluid, int amount, boolean simulate);

    /**
     * Check if the block at the given position has a compatible fluid handler on the given face.
     *
     * @param level     the level
     * @param pos       the position of the target block
     * @param direction the direction to check
     * @return true if the block exposes a compatible fluid handler
     */
    boolean hasHandler(Level level, BlockPos pos, Direction direction);
}
