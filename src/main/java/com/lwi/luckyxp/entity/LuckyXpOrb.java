package com.lwi.luckyxp.entity;

import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.api.LuckyXpApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * Blue Lucky XP orb -- a faithful port of vanilla {@link net.minecraft.world.entity.ExperienceOrb}
 * (gravity, fluids, follow the nearest player, merge, 5-min lifetime), except pickup grants Lucky XP
 * (via {@link LuckyXpApi}) instead of vanilla XP, and the value is synced via entity data (vanilla
 * ships it in a special packet). Spawned in clumps on a lucky-block break by {@code BreakXp}.
 */
public class LuckyXpOrb extends Entity {
    private static final EntityDataAccessor<Integer> DATA_VALUE =
            SynchedEntityData.defineId(LuckyXpOrb.class, EntityDataSerializers.INT);

    private int age;
    private int count = 1;
    private Player followingPlayer;

    public LuckyXpOrb(EntityType<? extends LuckyXpOrb> type, Level level) {
        super(type, level);
    }

    public LuckyXpOrb(Level level, double x, double y, double z, int value) {
        this(Registration.LUCKY_XP_ORB.get(), level);
        this.setPos(x, y, z);
        this.setYRot((float) (this.random.nextDouble() * 360.0));
        this.setDeltaMovement((this.random.nextDouble() * 0.2 - 0.1) * 2.0,
                this.random.nextDouble() * 0.2 * 2.0,
                (this.random.nextDouble() * 0.2 - 0.1) * 2.0);
        this.setValue(value);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_VALUE, 0);
    }

    public int getValue() {
        return this.entityData.get(DATA_VALUE);
    }

    public void setValue(int value) {
        this.entityData.set(DATA_VALUE, value);
    }

    @Override
    public void tick() {
        super.tick();
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        if (this.isEyeInFluid(FluidTags.WATER)) {
            this.setUnderwaterMovement();
        } else if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.03, 0.0));
        }
        if (this.level().getFluidState(this.blockPosition()).is(FluidTags.LAVA)) {
            this.setDeltaMovement((this.random.nextFloat() - this.random.nextFloat()) * 0.2f, 0.2f,
                    (this.random.nextFloat() - this.random.nextFloat()) * 0.2f);
        }
        if (!this.level().noCollision(this.getBoundingBox())) {
            this.moveTowardsClosestSpace(this.getX(),
                    (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0, this.getZ());
        }
        if (this.tickCount % 20 == 1) {
            this.scanForEntities();
        }
        if (this.followingPlayer != null && (this.followingPlayer.isSpectator() || this.followingPlayer.isDeadOrDying())) {
            this.followingPlayer = null;
        }
        if (this.followingPlayer != null) {
            Vec3 toPlayer = new Vec3(
                    this.followingPlayer.getX() - this.getX(),
                    this.followingPlayer.getY() + (double) this.followingPlayer.getEyeHeight() / 2.0 - this.getY(),
                    this.followingPlayer.getZ() - this.getZ());
            double distSqr = toPlayer.lengthSqr();
            if (distSqr < 64.0) {
                double pull = 1.0 - Math.sqrt(distSqr) / 8.0;
                this.setDeltaMovement(this.getDeltaMovement().add(toPlayer.normalize().scale(pull * pull * 0.1)));
            }
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        float friction = 0.98f;
        if (this.onGround()) {
            BlockPos below = this.getBlockPosBelowThatAffectsMyMovement();
            friction = this.level().getBlockState(below).getFriction(this.level(), below, this) * 0.98f;
        }
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 0.98, friction));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, -0.9, 1.0));
        }
        ++this.age;
        if (this.age >= 6000) {
            this.discard();
        }
    }

    @Override
    protected BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999f);
    }

    private void scanForEntities() {
        if (this.followingPlayer == null || this.followingPlayer.distanceToSqr(this) > 64.0) {
            this.followingPlayer = this.level().getNearestPlayer(this, 8.0);
        }
        if (this.level() instanceof ServerLevel) {
            for (LuckyXpOrb other : this.level().getEntities(EntityTypeTest.forClass(LuckyXpOrb.class),
                    this.getBoundingBox().inflate(0.5),
                    o -> o != this && !o.isRemoved() && o.getValue() == this.getValue())) {
                this.count += other.count;
                this.age = Math.min(this.age, other.age);
                other.discard();
            }
        }
    }

    private void setUnderwaterMovement() {
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.x * 0.99, Math.min(v.y + 5.0E-4, 0.06), v.z * 0.99);
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public void playerTouch(Player player) {
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer && serverPlayer.takeXpDelay == 0) {
            serverPlayer.takeXpDelay = 2;
            serverPlayer.take(this, 1);
            LuckyXpApi.addXp(serverPlayer, this.getValue());
            // Lucky pickup chime: the vanilla XP-orb sound, pitched higher for a brighter "lucky" feel
            // (custom entities don't trigger the client-side handleTakeItemEntity chime, so play it here).
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                    0.07F, (this.random.nextFloat() - this.random.nextFloat()) * 0.35F + 1.5F);
            --this.count;
            if (this.count == 0) {
                this.discard();
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putShort("Age", (short) this.age);
        tag.putShort("Value", (short) this.getValue());
        tag.putInt("Count", this.count);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getShort("Age");
        this.setValue(tag.getShort("Value"));
        this.count = Math.max(tag.getInt("Count"), 1);
    }

    /** Renderer icon (0-10) by value, same thresholds as vanilla ExperienceOrb. */
    public int getIcon() {
        int v = this.getValue();
        if (v >= 2477) return 10;
        if (v >= 1237) return 9;
        if (v >= 617) return 8;
        if (v >= 307) return 7;
        if (v >= 149) return 6;
        if (v >= 73) return 5;
        if (v >= 37) return 4;
        if (v >= 17) return 3;
        if (v >= 7) return 2;
        return v >= 3 ? 1 : 0;
    }

    private static int splitValue(int v) {
        if (v >= 2477) return 2477;
        if (v >= 1237) return 1237;
        if (v >= 617) return 617;
        if (v >= 307) return 307;
        if (v >= 149) return 149;
        if (v >= 73) return 73;
        if (v >= 37) return 37;
        if (v >= 17) return 17;
        if (v >= 7) return 7;
        return v >= 3 ? 3 : 1;
    }

    /** Spawn a clump of orbs totalling {@code amount}, like vanilla ExperienceOrb.award. */
    public static void award(ServerLevel level, Vec3 pos, int amount) {
        while (amount > 0) {
            int chunk = splitValue(amount);
            amount -= chunk;
            level.addFreshEntity(new LuckyXpOrb(level, pos.x, pos.y, pos.z, chunk));
        }
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }
}
