package com.lwi.luckyxp.client;

/** Client cache of the event blocks to draw debug "Luck +X" holograms over (fed by DebugBlocksPacket). */
public final class ClientDebugBlocks {
    public static long[] pos = new long[0];
    public static String[] labels = new String[0];

    private ClientDebugBlocks() {}

    public static void set(long[] p, String[] l) {
        pos = p;
        labels = l;
    }
}
