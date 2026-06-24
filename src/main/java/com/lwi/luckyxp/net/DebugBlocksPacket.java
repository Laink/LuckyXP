package com.lwi.luckyxp.net;

import com.lwi.luckyxp.client.ClientDebugBlocks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sends a debug player the nearby event blocks (packed pos + label) to draw "Luck +X" holograms over. */
public class DebugBlocksPacket {
    public final long[] pos;
    public final String[] labels;

    public DebugBlocksPacket(long[] pos, String[] labels) {
        this.pos = pos;
        this.labels = labels;
    }

    public static DebugBlocksPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        long[] p = new long[n];
        String[] l = new String[n];
        for (int i = 0; i < n; i++) {
            p[i] = buf.readLong();
            l[i] = buf.readUtf();
        }
        return new DebugBlocksPacket(p, l);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(pos.length);
        for (int i = 0; i < pos.length; i++) {
            buf.writeLong(pos[i]);
            buf.writeUtf(labels[i]);
        }
    }

    public static void handle(DebugBlocksPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientDebugBlocks.set(pkt.pos, pkt.labels)));
        ctx.setPacketHandled(true);
    }
}
