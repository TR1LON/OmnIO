package com.trilon.omnio.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trilon.omnio.api.conduit.ConduitSlot;
import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.content.conduit.ConnectionContainer;
import com.trilon.omnio.content.conduit.ConduitOffsetHelper;
import com.trilon.omnio.content.conduit.ConduitShape;
import com.trilon.omnio.content.conduit.OmniConduitBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Block Entity Renderer for conduit bundles.
 * <p>
 * Uses <strong>per-direction offsets</strong> (like EnderIO) so that multiple
 * conduits sharing a direction fan out in the perpendicular plane rather than
 * overlapping. The core for each conduit is positioned at the bounding box
 * of all its per-direction offsets, keeping a visual connection to every arm.
 */
public class ConduitBundleRenderer implements BlockEntityRenderer<OmniConduitBlockEntity> {

    /** Small inset to prevent Z-fighting at block boundaries. */
    private static final float FACE_INSET = 0.002f;

    /** Cached white-ish sprite from block atlas for UV mapping. */
    private TextureAtlasSprite whiteSprite;

    public ConduitBundleRenderer(BlockEntityRendererProvider.Context context) {
    }

    private TextureAtlasSprite getWhiteSprite() {
        if (whiteSprite == null) {
            whiteSprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(ResourceLocation.withDefaultNamespace("block/white_concrete"));
        }
        return whiteSprite;
    }

    @Override
    public void render(OmniConduitBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        List<ConduitSlot> slots = blockEntity.getSortedSlots();
        if (slots.isEmpty()) return;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());
        TextureAtlasSprite sprite = getWhiteSprite();
        float u0 = sprite.getU0(), v0 = sprite.getV0();
        float u1 = sprite.getU1(), v1 = sprite.getV1();
        float coreSize = ConduitShape.getCoreSize();
        float halfPx = coreSize / 32.0f;
        float armW = halfPx * 0.75f;

        poseStack.pushPose();

        // ---- Pre-compute: which conduit slots connect in each direction ----
        @SuppressWarnings("unchecked")
        List<ConduitSlot>[] connectedPerDir = new List[6];
        boolean[] hasConnection = new boolean[6];
        for (int d = 0; d < 6; d++) {
            connectedPerDir[d] = new ArrayList<>();
            Direction dir = Direction.from3DDataValue(d);
            for (ConduitSlot slot : slots) {
                ConnectionContainer c = blockEntity.getConnectionContainer(slot);
                if (c != null && c.isConnected(dir)) {
                    connectedPerDir[d].add(slot);
                }
            }
            hasConnection[d] = !connectedPerDir[d].isEmpty();
        }
        Direction.Axis mainAxis = ConduitOffsetHelper.findMainAxis(hasConnection);

        // ---- Render each conduit ----
        int globalIdx = 0;
        for (ConduitSlot slot : slots) {
            if (globalIdx >= 9) break;

            int color = ConduitRenderHelper.getConduitColor(slot.conduitId());
            float[] rgb = ConduitRenderHelper.unpackRGB(color);
            float r = rgb[0], g = rgb[1], b = rgb[2];

            // Collect per-direction 3D offsets for this conduit
            Map<Direction, float[]> dirOffsets = new EnumMap<>(Direction.class);
            for (int d = 0; d < 6; d++) {
                Direction dir = Direction.from3DDataValue(d);
                List<ConduitSlot> connected = connectedPerDir[d];
                int myIdx = connected.indexOf(slot);
                if (myIdx >= 0) {
                    int[] off2D = ConduitOffsetHelper.offsetConduit(myIdx, connected.size());
                    dirOffsets.put(dir, ConduitOffsetHelper.translationFor(dir.getAxis(), off2D));
                }
            }

            // ---- Determine core position ----
            float coreCx, coreCy, coreCz;
            float coreMinX, coreMinY, coreMinZ, coreMaxX, coreMaxY, coreMaxZ;

            if (dirOffsets.isEmpty()) {
                // Unconnected conduit: use default position from main axis
                float[] def = ConduitOffsetHelper.defaultCoreOffset(globalIdx, slots.size(), mainAxis);
                coreCx = 0.5f + def[0];
                coreCy = 0.5f + def[1];
                coreCz = 0.5f + def[2];
                coreMinX = coreCx - halfPx; coreMinY = coreCy - halfPx; coreMinZ = coreCz - halfPx;
                coreMaxX = coreCx + halfPx; coreMaxY = coreCy + halfPx; coreMaxZ = coreCz + halfPx;
            } else {
                // Bounding box of all per-direction offsets
                float minOx = Float.MAX_VALUE, minOy = Float.MAX_VALUE, minOz = Float.MAX_VALUE;
                float maxOx = -Float.MAX_VALUE, maxOy = -Float.MAX_VALUE, maxOz = -Float.MAX_VALUE;
                for (float[] o : dirOffsets.values()) {
                    minOx = Math.min(minOx, o[0]); minOy = Math.min(minOy, o[1]); minOz = Math.min(minOz, o[2]);
                    maxOx = Math.max(maxOx, o[0]); maxOy = Math.max(maxOy, o[1]); maxOz = Math.max(maxOz, o[2]);
                }
                coreCx = 0.5f + (minOx + maxOx) / 2f;
                coreCy = 0.5f + (minOy + maxOy) / 2f;
                coreCz = 0.5f + (minOz + maxOz) / 2f;
                // Expand core to span the full bounding box
                coreMinX = 0.5f + minOx - halfPx; coreMinY = 0.5f + minOy - halfPx; coreMinZ = 0.5f + minOz - halfPx;
                coreMaxX = 0.5f + maxOx + halfPx; coreMaxY = 0.5f + maxOy + halfPx; coreMaxZ = 0.5f + maxOz + halfPx;
            }

            // Render core
            renderBox(poseStack, consumer,
                    coreMinX, coreMinY, coreMinZ,
                    coreMaxX, coreMaxY, coreMaxZ,
                    r, g, b, 1.0f, u0, v0, u1, v1, packedLight);

            // ---- Render per-direction arms ----
            float armR = r * 0.85f, armG = g * 0.85f, armB = b * 0.85f;
            for (var entry : dirOffsets.entrySet()) {
                Direction dir = entry.getKey();
                float[] off = entry.getValue();
                float armCx = 0.5f + off[0];
                float armCy = 0.5f + off[1];
                float armCz = 0.5f + off[2];
                renderArm(poseStack, consumer, dir, armCx, armCy, armCz, halfPx, armW,
                        armR, armG, armB, u0, v0, u1, v1, packedLight);
            }

            globalIdx++;
        }

        // ---- Render pads on block-connected faces ----
        float padHalf = ConduitShape.getPadHalfSize() / 16.0f; // convert pixels to block units
        float padDepth = ConduitShape.getPadDepth() / 16.0f;
        for (int d = 0; d < 6; d++) {
            if (!hasConnection[d]) continue;
            Direction dir = Direction.from3DDataValue(d);

            // Check if any conduit on this face has a CONNECTED_BLOCK status
            boolean hasBlockConn = false;
            int padColor = 0xFF444466; // default gray
            for (ConduitSlot slot : connectedPerDir[d]) {
                ConnectionContainer c = blockEntity.getConnectionContainer(slot);
                if (c != null && c.getStatus(dir) == ConnectionStatus.CONNECTED_BLOCK) {
                    hasBlockConn = true;
                    padColor = ConduitRenderHelper.getConduitColor(slot.conduitId());
                    break; // use first conduit's color
                }
            }
            if (!hasBlockConn) continue;

            float[] padRgb = ConduitRenderHelper.unpackRGB(padColor);
            float padR = padRgb[0] * 0.7f, padG = padRgb[1] * 0.7f, padB = padRgb[2] * 0.7f;

            // Build pad box coordinates
            float px1, py1, pz1, px2, py2, pz2;
            switch (dir) {
                case DOWN -> {
                    px1 = 0.5f - padHalf; py1 = FACE_INSET;             pz1 = 0.5f - padHalf;
                    px2 = 0.5f + padHalf; py2 = FACE_INSET + padDepth;  pz2 = 0.5f + padHalf;
                }
                case UP -> {
                    px1 = 0.5f - padHalf; py1 = 1 - FACE_INSET - padDepth; pz1 = 0.5f - padHalf;
                    px2 = 0.5f + padHalf; py2 = 1 - FACE_INSET;            pz2 = 0.5f + padHalf;
                }
                case NORTH -> {
                    px1 = 0.5f - padHalf; py1 = 0.5f - padHalf; pz1 = FACE_INSET;
                    px2 = 0.5f + padHalf; py2 = 0.5f + padHalf; pz2 = FACE_INSET + padDepth;
                }
                case SOUTH -> {
                    px1 = 0.5f - padHalf; py1 = 0.5f - padHalf; pz1 = 1 - FACE_INSET - padDepth;
                    px2 = 0.5f + padHalf; py2 = 0.5f + padHalf; pz2 = 1 - FACE_INSET;
                }
                case WEST -> {
                    px1 = FACE_INSET;             py1 = 0.5f - padHalf; pz1 = 0.5f - padHalf;
                    px2 = FACE_INSET + padDepth;  py2 = 0.5f + padHalf; pz2 = 0.5f + padHalf;
                }
                case EAST -> {
                    px1 = 1 - FACE_INSET - padDepth; py1 = 0.5f - padHalf; pz1 = 0.5f - padHalf;
                    px2 = 1 - FACE_INSET;            py2 = 0.5f + padHalf; pz2 = 0.5f + padHalf;
                }
                default -> { continue; }
            }

            renderBox(poseStack, consumer,
                    px1, py1, pz1, px2, py2, pz2,
                    padR, padG, padB, 1.0f, u0, v0, u1, v1, packedLight);
        }

        poseStack.popPose();
    }

    /**
     * Renders a connector arm from just outside the core to the block face,
     * centred at (cx, cy, cz) in the perpendicular plane.
     */
    private void renderArm(PoseStack poseStack, VertexConsumer consumer,
                            Direction dir, float cx, float cy, float cz,
                            float halfPx, float armW,
                            float r, float g, float b,
                            float u0, float v0, float u1, float v1, int light) {
        float x1, y1, z1, x2, y2, z2;
        switch (dir) {
            case DOWN -> {
                x1 = cx - armW; y1 = FACE_INSET;     z1 = cz - armW;
                x2 = cx + armW; y2 = cy - halfPx;    z2 = cz + armW;
            }
            case UP -> {
                x1 = cx - armW; y1 = cy + halfPx;    z1 = cz - armW;
                x2 = cx + armW; y2 = 1 - FACE_INSET; z2 = cz + armW;
            }
            case NORTH -> {
                x1 = cx - armW; y1 = cy - armW;      z1 = FACE_INSET;
                x2 = cx + armW; y2 = cy + armW;      z2 = cz - halfPx;
            }
            case SOUTH -> {
                x1 = cx - armW; y1 = cy - armW;      z1 = cz + halfPx;
                x2 = cx + armW; y2 = cy + armW;      z2 = 1 - FACE_INSET;
            }
            case WEST -> {
                x1 = FACE_INSET;     y1 = cy - armW; z1 = cz - armW;
                x2 = cx - halfPx;    y2 = cy + armW; z2 = cz + armW;
            }
            case EAST -> {
                x1 = cx + halfPx;    y1 = cy - armW; z1 = cz - armW;
                x2 = 1 - FACE_INSET; y2 = cy + armW; z2 = cz + armW;
            }
            default -> { return; }
        }

        renderBox(poseStack, consumer,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2),
                r, g, b, 1.0f, u0, v0, u1, v1, light);
    }

    /**
     * Renders an axis-aligned box with 6 faces.
     * Face winding is counter-clockwise when viewed from outside (correct for backface culling).
     */
    private void renderBox(PoseStack poseStack, VertexConsumer consumer,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float r, float g, float b, float a,
                            float u0, float v0, float u1, float v1,
                            int light) {
        PoseStack.Pose pose = poseStack.last();

        int ri = (int) (r * 255);
        int gi = (int) (g * 255);
        int bi = (int) (b * 255);
        int ai = (int) (a * 255);

        // Down face (Y-) — viewed from below, CCW
        vertex(consumer, pose, x1, y1, z2, ri, gi, bi, ai, u0, v1, light, 0, -1, 0);
        vertex(consumer, pose, x1, y1, z1, ri, gi, bi, ai, u0, v0, light, 0, -1, 0);
        vertex(consumer, pose, x2, y1, z1, ri, gi, bi, ai, u1, v0, light, 0, -1, 0);
        vertex(consumer, pose, x2, y1, z2, ri, gi, bi, ai, u1, v1, light, 0, -1, 0);

        // Up face (Y+) — viewed from above, CCW
        vertex(consumer, pose, x1, y2, z1, ri, gi, bi, ai, u0, v0, light, 0, 1, 0);
        vertex(consumer, pose, x1, y2, z2, ri, gi, bi, ai, u0, v1, light, 0, 1, 0);
        vertex(consumer, pose, x2, y2, z2, ri, gi, bi, ai, u1, v1, light, 0, 1, 0);
        vertex(consumer, pose, x2, y2, z1, ri, gi, bi, ai, u1, v0, light, 0, 1, 0);

        // North face (Z-) — viewed from north, CCW
        vertex(consumer, pose, x2, y2, z1, ri, gi, bi, ai, u1, v0, light, 0, 0, -1);
        vertex(consumer, pose, x2, y1, z1, ri, gi, bi, ai, u1, v1, light, 0, 0, -1);
        vertex(consumer, pose, x1, y1, z1, ri, gi, bi, ai, u0, v1, light, 0, 0, -1);
        vertex(consumer, pose, x1, y2, z1, ri, gi, bi, ai, u0, v0, light, 0, 0, -1);

        // South face (Z+) — viewed from south, CCW
        vertex(consumer, pose, x1, y2, z2, ri, gi, bi, ai, u0, v0, light, 0, 0, 1);
        vertex(consumer, pose, x1, y1, z2, ri, gi, bi, ai, u0, v1, light, 0, 0, 1);
        vertex(consumer, pose, x2, y1, z2, ri, gi, bi, ai, u1, v1, light, 0, 0, 1);
        vertex(consumer, pose, x2, y2, z2, ri, gi, bi, ai, u1, v0, light, 0, 0, 1);

        // West face (X-) — viewed from west, CCW
        vertex(consumer, pose, x1, y2, z1, ri, gi, bi, ai, u0, v0, light, -1, 0, 0);
        vertex(consumer, pose, x1, y1, z1, ri, gi, bi, ai, u0, v1, light, -1, 0, 0);
        vertex(consumer, pose, x1, y1, z2, ri, gi, bi, ai, u1, v1, light, -1, 0, 0);
        vertex(consumer, pose, x1, y2, z2, ri, gi, bi, ai, u1, v0, light, -1, 0, 0);

        // East face (X+) — viewed from east, CCW
        vertex(consumer, pose, x2, y2, z2, ri, gi, bi, ai, u0, v0, light, 1, 0, 0);
        vertex(consumer, pose, x2, y1, z2, ri, gi, bi, ai, u0, v1, light, 1, 0, 0);
        vertex(consumer, pose, x2, y1, z1, ri, gi, bi, ai, u1, v1, light, 1, 0, 0);
        vertex(consumer, pose, x2, y2, z1, ri, gi, bi, ai, u1, v0, light, 1, 0, 0);
    }

    /**
     * Adds a single vertex using the BLOCK vertex format:
     * Position → Color → UV → UV2 (lightmap) → Normal
     */
    private void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                         float x, float y, float z,
                         int r, int g, int b, int a,
                         float u, float v,
                         int light,
                         float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setUv2(light & 0xFFFF, (light >> 16) & 0xFFFF)
                .setNormal(pose, nx, ny, nz);
    }
}
