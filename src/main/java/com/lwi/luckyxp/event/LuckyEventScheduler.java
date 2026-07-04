package com.lwi.luckyxp.event;

import com.lwi.luckyxp.LuckyEventConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * The daily automatic trigger: once per in-game day, at a random moment inside the configured morning
 * window, roll "does today get a Lucky event?" ({@code chancePerDay}, with a pity counter guaranteeing
 * one after {@code pityDays} consecutive event-less days). On success the normal roulette reveal plays
 * for everyone and the blocks appear — the exact same path as {@code /luckyevent start}.
 *
 * <p>Ticked from {@link LuckyEventHandlers} on the overworld only. Gates, retried every tick until the
 * window closes: at least one player online, no reveal already running, End/dragon rule
 * ({@link LuckyEventManager#startBlockReason}). A window that closes un-rolled while players are online
 * (e.g. a "set night" drop jumped past it) consumes the day AND counts as a dry day, so the pity still
 * progresses; an empty server leaves the day untouched. A backwards day-time jump (possible only without
 * Optional Suffering's TimeGuard) is self-healed by re-adopting the current day. Persistent state (last
 * rolled day + dry-day counter) lives in {@link LuckyEventManager}'s SavedData; only today's planned
 * moment is in-memory.
 */
public final class LuckyEventScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static long plannedDay = Long.MIN_VALUE;   // day we picked a trigger moment for
    private static int plannedTick;                    // today's trigger moment (day-time ticks)

    private LuckyEventScheduler() {}

    public static void tick(ServerLevel overworld) {
        if (!LuckyEventConfig.COMMON.autoEvents.get()) {
            return;
        }
        MinecraftServer server = overworld.getServer();
        LuckyEventManager mgr = LuckyEventManager.get(server);
        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        long lastRolled = mgr.lastRolledDay();
        if (day < lastRolled) {
            // Day-time went backwards (something rewound the clock and no TimeGuard redressed it):
            // re-adopt the current day so auto-events resume instead of freezing until time catches up.
            LOGGER.warn("Lucky event scheduler: day-time went backwards (day {}, last rolled day {}); re-adopting", day, lastRolled);
            mgr.markRolled(day - 1);
            return;
        }
        if (day == lastRolled) {
            return;                                     // today's roll is already done
        }
        int tod = (int) (dayTime % 24000L);
        int winStart = LuckyEventConfig.COMMON.windowStart.get();
        int winEnd = Math.max(winStart + 1, LuckyEventConfig.COMMON.windowEnd.get());
        if (day != plannedDay) {                        // first tick of a new day (or boot): pick today's moment
            plannedDay = day;
            plannedTick = winStart + overworld.getRandom().nextInt(winEnd - winStart);
        }
        if (tod >= winEnd) {
            // Window fully missed while players were on (a "set night" drop jumped past it, an all-gated
            // morning, or a join after the window): consume the day AND credit the pity, so eaten days
            // still bring the guaranteed event closer. An empty server does not consume the day.
            if (!server.getPlayerList().getPlayers().isEmpty()) {
                mgr.markRolled(day);
                mgr.setDryDays(mgr.dryDays() + 1);
                LOGGER.info("Lucky event window missed (day {}): counted as a dry day ({} total)", day, mgr.dryDays());
            }
            return;
        }
        if (tod < plannedTick) {
            return;                                     // today's moment is still ahead
        }
        if (mgr.hasActive() || server.getPlayerList().getPlayers().isEmpty()
                || LuckyEventManager.startBlockReason(server) != null) {
            return;                                     // gated: retry next tick while the window lasts
        }
        rollDay(server, overworld, mgr, day);
    }

    /**
     * One daily roll (chance + pity), marking the day as consumed. Also called by
     * {@code /luckyevent roll} to force today's roll for testing.
     *
     * @return whether an event fired
     */
    public static boolean rollDay(MinecraftServer server, ServerLevel overworld, LuckyEventManager mgr, long day) {
        mgr.markRolled(day);
        boolean fire = mgr.dryDays() >= LuckyEventConfig.COMMON.pityDays.get()
                || overworld.getRandom().nextDouble() < LuckyEventConfig.COMMON.chancePerDay.get();
        if (!fire) {
            mgr.setDryDays(mgr.dryDays() + 1);
            LOGGER.info("Lucky event daily roll (day {}): no event today ({} dry day(s))", day, mgr.dryDays());
            return false;
        }
        mgr.setDryDays(0);
        LuckyEvent ev = EventRolls.rollOutcome(overworld);
        mgr.start(server, ev, LuckyEventManager.REVEAL_TICKS, false, overworld.getRandom().nextLong());
        LOGGER.info("Lucky event auto-triggered (day {})", day);
        return true;
    }

    /** Forget the planned moment (server stop) so it never bleeds into another world. */
    public static void clear() {
        plannedDay = Long.MIN_VALUE;
        plannedTick = 0;
    }
}
