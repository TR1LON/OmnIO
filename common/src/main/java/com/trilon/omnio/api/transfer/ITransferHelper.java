package com.trilon.omnio.api.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Platform-neutral abstraction for resource transfer operations.
 * Each platform (NeoForge, Fabric) provides implementations that bridge
 * to the native capability/transfer API.
 *
 * <p>Implementations are loaded via {@link java.util.ServiceLoader} SPI.</p>
 *
 * @param <T> the resource type being transferred (energy amount, fluid stack, item stack, etc.)
 */
public interface ITransferHelper<T> {

    /**
     * Extract resources from the block at the given position + direction.
     *
     * @param level     the level
     * @param pos       the position of the target block
     * @param direction the direction to access the block from (the face of the target)
     * @param maxAmount the maximum amount to extract
     * @param simulate  if true, don't actually extract — just return what would be extracted
     * @return the amount actually extracted (or that would be extracted if simulating)
     */
    T extract(Level level, BlockPos pos, Direction direction, T maxAmount, boolean simulate);

    /**
     * Insert resources into the block at the given position + direction.
     *
     * @param level     the level
     * @param pos       the position of the target block
     * @param direction the direction to access the block from (the face of the target)
     * @param resource  the resource to insert
     * @param simulate  if true, don't actually insert — just return the remainder
     * @return the amount that could NOT be inserted (remainder)
     */
    T insert(Level level, BlockPos pos, Direction direction, T resource, boolean simulate);

    /**
     * Check if the block at the given position has a compatible handler on the given face.
     *
     * @param level     the level
     * @param pos       the position of the target block
     * @param direction the direction to check
     * @return true if the block exposes a compatible resource handler
     */
    boolean hasHandler(Level level, BlockPos pos, Direction direction);
}
