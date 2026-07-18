package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * While one of our shop screens is open, ACTION-BAR messages (the merchant/machine feedback the server
 * sends as overlay text) would render BEHIND the screen and its dim — unreadable. This reroutes them to
 * the screen's own {@link ScreenToast}, drawn on top of everything, and cancels the original overlay so
 * the same line isn't half-visible twice. Messages arriving with no shop screen open (or any other
 * screen) keep the vanilla action-bar path untouched.
 *
 * <p>FORGE bus (default — {@code ClientChatReceivedEvent} is not an {@code IModBusEvent}), client only.
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = Dist.CLIENT)
public final class ScreenMessageRouter {
    private ScreenMessageRouter() {}

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (!event.isOverlay()) {
            return;
        }
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof MerchantScreen merchant) {
            merchant.showToast(event.getMessage());
            event.setCanceled(true);
        } else if (screen instanceof VendingMachineScreen machine) {
            machine.showToast(event.getMessage());
            event.setCanceled(true);
        }
    }
}
