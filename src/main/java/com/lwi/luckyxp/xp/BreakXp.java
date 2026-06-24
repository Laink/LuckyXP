package com.lwi.luckyxp.xp;

import com.lwi.luckyxp.entity.LuckyXpOrb;
import com.lwi.luckyxp.event.EventBlockData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns a clump of blue Lucky XP orbs at a broken lucky block; the orbs fly to the player and grant
 * the XP on pickup. Hooked to Lucky Tweaks' break event.
 *
 * <p>MVP amount: flat base per break + a bonus scaled by the block's stored Luck (a proxy for drop
 * rarity). Blocks spawned by an XP event carry a ×mult (in {@link XpBlockData}) applied here on break.
 */
public final class BreakXp {
    /** XP for breaking any lucky block, regardless of outcome. */
    public static final int BASE_XP = 4;
    /** +1 XP for every this-many points of positive stored Luck on the block. */
    public static final int LUCK_DIVISOR = 5;

    private BreakXp() {}

    public static void onBroken(ServerPlayer player, ResourceLocation blockId, BlockPos pos, int capturedLuck) {
        int bonus = capturedLuck > 0 ? capturedLuck / LUCK_DIVISOR : 0;
        int xp = BASE_XP + bonus;
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // A block spawned by an XP event grants its rolled ×mult on break, then is consumed (also frees its protection).
        float mult = EventBlockData.get(level).consume(pos);
        if (mult > 1.0F) {
            xp = Math.round(xp * mult);
        }
        if (xp > 0) {
            LuckyXpOrb.award(level, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), xp);
        }
    }
}
