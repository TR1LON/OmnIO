package com.trilon.omnio.api.conduit;

/**
 * Per-side connection settings for a conduit node.
 * Controls how resources flow through each face of the conduit bundle.
 */
public interface IConnectionConfig {

    /**
     * @return the current connection status for this side
     */
    ConnectionStatus getStatus();

    /**
     * @return the transfer mode for this connection
     */
    TransferMode getTransferMode();

    /**
     * @return the priority for insertion (higher = receives resources first)
     */
    int getPriority();

    /**
     * @return the redstone control mode for this connection
     */
    RedstoneMode getRedstoneMode();

    /**
     * Transfer mode for a conduit connection side.
     */
    enum TransferMode {
        /** No resource transfer on this side */
        DISABLED,
        /** Only extract resources from the adjacent block */
        EXTRACT,
        /** Only insert resources into the adjacent block */
        INSERT,
        /** Both extract and insert on this side */
        BOTH
    }

    /**
     * Redstone control mode for a conduit connection.
     */
    enum RedstoneMode {
        /** Always active regardless of redstone */
        ALWAYS_ACTIVE,
        /** Active only when receiving a redstone signal */
        ACTIVE_WITH_SIGNAL,
        /** Active only when NOT receiving a redstone signal */
        ACTIVE_WITHOUT_SIGNAL,
        /** Never active */
        NEVER_ACTIVE
    }
}
