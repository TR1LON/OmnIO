package com.trilon.omnio.content.conduit;

import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.api.conduit.IConnectionConfig;
import net.minecraft.nbt.CompoundTag;

/**
 * Concrete implementation of {@link IConnectionConfig}.
 * Stores per-side connection settings: transfer mode, priority, redstone control.
 * Serializable to/from NBT for persistence.
 */
public class ConnectionConfig implements IConnectionConfig {

    private ConnectionStatus status;
    private TransferMode transferMode;
    private int priority;
    private RedstoneMode redstoneMode;

    public ConnectionConfig() {
        this.status = ConnectionStatus.DISCONNECTED;
        this.transferMode = TransferMode.DISABLED;
        this.priority = 0;
        this.redstoneMode = RedstoneMode.ALWAYS_ACTIVE;
    }

    public ConnectionConfig(ConnectionStatus status, TransferMode transferMode, int priority, RedstoneMode redstoneMode) {
        this.status = status;
        this.transferMode = transferMode;
        this.priority = priority;
        this.redstoneMode = redstoneMode;
    }

    @Override
    public ConnectionStatus getStatus() {
        return status;
    }

    @Override
    public TransferMode getTransferMode() {
        return transferMode;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public void setStatus(ConnectionStatus status) {
        this.status = status;
    }

    public void setTransferMode(TransferMode transferMode) {
        this.transferMode = transferMode;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setRedstoneMode(RedstoneMode redstoneMode) {
        this.redstoneMode = redstoneMode;
    }

    /**
     * Cycle the transfer mode to the next value.
     * DISABLED → EXTRACT → INSERT → BOTH → DISABLED
     */
    public void cycleTransferMode() {
        TransferMode[] modes = TransferMode.values();
        this.transferMode = modes[(this.transferMode.ordinal() + 1) % modes.length];
    }

    /**
     * Cycle the redstone mode to the next value.
     */
    public void cycleRedstoneMode() {
        RedstoneMode[] modes = RedstoneMode.values();
        this.redstoneMode = modes[(this.redstoneMode.ordinal() + 1) % modes.length];
    }

    // ---- NBT Serialization ----

    private static final String TAG_STATUS = "Status";
    private static final String TAG_TRANSFER_MODE = "TransferMode";
    private static final String TAG_PRIORITY = "Priority";
    private static final String TAG_REDSTONE_MODE = "RedstoneMode";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_STATUS, status.ordinal());
        tag.putInt(TAG_TRANSFER_MODE, transferMode.ordinal());
        tag.putInt(TAG_PRIORITY, priority);
        tag.putInt(TAG_REDSTONE_MODE, redstoneMode.ordinal());
        return tag;
    }

    public static ConnectionConfig load(CompoundTag tag) {
        ConnectionStatus status = ConnectionStatus.values()[tag.getInt(TAG_STATUS)];
        TransferMode transferMode = TransferMode.values()[tag.getInt(TAG_TRANSFER_MODE)];
        int priority = tag.getInt(TAG_PRIORITY);
        RedstoneMode redstoneMode = RedstoneMode.values()[tag.getInt(TAG_REDSTONE_MODE)];
        return new ConnectionConfig(status, transferMode, priority, redstoneMode);
    }

    /**
     * @return a default config for a newly formed conduit-to-conduit connection
     */
    public static ConnectionConfig conduitConnection() {
        return new ConnectionConfig(ConnectionStatus.CONNECTED_CONDUIT, TransferMode.DISABLED, 0, RedstoneMode.ALWAYS_ACTIVE);
    }

    /**
     * @return a default config for a newly formed conduit-to-block connection
     */
    public static ConnectionConfig blockConnection() {
        return new ConnectionConfig(ConnectionStatus.CONNECTED_BLOCK, TransferMode.BOTH, 0, RedstoneMode.ALWAYS_ACTIVE);
    }
}
