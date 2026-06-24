package com.lwi.luckyxp.event;

import com.lwi.luckyxp.LuckyXpMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side glue for Lucky XP events (design v4): drives the event reveal (overworld) and the block
 * apparition queue (every dimension). There is no break-time hook anymore — a Luck event's boost lives in
 * the spawned blocks' own Luck NBT, and an XP event's ×mult lives in {@link XpBlockData}.
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID)
public final class LuckyEventHandlers {
    private LuckyEventHandlers() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        LuckyBlockShower.tickLevel(level);          // event block apparition (every dimension)
        if (level.dimension() != Level.OVERWORLD) {
            return; // the one global event reveal is ticked on the overworld where its SavedData lives
        }
        MinecraftServer server = level.getServer();
        LuckyEventManager.get(server).serverTick(server);
        if (level.getGameTime() % 10 == 0) {
            EventDebug.tickSync(server);          // refresh debug "Luck +X" holograms for debug players
        }
    }

    /** Clear in-memory bookkeeping so it never bleeds into the next world (static state). */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LuckyBlockShower.clear();
        EventDebug.clear();
    }
}

