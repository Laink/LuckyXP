package com.lwi.luckyxp.client;

import com.lwi.luckyxp.net.SyncEventPacket;

/**
 * Client-side snapshot of the current global Lucky XP event, fed by {@link SyncEventPacket} and read by
 * the HUD timer (and, later, the roulette). The countdown is decremented locally each client tick
 * (see {@code ClientEventTicker}) and corrected by the server's periodic resync, so the timer is smooth.
 */
public final class ClientEventCache {
    public static boolean present;
    public static String typeId = "";
    public static boolean hasBlock;
    public static String blockId = "";
    public static int magnitude;
    public static int ticksRemaining;
    public static int totalTicks;

    private ClientEventCache() {}

    public static void set(SyncEventPacket pkt) {
        present = pkt.present;
        typeId = pkt.typeId;
        hasBlock = pkt.hasBlock;
        blockId = pkt.blockId;
        magnitude = pkt.magnitude;
        ticksRemaining = pkt.ticksRemaining;
        totalTicks = pkt.totalTicks;
    }

    /** Local 1-tick countdown between server resyncs (no-op when paused; gated by the caller). */
    public static void clientTick() {
        if (present && ticksRemaining > 0) {
            ticksRemaining--;
        }
    }
}
