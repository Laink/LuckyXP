package com.lwi.luckyxp.net;

import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.event.LuckyEvent;
import com.lwi.luckyxp.event.LuckyEventManager;
import com.lwi.luckyxp.xp.LuckyXpData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server -> client sync of a player's Lucky XP (pushed on every change + on login/respawn). */
public final class LuckyXpNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LuckyXpMod.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private LuckyXpNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncXpPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncXpPacket::decode)
                .encoder(SyncXpPacket::encode)
                .consumerMainThread(SyncXpPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncEventPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncEventPacket::decode)
                .encoder(SyncEventPacket::encode)
                .consumerMainThread(SyncEventPacket::handle)
                .add();
        CHANNEL.messageBuilder(DebugBlocksPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(DebugBlocksPacket::decode)
                .encoder(DebugBlocksPacket::encode)
                .consumerMainThread(DebugBlocksPacket::handle)
                .add();
    }

    /** Send a debug player the nearby event blocks to draw "Luck +X" holograms over (empty list = clear). */
    public static void sendDebugBlocks(ServerPlayer player, java.util.List<Long> positions, java.util.List<String> labels) {
        if (player.connection == null) {
            return;
        }
        long[] p = new long[positions.size()];
        for (int i = 0; i < p.length; i++) {
            p[i] = positions.get(i);
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DebugBlocksPacket(p, labels.toArray(new String[0])));
    }

    public static void sync(ServerPlayer player) {
        if (player.connection == null) {
            return; // fake player / no client connection to receive the HUD update
        }
        int level = LuckyXpData.getLevel(player);
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncXpPacket(level, LuckyXpData.getInto(player), LuckyXpData.xpToNext(level), LuckyXpData.getTotal(player))
        );
    }

    /** A wire snapshot of the current event reveal (or its absence). */
    private static SyncEventPacket snapshot(LuckyEventManager mgr) {
        LuckyEvent e = mgr.active();
        if (e == null) {
            return new SyncEventPacket(false, "", 0, "", 0, 0.0F, false, 0, 0, 0L);
        }
        int scope = e.isNothing() ? 0 : (e.isSingle() ? 1 : 2);
        String block = e.isSingle() && e.blockId() != null ? e.blockId().toString() : "";
        return new SyncEventPacket(true, e.type().id, scope, block, e.luckPercent(), e.xpMult(),
                e.isMega(), mgr.revealRemaining(), mgr.revealTotal(), mgr.seed());
    }

    /** Push the current global event state to every connected client (start/stop/periodic resync). */
    public static void broadcastEvent(MinecraftServer server, LuckyEventManager mgr) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), snapshot(mgr));
    }

    /** Send the current global event state to one player (e.g. on login, so they see an ongoing event). */
    public static void sendEvent(ServerPlayer player, LuckyEventManager mgr) {
        if (player.connection == null) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot(mgr));
    }
}
