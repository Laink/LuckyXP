package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpConfig;
import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckytweaks.client.LocatorOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the XP HUD: the vanilla XP bar (re-implemented, since {@link VanillaXpBarHider} cancels the
 * vanilla overlay) with its level number on the LEFT, plus our blue Lucky XP bar just above it with
 * its level number also on the LEFT.
 *
 * <p>Space is made the same way Quark's Hotbar Swapper does it: by translating the SHARED HUD
 * PoseStack, NOT by bumping ForgeGui's leftHeight/rightHeight. A "lift" overlay (below PLAYER_HEALTH)
 * does {@code pose.translate(0,-RESERVE,0)}; because Forge renders every ForgeGui overlay in one pass
 * without resetting the matrix, EVERYTHING after it -- hearts/armor/food/air AND the held-item name
 * that pops up on hotbar scroll (the overlap we hit before) AND any other mod's overlay in that
 * window -- rides up together. A "restore" overlay (below POTION_ICONS) undoes it so the potion icons
 * and F3 text stay put. The "bar" overlay cancels the lift locally (push / translate(+RESERVE) / pop)
 * so our two bars sit at their true position in the opened gap. Composes with Quark's own lift (both
 * just translate the shared matrix). All three are gated on the same {@code shouldShow()} so the
 * net translate stays balanced.
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LuckyXpHud {
    private static final int BAR_WIDTH = 182;
    private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    // HUD layout (bar Ys, number positions, reserve, gap) is hot-reloadable via config/luckyxp-client.toml (LuckyXpConfig).
    private static final int VANILLA_GREEN = 0xFF80FF20;
    private static final int LUCKY_BLUE = 0xFF40C8FF;
    // Our XP-bar sprite = the vanilla XP-bar sprite recoloured blue (empty groove rows 0-4, blue fill
    // rows 5-9, in a 256x256 sheet). Blitted exactly like vanilla blits icons.png -> the real bar look, blue.
    private static final ResourceLocation LUCKY_BAR = new ResourceLocation(LuckyXpMod.MODID, "textures/gui/lucky_xp_bar.png");

    private LuckyXpHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        // (1) Lift the whole bottom cluster up via the shared PoseStack (Quark's technique): hearts/
        //     armor/food/air + the held-item name all ride up. RESERVE when our blue bar shows, else a
        //     small GAP so the vanilla XP bar is never glued to the hearts.
        event.registerBelow(VanillaGuiOverlay.PLAYER_HEALTH.id(), "luckyxp_lift",
                (gui, graphics, partialTick, w, h) -> graphics.pose().translate(0.0, -liftAmount(), 0.0));
        // (2) Undo the lift before the potion icons / F3 so those are NOT shifted.
        event.registerBelow(VanillaGuiOverlay.POTION_ICONS.id(), "luckyxp_restore",
                (gui, graphics, partialTick, w, h) -> graphics.pose().translate(0.0, liftAmount(), 0.0));
        // (3) Draw our bars at their TRUE position (cancel the lift locally), in the gap that opened,
        //     after the (cancelled) vanilla XP overlay slot.
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "luckyxp_bar",
                (gui, graphics, partialTick, w, h) -> {
                    graphics.pose().pushPose();
                    graphics.pose().translate(0.0, liftAmount(), 0.0);
                    draw(graphics, w, h);
                    // Player Locator (Lucky Tweaks): markers on the green vanilla XP bar, name plaques
                    // raised above the blue Lucky XP bar so they don't cover it. Drawn here -- inside the
                    // lift-cancelled matrix -- so it sits at the bars' true on-screen position.
                    int markerY = h - LuckyXpConfig.CLIENT.vanillaXpBarY.get();
                    int plaqueY = shouldShow() ? h - LuckyXpConfig.CLIENT.luckyXpBarY.get() : markerY;
                    LocatorOverlay.renderLocatorOnBar(graphics, w, h, markerY, plaqueY, partialTick);
                    graphics.pose().popPose();
                });
    }

    /** Cluster lift in px: full "reserve" when the blue Lucky XP bar is shown, else a small "gap" above the vanilla XP bar (both from config). */
    private static int liftAmount() {
        return shouldShow() ? LuckyXpConfig.CLIENT.reserve.get() : LuckyXpConfig.CLIENT.gap.get();
    }

    private static boolean shouldShow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return false;
        }
        return ClientXpCache.level > 0 || ClientXpCache.into > 0 || ClientXpCache.total > 0;
    }

    private static void draw(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        int x = (screenWidth - BAR_WIDTH) / 2;
        LuckyXpConfig.Client cfg = LuckyXpConfig.CLIENT;
        int gapX = cfg.numberGapX.get();

        // (1) Vanilla XP bar re-implemented (we cancelled the vanilla overlay) -- green number on the LEFT.
        if (mc.gameMode != null && mc.gameMode.hasExperience()) {
            int gy = screenHeight - cfg.vanillaXpBarY.get(); // vanilla XP bar position
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(ICONS, x, gy, 0, 64, BAR_WIDTH, 5);
            int prog = (int) (mc.player.experienceProgress * 183.0F);
            if (prog > 0) {
                graphics.blit(ICONS, x, gy, 0, 69, Math.min(prog, BAR_WIDTH), 5);
            }
            if (mc.player.experienceLevel > 0) {
                String s = Integer.toString(mc.player.experienceLevel);
                drawNumber(graphics, font, s, x - font.width(s) - gapX, gy + cfg.vanillaNumberDy.get(), VANILLA_GREEN);
            }
        }

        // (2) Our blue Lucky XP bar just above it -- blue number on the LEFT (same column).
        if (shouldShow()) {
            int yBar = screenHeight - cfg.luckyXpBarY.get();
            int into = ClientXpCache.into;
            int need = ClientXpCache.toNext;
            // Same two-blit recipe as vanilla's renderExperienceBar, but from our blue sprite.
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(LUCKY_BAR, x, yBar, 0, 0, BAR_WIDTH, 5, 256, 256); // empty groove
            if (need > 0 && into > 0) {
                int filled = (int) (((float) into / (float) need) * 183.0F);
                if (filled > 0) {
                    graphics.blit(LUCKY_BAR, x, yBar, 0, 5, Math.min(filled, BAR_WIDTH), 5, 256, 256); // blue fill
                }
            }
            int level = ClientXpCache.level;
            if (level > 0) {
                String s = Integer.toString(level);
                drawNumber(graphics, font, s, x - font.width(s) - gapX, yBar + cfg.luckyNumberDy.get(), LUCKY_BLUE);
            }
        }
    }

    private static void drawNumber(GuiGraphics graphics, Font font, String s, int sx, int sy, int color) {
        graphics.drawString(font, s, sx + 1, sy, 0xFF000000, false);
        graphics.drawString(font, s, sx - 1, sy, 0xFF000000, false);
        graphics.drawString(font, s, sx, sy + 1, 0xFF000000, false);
        graphics.drawString(font, s, sx, sy - 1, 0xFF000000, false);
        graphics.drawString(font, s, sx, sy, color, false);
    }
}
