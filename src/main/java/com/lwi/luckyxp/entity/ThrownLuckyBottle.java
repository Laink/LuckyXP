package com.lwi.luckyxp.entity;

import com.lwi.luckyxp.Registration;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * The Lucky Experience Bottle in flight — the vanilla xp-bottle mechanic, but it bursts into blue
 * Lucky XP orbs instead of vanilla xp. Same throw arc and same 3-11 payout range as vanilla, so it
 * feels identical; only the currency (and the colour) differ.
 */
public class ThrownLuckyBottle extends ThrowableItemProjectile {
    public ThrownLuckyBottle(net.minecraft.world.entity.EntityType<? extends ThrownLuckyBottle> type, Level level) {
        super(type, level);
    }

    public ThrownLuckyBottle(Level level, LivingEntity thrower) {
        super(Registration.THROWN_LUCKY_BOTTLE.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Registration.LUCKY_EXPERIENCE_BOTTLE.get();
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);   // else invisible in flight
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.0);
            server.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 0.5, getZ(), 24, 0.3, 0.3, 0.3, 0.2);
            int amount = 3 + random.nextInt(5) + random.nextInt(5);     // vanilla's 3-11 range
            LuckyXpOrb.award(server, position(), amount);
            discard();
        }
    }
}
