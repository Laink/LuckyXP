package com.lwi.luckyxp.net;

import com.lwi.luckyxp.client.ClientEventCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Broadcasts the current Lucky XP event reveal (or its absence) to clients for the case-opening roulette.
 * Carries the v4 outcome: scope (0=nothing/1=single/2=jackpot), the single block id, the value
 * (luckPercent for LUCK, xpMult for XP), the mega flag, the reveal countdown, and a seed for a
 * deterministic spin. The event exists client-side only during the reveal (no temporal phase).
 */
public class SyncEventPacket {
    public final boolean present;
    public final String typeId;
    public final int scope;         // 0 = NOTHING, 1 = SINGLE, 2 = JACKPOT
    public final String blockId;    // SINGLE only
    public final int luckPercent;   // LUCK value
    public final float xpMult;      // XP value
    public final boolean mega;      // value roll hit its max
    public final int ticksRemaining;
    public final int totalTicks;
    public final long seed;

    public SyncEventPacket(boolean present, String typeId, int scope, String blockId, int luckPercent,
                           float xpMult, boolean mega, int ticksRemaining, int totalTicks, long seed) {
        this.present = present;
        this.typeId = typeId;
        this.scope = scope;
        this.blockId = blockId;
        this.luckPercent = luckPercent;
        this.xpMult = xpMult;
        this.mega = mega;
        this.ticksRemaining = ticksRemaining;
        this.totalTicks = totalTicks;
        this.seed = seed;
    }

    public static SyncEventPacket decode(FriendlyByteBuf buf) {
        boolean present = buf.readBoolean();
        if (!present) {
            return new SyncEventPacket(false, "", 0, "", 0, 0.0F, false, 0, 0, 0L);
        }
        String typeId = buf.readUtf();
        int scope = buf.readVarInt();
        String blockId = scope == 1 ? buf.readUtf() : "";
        int luckPercent = buf.readVarInt();
        float xpMult = buf.readFloat();
        boolean mega = buf.readBoolean();
        int remaining = buf.readVarInt();
        int total = buf.readVarInt();
        long seed = buf.readLong();
        return new SyncEventPacket(true, typeId, scope, blockId, luckPercent, xpMult, mega, remaining, total, seed);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(present);
        if (!present) {
            return;
        }
        buf.writeUtf(typeId);
        buf.writeVarInt(scope);
        if (scope == 1) {
            buf.writeUtf(blockId);
        }
        buf.writeVarInt(luckPercent);
        buf.writeFloat(xpMult);
        buf.writeBoolean(mega);
        buf.writeVarInt(ticksRemaining);
        buf.writeVarInt(totalTicks);
        buf.writeLong(seed);
    }

    public static void handle(SyncEventPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientEventCache.set(pkt)));
        ctx.setPacketHandled(true);
    }
}
