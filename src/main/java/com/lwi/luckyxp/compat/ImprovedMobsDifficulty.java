package com.lwi.luckyxp.compat;

import io.github.flemmli97.improvedmobs.config.Config;
import io.github.flemmli97.improvedmobs.difficulty.DifficultyData;
import io.github.flemmli97.improvedmobs.platform.CrossPlatformStuff;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * The merchant's difficulty cut, spoken in Improved Mobs' own language.
 *
 * <p>Improved Mobs' difficulty is what its bottom-right readout shows and what every mob scales off, so
 * this goes through the mod's own data and its own sync packet -- the exact path its {@code /improvedmobs
 * difficulty add} command takes. Writing the value without {@code sendDifficultyDataTo} would move the
 * number on the server and leave the player staring at a stale HUD.
 *
 * <p>It honours both difficulty modes, because the mod's own reader does: {@code GLOBAL} keeps one
 * server-wide value, everything else keeps one per player. This pack runs {@code PLAYERMAX} (mobs scale to
 * the highest player within 256 blocks), which is why the cut is applied to EVERY player online rather than
 * only the buyer: cutting one player's number while a team-mate stands next to them with a higher one would
 * move the HUD and change nothing at all about the mobs.
 *
 * <p>Soft dependency: every Improved Mobs reference in this mod lives in this class, and nothing calls it
 * without {@link #isAvailable()} first, so Lucky XP runs fine without the mod installed.
 */
public final class ImprovedMobsDifficulty {
    private static final String MODID = "improvedmobs";

    private ImprovedMobsDifficulty() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MODID);
    }

    /** The highest difficulty currently in play, i.e. what the buyer sees and what mobs scale to. */
    public static float peak(MinecraftServer server) {
        if (!isAvailable()) {
            return 0.0F;
        }
        if (Config.CommonConfig.difficultyType == Config.DifficultyType.GLOBAL) {
            return DifficultyData.get(server).getDifficulty();
        }
        float max = 0.0F;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            float d = CrossPlatformStuff.INSTANCE.getPlayerDifficultyData(p)
                    .map(data -> data.getDifficultyLevel()).orElse(0.0F);
            max = Math.max(max, d);
        }
        return max;
    }

    /**
     * Lower the difficulty by {@code amount}, never below zero, and push it to every client so the readout
     * moves at once. Returns false when there was nothing left to cut, so the caller can refuse the sale
     * instead of taking levels for no change.
     */
    public static boolean lower(MinecraftServer server, float amount) {
        if (!isAvailable() || peak(server) <= 0.0F) {
            return false;
        }
        if (Config.CommonConfig.difficultyType == Config.DifficultyType.GLOBAL) {
            DifficultyData data = DifficultyData.get(server);
            data.setDifficulty(Math.max(0.0F, data.getDifficulty() - amount), server);
            return true;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CrossPlatformStuff.INSTANCE.getPlayerDifficultyData(p).ifPresent(data -> {
                data.setDifficultyLevel(Math.max(0.0F, data.getDifficultyLevel() - amount));
                CrossPlatformStuff.INSTANCE.sendDifficultyDataTo(p, server);
            });
        }
        return true;
    }
}
