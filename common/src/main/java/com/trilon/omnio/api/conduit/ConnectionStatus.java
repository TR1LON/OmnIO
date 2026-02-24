package com.trilon.omnio.api.conduit;

/**
 * Describes the connection state between a conduit node and one of its 6 neighbors.
 */
public enum ConnectionStatus {

    /** No connection exists on this side */
    DISCONNECTED,

    /** Connected to another conduit bundle block (conduit-to-conduit link) */
    CONNECTED_CONDUIT,

    /** Connected to a block endpoint (machine, chest, etc.) */
    CONNECTED_BLOCK,

    /** Connection exists but has been manually disabled by the player (e.g., via wrench) */
    DISABLED
}
