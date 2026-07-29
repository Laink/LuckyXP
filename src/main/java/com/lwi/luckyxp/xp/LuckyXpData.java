package com.lwi.luckyxp.xp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player Lucky XP storage, kept in {@code player.getPersistentData()} under the "luckyxp"
 * compound (survives logout natively; survives death via the Clone handler). The level curve mirrors
 * vanilla XP: the higher the level, the more points it takes to advance.
 *
 * <p>Stored as: current {@code level}, points accumulated into the current level ({@code into}), and
 * a lifetime {@code total} (informational). Levels are the spendable currency at vending machines.
 */
public final class LuckyXpData {
    private static final String ROOT = "luckyxp";
    private static final String K_LEVEL = "level";
    private static final String K_INTO = "into";
    private static final String K_TOTAL = "total";

    private LuckyXpData() {}

    private static CompoundTag root(Player player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag tag = data.getCompound(ROOT);
        if (!data.contains(ROOT)) {
            data.put(ROOT, tag);
        }
        return tag;
    }

    public static int getLevel(Player player) {
        return Math.max(0, root(player).getInt(K_LEVEL));
    }

    public static int getInto(Player player) {
        return Math.max(0, root(player).getInt(K_INTO));
    }

    public static long getTotal(Player player) {
        return root(player).getLong(K_TOTAL);
    }

    /** Vanilla XP curve: points needed to go from {@code level} to {@code level + 1}. */
    public static int xpToNext(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }
        return 7 + level * 2;
    }

    public static float progress(Player player) {
        int need = xpToNext(getLevel(player));
        return need <= 0 ? 0f : Math.min(1f, (float) getInto(player) / (float) need);
    }

    public static void add(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        CompoundTag tag = root(player);
        int level = Math.max(0, tag.getInt(K_LEVEL));
        int into = Math.max(0, tag.getInt(K_INTO)) + amount;
        tag.putLong(K_TOTAL, tag.getLong(K_TOTAL) + amount);
        int need = xpToNext(level);
        while (into >= need) {
            into -= need;
            level++;
            need = xpToNext(level);
        }
        tag.putInt(K_LEVEL, level);
        tag.putInt(K_INTO, into);
    }

    /** Spend whole levels (the machine currency). Returns false if the player can't afford it. */
    public static boolean spendLevels(Player player, int levels) {
        if (levels <= 0) {
            return true;
        }
        CompoundTag tag = root(player);
        int level = Math.max(0, tag.getInt(K_LEVEL));
        if (level < levels) {
            return false;
        }
        // Preserve the progress FRACTION across the spend (vanilla behaviour); absolute
        // points-into-level don't carry between differently-sized levels.
        int oldNeed = xpToNext(level);
        int into = Math.max(0, tag.getInt(K_INTO));
        float frac = oldNeed > 0 ? (float) into / (float) oldNeed : 0f;
        level -= levels;
        int newNeed = xpToNext(level);
        int newInto = Math.min(newNeed - 1, Math.max(0, Math.round(frac * (float) newNeed)));
        tag.putInt(K_LEVEL, level);
        tag.putInt(K_INTO, newInto);
        return true;
    }

    /** The player's spendable Lucky XP expressed in points: every full level plus the current progress. */
    public static int points(Player player) {
        CompoundTag tag = root(player);
        int level = Math.max(0, tag.getInt(K_LEVEL));
        int points = Math.max(0, tag.getInt(K_INTO));
        for (int l = 0; l < level; l++) {
            points += xpToNext(l);
        }
        return points;
    }

    /** Rebuild level + progress from a point total. The lifetime {@code total} is left alone: it records
     *  what was ever earned, and a death does not un-earn it. */
    public static void setPoints(Player player, int points) {
        CompoundTag tag = root(player);
        int level = 0;
        int left = Math.max(0, points);
        int need = xpToNext(level);
        while (left >= need) {
            left -= need;
            level++;
            need = xpToNext(level);
        }
        tag.putInt(K_LEVEL, level);
        tag.putInt(K_INTO, left);
    }

    /** Take {@code points} away (clamped at zero) and return how much was actually removed. */
    public static int removePoints(Player player, int points) {
        if (points <= 0) {
            return 0;
        }
        int had = points(player);
        int taken = Math.min(had, points);
        setPoints(player, had - taken);
        return taken;
    }

    /** Copy the Lucky XP compound across a death/dimension Clone so progress is never lost. */
    public static void copyAcrossClone(Player from, Player to) {
        CompoundTag src = from.getPersistentData().getCompound(ROOT);
        if (!src.isEmpty()) {
            to.getPersistentData().put(ROOT, src.copy());
        }
    }
}
