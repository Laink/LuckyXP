package com.lwi.luckyxp.xp;

import com.lwi.luckystats.api.LuckyStatsApi;
import com.lwi.luckyxp.entity.LuckyXpOrb;
import com.lwi.luckyxp.event.EventBlockData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns a clump of blue Lucky XP orbs at a broken lucky block; the orbs fly to the player and grant
 * the XP on pickup. Hooked to Lucky Tweaks' break event.
 *
 * <p>MVP amount: flat base per break + a bonus scaled by the block's stored Luck (a proxy for drop
 * rarity). Blocks spawned by an XP event carry a ×mult (in {@link EventBlockData}) applied here on break.
 */
public final class BreakXp {
    /** XP for breaking any lucky block, regardless of outcome. */
    public static final int BASE_XP = 4;
    /** +1 XP for every this-many points of positive stored Luck on the block. */
    public static final int LUCK_DIVISOR = 5;

    private BreakXp() {}

    public static void onBroken(ServerPlayer player, ResourceLocation blockId, BlockPos pos, int capturedLuck) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // A block spawned by an event is consumed on break: frees its protection + stops its sparkles.
        // MUST also happen on a silk-touch break, else the protection sweep would re-place the block
        // 5 ticks after the player picked it up (a dupe).
        EventBlockData.Entry entry = EventBlockData.get(level).consume(pos);
        // Silk touch = the block is picked up, not opened (no drop roll): no XP, no ×mult, no counter.
        // Awarding here would be an infinite pump (silk-break, re-place, repeat — the Luck NBT travels
        // with the item, so an infused block would even keep paying its luck bonus every cycle).
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, player.getMainHandItem()) > 0) {
            return;
        }
        int bonus = capturedLuck > 0 ? capturedLuck / LUCK_DIVISOR : 0;
        int xp = BASE_XP + bonus;
        if (entry != null) {
            LuckyStatsApi.incrementCounter(player, "luckyxp_event_blocks", 1);
            if (entry.xpMult > 1.0F) {
                xp = Math.round(xp * entry.xpMult);
            }
        }
        if (xp > 0) {
            LuckyXpOrb.award(level, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), xp);
        }
    }
}
