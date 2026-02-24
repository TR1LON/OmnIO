package com.trilon.omnio.api.tier;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;

/**
 * Base tier levels shared across all conduit types.
 * Each conduit type maps these base tiers to type-specific stats
 * (capacity, transfer rate, speed, etc.).
 */
public enum BaseTier implements StringRepresentable {

    BASIC(0, "basic", ChatFormatting.GREEN),
    ADVANCED(1, "advanced", ChatFormatting.RED),
    ELITE(2, "elite", ChatFormatting.AQUA),
    ULTIMATE(3, "ultimate", ChatFormatting.LIGHT_PURPLE),
    CREATIVE(4, "creative", ChatFormatting.GOLD);

    public static final Codec<BaseTier> CODEC = StringRepresentable.fromValues(BaseTier::values);

    private final int level;
    private final String serializedName;
    private final ChatFormatting color;

    BaseTier(int level, String serializedName, ChatFormatting color) {
        this.level = level;
        this.serializedName = serializedName;
        this.color = color;
    }

    /**
     * @return the numeric tier level (0 = BASIC, 4 = CREATIVE)
     */
    public int getLevel() {
        return level;
    }

    /**
     * @return the chat formatting color associated with this tier
     */
    public ChatFormatting getColor() {
        return color;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * @param other another base tier
     * @return true if this tier is strictly higher than the other
     */
    public boolean isHigherThan(BaseTier other) {
        return this.level > other.level;
    }
}
