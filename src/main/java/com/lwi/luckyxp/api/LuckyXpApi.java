package com.lwi.luckyxp.api;

import com.lwi.luckyxp.net.LuckyXpNetwork;
import com.lwi.luckyxp.xp.LuckyXpData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Public API of Lucky XP. Other mods (and the vending machine) add/spend Lucky XP through here; every
 * mutation re-syncs the owning client so the HUD bar stays live.
 */
public final class LuckyXpApi {
    private LuckyXpApi() {}

    /** Grant Lucky XP points to a player (rolls levels per the vanilla-like curve), then sync. */
    public static void addXp(ServerPlayer player, int amount) {
        LuckyXpData.add(player, amount);
        LuckyXpNetwork.sync(player);
    }

    /** Spend whole Lucky levels (the machine currency). Returns false if unaffordable; syncs on success. */
    public static boolean spendLevels(ServerPlayer player, int levels) {
        boolean ok = LuckyXpData.spendLevels(player, levels);
        if (ok) {
            LuckyXpNetwork.sync(player);
        }
        return ok;
    }

    public static int getLevel(Player player) {
        return LuckyXpData.getLevel(player);
    }

    public static long getTotalXp(Player player) {
        return LuckyXpData.getTotal(player);
    }
}
