package com.lwi.luckyxp.client;

import com.lwi.luckystats.api.LuckyStatsClientApi;
import com.lwi.luckystats.client.ScreenSections;
import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.xp.LuckBuffs;
import net.minecraft.client.resources.language.I18n;
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
        // Pinnable HUD lines (all unpinned by default -- opt-in from the HUD editor). Labels/titles are
        // lang KEYS (Lucky Stats resolves them at render); formatters resolve their own key per frame.
        LuckyStatsClientApi.registerHudStat(K_EVENTS, "luckyxp.stat.events_label",
                data -> I18n.get("luckyxp.hud.events", data.getInt(K_EVENTS)));
        LuckyStatsClientApi.registerHudStat(K_JACKPOTS, "luckyxp.stat.jackpots_label",
                data -> I18n.get("luckyxp.hud.jackpots", data.getInt(K_JACKPOTS)));
        LuckyStatsClientApi.registerHudStat(K_MEGAS, "luckyxp.stat.megas_label",
                data -> I18n.get("luckyxp.hud.megas", data.getInt(K_MEGAS)));
        LuckyStatsClientApi.registerHudStat(K_BLOCKS, "luckyxp.stat.blocks_label",
                data -> I18n.get("luckyxp.hud.blocks", data.getInt(K_BLOCKS)));

        LuckyStatsClientApi.registerScreenSection("luckyxp.section.events", data -> {
            int events = data.getInt(K_EVENTS);
            int blocks = data.getInt(K_BLOCKS);
            if (events <= 0 && blocks <= 0) {
                return null; // never saw an event -> hide the whole section
            }
            List<ScreenSections.Row> rows = new ArrayList<>();
            rows.add(new ScreenSections.Row("luckyxp.stat.events_row", String.valueOf(events)));
            rows.add(new ScreenSections.Row("luckyxp.stat.blocks_row", String.valueOf(blocks)));
            if (data.getInt(K_JACKPOTS) > 0) {
                rows.add(new ScreenSections.Row("luckyxp.stat.jackpots_row", String.valueOf(data.getInt(K_JACKPOTS))));
            }
            if (data.getInt(K_MEGAS) > 0) {
                rows.add(new ScreenSections.Row("luckyxp.stat.megas_row", String.valueOf(data.getInt(K_MEGAS))));
            }
            return rows;
        });

        // The merchant's luck buffs, added to the shared "Luck" section (Lucky Stats merges same-titled
        // sections, so these sit under the same header as Optional Suffering's total/malus rows). Each
        // row shows only when non-zero, and short labels keep them clear of the value column.
        LuckyStatsClientApi.registerScreenSection("luckystats.section.luck", data -> {
            int temp = data.getInt(LuckBuffs.STAT_TEMP);
            int perm = data.getInt(LuckBuffs.STAT_PERM);
            if (temp <= 0 && perm <= 0) {
                return null;
            }
            List<ScreenSections.Row> rows = new ArrayList<>();
            if (temp > 0) {
                rows.add(new ScreenSections.Row("luckyxp.stat.temp_luck", "+" + temp + "%"));
            }
            if (perm > 0) {
                rows.add(new ScreenSections.Row("luckyxp.stat.perm_luck", "+" + perm + "%"));
            }
            return rows;
        });
    }
}
