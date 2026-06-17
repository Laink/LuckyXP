package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.machine.Rarity;
import com.lwi.luckyxp.machine.VendingMachineBlockEntity;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(Registration.VENDING_MACHINE_MENU.get(), VendingMachineScreen::new));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Registration.LUCKY_XP_ORB.get(), LuckyXpOrbRenderer::new);
    }

    /** Tint the screen LED (tintindex 0) by the machine's rarity. The LED is on the UPPER half; the
     *  block entity (which holds the rarity) is on the LOWER half, hence pos.below(). */
    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        Block[] blocks = Registration.MACHINES.values().stream().map(RegistryObject::get).toArray(Block[]::new);
        event.register((state, level, pos, tint) -> {
            if (tint == 0 && level != null && pos != null
                    && level.getBlockEntity(pos.below()) instanceof VendingMachineBlockEntity be) {
                return 0xFF000000 | be.getRarity().pillColor();
            }
            return -1;
        }, blocks);
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        Item[] items = Registration.MACHINE_ITEMS.values().stream().map(RegistryObject::get).toArray(Item[]::new);
        event.register((stack, tint) -> tint == 0 ? (0xFF000000 | Rarity.COMMON.pillColor()) : -1, items);
    }
}
