package com.lwi.luckyxp.client;

import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.machine.MerchantMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The merchant's service list — deliberately more crafted/market-stall than the machines' bare CRT
 * screen: real spruce planks tiled as the board (not procedural stripes), a carved dark frame, the
 * shopkeeper's head in a framed portrait (top-right) following the cursor, and six service rows.
 * Affordability is brightness, never hue (colorblind); creative can always buy.
 */
public class MerchantScreen extends AbstractContainerScreen<MerchantMenu> {
    private static final String[] SERVICES = {
            "Reroll machine stock",
            "Convert machine type",
            "Luck boost (5 min, cursed)",
            "Permanent luck (+1-3%)",
            "Full heal",
            "Repair held item"
    };

    private static final int ROW_H = 18, HEADER = 46, PAD = 8, PORTRAIT = 40;

    /** The board itself: Minecraft's own spruce planks, tiled — real wood, not painted stripes. */
    private static final ResourceLocation PLANKS = new ResourceLocation("minecraft", "textures/block/spruce_planks.png");

    // Wood palette.
    private static final int FRAME_DARK = 0xFF2A1B0E;   // carved outer frame
    private static final int FRAME_EDGE = 0xFF5A3D22;   // frame bevel
    private static final int ROW_BG     = 0x882A180C;   // service row plate
    private static final int ROW_EDGE   = 0x30FFE0B0;   // 1px carved-slat highlight on each row
    private static final int HOVER      = 0x33F0C070;   // warm hover
    private static final int TXT        = 0xFFF0E0C0;   // cream
    private static final int TXT_DIM    = 0xFF8A7355;   // dim brown
    private static final int GOLD       = 0xFFE8C25E;

    private com.lwi.luckyxp.entity.LuckyMerchant portrait;

    public MerchantScreen(MerchantMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 208;
        this.imageHeight = HEADER + SERVICES.length * ROW_H + PAD;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft != null && minecraft.level != null && portrait == null) {
            portrait = new com.lwi.luckyxp.entity.LuckyMerchant(Registration.LUCKY_MERCHANT.get(), minecraft.level);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos, y0 = topPos, x1 = leftPos + imageWidth, y1 = topPos + imageHeight;

        // carved frame: dark border + a bevel line
        g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, FRAME_DARK);
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, FRAME_EDGE);

        // real spruce planks, tiled edge to edge
        for (int py = y0; py < y1; py += 16) {
            for (int px = x0; px < x1; px += 16) {
                int w = Math.min(16, x1 - px), h = Math.min(16, y1 - py);
                g.blit(PLANKS, px, py, 0.0F, 0.0F, w, h, 16, 16);
            }
        }
        // gentle top-to-bottom shading so the board doesn't look flat-lit
        g.fillGradient(x0, y0, x1, y1, 0x22000000, 0x5A000000);
        // darker header band to seat the title
        g.fill(x0, y0, x1, y0 + HEADER - 4, 0x44150A04);

        // header: engraved title + level
        g.drawString(font, "LUCKY MERCHANT", x0 + PAD, y0 + 9, GOLD, true);
        int level = ClientXpCache.level;
        g.drawString(font, level + " lvl", x0 + PAD, y0 + 24, TXT, false);

        // portrait frame (top-right) + the shopkeeper's head following the cursor
        int fx1 = x1 - PAD, fx0 = fx1 - PORTRAIT, fy0 = y0 + PAD - 2, fy1 = fy0 + PORTRAIT;
        g.fill(fx0 - 2, fy0 - 2, fx1 + 2, fy1 + 2, FRAME_DARK);
        g.fill(fx0 - 1, fy0 - 1, fx1 + 1, fy1 + 1, FRAME_EDGE);
        g.fill(fx0, fy0, fx1, fy1, 0xFF241812);                     // dark recess behind the head
        if (portrait != null) {
            g.enableScissor(fx0, fy0, fx1, fy1);
            // Bust framing: the villager is 1.95 tall with its head centred ~1.64 blocks above the
            // feet — drop the baseline far below the frame so the HEAD (not the belly) fills it.
            int scale = 48;
            int cx = (fx0 + fx1) / 2;
            int cy = fy0 + PORTRAIT / 2 + (int) (1.64F * scale);    // feet way below the scissor
            int eyeY = fy0 + PORTRAIT / 2 - 3;                      // where the eyes land on screen
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, cx, cy, scale, (float) cx - mouseX, (float) eyeY - mouseY, portrait);
            g.disableScissor();
        }

        // header divider
        g.fill(x0 + PAD - 2, y0 + HEADER - 4, x1 - PAD + 2, y0 + HEADER - 3, FRAME_DARK);

        // service rows
        boolean creative = minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
        for (int i = 0; i < SERVICES.length; i++) {
            int rowY = y0 + HEADER + i * ROW_H;
            g.fill(x0 + PAD - 2, rowY, x1 - PAD + 2, rowY + ROW_H - 2, ROW_BG);
            g.fill(x0 + PAD - 2, rowY, x1 - PAD + 2, rowY + 1, ROW_EDGE);       // carved-slat top edge
            if (rowAt(mouseX, mouseY) == i) {
                g.fill(x0 + PAD - 2, rowY, x1 - PAD + 2, rowY + ROW_H - 2, HOVER);
            }
            boolean afford = creative || level >= MerchantMenu.PRICES[i];
            int col = afford ? TXT : TXT_DIM;
            String cost = MerchantMenu.PRICES[i] + " lvl";
            g.drawString(font, SERVICES[i], x0 + PAD + 2, rowY + 5, col, false);
            g.drawString(font, cost, x1 - PAD - font.width(cost), rowY + 5, col, false);
        }
    }

    private int rowAt(int mouseX, int mouseY) {
        if (mouseX < leftPos + PAD - 2 || mouseX > leftPos + imageWidth - PAD + 2) {
            return -1;
        }
        int rel = mouseY - (topPos + HEADER);
        if (rel < 0) {
            return -1;
        }
        int idx = rel / ROW_H;
        return idx >= 0 && idx < SERVICES.length && rel % ROW_H < ROW_H - 2 ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int idx = rowAt((int) mouseX, (int) mouseY);
            if (idx >= 0 && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, idx);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // all drawn in renderBg (absolute coordinates)
    }
}
