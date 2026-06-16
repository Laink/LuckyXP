package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpMod;
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
 * vanilla overlay) with its level number moved to the LEFT, plus our blue Lucky XP bar just above it
 * with its level number also on the LEFT -- so both numbers line up on the left and the two bars
 * stack together in the centre.
 *
 * <p>Two overlays: a "reserve" (below PLAYER_HEALTH) bumps ForgeGui's stack heights so the hearts/
 * armor/food/air shift up to make room; the "bar" (above EXPERIENCE_BAR) does the drawing, after the
 * (now cancelled) vanilla XP overlay.
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LuckyXpHud {
    private static final int BAR_WIDTH = 182;
    private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final int RESERVE = 11;
    private static final int VANILLA_GREEN = 0xFF80FF20;
    private static final int LUCKY_BLUE = 0xFF40C8FF;
    // Our XP-bar sprite = the vanilla XP-bar sprite recoloured blue (empty groove rows 0-4, blue fill
    // rows 5-9, in a 256x256 sheet). Blitted exactly like vanilla blits icons.png -> the real bar look, blue.
    private static final ResourceLocation LUCKY_BAR = new ResourceLocation(LuckyXpMod.MODID, "textures/gui/lucky_xp_bar.png");

    private LuckyXpHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        // Reserve our row before the bottom cluster lays out (pushes hearts/armor/food/air up).
        event.registerBelow(VanillaGuiOverlay.PLAYER_HEALTH.id(), "luckyxp_reserve",
                (gui, graphics, partialTick, w, h) -> {
                    if (shouldShow()) {
                        gui.leftHeight += RESERVE;
                        gui.rightHeight += RESERVE;
                    }
                });
        // Draw after the (cancelled) vanilla XP overlay.
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "luckyxp_bar",
                (gui, graphics, partialTick, w, h) -> draw(graphics, w, h));
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

        // (1) Vanilla XP bar re-implemented (we cancelled the vanilla overlay) -- green number on the LEFT.
        if (mc.gameMode != null && mc.gameMode.hasExperience()) {
            int gy = screenHeight - 29; // vanilla XP bar position
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(ICONS, x, gy, 0, 64, BAR_WIDTH, 5);
            int prog = (int) (mc.player.experienceProgress * 183.0F);
            if (prog > 0) {
                graphics.blit(ICONS, x, gy, 0, 69, Math.min(prog, BAR_WIDTH), 5);
            }
            if (mc.player.experienceLevel > 0) {
                String s = Integer.toString(mc.player.experienceLevel);
                drawNumber(graphics, font, s, x - font.width(s) - 2, gy - 2, VANILLA_GREEN);
            }
        }

        // (2) Our blue Lucky XP bar just above it -- blue number on the LEFT (same column).
        if (shouldShow()) {
            int yBar = screenHeight - 38;
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
                drawNumber(graphics, font, s, x - font.width(s) - 2, yBar - 2, LUCKY_BLUE);
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
