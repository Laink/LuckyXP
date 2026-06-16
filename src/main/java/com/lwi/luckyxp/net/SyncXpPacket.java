package com.lwi.luckyxp.net;

import com.lwi.luckyxp.client.ClientXpCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Carries a player's Lucky XP snapshot to their client for the HUD bar. */
public class SyncXpPacket {
    public final int level;
    public final int into;
    public final int toNext;
    public final long total;

    public SyncXpPacket(int level, int into, int toNext, long total) {
        this.level = level;
        this.into = into;
        this.toNext = toNext;
        this.total = total;
    }

    public static SyncXpPacket decode(FriendlyByteBuf buf) {
        return new SyncXpPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(level);
        buf.writeVarInt(into);
        buf.writeVarInt(toNext);
        buf.writeVarLong(total);
    }

    public static void handle(SyncXpPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientXpCache.set(pkt.level, pkt.into, pkt.toNext, pkt.total)));
        ctx.setPacketHandled(true);
    }
}
