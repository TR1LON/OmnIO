package com.trilon.omnio.content.conduit;

import com.trilon.omnio.api.conduit.ConduitSlot;
import com.trilon.omnio.api.conduit.ConnectionStatus;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import java.util.Set;

/**
 * Manages VoxelShapes for conduit bundles with per-conduit positioning.
 * <p>
 * Each conduit slot occupies a small 3×3×3-pixel core within the block center.
 * Connectors extend from the core to the face edge (2px wide arms).
 * Block connections get an additional "pad" — a wider plate on the block face
 * that serves as a click target for per-face configuration.
 */
public final class ConduitShape {

    /** Core size in pixels (each conduit core is CORE_SIZE³). */
    private static final int CORE_SIZE = 3;

    /**
     * Pad dimensions in pixels. A rectangular plate sitting on the block face.
     * The pad is PAD_SIZE × PAD_SIZE on the face, PAD_DEPTH thick.
     */
    private static final float PAD_SIZE = 6.0f;
    private static final float PAD_DEPTH = 2.0f;

    /**
     * Pixel offsets from block-center (8,8,8) for up to 9 conduit slot positions.
     * Layout is a 3×3 grid in the XZ plane centered on Y=8.
     * <pre>
     *   Slot 0: center
     *   Slot 1: west          Slot 2: east
     *   Slot 3: north         Slot 4: south
     *   Slot 5: north-west    Slot 6: north-east
     *   Slot 7: south-west    Slot 8: south-east
     * </pre>
     */
    private static final float[][] SLOT_OFFSETS = {
            {0, 0, 0},       // 0: center
            {-3, 0, 0},      // 1: west
            {3, 0, 0},       // 2: east
            {0, 0, -3},      // 3: north
            {0, 0, 3},       // 4: south
            {-3, 0, -3},     // 5: NW
            {3, 0, -3},      // 6: NE
            {-3, 0, 3},      // 7: SW
            {3, 0, 3},       // 8: SE
    };

    /** Precomputed core VoxelShapes for each of the 9 slot positions. */
    private static final VoxelShape[] CORE_SHAPES = new VoxelShape[9];

    /** Precomputed connector VoxelShapes for each slot position × 6 directions. */
    private static final VoxelShape[][] CONNECTOR_SHAPES = new VoxelShape[9][6];

    /** Precomputed pad VoxelShapes per direction (shared by all conduits on that face). */
    private static final VoxelShape[] PAD_SHAPES = new VoxelShape[6];

    static {
        float half = CORE_SIZE / 2.0f;
        for (int slot = 0; slot < 9; slot++) {
            float cx = 8 + SLOT_OFFSETS[slot][0];
            float cy = 8 + SLOT_OFFSETS[slot][1];
            float cz = 8 + SLOT_OFFSETS[slot][2];

            // Core shape
            CORE_SHAPES[slot] = Block.box(
                    cx - half, cy - half, cz - half,
                    cx + half, cy + half, cz + half
            );

            // Connector arms extending from core edge to block face
            for (Direction dir : Direction.values()) {
                CONNECTOR_SHAPES[slot][dir.get3DDataValue()] = buildConnector(cx, cy, cz, half, dir);
            }
        }

        // Build pad shapes (centered on each face, PAD_SIZE × PAD_SIZE × PAD_DEPTH)
        float padHalf = PAD_SIZE / 2.0f;
        for (Direction dir : Direction.values()) {
            PAD_SHAPES[dir.get3DDataValue()] = buildPad(dir, padHalf, PAD_DEPTH);
        }
    }

    private static VoxelShape buildConnector(float cx, float cy, float cz, float half, Direction dir) {
        float x1, y1, z1, x2, y2, z2;
        switch (dir) {
            case DOWN -> {
                x1 = cx - half; y1 = 0;          z1 = cz - half;
                x2 = cx + half; y2 = cy - half;   z2 = cz + half;
            }
            case UP -> {
                x1 = cx - half; y1 = cy + half;   z1 = cz - half;
                x2 = cx + half; y2 = 16;          z2 = cz + half;
            }
            case NORTH -> {
                x1 = cx - half; y1 = cy - half;   z1 = 0;
                x2 = cx + half; y2 = cy + half;   z2 = cz - half;
            }
            case SOUTH -> {
                x1 = cx - half; y1 = cy - half;   z1 = cz + half;
                x2 = cx + half; y2 = cy + half;   z2 = 16;
            }
            case WEST -> {
                x1 = 0;          y1 = cy - half;   z1 = cz - half;
                x2 = cx - half;  y2 = cy + half;   z2 = cz + half;
            }
            case EAST -> {
                x1 = cx + half;  y1 = cy - half;   z1 = cz - half;
                x2 = 16;         y2 = cy + half;   z2 = cz + half;
            }
            default -> { return Shapes.empty(); }
        }
        // Ensure min < max (connector arm only exists if there's distance to travel)
        if (x1 >= x2 || y1 >= y2 || z1 >= z2) return Shapes.empty();
        return Block.box(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
        );
    }

    /**
     * Build a pad VoxelShape centered on the given face.
     * The pad is padHalf*2 × padHalf*2 on the face plane, padDepth thick from the face inward.
     */
    private static VoxelShape buildPad(Direction dir, float padHalf, float padDepth) {
        float cx = 8, cy = 8, cz = 8; // center of block in pixel space
        return switch (dir) {
            case DOWN  -> Block.box(cx - padHalf, 0, cz - padHalf, cx + padHalf, padDepth, cz + padHalf);
            case UP    -> Block.box(cx - padHalf, 16 - padDepth, cz - padHalf, cx + padHalf, 16, cz + padHalf);
            case NORTH -> Block.box(cx - padHalf, cy - padHalf, 0, cx + padHalf, cy + padHalf, padDepth);
            case SOUTH -> Block.box(cx - padHalf, cy - padHalf, 16 - padDepth, cx + padHalf, cy + padHalf, 16);
            case WEST  -> Block.box(0, cy - padHalf, cz - padHalf, padDepth, cy + padHalf, cz + padHalf);
            case EAST  -> Block.box(16 - padDepth, cy - padHalf, cz - padHalf, 16, cy + padHalf, cz + padHalf);
        };
    }

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Builds the combined VoxelShape for a conduit bundle.
     *
     * @param slots       the conduit slots present in this bundle (should be in deterministic order)
     * @param connections map of slot → connection container
     * @return combined hit-detection shape
     */
    public static VoxelShape buildCombinedShape(Collection<ConduitSlot> slots,
                                                 Map<ConduitSlot, ConnectionContainer> connections) {
        if (slots.isEmpty()) return CORE_SHAPES[0]; // fallback

        List<ConduitSlot> slotList = new ArrayList<>(slots);
        float half = CORE_SIZE / 2.0f; // 1.5 pixels

        // Pre-compute per-direction connected conduit lists (same logic as renderer)
        @SuppressWarnings("unchecked")
        List<ConduitSlot>[] connectedPerDir = new List[6];
        boolean[] hasConnection = new boolean[6];
        boolean[] hasBlockConnection = new boolean[6];

        for (int d = 0; d < 6; d++) {
            connectedPerDir[d] = new ArrayList<>();
            Direction dir = Direction.from3DDataValue(d);
            for (ConduitSlot slot : slotList) {
                ConnectionContainer c = connections.get(slot);
                if (c != null && c.isConnected(dir)) {
                    connectedPerDir[d].add(slot);
                    if (c.getStatus(dir) == ConnectionStatus.CONNECTED_BLOCK) {
                        hasBlockConnection[d] = true;
                    }
                }
            }
            hasConnection[d] = !connectedPerDir[d].isEmpty();
        }

        Direction.Axis mainAxis = ConduitOffsetHelper.findMainAxis(hasConnection);
        VoxelShape combined = Shapes.empty();

        int globalIdx = 0;
        for (ConduitSlot slot : slotList) {
            if (globalIdx >= 9) break;

            // Collect per-direction 3D offsets (same algorithm as ConduitBundleRenderer)
            EnumMap<Direction, float[]> dirOffsets = new EnumMap<>(Direction.class);
            for (int d = 0; d < 6; d++) {
                Direction dir = Direction.from3DDataValue(d);
                List<ConduitSlot> connected = connectedPerDir[d];
                int myIdx = connected.indexOf(slot);
                if (myIdx >= 0) {
                    int[] off2D = ConduitOffsetHelper.offsetConduit(myIdx, connected.size());
                    dirOffsets.put(dir, ConduitOffsetHelper.translationFor(dir.getAxis(), off2D));
                }
            }

            // Compute core bounds — mirrors the renderer's bounding-box logic
            float coreMinX, coreMinY, coreMinZ, coreMaxX, coreMaxY, coreMaxZ;

            if (dirOffsets.isEmpty()) {
                // Unconnected conduit: use default grid position
                float[] def = ConduitOffsetHelper.defaultCoreOffset(globalIdx, slotList.size(), mainAxis);
                float cx = 8 + def[0] * 16;
                float cy = 8 + def[1] * 16;
                float cz = 8 + def[2] * 16;
                coreMinX = cx - half; coreMinY = cy - half; coreMinZ = cz - half;
                coreMaxX = cx + half; coreMaxY = cy + half; coreMaxZ = cz + half;
            } else {
                // Bounding box of all per-direction offsets (in block units)
                float minOx = Float.MAX_VALUE, minOy = Float.MAX_VALUE, minOz = Float.MAX_VALUE;
                float maxOx = -Float.MAX_VALUE, maxOy = -Float.MAX_VALUE, maxOz = -Float.MAX_VALUE;
                for (float[] o : dirOffsets.values()) {
                    minOx = Math.min(minOx, o[0]); minOy = Math.min(minOy, o[1]); minOz = Math.min(minOz, o[2]);
                    maxOx = Math.max(maxOx, o[0]); maxOy = Math.max(maxOy, o[1]); maxOz = Math.max(maxOz, o[2]);
                }
                // Convert from block units to pixel space (center at 8)
                coreMinX = 8 + minOx * 16 - half; coreMinY = 8 + minOy * 16 - half; coreMinZ = 8 + minOz * 16 - half;
                coreMaxX = 8 + maxOx * 16 + half; coreMaxY = 8 + maxOy * 16 + half; coreMaxZ = 8 + maxOz * 16 + half;
            }

            // Add core shape
            combined = Shapes.or(combined, Block.box(coreMinX, coreMinY, coreMinZ,
                    coreMaxX, coreMaxY, coreMaxZ));

            // Add arm shapes for each connected direction at its offset position
            for (var entry : dirOffsets.entrySet()) {
                Direction dir = entry.getKey();
                float[] off = entry.getValue();
                // Arm center in pixel space
                float armCx = 8 + off[0] * 16;
                float armCy = 8 + off[1] * 16;
                float armCz = 8 + off[2] * 16;
                combined = Shapes.or(combined, buildConnector(armCx, armCy, armCz, half, dir));
            }

            globalIdx++;
        }

        // Add pad shapes on block-connected faces
        for (int d = 0; d < 6; d++) {
            if (hasBlockConnection[d]) {
                combined = Shapes.or(combined, PAD_SHAPES[d]);
            }
        }

        return combined;
    }

    /**
     * Returns the core VoxelShape for a specific slot index (0–8).
     */
    public static VoxelShape getCoreShape(int slotIndex) {
        return CORE_SHAPES[Math.clamp(slotIndex, 0, 8)];
    }

    /**
     * Returns the connector VoxelShape for a specific slot index and direction.
     */
    public static VoxelShape getConnectorShape(int slotIndex, Direction dir) {
        return CONNECTOR_SHAPES[Math.clamp(slotIndex, 0, 8)][dir.get3DDataValue()];
    }

    /**
     * Returns the pad VoxelShape for a specific direction.
     */
    public static VoxelShape getPadShape(Direction dir) {
        return PAD_SHAPES[dir.get3DDataValue()];
    }

    /**
     * Determines which pad (if any) a hit position falls within.
     * The position should be in block-local coordinates (0–1 range).
     *
     * @param localX block-local X (hitVec.x - blockPos.x)
     * @param localY block-local Y
     * @param localZ block-local Z
     * @return the Direction of the pad that contains this point, or null if not inside any pad
     */
    @Nullable
    public static Direction hitTestPad(double localX, double localY, double localZ) {
        // Convert to pixel space (0–16)
        double px = localX * 16.0;
        double py = localY * 16.0;
        double pz = localZ * 16.0;
        float padHalf = PAD_SIZE / 2.0f;
        // Check each direction's pad bounds
        for (Direction dir : Direction.values()) {
            double x1, y1, z1, x2, y2, z2;
            switch (dir) {
                case DOWN  -> { x1 = 8 - padHalf; y1 = 0;              z1 = 8 - padHalf; x2 = 8 + padHalf; y2 = PAD_DEPTH;      z2 = 8 + padHalf; }
                case UP    -> { x1 = 8 - padHalf; y1 = 16 - PAD_DEPTH; z1 = 8 - padHalf; x2 = 8 + padHalf; y2 = 16;             z2 = 8 + padHalf; }
                case NORTH -> { x1 = 8 - padHalf; y1 = 8 - padHalf;   z1 = 0;            x2 = 8 + padHalf; y2 = 8 + padHalf;   z2 = PAD_DEPTH; }
                case SOUTH -> { x1 = 8 - padHalf; y1 = 8 - padHalf;   z1 = 16 - PAD_DEPTH; x2 = 8 + padHalf; y2 = 8 + padHalf; z2 = 16; }
                case WEST  -> { x1 = 0;            y1 = 8 - padHalf;   z1 = 8 - padHalf; x2 = PAD_DEPTH;    y2 = 8 + padHalf;   z2 = 8 + padHalf; }
                case EAST  -> { x1 = 16 - PAD_DEPTH; y1 = 8 - padHalf; z1 = 8 - padHalf; x2 = 16;           y2 = 8 + padHalf;   z2 = 8 + padHalf; }
                default -> { continue; }
            }
            if (px >= x1 && px <= x2 && py >= y1 && py <= y2 && pz >= z1 && pz <= z2) {
                return dir;
            }
        }
        return null;
    }

    /**
     * @return pad half-size in pixels (used by renderer)
     */
    public static float getPadHalfSize() {
        return PAD_SIZE / 2.0f;
    }

    /**
     * @return pad depth in pixels (used by renderer)
     */
    public static float getPadDepth() {
        return PAD_DEPTH;
    }

    /**
     * Returns the pixel-space offset for a slot index.
     * Used by the renderer to position conduit cores.
     *
     * @return float[3] = {dx, dy, dz} offset from block center (8,8,8) in pixels
     */
    public static float[] getSlotOffset(int slotIndex) {
        return SLOT_OFFSETS[Math.clamp(slotIndex, 0, 8)];
    }

    /**
     * Returns the core size in pixels.
     */
    public static int getCoreSize() {
        return CORE_SIZE;
    }

    /**
     * Maps a ConduitSlot to its positional index within a bundle.
     * The order is determined by the iteration order of the slot collection.
     *
     * @param slots all slots in the bundle (should be in deterministic order)
     * @param target the slot to find
     * @return index 0–8, or -1 if not found
     */
    public static int getSlotIndex(Collection<ConduitSlot> slots, ConduitSlot target) {
        int idx = 0;
        for (ConduitSlot s : slots) {
            if (s.equals(target)) return idx;
            idx++;
            if (idx >= 9) break;
        }
        return -1;
    }

    /**
     * Returns a list of slot indices that are connected on the given direction.
     * Useful for rendering merged connector arms.
     */
    public static List<Integer> getConnectedSlotIndices(Collection<ConduitSlot> slots,
                                                         Map<ConduitSlot, ConnectionContainer> connections,
                                                         Direction dir) {
        List<Integer> result = new ArrayList<>();
        int idx = 0;
        for (ConduitSlot slot : slots) {
            if (idx >= 9) break;
            ConnectionContainer container = connections.get(slot);
            if (container != null && container.isConnected(dir)) {
                result.add(idx);
            }
            idx++;
        }
        return result;
    }

    private ConduitShape() {}
}
