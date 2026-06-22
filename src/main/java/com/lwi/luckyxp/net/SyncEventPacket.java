package com.lwi.luckyxp.net;

import com.lwi.luckyxp.client.ClientEventCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Broadcasts the current global Lucky XP event (or its absence) to clients for the HUD timer / roulette. */
public class SyncEventPacket {
    public final boolean present;
    public final String typeId;
    public final boolean hasBlock;
    public final String blockId;
    public final int magnitude;
    public final int ticksRemaining;
    public final int totalTicks;

    public SyncEventPacket(boolean present, String typeId, boolean hasBlock, String blockId,
                           int magnitude, int ticksRemaining, int totalTicks) {
        this.present = present;
        this.typeId = typeId;
        this.hasBlock = hasBlock;
        this.blockId = blockId;
        this.magnitude = magnitude;
        this.ticksRemaining = ticksRemaining;
        this.totalTicks = totalTicks;
    }

    public static SyncEventPacket decode(FriendlyByteBuf buf) {
        boolean present = buf.readBoolean();
        if (!present) {
            return new SyncEventPacket(false, "", false, "", 0, 0, 0);
        }
        String typeId = buf.readUtf();
        boolean hasBlock = buf.readBoolean();
        String blockId = hasBlock ? buf.readUtf() : "";
        int magnitude = buf.readInt();
        int remaining = buf.readVarInt();
        int total = buf.readVarInt();
        return new SyncEventPacket(true, typeId, hasBlock, blockId, magnitude, remaining, total);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(present);
        if (!present) {
            return;
        }
        buf.writeUtf(typeId);
        buf.writeBoolean(hasBlock);
        if (hasBlock) {
            buf.writeUtf(blockId);
        }
        buf.writeInt(magnitude);
        buf.writeVarInt(ticksRemaining);
        buf.writeVarInt(totalTicks);
    }

    public static void handle(SyncEventPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientEventCache.set(pkt)));
        ctx.setPacketHandled(true);
    }
}
