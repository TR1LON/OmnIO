package com.trilon.omnio.api.conduit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Uniquely identifies a conduit within a bundle block.
 * Combines the conduit variant ID (type + tier) with a color channel (0–15, DyeColor ordinal).
 *
 * <p>Two conduits of the same type can coexist in one bundle as long as they use
 * different channels. Each {@code ConduitSlot} forms its own independent network.</p>
 *
 * <p>Design rules:</p>
 * <ul>
 *   <li>Same conduitId + same channel = same network</li>
 *   <li>Same conduitId + different channel = separate networks</li>
 *   <li>Different conduitIds of the same base type (e.g., energy_basic vs energy_advanced)
 *       cannot coexist in one bundle regardless of channel</li>
 *   <li>Channel 0 = white (default when first placed)</li>
 * </ul>
 *
 * @param conduitId the registry ID of the conduit variant (e.g., "omnio:energy_basic")
 * @param channel   the color channel (0–15, corresponding to DyeColor ordinals)
 */
public record ConduitSlot(ResourceLocation conduitId, int channel) implements Comparable<ConduitSlot> {

    /** Default channel index (white / DyeColor.WHITE ordinal). */
    public static final int DEFAULT_CHANNEL = 0;

    /** Maximum channel index (black / DyeColor.BLACK ordinal). */
    public static final int MAX_CHANNEL = 15;

    // NBT tag keys
    private static final String TAG_CONDUIT_ID = "ConduitId";
    private static final String TAG_CHANNEL = "Channel";

    /**
     * Convenience constructor using default channel (white).
     */
    public ConduitSlot(ResourceLocation conduitId) {
        this(conduitId, DEFAULT_CHANNEL);
    }

    /**
     * Compact constructor with channel validation.
     */
    public ConduitSlot {
        if (conduitId == null) throw new IllegalArgumentException("conduitId must not be null");
        if (channel < 0 || channel > MAX_CHANNEL) {
            throw new IllegalArgumentException("channel must be 0–" + MAX_CHANNEL + ", got " + channel);
        }
    }

    /**
     * @return a ConduitSlot with the same conduitId but a different channel
     */
    public ConduitSlot withChannel(int newChannel) {
        return new ConduitSlot(conduitId, newChannel);
    }

    // ---- NBT Serialization ----

    /**
     * Serialize this slot to an NBT compound tag.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_CONDUIT_ID, conduitId.toString());
        tag.putInt(TAG_CHANNEL, channel);
        return tag;
    }

    /**
     * Deserialize a ConduitSlot from an NBT compound tag.
     *
     * @param tag the compound tag to read from
     * @return the deserialized ConduitSlot
     */
    public static ConduitSlot load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.parse(tag.getString(TAG_CONDUIT_ID));
        int ch = tag.getInt(TAG_CHANNEL);
        return new ConduitSlot(id, ch);
    }

    // ---- Comparable ----

    @Override
    public int compareTo(ConduitSlot other) {
        int cmp = this.conduitId.compareTo(other.conduitId);
        if (cmp != 0) return cmp;
        return Integer.compare(this.channel, other.channel);
    }

    @Override
    public String toString() {
        return conduitId + "#" + channel;
    }
}
