package com.trilon.omnio.content.conduit;

import net.minecraft.core.Direction;

/**
 * Calculates per-direction offsets for conduit arms in a bundle.
 * <p>
 * When multiple conduits in a bundle connect in the same direction, their
 * connector arms must be spread apart in the plane <em>perpendicular</em>
 * to the travel direction — otherwise they overlap.
 * <p>
 * Offset logic inspired by EnderIO's {@code OffsetHelper}.
 * <pre>
 *  Layout for 4+ conduits (1-indexed):
 *   615
 *   294
 *   738
 * </pre>
 */
public final class ConduitOffsetHelper {

    /** Grid step in block-space units (3 pixels = 3/16 of a block). */
    public static final float STEP = 3.0f / 16.0f;

    /**
     * 2D offset in the plane perpendicular to the arm direction.
     *
     * @param index the conduit's position among those connected in this direction (0-based)
     * @param total the number of conduits connected in this direction
     * @return int[2] = {a, b} grid-unit offsets
     */
    public static int[] offsetConduit(int index, int total) {
        if (total <= 1) return new int[]{0, 0};

        if (total == 2) {
            return index == 0 ? new int[]{0, -1} : new int[]{0, 1};
        }

        if (total == 3) {
            return switch (index) {
                case 0 -> new int[]{-1, -1};
                case 1 -> new int[]{0, 0};
                case 2 -> new int[]{1, 1};
                default -> new int[]{0, 0};
            };
        }

        // 4–9 conduits: spiral layout
        return switch (index) {
            case 0 -> new int[]{0, -1};
            case 1 -> new int[]{-1, 0};
            case 2 -> new int[]{0, 1};
            case 3 -> new int[]{1, 0};
            case 4 -> new int[]{1, -1};
            case 5 -> new int[]{-1, -1};
            case 6 -> new int[]{-1, 1};
            case 7 -> new int[]{1, 1};
            case 8 -> new int[]{0, 0};
            default -> new int[]{0, 0};
        };
    }

    /**
     * Convert a 2D perpendicular-plane offset to a 3D block-space translation.
     *
     * @param axis     the axis of travel (arm direction)
     * @param offset2D the 2D grid-unit offset (from {@link #offsetConduit})
     * @return float[3] = {dx, dy, dz} relative to block center (0.5, 0.5, 0.5)
     */
    public static float[] translationFor(Direction.Axis axis, int[] offset2D) {
        float a = offset2D[0] * STEP;
        float b = offset2D[1] * STEP;
        return switch (axis) {
            case X -> new float[]{0, b, a};   // arm goes ±X, offset in YZ
            case Y -> new float[]{a, 0, b};   // arm goes ±Y, offset in XZ
            case Z -> new float[]{a, b, 0};   // arm goes ±Z, offset in XY
        };
    }

    /**
     * Calculate a default 3D offset for the core of an unconnected conduit.
     *
     * @param globalIndex the conduit's overall index in the bundle (0-based)
     * @param bundleSize  the number of conduits in the bundle
     * @param mainAxis    the dominant axis of the bundle
     * @return float[3] relative to block center
     */
    public static float[] defaultCoreOffset(int globalIndex, int bundleSize, Direction.Axis mainAxis) {
        int[] off2D = offsetConduit(globalIndex, bundleSize);
        return translationFor(mainAxis, off2D);
    }

    /**
     * Determine the dominant ("main") axis for a bundle.
     * Prefers the axis of the last connected direction, defaulting to Z.
     *
     * @param hasConnection boolean[6] indexed by {@link Direction#get3DDataValue()}
     */
    public static Direction.Axis findMainAxis(boolean[] hasConnection) {
        Direction.Axis axis = Direction.Axis.Z;
        for (Direction dir : Direction.values()) {
            if (hasConnection[dir.get3DDataValue()]) {
                axis = dir.getAxis();
            }
        }
        return axis;
    }

    private ConduitOffsetHelper() {}
}
