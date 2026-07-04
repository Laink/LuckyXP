package com.lwi.luckyxp.event;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.event.LuckyEvent.Scope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The events' shared random rolls — one source of truth for the outcome odds, used by BOTH the
 * {@code /luckyevent} command and the daily auto-trigger ({@link LuckyEventScheduler}).
 */
public final class EventRolls {
    /** Block count of a JACKPOT (all-blocks) event. */
    public static final int JACKPOT_COUNT = 20;

    private EventRolls() {}

    /** Value roll for an XP event: x1.5 (30%) / x2 (60%) / x4 (10%, MEGA). */
    public static float rollXpMult(RandomSource rng) {
        int r = rng.nextInt(100);
        return r < 30 ? 1.5F : (r < 90 ? 2.0F : 4.0F);
    }

    /** Value roll for a Luck event: raw Luck +10..+100 (soft weights, ~10% at the +100 MEGA). */
    public static int rollLuckPercent(RandomSource rng) {
        int r = rng.nextInt(100);
        if (r < 20) return 10;
        if (r < 45) return 30;
        if (r < 65) return 50;
        if (r < 80) return 70;
        if (r < 90) return 90;
        return 100;
    }

    /** Hidden block count of a SINGLE-block event: 5-10. */
    public static int singleCount(RandomSource rng) {
        return 5 + rng.nextInt(6);
    }

    /** Full random outcome (roll 1 scope + roll 2 value): ~5% NOTHING, ~5% JACKPOT, else one random
     *  lucky block of this level's dimension; type = 50/50 XP / Luck. */
    public static LuckyEvent rollOutcome(ServerLevel level) {
        RandomSource rng = level.getRandom();
        boolean xp = rng.nextBoolean();
        LuckyEventType type = xp ? LuckyEventType.DOUBLE_XP : LuckyEventType.LUCK;
        int roll = rng.nextInt(100);
        if (roll < 5) {
            return LuckyEvent.nothing(type);
        }
        Scope scope = roll < 10 ? Scope.JACKPOT : Scope.SINGLE;
        ResourceLocation block = scope == Scope.SINGLE ? randomBlock(level) : null;
        if (scope == Scope.SINGLE && block == null) {
            scope = Scope.JACKPOT;                      // no lucky block here -> jackpot
        }
        int count = scope == Scope.JACKPOT ? JACKPOT_COUNT : singleCount(rng);
        return xp ? LuckyEvent.xp(scope, block, rollXpMult(rng), count)
                : LuckyEvent.luck(scope, block, rollLuckPercent(rng), count);
    }

    /** A random lucky-block id spawnable in this level's dimension (global list as fallback), or null if none. */
    @Nullable
    public static ResourceLocation randomBlock(ServerLevel level) {
        ResourceLocation dim = level.dimension().location();
        List<ResourceLocation> ids = LuckyTweaksApi.getLuckyBlockIds(dim);
        if (ids.isEmpty()) {
            ids = LuckyTweaksApi.getLuckyBlockIds();
        }
        if (ids.isEmpty()) {
            return null;
        }
        return ids.get(level.getRandom().nextInt(ids.size()));
    }
}
