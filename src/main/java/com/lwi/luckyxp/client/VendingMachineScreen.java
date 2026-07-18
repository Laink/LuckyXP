package com.lwi.luckyxp.client;

import com.lwi.luckyxp.machine.Article;
import com.lwi.luckyxp.machine.MachineType;
import com.lwi.luckyxp.machine.Rarity;
import com.lwi.luckyxp.machine.VendingMachineMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    /** The lucky-XP orb hugging each price (designer 2026-07-18): lit when affordable, dark when not. */
    private static final ResourceLocation XP_ICON =
            new ResourceLocation("luckyxp", "textures/gui/lucky_xp_icon.png");
    private static final ResourceLocation XP_ICON_OFF =
            new ResourceLocation("luckyxp", "textures/gui/lucky_xp_icon_off.png");
    private static final int XP_ICON_SIZE = 9;

    private final VendingLayout L;
    private int animTicks;
    private int scrollRow;
    private boolean draggingThumb;

    // Purchase feedback (client-only, wall-clock timed so it is framerate-independent).
    private int flashRow = -1;
    private long flashUntilMs;
    private static final long FLASH_MS = 450L;
    /** On-top message plate (server feedback rerouted here by {@link ScreenMessageRouter}). */
    private final ScreenToast toast = new ScreenToast();

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
        // Drawn last: server feedback BELOW the panel (20px under it), never over the GUI itself —
        // clamped to the screen edge for tall GUI scales where there is no room underneath.
        toast.render(g, font, leftPos + L.panelW / 2,
                Math.min(topPos + L.panelH + 20, this.height - 15));
    }

    /** Show a message ON TOP of the screen (used by {@link ScreenMessageRouter} for action-bar feedback). */
    public void showToast(Component message) {
        toast.show(message);
    }

    private void flash(int row) {
        flashRow = row;
        flashUntilMs = System.currentTimeMillis() + FLASH_MS;
    }

    /** Whether the given row should be tinted red this frame (fast blink while the flash lasts). */
    private boolean isFlashing(int idx) {
        return idx == flashRow && System.currentTimeMillis() < flashUntilMs
                && (System.currentTimeMillis() / 120L) % 2L == 0L;
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
        int y = topPos + L.headerTypeY;
        g.drawString(this.font, typeLabel(), leftPos + 8, y, L.cTxt, false);          // machine title, left
        String timer = timerLabel();
        if (!timer.isEmpty()) {                                                        // countdown, right (designer 2026-07-18: 1px right)
            g.drawString(this.font, timer, leftPos + L.panelW - 7 - font.width(timer), y, timerColor(), false);
        }
    }

    /** Header timer colour: normal CRT green, blinking red at 2 Hz in the final {@code URGENT_SECONDS}
     *  (in sync with the in-world display), solid red once CLOSED. */
    private int timerColor() {
        if (minecraft == null || minecraft.level == null || menu.closeAt() < 0) {
            return L.cTimer;
        }
        long remaining = menu.closeAt() - minecraft.level.getGameTime();
        if (remaining <= 0) {
            return 0xFFFF5555;                                                          // CLOSED
        }
        boolean urgent = remaining / 20 <= com.lwi.luckyxp.machine.StandTimer.URGENT_SECONDS;
        if (urgent && (minecraft.level.getGameTime() / 10) % 2 == 0) {
            return 0xFFFF5555;                                                          // red on alternate half-seconds
        }
        return L.cTimer;
    }

    /** Live stand countdown for the header, derived each frame from the client's own synced game time
     *  (so it ticks down without any per-tick packet). Empty until the timer is armed; "CLOSED" once up. */
    private String timerLabel() {
        long closeAt = menu.closeAt();
        if (closeAt < 0 || minecraft == null || minecraft.level == null) {
            return "";
        }
        long remaining = closeAt - minecraft.level.getGameTime();
        if (remaining <= 0) {
            return I18n.get("luckyxp.gui.closed");
        }
        int s = (int) (remaining / 20);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        List<Article> stock = menu.getStock();
        if (stock.isEmpty()) {
            centered(g, I18n.get("luckyxp.gui.empty"), leftPos + L.panelW / 2, topPos + L.listTop + 50, L.cTxtDim);
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
            boolean sold = a.sold();
            // Creative buys everything free (like the vanilla anvil), so nothing is ever locked there.
            boolean afford = !sold && (isCreative() || level >= a.costLevels());
            if (isFlashing(idx)) {
                g.fill(listLeft, rowY, listRight, rowY + L.rowH - 1, 0x66FF3030);   // rejected: red blink
            } else if (idx == hovered && !sold) {
                g.fill(listLeft, rowY, listRight, rowY + L.rowH - 1, L.cHover);
            }
            ItemStack stack = a.stack();
            int ix = leftPos + L.iconX;
            int iy = rowY + L.iconYOff;
            boolean locked = !sold && !afford;
            if (locked) {
                // Best-effort dimming of the item render (designer 2026-07-18). The shader colour is a
                // global multiplier the item pipeline honours for most models; if some don't, they just
                // stay full-bright — no harm done.
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.55F);
            }
            g.renderItem(stack, ix, iy);
            g.renderItemDecorations(this.font, stack, ix, iy);
            if (locked) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }

            // Price = the bare number in lucky-XP blue with its dark outline, an XP orb hugging its
            // left (lit or dark with affordability). SOLD keeps its plain label — it is not a price.
            String cost = sold ? I18n.get("luckyxp.gui.sold") : Integer.toString(a.costLevels());
            int costX = listRight - font.width(cost) - L.costRightPad;
            int textY = rowY + L.textYOff;

            // Strip embedded formatting codes (addon names carry literal colour/bold codes) so every
            // name renders in the CRT's own normalized colours.
            String rawName = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString());
            String name = trim(rawName != null ? rawName : stack.getHoverName().getString(),
                    costX - (leftPos + L.nameX) - 14);
            g.drawString(this.font, name, leftPos + L.nameX, textY,
                    sold ? L.cTxtDim : (afford ? L.cTxt : L.cNameLock), false);
            if (sold) {
                g.drawString(this.font, cost, costX, textY, L.cTxtLock, false);
                // Single-purchase line already bought: strike the name through.
                int lineY = textY + font.lineHeight / 2 - 1;
                g.fill(leftPos + L.nameX - 1, lineY, leftPos + L.nameX + font.width(name) + 1, lineY + 1, L.cTxtLock);
            } else {
                // Orb BEHIND the number, the digits overlapping it by 3px — the vanilla enchanting
                // screen's level-orb look. Drawn first so the outlined number sits on top.
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                g.blit(afford ? XP_ICON : XP_ICON_OFF, costX + 3 - XP_ICON_SIZE, rowY + (L.rowH - XP_ICON_SIZE) / 2,
                        0.0F, 0.0F, XP_ICON_SIZE, XP_ICON_SIZE, XP_ICON_SIZE, XP_ICON_SIZE);
                RenderSystem.disableBlend();
                drawOutlined(g, cost, costX, textY, afford ? L.cCost : L.cCostLock, L.cCostOutline);
            }
        }
    }

    /** 8-way text outline: the outline colour stamped around, the number on top. */
    private void drawOutlined(GuiGraphics g, String s, int x, int y, int color, int outline) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    g.drawString(this.font, s, x + dx, y + dy, outline, false);
                }
            }
        }
        g.drawString(this.font, s, x, y, color, false);
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
        // One string, one colour — the lucky-XP blue (designer 2026-07-18).
        g.drawString(this.font, I18n.get("luckyxp.gui.lucky_lvl", ClientXpCache.level),
                leftPos + 8, topPos + L.footerY, L.cLvl, false);
        renderRarityBadge(g);
    }

    /** Rarity word (right-aligned at rarity_text_x) + a blinking rarity-coloured pill (pulse + glow). */
    private void renderRarityBadge(GuiGraphics g) {
        Rarity r = menu.getRarity();
        int rgb = r.pillColor();
        int argb = 0xFF000000 | rgb;
        String label = I18n.get("luckyxp.rarity." + r.getSerializedName());
        g.drawString(this.font, label, leftPos + L.rarityTextX - font.width(label), topPos + L.rarityTextY, argb, false);
        float pulse = 0.5F + 0.5F * (float) Math.sin(animTicks * L.rarityPillSpeed);
        int cx = leftPos + L.rarityPillX, cy = topPos + L.rarityPillY, rad = L.rarityPillRadius;
        drawDot(g, cx, cy, rad + 2, withAlpha(rgb, (int) (35 + 55 * pulse)));   // halo
        drawDot(g, cx, cy, rad, withAlpha(rgb, (int) (130 + 125 * pulse)));     // blinking core
        drawDot(g, cx - 1, cy - 1, rad - 2, withAlpha(0xFFFFFF, (int) (80 * pulse)));  // highlight
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

    // ---- JEI integration ----

    /** The stock article under the mouse (stack + on-screen row rect), for JEI's R/U hotkeys. Null = none. */
    @javax.annotation.Nullable
    public JeiHover jeiHover(double mouseX, double mouseY) {
        int idx = rowAt((int) mouseX, (int) mouseY);
        if (idx < 0) {
            return null;
        }
        int rowY = topPos + L.listTop + (idx - scrollRow) * L.rowH;
        return new JeiHover(menu.getStock().get(idx).stack(),
                new net.minecraft.client.renderer.Rect2i(listLeft(), rowY, listRight() - listLeft(), L.rowH));
    }

    public record JeiHover(ItemStack stack, net.minecraft.client.renderer.Rect2i area) {}

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
                if (a.sold()) {
                    flash(idx);
                    playClick(0.6F);
                } else if (isCreative() || ClientXpCache.level >= a.costLevels()) {
                    // The goods drop out of the tray onto the ground, so inventory space is never a
                    // blocker — the sale always goes through if it's affordable.
                    buy(idx);
                    menu.markSoldLocal(idx);                    // immediate SOLD feedback; server is authoritative
                    playPurchase();                             // "cha-ching" + the tray's piston clunk
                    // Tell the buyer WHERE their purchase went: it's on the ground, not in the bag.
                    showToast(Component.translatable("luckyxp.gui.dispensed")
                            .withStyle(net.minecraft.ChatFormatting.GREEN));
                } else {
                    playClick(0.6F);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Creative mode buys everything for free — mirror of the server-side instabuild bypass. */
    private boolean isCreative() {
        return minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
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

    /** A proper "cha-ching": two bright amethyst chimes an octave apart, plus the XP-orb pickup blip
     *  underneath for the transaction feel, and a redstone-piston clunk — the machine's tray pushing
     *  the goods out (user 2026-07-19). Played only on a successful purchase. */
    private void playPurchase() {
        if (minecraft == null) {
            return;
        }
        var sm = minecraft.getSoundManager();
        sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.9F));
        sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.8F, 0.7F));
        sm.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4F, 0.4F));
        sm.play(SimpleSoundInstance.forUI(SoundEvents.PISTON_EXTEND, 1.1F, 0.8F));
    }

    // ---- helpers ----

    private String typeLabel() {
        MachineType t = menu.getMachineType();
        return I18n.get(switch (t) {
            case POTIONS -> "luckyxp.machine.type.consumables";
            case INFUSED_LB -> "luckyxp.machine.type.lucky_blocks";
            case ORES -> "luckyxp.machine.type.minerals";
            case TOOLS -> "luckyxp.machine.type.tools";
        });
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

}
