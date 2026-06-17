package com.lwi.luckyxp.client;

import com.lwi.luckyxp.machine.Article;
import com.lwi.luckyxp.machine.MachineType;
import com.lwi.luckyxp.machine.Rarity;
import com.lwi.luckyxp.machine.VendingMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Old-CRT-styled trade screen: a green phosphor panel with drifting scanlines, a scrollable list of
 * articles (icon + name + level cost), and a Lucky-level footer. Buying spends Lucky levels
 * (server-authoritative). Affordability is shown by brightness + a padlock, never by hue (colorblind).
 *
 * <p>All positions/sizes/colours come from {@link VendingLayout} (gui/vending_layout.json), reloaded
 * on each open — edit the JSON, F3+T, reopen the machine.
 */
public class VendingMachineScreen extends AbstractContainerScreen<VendingMachineMenu> {
    private final VendingLayout L;
    private int animTicks;
    private int scrollRow;
    private boolean draggingThumb;

    public VendingMachineScreen(VendingMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.L = VendingLayout.load();
        this.imageWidth = L.panelW;
        this.imageHeight = L.panelH;
    }

    @Override
    protected void init() {
        super.init();
        clampScroll();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        animTicks++;
    }

    private int maxScroll() {
        return Math.max(0, menu.getStock().size() - L.visibleRows);
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll()));
    }

    private int listLeft() {
        return leftPos + L.listXPad;
    }

    private int listRight() {
        return maxScroll() > 0 ? leftPos + L.panelW - L.scrollbarRightMargin - 2 : leftPos + L.panelW - L.listXPad;
    }

    // ---- rendering ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, pt);
        renderHeader(g);
        renderList(g, mouseX, mouseY);
        renderScrollbar(g);
        renderFooter(g);
        renderScanlines(g, pt);
        renderHoverTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + L.panelW, y + L.panelH, L.cBorder);
        g.fill(x + L.border, y + L.border, x + L.panelW - L.border, y + L.panelH - L.border, L.cBg);
        g.fill(x + 7, y + L.headerDividerY, x + L.panelW - 7, y + L.headerDividerY + 1, L.cDivider);
        g.fill(x + 7, y + L.footerDividerY, x + L.panelW - 7, y + L.footerDividerY + 1, L.cDivider);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // all custom drawing happens in render() in screen space
    }

    private void renderHeader(GuiGraphics g) {
        centered(g, typeLabel(), leftPos + L.panelW / 2, topPos + L.headerTypeY, L.cTxt);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        List<Article> stock = menu.getStock();
        if (stock.isEmpty()) {
            centered(g, "-- EMPTY --", leftPos + L.panelW / 2, topPos + L.listTop + 50, L.cTxtDim);
            return;
        }
        int level = ClientXpCache.level;
        int hovered = rowAt(mouseX, mouseY);
        int listLeft = listLeft();
        int listRight = listRight();
        for (int i = 0; i < L.visibleRows; i++) {
            int idx = scrollRow + i;
            if (idx >= stock.size()) {
                break;
            }
            Article a = stock.get(idx);
            int rowY = topPos + L.listTop + i * L.rowH;
            boolean afford = level >= a.costLevels();
            if (idx == hovered) {
                g.fill(listLeft, rowY, listRight, rowY + L.rowH - 1, L.cHover);
            }
            ItemStack stack = a.stack();
            int ix = leftPos + L.iconX;
            int iy = rowY + L.iconYOff;
            g.renderItem(stack, ix, iy);
            g.renderItemDecorations(this.font, stack, ix, iy);

            String cost = a.costLevels() + " lvl";
            int costX = listRight - font.width(cost) - L.costRightPad;
            int textY = rowY + L.textYOff;

            String name = trim(stack.getHoverName().getString(), costX - (leftPos + L.nameX) - 8);
            g.drawString(this.font, name, leftPos + L.nameX, textY, afford ? L.cTxt : L.cTxtDim, false);
            g.drawString(this.font, cost, costX, textY, afford ? L.cTxt : L.cTxtLock, false);
            if (!afford) {
                drawLock(g, costX - 11, textY - 1, L.cTxtLock);
            }
        }
    }

    private void renderScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackX = leftPos + L.panelW - L.scrollbarRightMargin;
        int trackTop = topPos + L.scrollbarTop;
        int trackH = L.scrollbarHeight;
        g.fill(trackX, trackTop, trackX + L.scrollbarW, trackTop + trackH, L.cSbTrack);
        int total = menu.getStock().size();
        int thumbH = Math.max(18, trackH * L.visibleRows / total);
        int thumbY = trackTop + (trackH - thumbH) * scrollRow / max;
        g.fill(trackX, thumbY, trackX + L.scrollbarW, thumbY + thumbH, L.cSbThumb);
    }

    private void renderFooter(GuiGraphics g) {
        String lab = "LUCKY LVL ";
        int x = leftPos + 8, y = topPos + L.footerY;
        g.drawString(this.font, lab, x, y, L.cTxtDim, false);                                  // label en vert atténué
        g.drawString(this.font, Integer.toString(ClientXpCache.level), x + font.width(lab), y, L.cTxt, false);  // niveau en vert vif
        renderRarityBadge(g);
    }

    /** Rarity word (right-aligned at rarity_text_x) + a blinking rarity-coloured pill (pulse + glow). */
    private void renderRarityBadge(GuiGraphics g) {
        Rarity r = menu.getRarity();
        int rgb = r.pillColor();
        int argb = 0xFF000000 | rgb;
        String label = r.name();
        g.drawString(this.font, label, leftPos + L.rarityTextX - font.width(label), topPos + L.rarityTextY, argb, false);
        float pulse = 0.5F + 0.5F * (float) Math.sin(animTicks * L.rarityPillSpeed);
        int cx = leftPos + L.rarityPillX, cy = topPos + L.rarityPillY, rad = L.rarityPillRadius;
        drawDot(g, cx, cy, rad + 2, withAlpha(rgb, (int) (35 + 55 * pulse)));   // halo
        drawDot(g, cx, cy, rad, withAlpha(rgb, (int) (130 + 125 * pulse)));     // coeur clignotant
        drawDot(g, cx - 1, cy - 1, rad - 2, withAlpha(0xFFFFFF, (int) (80 * pulse)));  // reflet
    }

    private void drawDot(GuiGraphics g, int cx, int cy, int r, int argb) {
        if (r <= 0) {
            g.fill(cx, cy, cx + 1, cy + 1, argb);
            return;
        }
        double rr = (r + 0.5) * (r + 0.5);   // (r+0.5)^2 threshold = nicely round filled circle
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.floor(Math.sqrt(rr - (double) dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, argb);
        }
    }

    private static int withAlpha(int rgb, int a) {
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private void renderScanlines(GuiGraphics g, float pt) {
        int x0 = leftPos + L.border + 1, x1 = leftPos + L.panelW - L.border - 1;
        int y0 = topPos + L.border + 1, y1 = topPos + L.panelH - L.border - 1;
        for (int y = y0; y < y1; y += L.scanSpacing) {
            g.fill(x0, y, x1, y + 1, L.cScanDark);
        }
        float t = animTicks + pt;
        int h = y1 - y0;
        for (int k = 0; k < L.scanMoving && h > 0; k++) {
            int yy = y0 + (int) ((t * L.scanSpeed + k * (h / (float) Math.max(1, L.scanMoving))) % h);
            g.fill(x0, yy, x1, yy + 1, L.cScanLine);
        }
    }

    private void renderHoverTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int idx = rowAt(mouseX, mouseY);
        if (idx >= 0 && idx < menu.getStock().size()) {
            g.renderTooltip(this.font, menu.getStock().get(idx).stack(), mouseX, mouseY);
        }
    }

    // ---- input ----

    private int rowAt(int mouseX, int mouseY) {
        int top = topPos + L.listTop;
        if (mouseX < listLeft() || mouseX > listRight() || mouseY < top || mouseY >= top + L.visibleRows * L.rowH) {
            return -1;
        }
        int idx = scrollRow + (mouseY - top) / L.rowH;
        return idx < menu.getStock().size() ? idx : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll() > 0) {
            scrollRow = Math.max(0, Math.min(scrollRow - (int) Math.signum(delta), maxScroll()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int trackX = leftPos + L.panelW - L.scrollbarRightMargin;
            int trackTop = topPos + L.scrollbarTop;
            int trackH = L.scrollbarHeight;
            if (maxScroll() > 0 && mouseX >= trackX && mouseX <= trackX + L.scrollbarW && mouseY >= trackTop && mouseY <= trackTop + trackH) {
                draggingThumb = true;
                setScrollFromMouse(mouseY);
                return true;
            }
            int idx = rowAt((int) mouseX, (int) mouseY);
            if (idx >= 0) {
                Article a = menu.getStock().get(idx);
                if (ClientXpCache.level >= a.costLevels()) {
                    buy(idx);
                    playClick(1.2F);
                } else {
                    playClick(0.6F);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingThumb) {
            setScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingThumb = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void setScrollFromMouse(double mouseY) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        double frac = (mouseY - (topPos + L.scrollbarTop)) / (double) L.scrollbarHeight;
        scrollRow = Math.max(0, Math.min((int) Math.round(frac * max), max));
    }

    private void buy(int index) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
        }
    }

    private void playClick(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    // ---- helpers ----

    private String typeLabel() {
        MachineType t = menu.getMachineType();
        return switch (t) {
            case POTIONS -> "POTIONS";
            case INFUSED_LB -> "LUCKY BLOCKS";
            case ORES -> "MINERALS";
        };
    }

    private void centered(GuiGraphics g, String s, int cx, int y, int color) {
        g.drawString(this.font, s, cx - font.width(s) / 2, y, color, false);
    }

    private String trim(String s, int maxW) {
        if (font.width(s) <= maxW) {
            return s;
        }
        while (s.length() > 1 && font.width(s + "..") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    private void drawLock(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 3, x + 6, y + 8, color);
        g.fill(x + 1, y, x + 2, y + 4, color);
        g.fill(x + 4, y, x + 5, y + 4, color);
        g.fill(x + 1, y, x + 5, y + 1, color);
    }
}
