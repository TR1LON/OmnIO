package com.trilon.omnio.api.conduit.network;

import java.util.Collection;

import com.trilon.omnio.api.conduit.IConduitNode;
import com.trilon.omnio.api.conduit.IConduitType;

/**
 * Represents a connected graph of conduit nodes of the same type.
 * Provides access to all nodes, endpoints, and the network's mutable context.
 */
public interface IConduitNetwork {

    /**
     * @return the conduit type that all nodes in this network share
     */
    IConduitType<?> getType();

    /**
     * @return all nodes in this network (including non-ticking ones in unloaded chunks)
     */
    Collection<? extends IConduitNode> getAllNodes();

    /**
     * @return only nodes in currently ticking (loaded) chunks
     */
    Collection<? extends IConduitNode> getTickingNodes();

    /**
     * @return nodes that have at least one CONNECTED_BLOCK connection
     */
    Collection<? extends IConduitNode> getBlockEndpoints();

    /**
     * @return the per-network mutable context (energy pool, locked fluid, etc.)
     */
    IConduitNetworkContext<?> getContext();

    /**
     * @return the number of nodes in this network
     */
    int size();

    /**
     * @return a unique stable identifier for this network instance
     */
    long getId();
}
