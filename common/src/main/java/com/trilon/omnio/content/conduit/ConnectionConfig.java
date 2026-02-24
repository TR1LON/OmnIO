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
    /** Redstone conduit channel (0-15, maps to DyeColor). Ignored by non-redstone conduits. */
    private int channel;

    public ConnectionConfig() {
        this.status = ConnectionStatus.DISCONNECTED;
        this.transferMode = TransferMode.DISABLED;
        this.priority = 0;
        this.redstoneMode = RedstoneMode.ALWAYS_ACTIVE;
        this.channel = 0;
    }

    public ConnectionConfig(ConnectionStatus status, TransferMode transferMode, int priority, RedstoneMode redstoneMode) {
        this(status, transferMode, priority, redstoneMode, 0);
    }

    public ConnectionConfig(ConnectionStatus status, TransferMode transferMode, int priority, RedstoneMode redstoneMode, int channel) {
        this.status = status;
        this.transferMode = transferMode;
        this.priority = priority;
        this.redstoneMode = redstoneMode;
        this.channel = Math.max(0, Math.min(15, channel));
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
     * @return the redstone channel (0-15) for this connection
     */
    @Override
    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = Math.max(0, Math.min(15, channel));
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
    private static final String TAG_CHANNEL = "Channel";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_STATUS, status.name());
        tag.putString(TAG_TRANSFER_MODE, transferMode.name());
        tag.putInt(TAG_PRIORITY, priority);
        tag.putString(TAG_REDSTONE_MODE, redstoneMode.name());
        if (channel != 0) {
            tag.putInt(TAG_CHANNEL, channel);
        }
        return tag;
    }

    public static ConnectionConfig load(CompoundTag tag) {
        ConnectionStatus status = safeEnum(ConnectionStatus.class, tag.getString(TAG_STATUS), ConnectionStatus.DISCONNECTED);
        TransferMode transferMode = safeEnum(TransferMode.class, tag.getString(TAG_TRANSFER_MODE), TransferMode.DISABLED);
        int priority = tag.getInt(TAG_PRIORITY);
        RedstoneMode redstoneMode = safeEnum(RedstoneMode.class, tag.getString(TAG_REDSTONE_MODE), RedstoneMode.ALWAYS_ACTIVE);
        int channel = tag.contains(TAG_CHANNEL) ? tag.getInt(TAG_CHANNEL) : 0;
        return new ConnectionConfig(status, transferMode, priority, redstoneMode, channel);
    }

    /**
     * Safely parse an enum from a string name, returning a fallback if the name is invalid.
     */
    private static <E extends Enum<E>> E safeEnum(Class<E> enumClass, String name, E fallback) {
        try {
            return Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
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
