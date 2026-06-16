package com.lwi.luckyxp.event;

import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.net.LuckyXpNetwork;
import com.lwi.luckyxp.xp.LuckyXpData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side lifecycle: keep Lucky XP across death/dimension and push it to the client on join. */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID)
public final class LuckyXpServerEvents {
    private LuckyXpServerEvents() {}

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // persistentData is not auto-copied on death; carry our compound over (idempotent on dim change).
        LuckyXpData.copyAcrossClone(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LuckyXpNetwork.sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LuckyXpNetwork.sync(player);
        }
    }
}
