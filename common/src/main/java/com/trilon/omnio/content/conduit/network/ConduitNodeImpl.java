package com.trilon.omnio.content.conduit.network;

import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.api.conduit.IConduitNode;
import com.trilon.omnio.api.conduit.IConnectionConfig;
import com.trilon.omnio.content.conduit.ConnectionConfig;
import com.trilon.omnio.content.conduit.ConnectionContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Concrete implementation of {@link IConduitNode}.
 * Represents one conduit of a specific type at a specific block position
 * within the conduit network graph.
 *
 * <p>Each node wraps its {@link ConnectionContainer} from the block entity and
 * provides a graph-friendly view of neighbors and endpoint status.</p>
 */
public class ConduitNodeImpl implements IConduitNode {

    private final BlockPos pos;

    /**
     * Neighbor nodes in the network graph (conduit-to-conduit connections).
     * Populated by the network manager during graph construction.
     */
    private final Map<Direction, ConduitNodeImpl> neighborNodes = new EnumMap<>(Direction.class);

    /**
     * Cached connection configs from the block entity's ConnectionContainer.
     * Updated whenever the block entity notifies the network of changes.
     */
    private final Map<Direction, ConnectionConfig> connectionConfigs = new EnumMap<>(Direction.class);

    /**
     * Whether this node is in a loaded chunk and should participate in ticking.
     */
    private boolean ticking = true;

    /**
     * Reference to the network this node belongs to. Null if orphaned.
     */
    @Nullable
    private ConduitNetwork network;

    public ConduitNodeImpl(BlockPos pos) {
        this.pos = pos.immutable();
    }

    // ---- IConduitNode implementation ----

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public Map<Direction, IConnectionConfig> getConnections() {
        return Collections.unmodifiableMap(connectionConfigs);
    }

    @Override
    @Nullable
    public IConnectionConfig getConnection(Direction direction) {
        return connectionConfigs.get(direction);
    }

    @Override
    public boolean isConnected(Direction direction) {
        ConnectionConfig config = connectionConfigs.get(direction);
        if (config == null) return false;
        ConnectionStatus status = config.getStatus();
        return status == ConnectionStatus.CONNECTED_CONDUIT || status == ConnectionStatus.CONNECTED_BLOCK;
    }

    // ---- Graph node methods ----

    /**
     * @return the neighbor graph nodes (conduit-to-conduit only)
     */
    public Map<Direction, ConduitNodeImpl> getNeighborNodes() {
        return neighborNodes;
    }

    /**
     * Set a graph neighbor in the given direction.
     */
    public void setNeighbor(Direction direction, @Nullable ConduitNodeImpl neighbor) {
        if (neighbor != null) {
            neighborNodes.put(direction, neighbor);
        } else {
            neighborNodes.remove(direction);
        }
    }

    /**
     * Remove all graph neighbor references.
     */
    public void clearNeighbors() {
        neighborNodes.clear();
    }

    /**
     * @return the set of directions that have conduit-to-conduit connections
     */
    public Set<Direction> getConduitConnectedDirections() {
        Set<Direction> dirs = EnumSet.noneOf(Direction.class);
        for (Map.Entry<Direction, ConnectionConfig> entry : connectionConfigs.entrySet()) {
            if (entry.getValue().getStatus() == ConnectionStatus.CONNECTED_CONDUIT) {
                dirs.add(entry.getKey());
            }
        }
        return dirs;
    }

    /**
     * @return true if this node has at least one CONNECTED_BLOCK connection
     */
    public boolean isBlockEndpoint() {
        for (ConnectionConfig config : connectionConfigs.values()) {
            if (config.getStatus() == ConnectionStatus.CONNECTED_BLOCK) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the directions that have CONNECTED_BLOCK status
     */
    public Set<Direction> getBlockEndpointDirections() {
        Set<Direction> dirs = EnumSet.noneOf(Direction.class);
        for (Map.Entry<Direction, ConnectionConfig> entry : connectionConfigs.entrySet()) {
            if (entry.getValue().getStatus() == ConnectionStatus.CONNECTED_BLOCK) {
                dirs.add(entry.getKey());
            }
        }
        return dirs;
    }

    // ---- Connection config sync ----

    /**
     * Update the cached connection configs from the block entity's ConnectionContainer.
     * Called by the network manager whenever the block entity signals a change.
     */
    public void syncFromContainer(ConnectionContainer container) {
        connectionConfigs.clear();
        for (Direction dir : Direction.values()) {
            connectionConfigs.put(dir, container.getConfig(dir));
        }
    }

    // ---- Ticking state ----

    /**
     * @return true if this node is in a loaded chunk and should participate in ticking
     */
    public boolean isTicking() {
        return ticking;
    }

    public void setTicking(boolean ticking) {
        this.ticking = ticking;
    }

    // ---- Network reference ----

    /**
     * @return the network this node belongs to, or null if orphaned
     */
    @Nullable
    public ConduitNetwork getNetwork() {
        return network;
    }

    public void setNetwork(@Nullable ConduitNetwork network) {
        this.network = network;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConduitNodeImpl other)) return false;
        return pos.equals(other.pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }

    @Override
    public String toString() {
        return "ConduitNode[" + pos.toShortString() + "]";
    }
}
