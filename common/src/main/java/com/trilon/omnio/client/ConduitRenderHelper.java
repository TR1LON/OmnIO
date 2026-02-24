package com.trilon.omnio.client;

import com.trilon.omnio.api.conduit.ConduitSlot;
import com.trilon.omnio.api.conduit.IConduitType;
import com.trilon.omnio.api.tier.BaseTier;
import com.trilon.omnio.api.tier.ITier;
import com.trilon.omnio.content.conduit.network.ConduitTypeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

/**
 * Color mapping utilities for conduit rendering and item tinting.
 */
public final class ConduitRenderHelper {

    // ========================================================================
    // Type base colors (packed 0xRRGGBB)
    // ========================================================================

    private static final int COLOR_ENERGY   = 0xFFAA00;  // warm yellow-orange
    private static final int COLOR_FLUID    = 0x3399FF;  // sky blue
    private static final int COLOR_ITEM     = 0x44BB44;  // green
    private static final int COLOR_REDSTONE = 0xCC0000;  // dark red
    private static final int COLOR_UNKNOWN  = 0xAAAAAA;  // gray fallback

    // Tier brightness modifiers (multiplied against base color)
    private static final float TIER_BASIC_MUL    = 0.7f;
    private static final float TIER_ADVANCED_MUL = 0.85f;
    private static final float TIER_ELITE_MUL    = 1.0f;
    private static final float TIER_ULTIMATE_MUL = 1.0f;
    private static final float TIER_CREATIVE_MUL = 1.0f;

    // Tier additional glow (added to each channel)
    private static final int TIER_BASIC_ADD    = 0;
    private static final int TIER_ADVANCED_ADD = 10;
    private static final int TIER_ELITE_ADD    = 25;
    private static final int TIER_ULTIMATE_ADD = 40;
    private static final int TIER_CREATIVE_ADD = 60;

    // ========================================================================
    // DyeColor channel colors (packed 0xRRGGBB)
    // ========================================================================

    private static final int[] DYE_COLORS = new int[16];

    static {
        DYE_COLORS[DyeColor.WHITE.getId()]      = 0xF9FFFE;
        DYE_COLORS[DyeColor.ORANGE.getId()]     = 0xF9801D;
        DYE_COLORS[DyeColor.MAGENTA.getId()]    = 0xC74EBD;
        DYE_COLORS[DyeColor.LIGHT_BLUE.getId()] = 0x3AB3DA;
        DYE_COLORS[DyeColor.YELLOW.getId()]     = 0xFED83D;
        DYE_COLORS[DyeColor.LIME.getId()]       = 0x80C71F;
        DYE_COLORS[DyeColor.PINK.getId()]       = 0xF38BAA;
        DYE_COLORS[DyeColor.GRAY.getId()]       = 0x474F52;
        DYE_COLORS[DyeColor.LIGHT_GRAY.getId()] = 0x9D9D97;
        DYE_COLORS[DyeColor.CYAN.getId()]       = 0x169C9C;
        DYE_COLORS[DyeColor.PURPLE.getId()]     = 0x8932B8;
        DYE_COLORS[DyeColor.BLUE.getId()]       = 0x3C44AA;
        DYE_COLORS[DyeColor.BROWN.getId()]      = 0x835432;
        DYE_COLORS[DyeColor.GREEN.getId()]      = 0x5E7C16;
        DYE_COLORS[DyeColor.RED.getId()]        = 0xB02E26;
        DYE_COLORS[DyeColor.BLACK.getId()]      = 0x1D1D21;
    }

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Returns the packed 0xRRGGBB color for a conduit type, factoring in tier.
     */
    public static int getConduitColor(ResourceLocation conduitId) {
        int baseColor = getTypeBaseColor(conduitId);
        BaseTier tier = getConduitTier(conduitId);
        return applyTierModifier(baseColor, tier);
    }

    /**
     * Returns the packed 0xRRGGBB color for a ConduitSlot (type + channel tint).
     * The core body uses the type color; the channel color is returned separately.
     */
    public static int getSlotColor(ConduitSlot slot) {
        return getConduitColor(slot.conduitId());
    }

    /**
     * Returns the packed 0xRRGGBB channel color for rendering the channel indicator.
     */
    public static int getChannelColor(int channel) {
        if (channel < 0 || channel >= 16) return DYE_COLORS[0];
        return DYE_COLORS[channel];
    }

    /**
     * Returns the item tint color for a conduit item (used by ItemColor).
     *
     * @param conduitId the conduit type ID
     * @param tintIndex layer index (0 = base color, 1 = channel color)
     * @param channel   the channel (0–15)
     * @return packed 0xAARRGGBB color
     */
    public static int getItemTintColor(ResourceLocation conduitId, int tintIndex, int channel) {
        if (tintIndex == 0) {
            return 0xFF000000 | getConduitColor(conduitId);
        } else if (tintIndex == 1) {
            return 0xFF000000 | getChannelColor(channel);
        }
        return 0xFFFFFFFF;
    }

    /**
     * Extracts RGB components from a packed color.
     *
     * @return float[3] = {r, g, b} each in [0, 1]
     */
    public static float[] unpackRGB(int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new float[]{r, g, b};
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private static int getTypeBaseColor(ResourceLocation conduitId) {
        String path = conduitId.getPath();
        if (path.startsWith("energy_conduit")) return COLOR_ENERGY;
        if (path.startsWith("fluid_conduit")) return COLOR_FLUID;
        if (path.startsWith("item_conduit")) return COLOR_ITEM;
        if (path.startsWith("redstone_conduit")) return COLOR_REDSTONE;
        return COLOR_UNKNOWN;
    }

    @SuppressWarnings("rawtypes")
    private static BaseTier getConduitTier(ResourceLocation conduitId) {
        IConduitType type = ConduitTypeRegistry.get(conduitId);
        if (type != null && type.getTicker() != null) {
            // Extract tier from the type's ticker or the type itself
            String path = conduitId.getPath();
            if (path.endsWith("_basic")) return BaseTier.BASIC;
            if (path.endsWith("_advanced")) return BaseTier.ADVANCED;
            if (path.endsWith("_elite")) return BaseTier.ELITE;
            if (path.endsWith("_ultimate")) return BaseTier.ULTIMATE;
            if (path.endsWith("_creative")) return BaseTier.CREATIVE;
        }
        return BaseTier.BASIC;
    }

    private static int applyTierModifier(int baseColor, BaseTier tier) {
        float mul;
        int add;
        switch (tier) {
            case BASIC -> { mul = TIER_BASIC_MUL; add = TIER_BASIC_ADD; }
            case ADVANCED -> { mul = TIER_ADVANCED_MUL; add = TIER_ADVANCED_ADD; }
            case ELITE -> { mul = TIER_ELITE_MUL; add = TIER_ELITE_ADD; }
            case ULTIMATE -> { mul = TIER_ULTIMATE_MUL; add = TIER_ULTIMATE_ADD; }
            case CREATIVE -> { mul = TIER_CREATIVE_MUL; add = TIER_CREATIVE_ADD; }
            default -> { mul = 1.0f; add = 0; }
        }

        int r = Math.clamp((int) (((baseColor >> 16) & 0xFF) * mul) + add, 0, 255);
        int g = Math.clamp((int) (((baseColor >> 8) & 0xFF) * mul) + add, 0, 255);
        int b = Math.clamp((int) ((baseColor & 0xFF) * mul) + add, 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private ConduitRenderHelper() {}
}
