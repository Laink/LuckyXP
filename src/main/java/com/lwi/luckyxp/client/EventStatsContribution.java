package com.lwi.luckyxp.client;

import com.lwi.luckystats.api.LuckyStatsClientApi;
import com.lwi.luckystats.client.ScreenSections;
import com.lwi.luckyxp.LuckyXpMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the Lucky-events history with Lucky Stats: a "Lucky Events" section on the stats screen
 * (events witnessed, jackpots, mega jackpots, event blocks opened — hidden until the player saw a
 * first event) plus the same four counters as pinnable HUD lines (unpinned by default; the player
 * opts in from the HUD editor). The server feeds the counters through {@code LuckyStatsApi}
 * (LuckyEventManager.fire + BreakXp).
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EventStatsContribution {
    private static final String K_EVENTS = "luckyxp_events";
    private static final String K_JACKPOTS = "luckyxp_jackpots";
    private static final String K_MEGAS = "luckyxp_megas";
    private static final String K_BLOCKS = "luckyxp_event_blocks";

    private EventStatsContribution() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Pinnable HUD lines (all unpinned by default -- opt-in from the HUD editor).
        LuckyStatsClientApi.registerHudStat(K_EVENTS, "Lucky events",
                data -> "Lucky events: " + data.getInt(K_EVENTS));
        LuckyStatsClientApi.registerHudStat(K_JACKPOTS, "Event jackpots",
                data -> "Jackpots: " + data.getInt(K_JACKPOTS));
        LuckyStatsClientApi.registerHudStat(K_MEGAS, "Event mega jackpots",
                data -> "Mega jackpots: " + data.getInt(K_MEGAS));
        LuckyStatsClientApi.registerHudStat(K_BLOCKS, "Event blocks opened",
                data -> "Event blocks: " + data.getInt(K_BLOCKS));

        LuckyStatsClientApi.registerScreenSection("Lucky Events", data -> {
            int events = data.getInt(K_EVENTS);
            int blocks = data.getInt(K_BLOCKS);
            if (events <= 0 && blocks <= 0) {
                return null; // never saw an event -> hide the whole section
            }
            List<ScreenSections.Row> rows = new ArrayList<>();
            rows.add(new ScreenSections.Row("Events", String.valueOf(events)));
            rows.add(new ScreenSections.Row("Event blocks opened", String.valueOf(blocks)));
            if (data.getInt(K_JACKPOTS) > 0) {
                rows.add(new ScreenSections.Row("Jackpots", String.valueOf(data.getInt(K_JACKPOTS))));
            }
            if (data.getInt(K_MEGAS) > 0) {
                rows.add(new ScreenSections.Row("Mega jackpots", String.valueOf(data.getInt(K_MEGAS))));
            }
            return rows;
        });
    }
}
