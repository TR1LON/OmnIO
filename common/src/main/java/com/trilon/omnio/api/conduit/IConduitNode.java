package com.trilon.omnio.api.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Map;

/**
 * Represents a single node in a conduit network, corresponding to
 * one conduit of a specific type within a bundle block at a position.
 */
public interface IConduitNode {

    /**
     * @return the block position of this node in the world
     */
    BlockPos getPos();

    /**
     * @return the per-direction connection configurations for this node
     */
    Map<Direction, IConnectionConfig> getConnections();

    /**
     * @param direction the direction to check
     * @return the connection config for the given direction, or null if not connected
     */
    IConnectionConfig getConnection(Direction direction);

    /**
     * @param direction the direction to check
     * @return true if this node has any connection (conduit or block) in the given direction
     */
    boolean isConnected(Direction direction);
}
