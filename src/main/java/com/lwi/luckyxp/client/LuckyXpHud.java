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

import java.util.Set;

/**
 * Draws the blue Lucky XP bar above the vanilla XP bar, with its level number centered like vanilla's.
 *
 * <p><b>The vanilla bar is left to vanilla.</b> We used to cancel its overlay and redraw it ourselves,
 * to move its level number to the left; that also meant {@code Gui.renderExperienceBar} never ran, and
 * any mod hooking it got nothing -- Immersive Hotbar's animated bar, front glow, level-up particles and
 * number pulse all died silently. Drawing only our own bar costs us the left-aligned green number and
 * buys back every one of those, for free and for any future HUD mod.
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
    // HUD layout (bar Y, number offset, reserve, gap) is hot-reloadable via config/luckyxp-client.toml (LuckyXpConfig).
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
        // (3) The blue bar, at its TRUE position (cancel the lift locally), in the gap that opened.
        //     BELOW the vanilla XP overlay on purpose: the level number swells on level-up (Immersive
        //     Hotbar), and drawing our bar afterwards would clip the top of it.
        event.registerBelow(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "luckyxp_bar",
                (gui, graphics, partialTick, w, h) -> {
                    graphics.pose().pushPose();
                    graphics.pose().translate(0.0, liftAmount(), 0.0);
                    draw(graphics, w, h, partialTick);
                    graphics.pose().popPose();
                });
        // (4) The level-up burst, over everything else.
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "luckyxp_overlay",
                (gui, graphics, partialTick, w, h) -> {
                    graphics.pose().pushPose();
                    graphics.pose().translate(0.0, liftAmount(), 0.0);
                    LuckyXpBarAnimator.renderParticles(graphics);
                    graphics.pose().popPose();
                });
    }

    /**
     * Overlays that must NOT ride the cluster lift, un-lifted around their own render.
     *
     * <p>The vanilla XP bar is one: it belongs at its vanilla height, and the room we open is for the
     * blue bar ABOVE it. Improved Mobs' bottom-right difficulty text is the other -- it registers below
     * EXPERIENCE_BAR, inside our lift window, so it rode up with the cluster and covered the
     * shared-lives hearts.
     *
     * <p>Forge forbids ordering an overlay against ANOTHER mod's ("Only order against vanilla's and
     * your own"), so the counter-lift wraps their render through the per-overlay Pre/Post events
     * instead. Pre runs at LOWEST (and skips cancelled events), so the +/- translate pair can never go
     * unbalanced.
     */
    @Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = Dist.CLIENT)
    public static final class UnliftedOverlays {
        private static final Set<ResourceLocation> IDS = Set.of(
                VanillaGuiOverlay.EXPERIENCE_BAR.id(),
                new ResourceLocation("improvedmobs", "difficulty_overlay"));

        private UnliftedOverlays() {}

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void onPre(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
            if (IDS.contains(event.getOverlay().id())) {
                event.getGuiGraphics().pose().translate(0.0, liftAmount(), 0.0);
            }
        }

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void onPost(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post event) {
            if (IDS.contains(event.getOverlay().id())) {
                event.getGuiGraphics().pose().translate(0.0, -liftAmount(), 0.0);
            }
        }
    }

    /** Cluster lift in px: full "reserve" when the blue Lucky XP bar is shown, else a small "gap" above the vanilla XP bar (both from config). */
    private static int liftAmount() {
        return shouldShow() ? LuckyXpConfig.CLIENT.reserve.get() : LuckyXpConfig.CLIENT.gap.get();
    }

    /**
     * Exactly the rule vanilla applies to its own XP bar: shown whenever experience is a thing, empty
     * or not, and gone in creative and spectator. It used to also require some Lucky XP to exist, which
     * made the bar pop in mid-run while the green one had been sitting there empty since spawn -- two
     * bars that are meant to read as one pair should appear and disappear together.
     */
    private static boolean shouldShow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return false;
        }
        return mc.gameMode != null && mc.gameMode.hasExperience();
    }

    private static void draw(GuiGraphics graphics, int screenWidth, int screenHeight, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        int x = (screenWidth - BAR_WIDTH) / 2;
        LuckyXpConfig.Client cfg = LuckyXpConfig.CLIENT;

        LuckyXpBarAnimator.tick();

        // The green bar is drawn by vanilla (and animated by whatever hooks renderExperienceBar). Ours
        // sits above it, same two-blit recipe as vanilla's, from the blue sprite.
        if (shouldShow()) {
            int yBar = screenHeight - cfg.luckyXpBarY.get();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(LUCKY_BAR, x, yBar, 0, 0, BAR_WIDTH, 5, 256, 256); // empty groove
            int filled = (int) (LuckyXpBarAnimator.progress() * 183.0F);
            if (filled > 0) {
                graphics.blit(LUCKY_BAR, x, yBar, 0, 5, Math.min(filled, BAR_WIDTH), 5, 256, 256); // blue fill
            }
            LuckyXpBarAnimator.renderGlow(graphics, x, yBar, 5);

            // Player Locator (Lucky Tweaks) rides OUR bar, not the vanilla one: drawn here it lands on
            // top of the blue fill but under both level numbers, which is the order we want. On the
            // vanilla bar it could not -- vanilla draws its bar and its number in one method, leaving
            // no seam between them for an overlay, and the only injection point is the one Immersive
            // Hotbar already uses to scale that number.
            LocatorOverlay.renderLocatorOnBar(graphics, screenWidth, screenHeight, yBar, yBar, partialTick);

            int level = ClientXpCache.level;
            if (level > 0) {
                // Centered, exactly like vanilla's own level number, so both bars read as one unit,
                // and swelling on level-up the same way.
                String s = Integer.toString(level);
                int sx = (screenWidth - font.width(s)) / 2;
                int sy = yBar + cfg.luckyNumberDy.get();
                float scale = LuckyXpBarAnimator.pulseScale();
                if (scale > 1.0F) {
                    float cx = sx + font.width(s) / 2.0F;
                    float cy = sy + 4.0F;
                    graphics.pose().pushPose();
                    graphics.pose().translate(cx, cy, 0.0F);
                    graphics.pose().scale(scale, scale, 1.0F);
                    graphics.pose().translate(-cx, -cy, 0.0F);
                    drawNumber(graphics, font, s, sx, sy, LUCKY_BLUE);
                    graphics.pose().popPose();
                } else {
                    drawNumber(graphics, font, s, sx, sy, LUCKY_BLUE);
                }
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
