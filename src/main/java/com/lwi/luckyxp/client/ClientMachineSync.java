package com.lwi.luckyxp.client;

import com.lwi.luckyxp.machine.VendingMachineMenu;
import net.minecraft.client.Minecraft;

/** Client hook for {@code net.SyncMachineSoldPacket}: marks a line sold in the vending menu that is
 *  open right now. The packet only reaches players whose open menu is that machine's, so no position
 *  check is needed here. */
public final class ClientMachineSync {

    private ClientMachineSync() {}

    public static void markSold(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof VendingMachineMenu menu) {
            menu.markSoldLocal(index);
        }
    }
}
