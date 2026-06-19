package com.lwi.luckyxp;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Lucky XP -- a parallel "blue XP" economy. Break lucky blocks to earn Lucky XP and levels, then
 * spend levels at rarity-tiered vending machines for rewards. Pure sibling mod: consumes Lucky
 * Tweaks' API (the lucky-block-broken event); owns its own per-player XP storage, HUD and blocks.
 */
@Mod(LuckyXpMod.MODID)
public class LuckyXpMod {
    public static final String MODID = "luckyxp";

    public LuckyXpMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        Registration.init(modEventBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LuckyXpConfig.CLIENT_SPEC);
    }
}
