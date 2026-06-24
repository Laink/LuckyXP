package com.lwi.luckyxp.client;

import com.lwi.luckyxp.net.SyncEventPacket;

/**
 * Client-side snapshot of the current Lucky XP event reveal (design v4), fed by {@link SyncEventPacket}
 * and read by the case-opening roulette. The event exists client-side only while the reveal plays; the
 * countdown is decremented locally each client tick (see {@code ClientEventTicker}) and corrected by the
 * server's periodic resync. A new {@link #seed} marks a brand-new reveal.
 */
public final class ClientEventCache {
    public static boolean present;
    public static String typeId = "";
    public static int scope;            // 0 = NOTHING, 1 = SINGLE, 2 = JACKPOT
    public static String blockId = "";
    public static int luckPercent;
    public static float xpMult;
    public static boolean mega;
    public static int ticksRemaining;
    public static int totalTicks;
    public static long seed;

    private ClientEventCache() {}

    public static void set(SyncEventPacket pkt) {
        present = pkt.present;
        typeId = pkt.typeId;
        scope = pkt.scope;
        blockId = pkt.blockId;
        luckPercent = pkt.luckPercent;
        xpMult = pkt.xpMult;
        mega = pkt.mega;
        ticksRemaining = pkt.ticksRemaining;
        totalTicks = pkt.totalTicks;
        seed = pkt.seed;
    }

    public static boolean isNothing() {
        return scope == 0;
    }

    public static boolean isSingle() {
        return scope == 1;
    }

    public static boolean isJackpot() {
        return scope == 2;
    }

    /** Local 1-tick countdown of the reveal between server resyncs (no-op when paused; gated by caller). */
    public static void clientTick() {
        if (present && ticksRemaining > 0) {
            ticksRemaining--;
        }
    }
}
