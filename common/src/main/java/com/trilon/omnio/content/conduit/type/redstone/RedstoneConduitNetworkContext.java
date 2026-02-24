package com.trilon.omnio.content.conduit.type.redstone;

import com.trilon.omnio.content.conduit.network.ConduitNetworkContext;

import java.util.Arrays;

/**
 * Per-network redstone signal state for redstone conduit networks.
 *
 * <p>Maintains 16 signal channels (corresponding to DyeColor ordinals 0-15).
 * Each channel holds the strongest signal strength (0-15) detected during the
 * current tick's extract phase. The insert phase then outputs those strengths
 * to connected blocks.</p>
 *
 * <p>Signals are recalculated every tick — there is no persistent buffer.
 * On merge, channel strengths are combined (max of each channel).
 * On split, channels are copied in full (every fragment sees the same signals
 * until the next recalculation).</p>
 */
public class RedstoneConduitNetworkContext extends ConduitNetworkContext {

    /**
     * Signal strength per channel (index = DyeColor ordinal, value = 0-15).
     */
    private final int[] channelStrengths = new int[16];

    public RedstoneConduitNetworkContext() {
        // All channels start at 0
    }

    /**
     * @param channel the channel index (0-15)
     * @return the signal strength for that channel (0-15)
     */
    public int getSignal(int channel) {
        if (channel < 0 || channel >= 16) return 0;
        return channelStrengths[channel];
    }

    /**
     * Set the signal strength for a channel.
     *
     * @param channel  the channel index (0-15)
     * @param strength the signal strength (clamped to 0-15)
     */
    public void setSignal(int channel, int strength) {
        if (channel < 0 || channel >= 16) return;
        int clamped = Math.max(0, Math.min(15, strength));
        if (channelStrengths[channel] != clamped) {
            channelStrengths[channel] = clamped;
            markDirty();
        }
    }

    /**
     * Update a channel to the maximum of its current value and the given strength.
     * Used during the extract phase when multiple sources feed the same channel.
     *
     * @param channel  the channel index (0-15)
     * @param strength the signal strength to consider
     */
    public void updateSignal(int channel, int strength) {
        if (channel < 0 || channel >= 16) return;
        int clamped = Math.max(0, Math.min(15, strength));
        if (clamped > channelStrengths[channel]) {
            channelStrengths[channel] = clamped;
            markDirty();
        }
    }

    /**
     * Clear all channel strengths to 0. Called at the start of each tick
     * before the extract phase recalculates them.
     */
    public void clearSignals() {
        boolean hadSignal = false;
        for (int i = 0; i < 16; i++) {
            if (channelStrengths[i] != 0) hadSignal = true;
            channelStrengths[i] = 0;
        }
        if (hadSignal) markDirty();
    }

    /**
     * @return true if any channel has a non-zero signal
     */
    public boolean hasAnySignal() {
        for (int s : channelStrengths) {
            if (s > 0) return true;
        }
        return false;
    }

    // ---- Merge / Split ----

    @Override
    public void mergeFrom(ConduitNetworkContext other) {
        if (other instanceof RedstoneConduitNetworkContext rsOther) {
            for (int i = 0; i < 16; i++) {
                channelStrengths[i] = Math.max(channelStrengths[i], rsOther.channelStrengths[i]);
            }
        }
        markDirty();
    }

    @Override
    public ConduitNetworkContext split(double fraction) {
        // Redstone signals are stateless per-tick; just copy current strengths
        RedstoneConduitNetworkContext newCtx = new RedstoneConduitNetworkContext();
        System.arraycopy(this.channelStrengths, 0, newCtx.channelStrengths, 0, 16);
        markDirty();
        return newCtx;
    }

    @Override
    public String toString() {
        return "RedstoneContext" + Arrays.toString(channelStrengths);
    }
}
