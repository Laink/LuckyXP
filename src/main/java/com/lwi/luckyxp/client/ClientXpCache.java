package com.lwi.luckyxp.client;

/** Client-side snapshot of the local player's Lucky XP, fed by {@code SyncXpPacket}, read by the HUD. */
public final class ClientXpCache {
    public static int level;
    public static int into;
    public static int toNext;
    public static long total;

    private ClientXpCache() {}

    public static void set(int level, int into, int toNext, long total) {
        ClientXpCache.level = level;
        ClientXpCache.into = into;
        ClientXpCache.toNext = toNext;
        ClientXpCache.total = total;
    }
}
