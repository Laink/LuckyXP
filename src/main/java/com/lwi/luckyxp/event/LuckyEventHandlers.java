package com.lwi.luckyxp.event;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.LuckyXpMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Server-side glue for Lucky XP events: counts the single global event down (overworld tick), and
 * applies a LUCK event's chance % to qualifying lucky-block breaks. The DOUBLE_XP multiplier is applied
 * separately in {@code BreakXp} (it scales the XP award, not the drop roll).
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID)
public final class LuckyEventHandlers {
    private LuckyEventHandlers() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (level.dimension() != Level.OVERWORLD) {
            return; // one global event, ticked on the overworld where its SavedData lives
        }
        MinecraftServer server = level.getServer();
        LuckyEventManager.get(server).serverTick(server);
    }

    /**
     * Apply the active LUCK event's chance to a matching lucky-block break, at {@code HIGH} (after Lucky
     * Tweaks captures + resets the break state at HIGHEST), exactly like the ring/belt/malus do.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLuckyBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!LuckyTweaksApi.isLuckyBlock(event.getState())) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        LuckyEvent active = LuckyEventManager.get(server).active();
        if (active == null || active.type() != LuckyEventType.LUCK) {
            return;
        }
        ResourceLocation broken = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
        if (active.appliesTo(broken)) {
            LuckyTweaksApi.addChance(active.magnitude());
        }
    }
}
