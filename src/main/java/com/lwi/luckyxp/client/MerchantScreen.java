package com.lwi.luckyxp.client;

import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.machine.MerchantMenu;
import com.lwi.luckyxp.machine.Rarity;
import com.lwi.luckyxp.entity.LuckyMerchant;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/**
 * The mad scientist's shop, on the designer's textures (2026-07-18): his blackboard top-left with the
 * live 3D merchant standing in front of it, a metal-plaque service list under a piped overlay frame, the
 * stand countdown on the top-right clock, the buyer's Lucky level on the blue bottom bar, the merchant's
 * rarity on a little LCD (with its coloured LED), and — when his hat earns one — a tilted discount coupon.
 *
 * <p>Layout contract: every coordinate below is straight from the designer's spec sheet (menu-relative
 * pixels on a 290x267 GUI, cropped from two 290x384 sheets). Draw order is background -> 3D merchant
 * (cropped at the board's foot) -> plaque rows (scissored) -> scrollbar -> overlay frame -> coupon +
 * LED -> texts, so the pipes pass in FRONT of the merchant and the coupon sits on the frame.
 */
public class MerchantScreen extends AbstractContainerScreen<MerchantMenu> {
    /** Order MUST match MerchantMenu's SERVICE_* indices: a row's position IS the button id.
     *  Lang keys (en_us/fr_fr) — the machine's ITEMS keep their own Minecraft localisation. */
    private static final String[] SERVICES = {
            "luckyxp.merchant.service.reroll",
            "luckyxp.merchant.service.convert",
            "luckyxp.merchant.service.temp_luck",
            "luckyxp.merchant.service.perm_luck",
            "luckyxp.merchant.service.heal",
            "luckyxp.merchant.service.repair",
            "luckyxp.merchant.service.difficulty",
            "luckyxp.merchant.service.life"
    };

    private static final ResourceLocation BG =
            new ResourceLocation("luckyxp", "textures/gui/lucky_merchant_gui_background.png");
    private static final ResourceLocation OVERLAY =
            new ResourceLocation("luckyxp", "textures/gui/lucky_merchant_gui_overlays.png");
    /** The price tag's Lucky-XP orb (designer 2026-07-18): lit when affordable, dark when not. */
    private static final ResourceLocation XP_ICON =
            new ResourceLocation("luckyxp", "textures/gui/lucky_xp_icon.png");
    private static final ResourceLocation XP_ICON_OFF =
            new ResourceLocation("luckyxp", "textures/gui/lucky_xp_icon_off.png");
    private static final int XP_ICON_SIZE = 9, XP_ICON_OVERLAP = 3;   // digits ride 3px onto the orb
    /** Both sheets share this size; blit() must know it (defaults assume 256x256). */
    private static final int TEX_W = 290, TEX_H = 384;

    // ---- designer spec: crops in the background sheet (x y w h) ----
    private static final int GUI_W = 290, GUI_H = 267;
    private static final int ROW_U = 1, ROW_V = 271, ROW_W = 226, ROW_H = 20;
    private static final int SB_U = 1, SB_V = 292, SB_W = 5, SB_TEX_H = 67, SB_CAP = 3;   // 3-slice caps
    private static final int LED_V = 292, LED_SIZE = 9;                                    // u per rarity below
    private static final int COUPON_U = 8, COUPON_V = 304, COUPON_W = 40, COUPON_H = 32;

    // ---- designer spec: placements (menu-relative) ----
    private static final int LIST_X = 27, LIST_TOP = 124, ROW_PAD = 2, ROW_STRIDE = ROW_H + ROW_PAD;
    private static final int CLIP_X0 = 24, CLIP_Y0 = 122, CLIP_X1 = 256, CLIP_Y1 = 249;
    private static final int TEXT_X = 32;                    // row label X (LIST_X + 5px padding)
    private static final int COST_RIGHT = LIST_X + ROW_W - 5;
    private static final int SB_X = 265, SB_TRACK_TOP = 119, SB_TRACK_BOTTOM = 231;
    private static final int LED_X = 256, LED_Y = 249;
    private static final int COUPON_X = 156, COUPON_Y = 228;
    private static final float COUPON_ANGLE = -22.57F;       // ticket tilt, rising left-to-right
    private static final int BOARD_FOOT_Y = 93;              // 3D merchant cropped below this line only
    private static final int BOARD_CENTER_X = 16 + 116 / 2;  // board is at (16,0) 116x93
    private static final int NPC_SCALE = 50;
    private static final int NAME_X = 23, NAME_Y = 97, NAME_W = 100, NAME_H = 9;
    private static final int TIMER_X = 224, TIMER_Y = 93, TIMER_W = 34, TIMER_H = 15;
    private static final int LVL_Y = 248, LVL_H = 10;        // text left-aligned at TEXT_X (spec)
    private static final int LCD_X = 181, LCD_Y = 248, LCD_W = 69, LCD_H = 10;

    // ---- designer spec: colours ----
    private static final int C_ROW_TEXT = 0xFFFEE9BA;
    private static final int C_COST = 0xFF87ECFF;
    private static final int C_COST_LOCKED = 0xFF0085AC;
    private static final int C_COST_OUTLINE = 0xFF03130B;    // same dark rim as the machine's prices
    private static final int C_NAME = 0xFF121315;
    private static final int C_NAME_SHADOW = 0x40121315;     // same hue, 25% opacity
    private static final int C_TIMER = 0xFFFEE9BA;
    private static final int C_TIMER_SHADOW = 0x40FEE9BA;
    private static final int C_LVL = 0xFFB7EEF8;
    private static final int C_HOVER = 0x18FFFFFF;           // soft brightness lift, matches VM's hover idea

    private LuckyMerchant portrait;
    private int scrollPx;
    private boolean draggingThumb;
    /** On-top message plate (server feedback rerouted here by {@link ScreenMessageRouter}). */
    private final ScreenToast toast = new ScreenToast();

    public MerchantScreen(MerchantMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft != null && minecraft.level != null && portrait == null) {
            portrait = new LuckyMerchant(Registration.LUCKY_MERCHANT.get(), minecraft.level);
        }
    }

    // ---- scroll geometry (content vs the scissored viewport) ----

    /** Rows plus the spec's one-full-item margin under the last one, so the bottom overlay decorations
     *  can never hide it at full scroll. */
    private int contentHeight() {
        return SERVICES.length * ROW_STRIDE - ROW_PAD + ROW_STRIDE;
    }

    private int viewHeight() {
        return CLIP_Y1 - LIST_TOP;
    }

    private int maxScrollPx() {
        return Math.max(0, contentHeight() - viewHeight());
    }

    private void clampScroll() {
        scrollPx = Math.max(0, Math.min(scrollPx, maxScrollPx()));
    }

    // ---- rendering ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        // Drawn last: server feedback BELOW the panel (20px under it), never over the GUI itself —
        // clamped to the screen edge for tall GUI scales where there is no room underneath.
        toast.render(g, font, leftPos + GUI_W / 2,
                Math.min(topPos + GUI_H + 20, this.height - 15));
    }

    /** Show a message ON TOP of the screen (used by {@link ScreenMessageRouter} for action-bar feedback). */
    public void showToast(Component message) {
        toast.show(message);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos, y0 = topPos;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(BG, x0, y0, 0.0F, 0.0F, GUI_W, GUI_H, TEX_W, TEX_H);

        renderMerchant(g, mouseX, mouseY);
        renderRows(g, mouseX, mouseY);
        renderScrollbar(g);

        // The piped frame goes over everything drawn so far — including the 3D merchant's feet.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(OVERLAY, x0, y0, 0.0F, 0.0F, GUI_W, GUI_H, TEX_W, TEX_H);

        Rarity rarity = merchantRarity();
        renderCoupon(g, rarity);
        renderLed(g, rarity);
        RenderSystem.disableBlend();

        renderTexts(g, mouseX, mouseY, rarity);
    }

    /** The live merchant in front of his blackboard, cropped ONLY at the board's foot (y=93): he may
     *  overflow the top and the sides, and the overlay's pipework then covers his boots. */
    private void renderMerchant(GuiGraphics g, int mouseX, int mouseY) {
        if (portrait == null) {
            return;
        }
        if (minecraft != null && minecraft.level != null) {
            LuckyMerchant real = menu.merchant(minecraft.level);
            if (real != null) {
                portrait.setRarity(real.getRarity());        // his hat/glasses must match the seller's
            }
        }
        g.enableScissor(0, 0, this.width, topPos + BOARD_FOOT_Y);
        int cx = leftPos + BOARD_CENTER_X;
        int feetY = topPos + BOARD_FOOT_Y;
        int eyeY = feetY - (int) (1.64F * NPC_SCALE);        // villager-family eye line, 1.64 blocks up
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, cx, feetY, NPC_SCALE, (float) cx - mouseX, (float) eyeY - mouseY, portrait);
        g.disableScissor();
    }

    private void renderRows(GuiGraphics g, int mouseX, int mouseY) {
        int level = ClientXpCache.level;
        boolean creative = minecraft != null && minecraft.player != null
                && minecraft.player.getAbilities().instabuild;
        int hovered = rowAt(mouseX, mouseY);
        g.enableScissor(leftPos + CLIP_X0, topPos + CLIP_Y0, leftPos + CLIP_X1, topPos + CLIP_Y1);
        for (int i = 0; i < SERVICES.length; i++) {
            int rowY = topPos + LIST_TOP + i * ROW_STRIDE - scrollPx;
            if (rowY + ROW_H < topPos + CLIP_Y0 || rowY > topPos + CLIP_Y1) {
                continue;
            }
            int rowX = leftPos + LIST_X;
            g.blit(BG, rowX, rowY, ROW_U, ROW_V, ROW_W, ROW_H, TEX_W, TEX_H);
            if (i == hovered) {
                g.fill(rowX, rowY, rowX + ROW_W, rowY + ROW_H, C_HOVER);
            }
            // Priced through the menu, from the merchant's own (synced) rarity, so what is drawn here is
            // exactly what the server will charge.
            int price = this.menu.price(this.minecraft.level, i);
            boolean afford = creative || level >= price;
            int textY = rowY + (ROW_H - 8) / 2;
            g.drawString(font, I18n.get(SERVICES[i]), leftPos + TEXT_X, textY, C_ROW_TEXT, false);
            // Price on its orb, right-aligned — the number overlaps the orb by 3px, the vanilla
            // enchanting screen's level-orb look (orb first, digits on top). Both go dark together
            // when the buyer can't afford the line.
            String cost = Integer.toString(price);
            int costX = leftPos + COST_RIGHT - font.width(cost);
            g.blit(afford ? XP_ICON : XP_ICON_OFF,
                    costX + XP_ICON_OVERLAP - XP_ICON_SIZE, rowY + (ROW_H - XP_ICON_SIZE) / 2,
                    0.0F, 0.0F, XP_ICON_SIZE, XP_ICON_SIZE, XP_ICON_SIZE, XP_ICON_SIZE);
            // Outlined like the machine's prices: the rim is what keeps the digits readable on the orb.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        g.drawString(font, cost, costX + dx, textY + dy, C_COST_OUTLINE, false);
                    }
                }
            }
            g.drawString(font, cost, costX, textY, afford ? C_COST : C_COST_LOCKED, false);
        }
        g.disableScissor();
    }

    /** Designer's 3-slice bar: 3px caps kept crisp, only the shaft stretched to the computed height. */
    private void renderScrollbar(GuiGraphics g) {
        int trackH = SB_TRACK_BOTTOM - SB_TRACK_TOP;
        int thumbH = maxScrollPx() <= 0
                ? trackH
                : Math.max(18, Math.min(trackH, trackH * viewHeight() / contentHeight()));
        int travel = trackH - thumbH;
        int thumbY = topPos + SB_TRACK_TOP
                + (maxScrollPx() <= 0 ? 0 : travel * scrollPx / maxScrollPx());
        int x = leftPos + SB_X;
        g.blit(BG, x, thumbY, SB_U, SB_V, SB_W, SB_CAP, TEX_W, TEX_H);
        g.blit(BG, x, thumbY + SB_CAP, SB_W, thumbH - 2 * SB_CAP,
                SB_U, SB_V + SB_CAP, SB_W, SB_TEX_H - 2 * SB_CAP, TEX_W, TEX_H);
        g.blit(BG, x, thumbY + thumbH - SB_CAP, SB_U, SB_V + SB_TEX_H - SB_CAP, SB_W, SB_CAP, TEX_W, TEX_H);
    }

    /** The tilted coupon, only when the merchant's rarity actually discounts something. Drawn OVER the
     *  overlay frame (the mock shows the ticket sitting on it), its percentage written along its tilt. */
    private void renderCoupon(GuiGraphics g, Rarity rarity) {
        int pct = Math.round(rarity.merchantDiscount() * 100.0F);
        if (pct <= 0) {
            return;
        }
        g.blit(BG, leftPos + COUPON_X, topPos + COUPON_Y, COUPON_U, COUPON_V, COUPON_W, COUPON_H, TEX_W, TEX_H);
        String label = "-" + pct + "%";
        g.pose().pushPose();
        g.pose().translate(leftPos + COUPON_X + COUPON_W / 2.0F, topPos + COUPON_Y + COUPON_H / 2.0F, 0.0F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(COUPON_ANGLE));
        g.drawString(font, label, -font.width(label) / 2, -4, C_NAME, false);
        g.pose().popPose();
    }

    private void renderLed(GuiGraphics g, Rarity rarity) {
        int u = switch (rarity) {                            // green / blue / purple / yellow in the sheet
            case COMMON -> 7;
            case RARE -> 17;
            case EPIC -> 27;
            case LEGENDARY -> 37;
        };
        g.blit(BG, leftPos + LED_X, topPos + LED_Y, u, LED_V, LED_SIZE, LED_SIZE, TEX_W, TEX_H);
    }

    private void renderTexts(GuiGraphics g, int mouseX, int mouseY, Rarity rarity) {
        // Nametag, centred on the banner, with the designer's 25%-opacity same-hue shadow.
        String name = merchantName();
        int nx = leftPos + NAME_X + (NAME_W - font.width(name)) / 2;
        int ny = topPos + NAME_Y + (NAME_H - 8) / 2 + 1;
        g.drawString(font, name, nx + 1, ny + 1, C_NAME_SHADOW, false);
        g.drawString(font, name, nx, ny, C_NAME, false);

        // Stand countdown on the clock's black screen (blinks red on the final seconds, like the machine).
        String timer = timerLabel();
        if (!timer.isEmpty()) {
            int tx = leftPos + TIMER_X + (TIMER_W - font.width(timer)) / 2;
            int ty = topPos + TIMER_Y + (TIMER_H - 8) / 2 + 1;
            g.drawString(font, timer, tx + 1, ty + 1, C_TIMER_SHADOW, false);
            g.drawString(font, timer, tx, ty, timerColor(), false);
        }

        // The buyer's own Lucky level on the blue bar — same wording as the machine's footer.
        // No +1 nudge here: on the bar's 10px the glyphs sit right at (LVL_H - 8) / 2 (user 2026-07-19).
        g.drawString(font, I18n.get("luckyxp.gui.lucky_lvl", ClientXpCache.level),
                leftPos + TEXT_X, topPos + LVL_Y + (LVL_H - 8) / 2, C_LVL, false);

        renderRarityLcd(g, mouseX, mouseY, rarity);
    }

    /** Rarity word in the machine-GUI's own rarity colour, cropped to the LCD glass (1px margin) and
     *  marquee-scrolled under the mouse if it ever outgrows the screen. */
    private void renderRarityLcd(GuiGraphics g, int mouseX, int mouseY, Rarity rarity) {
        String label = I18n.get("luckyxp.rarity." + rarity.getSerializedName());
        int color = 0xFF000000 | rarity.pillColor();
        int innerX = leftPos + LCD_X + 1, innerW = LCD_W - 2;
        int textY = topPos + LCD_Y + (LCD_H - 8) / 2 + 1;
        int w = font.width(label);
        g.enableScissor(innerX, topPos + LCD_Y, innerX + innerW, topPos + LCD_Y + LCD_H);
        boolean hoverLcd = mouseX >= innerX && mouseX < innerX + innerW
                && mouseY >= topPos + LCD_Y && mouseY < topPos + LCD_Y + LCD_H;
        if (w > innerW && hoverLcd) {
            int span = w + 16;                               // text + gap, looped for a seamless crawl
            int shift = (int) ((System.currentTimeMillis() / 40L) % span);
            g.drawString(font, label, innerX - shift, textY, color, false);
            g.drawString(font, label, innerX - shift + span, textY, color, false);
        } else {
            g.drawString(font, label, innerX, textY, color, false);
        }
        g.disableScissor();
    }

    // ---- data helpers ----

    private Rarity merchantRarity() {
        if (minecraft != null && minecraft.level != null) {
            LuckyMerchant m = menu.merchant(minecraft.level);
            if (m != null) {
                return m.getRarity();
            }
        }
        return Rarity.COMMON;
    }

    private String merchantName() {
        if (minecraft != null && minecraft.level != null) {
            LuckyMerchant m = menu.merchant(minecraft.level);
            if (m != null) {
                return m.getName().getString().toUpperCase(Locale.ROOT);
            }
        }
        return "LUCKY MERCHANT";
    }

    /** Same countdown derivation as the vending machine: closeAt is fixed once armed, so the client's own
     *  synced game time is all it takes to tick down without any packet. */
    private String timerLabel() {
        long closeAt = menu.closeAt();
        if (closeAt < 0 || minecraft == null || minecraft.level == null) {
            return "";
        }
        long remaining = closeAt - minecraft.level.getGameTime();
        if (remaining <= 0) {
            return "00:00";                                  // the stand's ticker boots this screen anyway
        }
        int s = (int) (remaining / 20);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private int timerColor() {
        if (minecraft == null || minecraft.level == null || menu.closeAt() < 0) {
            return C_TIMER;
        }
        long remaining = menu.closeAt() - minecraft.level.getGameTime();
        if (remaining <= 0) {
            return 0xFFFF5555;
        }
        boolean urgent = remaining / 20 <= com.lwi.luckyxp.machine.StandTimer.URGENT_SECONDS;
        if (urgent && (minecraft.level.getGameTime() / 10) % 2 == 0) {
            return 0xFFFF5555;                               // red on alternate half-seconds
        }
        return C_TIMER;
    }

    // ---- input ----

    private int rowAt(int mouseX, int mouseY) {
        if (mouseX < leftPos + LIST_X || mouseX >= leftPos + LIST_X + ROW_W
                || mouseY < topPos + CLIP_Y0 || mouseY >= topPos + CLIP_Y1) {
            return -1;
        }
        int rel = mouseY - (topPos + LIST_TOP) + scrollPx;
        if (rel < 0 || rel % ROW_STRIDE >= ROW_H) {          // the 2px gap between plaques is dead space
            return -1;
        }
        int idx = rel / ROW_STRIDE;
        return idx < SERVICES.length ? idx : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScrollPx() > 0) {
            scrollPx -= (int) Math.signum(delta) * ROW_STRIDE;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int trackH = SB_TRACK_BOTTOM - SB_TRACK_TOP;
            if (maxScrollPx() > 0
                    && mouseX >= leftPos + SB_X && mouseX < leftPos + SB_X + SB_W
                    && mouseY >= topPos + SB_TRACK_TOP && mouseY < topPos + SB_TRACK_TOP + trackH) {
                draggingThumb = true;
                setScrollFromMouse(mouseY);
                return true;
            }
            int idx = rowAt((int) mouseX, (int) mouseY);
            if (idx >= 0 && minecraft != null && minecraft.gameMode != null) {
                boolean creative = minecraft.player != null && minecraft.player.getAbilities().instabuild;
                if (creative || ClientXpCache.level >= this.menu.price(this.minecraft.level, idx)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, idx);
                } else {
                    // Refused locally, like the vending machine — the dark price already says why.
                    minecraft.getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.6F));
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
        int max = maxScrollPx();
        if (max <= 0) {
            return;
        }
        int trackH = SB_TRACK_BOTTOM - SB_TRACK_TOP;
        double frac = (mouseY - (topPos + SB_TRACK_TOP)) / (double) trackH;
        scrollPx = (int) Math.round(Math.max(0.0, Math.min(1.0, frac)) * max);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // all drawn in renderBg (absolute coordinates)
    }
}
