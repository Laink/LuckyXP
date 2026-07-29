package com.lwi.luckyxp.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells clients a vending machine line is now sold, so a second shopper's screen updates live
 *  instead of still offering an article the server will refuse. */
public class SyncMachineSoldPacket {
    public final int index;

    public SyncMachineSoldPacket(int index) {
        this.index = index;
    }

    public static SyncMachineSoldPacket decode(FriendlyByteBuf buf) {
        return new SyncMachineSoldPacket(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(index);
    }

    public static void handle(SyncMachineSoldPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lwi.luckyxp.client.ClientMachineSync.markSold(pkt.index)));
        ctx.setPacketHandled(true);
    }
}
