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

    /** Small inset to prevent Z-fighting at block boundaries. Zero = flush for seamless connections. */
    private static final float FACE_INSET = 0.0f;

    // EnderIO-style layered shading: dark shell → mid-tone → bright center
    /** Border thickness for the mid-tone layer inset (block units). */
    private static final float SHELL_1 = 0.040f;
    /** Total inset for the bright center layer (block units). */
    private static final float SHELL_2 = 0.070f;
    /** Shell color: dark charcoal gray (RGB). Multiplied with bright shell texture. */
    private static final float SHELL_R = 0.14f, SHELL_G = 0.14f, SHELL_B = 0.16f;
    /** Junction box color: slightly darker than shell for a subtle distinction. */
    private static final float JUNCTION_R = 0.12f, JUNCTION_G = 0.12f, JUNCTION_B = 0.13f;
    /** Extra padding around the conduit bounding box for the junction (block units). */
    private static final float JUNCTION_PAD = 0.015f;

    /** Cached textured shell sprite with checker corners. */
    private TextureAtlasSprite shellSprite;
    /** Cached animated conduit flow sprite for mid and center layers. */
    private TextureAtlasSprite flowSprite;
    /** Cached flat white sprite for pads / solid flat faces. */
    private TextureAtlasSprite whiteSprite;

    public ConduitBundleRenderer(BlockEntityRendererProvider.Context context) {
    }

    private TextureAtlasSprite getShellSprite() {
        if (shellSprite == null) {
            shellSprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(ResourceLocation.fromNamespaceAndPath("omnio", "block/conduit_shell"));
        }
        return shellSprite;
    }

    private TextureAtlasSprite getWhiteSprite() {
        if (whiteSprite == null) {
            whiteSprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(ResourceLocation.withDefaultNamespace("block/white_concrete"));
        }
        return whiteSprite;
    }

    private TextureAtlasSprite getFlowSprite() {
        if (flowSprite == null) {
            flowSprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(ResourceLocation.fromNamespaceAndPath("omnio", "block/conduit_flow"));
        }
        return flowSprite;
    }

    @Override
    public void render(OmniConduitBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        List<ConduitSlot> slots = blockEntity.getSortedSlots();
        if (slots.isEmpty()) return;

        VertexConsumer solidConsumer = bufferSource.getBuffer(RenderType.solid());
        TextureAtlasSprite shellSpr = getShellSprite();
        TextureAtlasSprite flowSpr = getFlowSprite();
        TextureAtlasSprite whiteSpr = getWhiteSprite();
        float su0 = shellSpr.getU0(), sv0 = shellSpr.getV0();
        float su1 = shellSpr.getU1(), sv1 = shellSpr.getV1();
        float fu0 = flowSpr.getU0(), fv0 = flowSpr.getV0();
        float fu1 = flowSpr.getU1(), fv1 = flowSpr.getV1();
        float wu0 = whiteSpr.getU0(), wv0 = whiteSpr.getV0();
        float wu1 = whiteSpr.getU1(), wv1 = whiteSpr.getV1();
        float coreSize = ConduitShape.getCoreSize();
        float halfPx = coreSize / 32.0f;

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

        // ---- Junction box: when 2+ conduits share this block, draw a containing hub ----
        if (slots.size() >= 2) {
            // Compute bounding box of ALL conduit cores
            float jMinX = Float.MAX_VALUE, jMinY = Float.MAX_VALUE, jMinZ = Float.MAX_VALUE;
            float jMaxX = -Float.MAX_VALUE, jMaxY = -Float.MAX_VALUE, jMaxZ = -Float.MAX_VALUE;

            int gIdx = 0;
            for (ConduitSlot slot : slots) {
                if (gIdx >= 9) break;

                // Same core position logic as the main render loop
                Map<Direction, float[]> dOff = new EnumMap<>(Direction.class);
                for (int d = 0; d < 6; d++) {
                    Direction dir = Direction.from3DDataValue(d);
                    List<ConduitSlot> connected = connectedPerDir[d];
                    int myIdx = connected.indexOf(slot);
                    if (myIdx >= 0) {
                        int[] off2D = ConduitOffsetHelper.offsetConduit(myIdx, connected.size());
                        dOff.put(dir, ConduitOffsetHelper.translationFor(dir.getAxis(), off2D));
                    }
                }

                float cMinX, cMinY, cMinZ, cMaxX, cMaxY, cMaxZ;
                if (dOff.isEmpty()) {
                    float[] def = ConduitOffsetHelper.defaultCoreOffset(gIdx, slots.size(), mainAxis);
                    float cx = 0.5f + def[0], cy = 0.5f + def[1], cz = 0.5f + def[2];
                    cMinX = cx - halfPx; cMinY = cy - halfPx; cMinZ = cz - halfPx;
                    cMaxX = cx + halfPx; cMaxY = cy + halfPx; cMaxZ = cz + halfPx;
                } else {
                    float mnX = Float.MAX_VALUE, mnY = Float.MAX_VALUE, mnZ = Float.MAX_VALUE;
                    float mxX = -Float.MAX_VALUE, mxY = -Float.MAX_VALUE, mxZ = -Float.MAX_VALUE;
                    for (float[] o : dOff.values()) {
                        mnX = Math.min(mnX, o[0]); mnY = Math.min(mnY, o[1]); mnZ = Math.min(mnZ, o[2]);
                        mxX = Math.max(mxX, o[0]); mxY = Math.max(mxY, o[1]); mxZ = Math.max(mxZ, o[2]);
                    }
                    cMinX = 0.5f + mnX - halfPx; cMinY = 0.5f + mnY - halfPx; cMinZ = 0.5f + mnZ - halfPx;
                    cMaxX = 0.5f + mxX + halfPx; cMaxY = 0.5f + mxY + halfPx; cMaxZ = 0.5f + mxZ + halfPx;
                }
                jMinX = Math.min(jMinX, cMinX); jMinY = Math.min(jMinY, cMinY); jMinZ = Math.min(jMinZ, cMinZ);
                jMaxX = Math.max(jMaxX, cMaxX); jMaxY = Math.max(jMaxY, cMaxY); jMaxZ = Math.max(jMaxZ, cMaxZ);
                gIdx++;
            }

            // Expand slightly beyond the conduit cores
            jMinX -= JUNCTION_PAD; jMinY -= JUNCTION_PAD; jMinZ -= JUNCTION_PAD;
            jMaxX += JUNCTION_PAD; jMaxY += JUNCTION_PAD; jMaxZ += JUNCTION_PAD;

            // Draw the junction hub using the textured shell sprite
            renderBox(poseStack, solidConsumer,
                    jMinX, jMinY, jMinZ, jMaxX, jMaxY, jMaxZ,
                    JUNCTION_R, JUNCTION_G, JUNCTION_B, 1.0f,
                    su0, sv0, su1, sv1, packedLight);
        }

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

            // Render core with layered shading (dark shell → mid → bright)
            renderConduitSegment(poseStack, solidConsumer,
                    coreMinX, coreMinY, coreMinZ,
                    coreMaxX, coreMaxY, coreMaxZ,
                    r, g, b, null,
                    su0, sv0, su1, sv1,
                    fu0, fv0, fu1, fv1, packedLight);

            // ---- Render per-direction arms with layered shading ----
            for (var entry : dirOffsets.entrySet()) {
                Direction dir = entry.getKey();
                float[] off = entry.getValue();
                float armCx = 0.5f + off[0];
                float armCy = 0.5f + off[1];
                float armCz = 0.5f + off[2];
                renderArm(poseStack, solidConsumer, dir, armCx, armCy, armCz, halfPx,
                        r, g, b, su0, sv0, su1, sv1,
                        fu0, fv0, fu1, fv1, packedLight);
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
            for (ConduitSlot slot : connectedPerDir[d]) {
                ConnectionContainer c = blockEntity.getConnectionContainer(slot);
                if (c != null && c.getStatus(dir) == ConnectionStatus.CONNECTED_BLOCK) {
                    hasBlockConn = true;
                    break;
                }
            }
            if (!hasBlockConn) continue;

            // Pads use dark shell color for EnderIO-style endcaps
            float padR = SHELL_R, padG = SHELL_G, padB = SHELL_B;

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

            renderBox(poseStack, solidConsumer,
                    px1, py1, pz1, px2, py2, pz2,
                    padR, padG, padB, 1.0f, su0, sv0, su1, sv1, packedLight);
        }

        poseStack.popPose();
    }

    /**
     * Renders a connector arm from just outside the core to the block face,
     * centred at (cx, cy, cz) in the perpendicular plane.
     * Uses layered shading (dark shell → mid → bright) on perpendicular axes.
     */
    private void renderArm(PoseStack poseStack, VertexConsumer consumer,
                            Direction dir, float cx, float cy, float cz,
                            float halfPx,
                            float r, float g, float b,
                            float su0, float sv0, float su1, float sv1,
                            float fu0, float fv0, float fu1, float fv1,
                            int light) {
        float w = halfPx; // arm width matches core half-size
        float x1, y1, z1, x2, y2, z2;
        switch (dir) {
            case DOWN -> {
                x1 = cx - w; y1 = FACE_INSET;     z1 = cz - w;
                x2 = cx + w; y2 = cy - halfPx;    z2 = cz + w;
            }
            case UP -> {
                x1 = cx - w; y1 = cy + halfPx;    z1 = cz - w;
                x2 = cx + w; y2 = 1 - FACE_INSET; z2 = cz + w;
            }
            case NORTH -> {
                x1 = cx - w; y1 = cy - w;         z1 = FACE_INSET;
                x2 = cx + w; y2 = cy + w;         z2 = cz - halfPx;
            }
            case SOUTH -> {
                x1 = cx - w; y1 = cy - w;         z1 = cz + halfPx;
                x2 = cx + w; y2 = cy + w;         z2 = 1 - FACE_INSET;
            }
            case WEST -> {
                x1 = FACE_INSET;  y1 = cy - w; z1 = cz - w;
                x2 = cx - halfPx; y2 = cy + w; z2 = cz + w;
            }
            case EAST -> {
                x1 = cx + halfPx;    y1 = cy - w; z1 = cz - w;
                x2 = 1 - FACE_INSET; y2 = cy + w; z2 = cz + w;
            }
            default -> { return; }
        }

        renderConduitSegment(poseStack, consumer,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2),
                r, g, b, dir.getAxis(),
                su0, sv0, su1, sv1,
                fu0, fv0, fu1, fv1, light);
    }

    /**
     * Renders a conduit segment with layered shading.
     * Layer 0 (shell): textured shell sprite at the surface with checker corners.
     * Layers 1-2 (mid, bright): animated flow sprite, nudged slightly outward
     * so they paint on top of the shell. The bigger insets make the colored area
     * smaller, and the darker mid-tone gives a visual depth illusion.
     *
     * @param travelAxis the arm's travel axis ({@code null} for cores → inset all in-plane axes)
     */
    private void renderConduitSegment(PoseStack poseStack, VertexConsumer consumer,
                                       float x1, float y1, float z1,
                                       float x2, float y2, float z2,
                                       float r, float g, float b,
                                       Direction.Axis travelAxis,
                                       float su0, float sv0, float su1, float sv1,
                                       float fu0, float fv0, float fu1, float fv1,
                                       int light) {
        PoseStack.Pose pose = poseStack.last();
        float eps = 0.001f; // small outward nudge so each layer paints on top of the previous
        float midR = r * 0.30f, midG = g * 0.30f, midB = b * 0.30f; // darker mid-tone for depth illusion

        for (Direction face : Direction.values()) {
            Direction.Axis faceAxis = face.getAxis();
            int nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();

            float[][] colors = {
                { SHELL_R, SHELL_G, SHELL_B },
                { midR, midG, midB },
                { r, g, b }
            };
            float[] insets = { 0, SHELL_1, SHELL_2 };

            for (int li = 0; li < 3; li++) {
                float inset = insets[li];
                // Each successive layer nudged outward so it paints on top
                float nudge = li * eps;

                // Pick UVs: shell layer uses textured shell sprite, inner layers use animated flow
                float lu0, lv0, lu1, lv1;
                if (li == 0) {
                    lu0 = su0; lv0 = sv0; lu1 = su1; lv1 = sv1;
                } else {
                    lu0 = fu0; lv0 = fv0; lu1 = fu1; lv1 = fv1;
                }

                // In-plane bounds with inset (only on axes != travelAxis)
                float lx1 = x1, ly1 = y1, lz1 = z1;
                float lx2 = x2, ly2 = y2, lz2 = z2;

                if (faceAxis != Direction.Axis.X && (travelAxis == null || travelAxis != Direction.Axis.X)) {
                    lx1 += inset; lx2 -= inset;
                }
                if (faceAxis != Direction.Axis.Y && (travelAxis == null || travelAxis != Direction.Axis.Y)) {
                    ly1 += inset; ly2 -= inset;
                }
                if (faceAxis != Direction.Axis.Z && (travelAxis == null || travelAxis != Direction.Axis.Z)) {
                    lz1 += inset; lz2 -= inset;
                }

                // Skip if inset collapsed the face
                if (faceAxis != Direction.Axis.X && lx1 >= lx2) continue;
                if (faceAxis != Direction.Axis.Y && ly1 >= ly2) continue;
                if (faceAxis != Direction.Axis.Z && lz1 >= lz2) continue;

                // Face position on its normal axis, nudged outward so inner layers paint on top
                float fp;
                switch (faceAxis) {
                    case X -> fp = (nx > 0 ? x2 : x1) + nx * nudge;
                    case Y -> fp = (ny > 0 ? y2 : y1) + ny * nudge;
                    case Z -> fp = (nz > 0 ? z2 : z1) + nz * nudge;
                    default -> fp = 0;
                }

                int cr = (int)(colors[li][0] * 255);
                int cg = (int)(colors[li][1] * 255);
                int cb = (int)(colors[li][2] * 255);

                // Emit 4 vertices (CCW from outside)
                switch (face) {
                    case DOWN -> {
                        vertex(consumer, pose, lx1, fp, lz2, cr, cg, cb, 255, lu0, lv1, light, 0, -1, 0);
                        vertex(consumer, pose, lx1, fp, lz1, cr, cg, cb, 255, lu0, lv0, light, 0, -1, 0);
                        vertex(consumer, pose, lx2, fp, lz1, cr, cg, cb, 255, lu1, lv0, light, 0, -1, 0);
                        vertex(consumer, pose, lx2, fp, lz2, cr, cg, cb, 255, lu1, lv1, light, 0, -1, 0);
                    }
                    case UP -> {
                        vertex(consumer, pose, lx1, fp, lz1, cr, cg, cb, 255, lu0, lv0, light, 0, 1, 0);
                        vertex(consumer, pose, lx1, fp, lz2, cr, cg, cb, 255, lu0, lv1, light, 0, 1, 0);
                        vertex(consumer, pose, lx2, fp, lz2, cr, cg, cb, 255, lu1, lv1, light, 0, 1, 0);
                        vertex(consumer, pose, lx2, fp, lz1, cr, cg, cb, 255, lu1, lv0, light, 0, 1, 0);
                    }
                    case NORTH -> {
                        vertex(consumer, pose, lx2, ly2, fp, cr, cg, cb, 255, lu1, lv0, light, 0, 0, -1);
                        vertex(consumer, pose, lx2, ly1, fp, cr, cg, cb, 255, lu1, lv1, light, 0, 0, -1);
                        vertex(consumer, pose, lx1, ly1, fp, cr, cg, cb, 255, lu0, lv1, light, 0, 0, -1);
                        vertex(consumer, pose, lx1, ly2, fp, cr, cg, cb, 255, lu0, lv0, light, 0, 0, -1);
                    }
                    case SOUTH -> {
                        vertex(consumer, pose, lx1, ly2, fp, cr, cg, cb, 255, lu0, lv0, light, 0, 0, 1);
                        vertex(consumer, pose, lx1, ly1, fp, cr, cg, cb, 255, lu0, lv1, light, 0, 0, 1);
                        vertex(consumer, pose, lx2, ly1, fp, cr, cg, cb, 255, lu1, lv1, light, 0, 0, 1);
                        vertex(consumer, pose, lx2, ly2, fp, cr, cg, cb, 255, lu1, lv0, light, 0, 0, 1);
                    }
                    case WEST -> {
                        vertex(consumer, pose, fp, ly2, lz1, cr, cg, cb, 255, lu0, lv0, light, -1, 0, 0);
                        vertex(consumer, pose, fp, ly1, lz1, cr, cg, cb, 255, lu0, lv1, light, -1, 0, 0);
                        vertex(consumer, pose, fp, ly1, lz2, cr, cg, cb, 255, lu1, lv1, light, -1, 0, 0);
                        vertex(consumer, pose, fp, ly2, lz2, cr, cg, cb, 255, lu1, lv0, light, -1, 0, 0);
                    }
                    case EAST -> {
                        vertex(consumer, pose, fp, ly2, lz2, cr, cg, cb, 255, lu0, lv0, light, 1, 0, 0);
                        vertex(consumer, pose, fp, ly1, lz2, cr, cg, cb, 255, lu0, lv1, light, 1, 0, 0);
                        vertex(consumer, pose, fp, ly1, lz1, cr, cg, cb, 255, lu1, lv1, light, 1, 0, 0);
                        vertex(consumer, pose, fp, ly2, lz1, cr, cg, cb, 255, lu1, lv0, light, 1, 0, 0);
                    }
                }
            }
        }
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
