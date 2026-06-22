package com.lwi.luckyxp.xp;

import com.lwi.luckyxp.entity.LuckyXpOrb;
import com.lwi.luckyxp.event.LuckyEvent;
import com.lwi.luckyxp.event.LuckyEventManager;
import com.lwi.luckyxp.event.LuckyEventType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns a clump of blue Lucky XP orbs at a broken lucky block; the orbs fly to the player and grant
 * the XP on pickup. Hooked to Lucky Tweaks' break event.
 *
 * <p>MVP amount: flat base per break + a bonus scaled by the block's stored Luck (a proxy for drop
 * rarity). Scaling by the exact rolled tier is a later refinement. Constants for now; config later.
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
        // A running Double-XP event multiplies the award on its boosted block (or every block, if jackpot).
        MinecraftServer server = player.getServer();
        if (server != null) {
            LuckyEvent active = LuckyEventManager.get(server).active();
            if (active != null && active.type() == LuckyEventType.DOUBLE_XP && active.appliesTo(blockId)) {
                xp *= active.magnitude();
            }
        }
        if (xp > 0 && player.level() instanceof ServerLevel level) {
            LuckyXpOrb.award(level, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), xp);
        }
    }
}
