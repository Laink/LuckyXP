package com.lwi.luckyxp.xp;

import com.lwi.luckystats.api.LuckyStatsApi;
import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.LuckyXpCommonConfig;
import com.lwi.luckyxp.LuckyXpMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The merchant's luck buffs. They act in TWO places, both mirroring how the Lucky Ring works:
 *
 * <ul>
 *   <li><b>The roll</b> — {@link #onLuckyBreak} contributes to every lucky-block break through Lucky
 *       Tweaks' {@code addChance}, the same channel the ring and the invasion malus use (BreakEvent,
 *       priority below Lucky Tweaks' HIGHEST reset).</li>
 *   <li><b>The HUD</b> — {@link #onPlayerTick} reports the same value into the shared Lucky Stats
 *       {@code luck_modifiers} sub-compound (key {@code "luckyxp_merchant"}), so the "Chance" HUD line
 *       shows it. Without this the buff worked but was invisible — the HUD sums that compound, NOT the
 *       transient break-time addChance.</li>
 * </ul>
 *
 * <p>Stored in the player's {@code luckyxp} persistent compound, which {@link LuckyXpData#copyAcrossClone}
 * carries across death — a bought permanent buff must not vanish on a death.
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID)
public final class LuckBuffs {
    private static final String K_PERM = "permLuck";
    private static final String K_TEMP_PCT = "tempLuckPct";
    private static final String K_TEMP_UNTIL = "tempLuckUntil";

    /** Shared with Lucky Tools' GearLuckReporter and Optional Suffering's LuckCompat. */
    private static final String SUB_KEY = "luck_modifiers";
    private static final String SRC_MERCHANT = "luckyxp_merchant";
    private static final int INTERVAL = 20;                 // HUD refresh cadence (~1s), like GearLuckReporter

    /** Synced stats keys read by the "Luck" section of the Lucky Stats screen (see EventStatsContribution). */
    public static final String STAT_PERM = "luckyxp_perm_luck";
    public static final String STAT_TEMP = "luckyxp_temp_luck";

    private LuckBuffs() {}

    /** The permanent-luck ceiling (config), also enforced when the merchant sells the service. */
    public static int permLuckCap() {
        return LuckyXpCommonConfig.COMMON.permLuckCap.get();
    }

    private static CompoundTag root(Player player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag tag = data.getCompound("luckyxp");
        if (!data.contains("luckyxp")) {
            data.put("luckyxp", tag);
        }
        return tag;
    }

    /** The player's current merchant luck: permanent + the surge while it lasts. */
    private static int currentLuck(ServerPlayer player) {
        CompoundTag tag = root(player);
        int luck = tag.getInt(K_PERM);
        if (tag.getLong(K_TEMP_UNTIL) > player.level().getGameTime()) {
            luck += tag.getInt(K_TEMP_PCT);
        }
        return luck;
    }

    /** The player's permanent merchant luck so far (%). */
    public static int getPermLuck(ServerPlayer player) {
        return root(player).getInt(K_PERM);
    }

    /** The active temporary luck boost (%), or 0 if none is running. */
    public static int getTempLuck(ServerPlayer player) {
        CompoundTag tag = root(player);
        return tag.getLong(K_TEMP_UNTIL) > player.level().getGameTime() ? tag.getInt(K_TEMP_PCT) : 0;
    }

    /** Add permanent luck (merchant service), clamped to the configured cap; returns the new total. */
    public static int addPermLuck(ServerPlayer player, int pct) {
        CompoundTag tag = root(player);
        int total = Math.min(permLuckCap(), tag.getInt(K_PERM) + pct);
        tag.putInt(K_PERM, total);
        return total;
    }

    /** Start (or replace) the 5-minute luck boost. */
    public static void setTempLuck(ServerPlayer player, int pct, int durationTicks) {
        CompoundTag tag = root(player);
        tag.putInt(K_TEMP_PCT, pct);
        tag.putLong(K_TEMP_UNTIL, player.level().getGameTime() + durationTicks);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLuckyBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !LuckyTweaksApi.isLuckyBlock(event.getState())) {
            return;
        }
        int chance = currentLuck(player);
        if (chance != 0) {
            LuckyTweaksApi.addChance(chance);               // affects THIS break's roll
        }
    }

    /** Keep the HUD's "Chance" line in sync with the buff, exactly like Lucky Tools' gear reporter. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % INTERVAL != 0) {
            return;
        }
        int luck = currentLuck(player);
        CompoundTag stats = LuckyStatsApi.getStats(player);
        CompoundTag mods = stats.getCompound(SUB_KEY);
        if (mods.getInt(SRC_MERCHANT) != luck) {
            mods.putInt(SRC_MERCHANT, luck);
            stats.put(SUB_KEY, mods);
        }
        // Break the total into its two parts for the "Luck" section of the stats screen.
        int perm = root(player).getInt(K_PERM);
        int temp = getTempLuck(player);
        if (stats.getInt(STAT_PERM) != perm) {
            stats.putInt(STAT_PERM, perm);
        }
        if (stats.getInt(STAT_TEMP) != temp) {
            stats.putInt(STAT_TEMP, temp);
        }
    }
}
