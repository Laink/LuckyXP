package com.lwi.luckyxp.xp;

import com.lwi.luckystats.api.LuckyStatsApi;
import com.lwi.luckytweaks.util.ServerScheduler;
import com.lwi.luckyxp.LuckyXpCommonConfig;
import com.lwi.luckyxp.entity.LuckyXpOrb;
import com.lwi.luckyxp.event.EventBlockData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Spawns a clump of blue Lucky XP orbs at a broken lucky block; the orbs fly to the player and grant
 * the XP on pickup. Hooked to Lucky Tweaks' break event.
 *
 * <p><b>Every lucky block pays the same.</b> The amount no longer scales with the block's stored Luck
 * (user 2026-07-08: an infused block dropping the same XP as a plain one is what players expect) — it
 * is a flat {@code baseXp}, times the {@code xpMult} of an XP event if the block came from one.
 *
 * <p>The one exception is the outcome: a <b>legendary drop doubles the XP</b>. That cannot be known at
 * break time — Lucky Tweaks fires its break event <i>before</i> the drop is rolled — so the break pays
 * the normal amount and {@link #onLegendaryDrop} tops it up, notified by Lucky Tweaks' legendary-drop
 * bus at the roll.
 *
 * <p>The top-up is then held back for {@code legendaryXpDelayTicks} (44 by default) so it lands with
 * the reveal, not with the break: the suspense wrap in the lucky blocks' {@code drops.txt} plays its
 * fanfare and materialises the legendary item at {@code delay=2.2}. A second clump of orbs bursting
 * alongside the prize is what tells the player the XP was doubled — paid on the break tick, it would
 * just have looked like a slightly bigger first clump.
 */
public final class BreakXp {
    private BreakXp() {}

    /** XP for breaking any lucky block, whatever it drops ({@code [xp] baseXp}, default 4). */
    public static int baseXp() {
        return LuckyXpCommonConfig.COMMON.baseXp.get();
    }

    /** Multiplier applied when the rolled drop is legendary ({@code [xp] legendaryMultiplier}, default 2). */
    public static int legendaryMultiplier() {
        return LuckyXpCommonConfig.COMMON.legendaryMultiplier.get();
    }

    /** Ticks the bonus waits so it lands on the reveal ({@code [xp] legendaryXpDelayTicks}, default 44). */
    public static int legendaryXpDelayTicks() {
        return LuckyXpCommonConfig.COMMON.legendaryXpDelayTicks.get();
    }

    /** What the break just paid, so a legendary roll on the same tick can top it up to the multiple. */
    private record Awarded(UUID player, BlockPos pos, int xp) {}

    private static final ThreadLocal<Awarded> LAST_AWARD = ThreadLocal.withInitial(() -> null);

    public static void onBroken(ServerPlayer player, ResourceLocation blockId, BlockPos pos, int capturedLuck) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // A block spawned by an event is consumed on break: frees its protection + stops its sparkles.
        // MUST also happen on a silk-touch break, else the protection sweep would re-place the block
        // 5 ticks after the player picked it up (a dupe).
        EventBlockData.Entry entry = EventBlockData.get(level).consume(pos);
        // Silk touch = the block is picked up, not opened (no drop roll): no XP, no ×mult, no counter.
        // Awarding here would be an infinite pump (silk-break, re-place, repeat).
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, player.getMainHandItem()) > 0) {
            return;
        }
        int xp = baseXp();
        if (entry != null) {
            LuckyStatsApi.incrementCounter(player, "luckyxp_event_blocks", 1);
            if (entry.xpMult > 1.0F) {
                xp = Math.round(xp * entry.xpMult);
            }
        }
        if (xp > 0) {
            award(level, pos, xp);
            LAST_AWARD.set(new Awarded(player.getUUID(), pos, xp));
        }
    }

    /**
     * Top up the break's XP because the drop it rolled turned out legendary. Fired by Lucky Tweaks on
     * the break tick, at most once per break, so {@link #LAST_AWARD} still describes this very break —
     * we re-check the player and the position anyway, and consume the record so a second legendary
     * signal could never pay twice.
     *
     * <p>The orbs are then scheduled for the reveal tick. They spawn at the block's position, not on
     * the player, so walking away (or logging off) during the suspense cannot lose them.
     */
    public static void onLegendaryDrop(ServerPlayer player, BlockPos pos) {
        Awarded last = LAST_AWARD.get();
        if (last == null || !last.player().equals(player.getUUID())) {
            return;                                     // not the break we just paid for (or silk-touched)
        }
        if (pos != null && !pos.equals(last.pos())) {
            return;
        }
        LAST_AWARD.set(null);                           // one top-up per break
        int extra = last.xp() * (legendaryMultiplier() - 1);
        if (extra <= 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos at = last.pos();
        int delay = legendaryXpDelayTicks();
        if (delay <= 0) {
            award(level, at, extra);
        } else {
            ServerScheduler.schedule(delay, () -> award(level, at, extra));
        }
    }

    private static void award(ServerLevel level, BlockPos pos, int xp) {
        LuckyXpOrb.award(level, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), xp);
    }
}
