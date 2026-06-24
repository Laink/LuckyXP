package com.lwi.luckyxp.event;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.net.LuckyXpNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Draws a floating "Luck +X" / "×N XP" hologram above EVERY lucky block near a player — driven by Lucky
 * Tweaks' single debug toggle {@code /luckychance debug} (so that one command shows the chat recap AND the
 * block luck). The server scans the nearby chunks' block entities (lucky blocks store their Luck on a BE),
 * reads the same Luck the inventory tooltip shows, and resyncs labels to debug players periodically.
 * (Precursor to a future "lucky glasses" item.)
 */
public final class EventDebug {
    private static final Set<UUID> SHOWING = new HashSet<>();   // players we've sent holograms to (to clear on off)
    private static final int CHUNK_RADIUS = 3;                  // 7x7 chunk scan around the player
    private static final int MAX = 300;

    private EventDebug() {}

    /** Each call (throttled by the caller): refresh holograms for debug-on players, clear for those who turned it off. */
    public static void tickSync(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (LuckyTweaksApi.isBreakDebugOn(p)) {
                sync(p);
                SHOWING.add(p.getUUID());
            } else if (SHOWING.remove(p.getUUID())) {
                LuckyXpNetwork.sendDebugBlocks(p, List.of(), List.of());   // clear the client
            }
        }
    }

    private static void sync(ServerPlayer player) {
        List<Long> positions = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (player.level() instanceof ServerLevel level) {
            EventBlockData events = EventBlockData.get(level);
            int pcx = player.chunkPosition().x;
            int pcz = player.chunkPosition().z;
            outer:
            for (int cx = pcx - CHUNK_RADIUS; cx <= pcx + CHUNK_RADIUS; cx++) {
                for (int cz = pcz - CHUNK_RADIUS; cz <= pcz + CHUNK_RADIUS; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (Map.Entry<BlockPos, BlockEntity> me : chunk.getBlockEntities().entrySet()) {
                        BlockEntity be = me.getValue();
                        if (!LuckyTweaksApi.isLuckyBlock(be.getBlockState())) {
                            continue;
                        }
                        BlockPos pos = me.getKey();
                        positions.add(pos.asLong());
                        labels.add(label(be, events, pos));
                        if (positions.size() >= MAX) {
                            break outer;
                        }
                    }
                }
            }
        }
        LuckyXpNetwork.sendDebugBlocks(player, positions, labels);
    }

    private static String label(BlockEntity be, EventBlockData events, BlockPos pos) {
        float xpMult = events.xpMultAt(pos);
        if (xpMult > 0.0F) {
            String m = xpMult == Math.floor(xpMult) ? String.valueOf((int) xpMult) : String.valueOf(xpMult);
            return "x" + m + " XP";
        }
        int luck = 0;
        try {
            luck = be.saveWithoutMetadata().getInt("Luck");
        } catch (Throwable ignored) {
            // unreadable BE -> show Luck 0
        }
        return "Luck " + (luck > 0 ? "+" : "") + luck;
    }

    /** Forget tracking (on server stop). */
    public static void clear() {
        SHOWING.clear();
    }
}
