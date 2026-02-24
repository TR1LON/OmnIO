package com.trilon.omnio.api.conduit;

import com.trilon.omnio.api.tier.ITier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Defines a specific type/category of conduit (energy, fluid, item, redstone, etc.).
 * Each conduit type is registered in the OmnIO conduit type registry and defines
 * how conduits of this type behave, connect, and transfer resources.
 *
 * @param <T> the tier enum type for this conduit type
 */
public interface IConduitType<T extends ITier> {

    /**
     * @return the unique registry ID for this conduit type
     */
    ResourceLocation getId();

    /**
     * @return the ticker responsible for performing transfers for this conduit type
     */
    IConduitTicker getTicker();

    /**
     * @return the number of game ticks between each tick cycle for this conduit type
     */
    int getTickRate();

    /**
     * Checks whether this conduit type can form a block-endpoint connection
     * to the given neighboring block position from the given direction.
     *
     * @param level     the server level
     * @param conduitPos the position of the conduit bundle block
     * @param direction the direction from the conduit to the neighbor
     * @return true if the neighbor block is a valid endpoint for this conduit type
     */
    boolean canConnectToBlock(Level level, BlockPos conduitPos, Direction direction);

    /**
     * Checks whether two conduits of this type can connect to each other.
     * Used to prevent incompatible conduits (e.g., different tiers) from connecting.
     *
     * @param selfTier  the tier of the local conduit
     * @param otherTier the tier of the neighbor conduit
     * @return true if a conduit-to-conduit connection should be formed
     */
    boolean canConnectConduits(T selfTier, T otherTier);
}
