package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Drives the smooth client-side countdown of the event timer between the server's per-second resyncs. */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = Dist.CLIENT)
public final class ClientEventTicker {
    private ClientEventTicker() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) {
            ClientEventCache.clientTick();
        }
    }
}
