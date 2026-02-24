package com.trilon.omnio.client;

import com.trilon.omnio.api.conduit.ConduitSlot;
import com.trilon.omnio.api.conduit.IConnectionConfig.RedstoneMode;
import com.trilon.omnio.api.conduit.IConnectionConfig.TransferMode;
import com.trilon.omnio.content.conduit.ConduitMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Per-face conduit configuration screen.
 * Opens when the player right-clicks a connection pad.
 * Shows only the settings for that specific face, with a conduit selector
 * to choose which cable in the bundle to configure.
 *
 * <p>Layout (compact, centered):</p>
 * <pre>
 *   ┌─────────────────────────────┐
 *   │  South Face                 │
 *   ├─────────────────────────────┤
 *   │  Conduit: [I] [E] [F]      │
 *   ├─────────────────────────────┤
 *   │  Mode       [ Extract  ]   │
 *   │  Priority   [-] 0 [+]      │
 *   │  Redstone   [ Always   ]   │
 *   ├─────────────────────────────┤
 *   │  Channel    ■■■■■■■■       │
 *   │             ■■■■■■■■       │
 *   └─────────────────────────────┘
 * </pre>
 */
public class ConduitScreen extends AbstractContainerScreen<ConduitMenu> {

    // ---- Colours ----
    private static final int BG           = 0xF0101018;
    private static final int PANEL_BG     = 0xE0181828;
    private static final int BORDER       = 0xFF3A3A5A;
    private static final int ACCENT       = 0xFF6A6ADA;
    private static final int HIGHLIGHT    = 0xFFFFFF00;
    private static final int TEXT_WHITE   = 0xFFFFFFFF;
    private static final int TEXT_DIM     = 0xFFAAAAAA;
    private static final int BTN_NORMAL   = 0xFF2A2A3A;
    private static final int BTN_HOVER    = 0xFF3A3A5A;
    private static final int BTN_BORDER   = 0xFF5A5A7A;

    // Layout
    private static final int GUI_W = 180;
    private static final int GUI_H = 190;
    private static final int PAD   = 8;
    private static final int TAB_SIZE = 20;
    private static final int TAB_GAP  = 3;

    // Channel colours (dye palette)
    private static final int[] CH_COLORS = {
            0xFFF9FFFE, 0xFFF9801D, 0xFFC74EBD, 0xFF3AB3DA,
            0xFFFED83D, 0xFF80C71F, 0xFFF38BAA, 0xFF474F52,
            0xFF9D9D97, 0xFF169C9C, 0xFF8932B8, 0xFF3C44AA,
            0xFF835432, 0xFF5E7C16, 0xFFB02E26, 0xFF1D1D21,
    };
    private static final String[] DYE_NAMES = {
            "White", "Orange", "Magenta", "Light Blue",
            "Yellow", "Lime", "Pink", "Gray",
            "Light Gray", "Cyan", "Purple", "Blue",
            "Brown", "Green", "Red", "Black",
    };

    private static final String[] FACE_NAMES = {"Down", "Up", "North", "South", "West", "East"};

    private Component hoverTooltip = null;

    public ConduitScreen(ConduitMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth  = GUI_W;
        this.imageHeight = GUI_H;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = PAD;
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight + 100; // hide
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (hoverTooltip != null) {
            g.renderTooltip(font, hoverTooltip, mx, my);
        }
    }

    // ====================================================================
    //  Background
    // ====================================================================

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        hoverTooltip = null;
        int x = leftPos, y = topPos;
        int cw = GUI_W - PAD * 2; // content width

        // Main panel
        g.fill(x, y, x + GUI_W, y + GUI_H, BG);
        drawBorder(g, x, y, GUI_W, GUI_H, BORDER);

        int cy = y + PAD; // current Y cursor

        // ---- Face header ----
        Direction face = menu.getSelectedFace();
        String faceName = FACE_NAMES[face.get3DDataValue()] + " Face";
        g.fill(x + 1, cy - 2, x + GUI_W - 1, cy + 12, 0xFF16162A);
        g.drawString(font, faceName, x + PAD, cy, ACCENT, false);
        cy += 16;

        // ---- Separator ----
        g.fill(x + PAD, cy, x + GUI_W - PAD, cy + 1, BORDER);
        cy += 5;

        // ---- Conduit selector tabs ----
        g.drawString(font, "Conduit", x + PAD, cy, TEXT_DIM, false);
        cy += 12;
        renderConduitTabs(g, x + PAD, cy, mx, my);
        cy += TAB_SIZE + 6;

        // ---- Separator ----
        g.fill(x + PAD, cy, x + GUI_W - PAD, cy + 1, BORDER);
        cy += 5;

        // ---- Settings ----
        renderSettings(g, x + PAD, cy, cw, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) { }

    // ====================================================================
    //  Conduit selector (tab row)
    // ====================================================================

    private void renderConduitTabs(GuiGraphics g, int px, int py, int mx, int my) {
        List<ConduitSlot> slots = menu.getConduitSlots();
        for (int i = 0; i < slots.size(); i++) {
            int tx = px + i * (TAB_SIZE + TAB_GAP);
            ConduitSlot slot = slots.get(i);
            int color = ConduitRenderHelper.getConduitColor(slot.conduitId());
            boolean sel = i == menu.getSelectedSlotIndex();
            boolean hov = isIn(mx, my, tx, py, TAB_SIZE, TAB_SIZE);

            // Tab background
            g.fill(tx, py, tx + TAB_SIZE, py + TAB_SIZE, 0xFF000000 | color);
            if (hov) g.fill(tx, py, tx + TAB_SIZE, py + TAB_SIZE, 0x44FFFFFF);

            // Type icon
            String icon = typeIcon(slot.conduitId().getPath());
            int iw = font.width(icon);
            g.drawString(font, icon, tx + (TAB_SIZE - iw) / 2, py + 6, TEXT_WHITE, true);

            // Selection border
            if (sel) drawBorder(g, tx - 1, py - 1, TAB_SIZE + 2, TAB_SIZE + 2, HIGHLIGHT);
            else     drawBorder(g, tx, py, TAB_SIZE, TAB_SIZE, BTN_BORDER);

            // Tooltip
            if (hov) hoverTooltip = Component.literal(conduitName(slot.conduitId().getPath()));
        }
    }

    // ====================================================================
    //  Settings for selected conduit on this face
    // ====================================================================

    private void renderSettings(GuiGraphics g, int px, int py, int cw, int mx, int my) {
        int labelX = px;
        int btnX   = px + 60;
        int btnW   = cw - 60;

        // Transfer Mode
        TransferMode mode = menu.getTransferMode();
        g.drawString(font, "Mode", labelX, py + 3, TEXT_DIM, false);
        drawBtn(g, btnX, py, btnW, 14, modeLabel(mode), modeColor(mode), mx, my);
        if (isIn(mx, my, btnX, py, btnW, 14))
            hoverTooltip = Component.literal("Click to cycle: Off \u2192 Extract \u2192 Insert \u2192 Both");
        py += 18;

        // Priority
        int pri = menu.getPriority();
        g.drawString(font, "Priority", labelX, py + 3, TEXT_DIM, false);
        drawBtn(g, btnX, py, 16, 14, "-", TEXT_WHITE, mx, my);
        String ps = String.valueOf(pri);
        int pw = font.width(ps);
        g.drawString(font, ps, btnX + 20 + (btnW - 56 - pw) / 2, py + 3, TEXT_WHITE, false);
        drawBtn(g, btnX + btnW - 16, py, 16, 14, "+", TEXT_WHITE, mx, my);
        py += 18;

        // Redstone Mode
        RedstoneMode rMode = menu.getRedstoneMode();
        g.drawString(font, "Redstone", labelX, py + 3, TEXT_DIM, false);
        drawBtn(g, btnX, py, btnW, 14, rsLabel(rMode), TEXT_WHITE, mx, my);
        if (isIn(mx, my, btnX, py, btnW, 14))
            hoverTooltip = Component.literal(rsTip(rMode));
        py += 22;

        // ---- Separator ----
        g.fill(px, py, px + GUI_W - PAD * 2, py + 1, BORDER);
        py += 5;

        // Channel palette
        g.drawString(font, "Channel", px, py, TEXT_DIM, false);
        py += 12;
        int ch = menu.getChannel();
        int chSize = 14;
        int chGap  = 1;
        int cols = 8;
        for (int i = 0; i < 16; i++) {
            int col = i % cols;
            int row = i / cols;
            int ccx = px + col * (chSize + chGap);
            int ccy = py + row * (chSize + chGap);
            g.fill(ccx, ccy, ccx + chSize, ccy + chSize, CH_COLORS[i]);
            if (i == ch) {
                drawBorder(g, ccx - 1, ccy - 1, chSize + 2, chSize + 2, HIGHLIGHT);
            }
            if (isIn(mx, my, ccx, ccy, chSize, chSize)) {
                hoverTooltip = Component.literal(DYE_NAMES[i] + (i == ch ? " (active)" : ""));
            }
        }
    }

    // ====================================================================
    //  Click Handling
    // ====================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            if (handleTabClicks(mx, my))     return true;
            if (handleSettingsClicks(mx, my)) return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private boolean handleTabClicks(double mx, double my) {
        int px = leftPos + PAD;
        int py = topPos + PAD + 16 + 5 + 12; // face header + sep + "Conduit" label
        List<ConduitSlot> slots = menu.getConduitSlots();
        for (int i = 0; i < slots.size(); i++) {
            int tx = px + i * (TAB_SIZE + TAB_GAP);
            if (isIn(mx, my, tx, py, TAB_SIZE, TAB_SIZE)) {
                send(ConduitMenu.BTN_SELECT_SLOT_BASE + i);
                return true;
            }
        }
        return false;
    }

    private boolean handleSettingsClicks(double mx, double my) {
        int px = leftPos + PAD;
        int cw = GUI_W - PAD * 2;
        int btnX = px + 60;
        int btnW = cw - 60;
        int py = topPos + PAD + 16 + 5 + 12 + TAB_SIZE + 6 + 1 + 5; // after tabs + sep

        // Mode button
        if (isIn(mx, my, btnX, py, btnW, 14)) { send(ConduitMenu.BTN_CYCLE_TRANSFER); return true; }
        py += 18;

        // Priority -/+
        if (isIn(mx, my, btnX, py, 16, 14))             { send(ConduitMenu.BTN_PRIORITY_DOWN); return true; }
        if (isIn(mx, my, btnX + btnW - 16, py, 16, 14)) { send(ConduitMenu.BTN_PRIORITY_UP);   return true; }
        py += 18;

        // Redstone button
        if (isIn(mx, my, btnX, py, btnW, 14)) { send(ConduitMenu.BTN_CYCLE_REDSTONE); return true; }
        py += 22 + 1 + 5 + 12; // sep + "Channel" label

        // Channel palette
        int chSize = 14, chGap = 1;
        for (int i = 0; i < 16; i++) {
            int col = i % 8;
            int row = i / 8;
            int ccx = px + col * (chSize + chGap);
            int ccy = py + row * (chSize + chGap);
            if (isIn(mx, my, ccx, ccy, chSize, chSize)) {
                send(ConduitMenu.BTN_SET_CHANNEL_BASE + i);
                return true;
            }
        }
        return false;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.player != null && minecraft.gameMode != null) {
            menu.clickMenuButton(minecraft.player, id);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    // ====================================================================
    //  Helpers
    // ====================================================================

    private static String typeIcon(String path) {
        if (path.startsWith("energy"))   return "E";
        if (path.startsWith("fluid"))    return "F";
        if (path.startsWith("item"))     return "I";
        if (path.startsWith("redstone")) return "R";
        return "?";
    }

    private static String conduitName(String path) {
        String[] p = path.split("_");
        StringBuilder sb = new StringBuilder();
        sb.append(cap(p[0]));
        for (int i = 1; i < p.length; i++) {
            if (p[i].equals("conduit")) continue;
            sb.append(" (").append(cap(p[i])).append(")");
        }
        return sb.toString();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String modeLabel(TransferMode m) {
        return switch (m) {
            case DISABLED -> "Off";
            case INSERT   -> "Insert";
            case EXTRACT  -> "Extract";
            case BOTH     -> "Both";
        };
    }

    private static int modeColor(TransferMode m) {
        return switch (m) {
            case DISABLED -> 0xFFAA4444;
            case INSERT   -> 0xFF44CC44;
            case EXTRACT  -> 0xFFFFBB44;
            case BOTH     -> 0xFF44AAFF;
        };
    }

    private static String rsLabel(RedstoneMode m) {
        return switch (m) {
            case ALWAYS_ACTIVE         -> "Always On";
            case ACTIVE_WITH_SIGNAL    -> "Signal On";
            case ACTIVE_WITHOUT_SIGNAL -> "Signal Off";
            case NEVER_ACTIVE          -> "Never";
        };
    }

    private static String rsTip(RedstoneMode m) {
        return switch (m) {
            case ALWAYS_ACTIVE         -> "Always active regardless of redstone";
            case ACTIVE_WITH_SIGNAL    -> "Active only with redstone signal";
            case ACTIVE_WITHOUT_SIGNAL -> "Active only without redstone signal";
            case NEVER_ACTIVE          -> "Never active (disabled)";
        };
    }

    private boolean isIn(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void drawBtn(GuiGraphics g, int x, int y, int w, int h, String label, int textColor, int mx, int my) {
        boolean hov = isIn(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hov ? BTN_HOVER : BTN_NORMAL);
        drawBorder(g, x, y, w, h, BTN_BORDER);
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2, y + 3, textColor, false);
    }
}
