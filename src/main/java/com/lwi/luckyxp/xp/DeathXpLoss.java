package com.lwi.luckyxp.xp;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckytweaks.api.PlayerFellListener;
import com.lwi.luckyxp.LuckyXpCommonConfig;
import com.lwi.luckyxp.net.LuckyXpNetwork;
import net.minecraft.server.level.ServerPlayer;

/**
 * Falling costs Lucky XP, and none of it can be picked back up: unlike vanilla experience, nothing
 * drops as orbs — the XP is simply gone.
 *
 * <ul>
 *   <li><b>Died</b> — solo respawn, gave up, bled out, or the run's last life: the whole balance goes.</li>
 *   <li><b>Downed</b> — knocked down in multiplayer and still revivable: {@code downedLossPercent}
 *       of the balance goes.</li>
 * </ul>
 *
 * <p>Driven by Lucky Tweaks' {@code PlayerFellListener}, not by {@code LivingDeathEvent}: the pack
 * CANCELS a death it saves, so from the outside a spent life is indistinguishable from a Born in Chaos
 * Death Totem save. Only the shared-lives handler knows which path it took (GUIDE §13).
 */
public final class DeathXpLoss {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private DeathXpLoss() {}

    /** Called once from the mod constructor. */
    public static void register() {
        LuckyTweaksApi.registerPlayerFellListener(DeathXpLoss::onFell);
    }

    private static void onFell(ServerPlayer player, PlayerFellListener.Reason reason) {
        if (!LuckyXpCommonConfig.COMMON.loseXpOnDeath.get() || player.isCreative() || player.isSpectator()) {
            return;
        }
        int points = LuckyXpData.points(player);
        if (points <= 0) {
            return;
        }
        int take;
        if (reason == PlayerFellListener.Reason.DOWNED) {
            int percent = LuckyXpCommonConfig.COMMON.downedLossPercent.get();
            if (percent <= 0) {
                return;
            }
            take = (int) Math.ceil(points * percent / 100.0);
        } else {
            take = points;
        }
        int lost = LuckyXpData.removePoints(player, take);
        if (lost > 0) {
            LuckyXpNetwork.sync(player);
            LOGGER.debug("[xploss] {} lost {} Lucky XP ({})", player.getScoreboardName(), lost, reason);
        }
    }
}
