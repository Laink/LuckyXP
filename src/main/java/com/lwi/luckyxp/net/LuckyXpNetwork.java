package com.lwi.luckyxp.net;

import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.xp.LuckyXpData;
import net.minecraft.resources.ResourceLocation;
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
}
