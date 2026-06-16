package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cancels the vanilla experience-bar overlay so {@link LuckyXpHud} can redraw the XP bar with its
 * level number moved to the LEFT (vanilla draws the bar + a centered number together, in one method,
 * so the only way to relocate just the number is to replace the whole overlay). FORGE event bus.
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = Dist.CLIENT)
public final class VanillaXpBarHider {
    private VanillaXpBarHider() {}

    @SubscribeEvent
    public static void onPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())) {
            event.setCanceled(true);
        }
    }
}
