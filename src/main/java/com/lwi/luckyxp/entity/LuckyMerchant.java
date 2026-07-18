package com.lwi.luckyxp.entity;

import com.lwi.luckyxp.machine.MerchantMenu;
import com.lwi.luckyxp.machine.Rarity;
import com.lwi.luckyxp.machine.VendingMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The stand's merchant: a stationary NPC placed next to each vending machine at worldgen, selling six
 * services for Lucky levels (see {@link MerchantMenu}). Indestructible and unmovable, like the machine
 * he tends — he IS the machine's customer service: reroll, type change, luck buffs, heal, repair.
 *
 * <p>He never walks (no movement goals), never despawns, cannot be hurt or pushed. He remembers his
 * machine's position ({@code MachinePos}) so the reroll/convert services know their target.
 */
public class LuckyMerchant extends PathfinderMob implements GeoEntity {
    /** GeckoLib controller + triggerable animation names, matching lucky_merchant.animation.json. */
    public static final String CONTROLLER = "controller";
    public static final String ANIM_SALE = "sale";
    /** Blend time between idle and sale: with 0 the pose snapped, which read as a stutter. */
    private static final int TRANSITION_TICKS = 5;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SALE = RawAnimation.begin().thenPlay(ANIM_SALE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private BlockPos machinePos = BlockPos.ZERO;

    /** A pitched two-note villager "voice" fired on a sale — a deranged-genius cackle, not a real
     *  villager. The first note plays with {@link #playSale()}; the second is fired a few ticks later
     *  from {@link #tick()} so the pair lands as a little "he-HEE!" instead of one muddy blob. */
    private int cackleAt = -1;
    private float cacklePitch = 1.6F;

    /** Set at the stand's ruin (timer end): swaps the merchant to his blown-up texture. Synced so the
     *  client renderer picks the exploded skin; persisted so a ruined stand stays ruined on reload. */
    private static final EntityDataAccessor<Boolean> EXPLODED =
            SynchedEntityData.defineId(LuckyMerchant.class, EntityDataSerializers.BOOLEAN);

    /** His own rarity, rolled independently of the machine he tends: it colours his hat and discounts all
     *  six of his services (see {@link Rarity#discountedPrice}). Synced because the hat is client-side and
     *  the trade screen prices its buttons from it; persisted so a stand keeps its merchant on reload. */
    private static final EntityDataAccessor<String> RARITY =
            SynchedEntityData.defineId(LuckyMerchant.class, EntityDataSerializers.STRING);

    public LuckyMerchant(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(EXPLODED, false);
        this.entityData.define(RARITY, Rarity.COMMON.getSerializedName());
    }

    /** Blow him up (ruined-stand state) — cosmetic only; he already refuses all trades once closed. */
    public void setExploded(boolean exploded) {
        this.entityData.set(EXPLODED, exploded);
    }

    public boolean isExploded() {
        return this.entityData.get(EXPLODED);
    }

    public void setRarity(Rarity rarity) {
        this.entityData.set(RARITY, rarity.getSerializedName());
    }

    public Rarity getRarity() {
        return Rarity.byId(this.entityData.get(RARITY), Rarity.COMMON);
    }

    /** What this merchant charges for a service, after his rarity's cut. Same answer on both sides. */
    public int priceOf(int basePrice) {
        return getRarity().discountedPrice(basePrice);
    }

    /** Play the SALE animation once, then fall back to idle (server-side; GeckoLib syncs it to viewers).
     *  Also gives a pitched villager "hehe!" — a high, slightly random cackle for the mad-scientist vibe
     *  (he's meant to be unpredictable). The follow-up note is scheduled in {@link #tick()}. */
    public void playSale() {
        triggerAnim(CONTROLLER, ANIM_SALE);
        if (!level().isClientSide) {
            float p1 = 1.4F + random.nextFloat() * 0.4F;              // 1.4–1.8: high, giddy
            level().playSound(null, blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, p1);
            cacklePitch = Math.min(2.0F, p1 + 0.2F);                  // second note a touch higher: "he-HEE!"
            cackleAt = tickCount + 4;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && cackleAt >= 0 && tickCount >= cackleAt) {
            cackleAt = -1;
            level().playSound(null, blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, cacklePitch);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER, TRANSITION_TICKS, state -> state.setAndContinue(IDLE))
                .triggerableAnim(ANIM_SALE, SALE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public void setMachinePos(BlockPos pos) {
        this.machinePos = pos;
    }

    @Override
    protected void registerGoals() {
        // Probability 1.0, not the vanilla 0.02: a shopkeeper should WATCH his customer, not glance
        // at them 2% of the time while RandomLookAround points him at a wall (user 2026-07-19).
        // He idle-gazes only when nobody is within range.
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F, 1.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;                                   // nothing hurts the shopkeeper
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // and he pushes no one either
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /**
     * One-shot repair, ONLY for merchants saved before MachinePos persistence existed: their tag
     * reads back as (0,0,0), never a legitimate machine spot. A merchant with a real link — even
     * one whose machine is gone — must NEVER rebind on his own: machines may become portable one
     * day, and a shopkeeper adopting whatever a player drops next to him would be chaos.
     */
    private void rebindIfLegacyBroken() {
        if (!machinePos.equals(BlockPos.ZERO)) {
            return;
        }
        BlockPos base = blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, -2, -3), base.offset(3, 2, 3))) {
            if (level().getBlockEntity(p) instanceof VendingMachineBlockEntity) {
                double d = p.distSqr(base);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        if (best != null) {
            machinePos = best;
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            rebindIfLegacyBroken();
            BlockPos target = machinePos;
            // The merchant belongs to the stand: once its window closed, he is done trading — and his
            // first customer arms the same countdown the machine does.
            long closeAtSnapshot = -1L;
            if (level().getBlockEntity(target) instanceof VendingMachineBlockEntity machine) {
                if (machine.isClosed()) {
                    serverPlayer.displayClientMessage(Component.translatable("luckyxp.msg.stand_closed")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                    return InteractionResult.sidedSuccess(level().isClientSide);
                }
                machine.startTimerIfNeeded(level());
                // Snapshot AFTER arming: the first customer's own screen must already show the countdown.
                closeAtSnapshot = machine.closeAt();
            }
            final long closeAt = closeAtSnapshot;
            int merchantId = getId();
            NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("entity.luckyxp.lucky_merchant");
                }

                @Nullable
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new MerchantMenu(id, inv, target, merchantId, closeAt);
                }
            }, buf -> {
                buf.writeBlockPos(target);
                buf.writeVarInt(merchantId);
                buf.writeLong(closeAt);
            });
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("MachinePos", machinePos.asLong());
        tag.putBoolean("Exploded", isExploded());
        tag.putString("Rarity", getRarity().getSerializedName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        machinePos = BlockPos.of(tag.getLong("MachinePos"));
        setExploded(tag.getBoolean("Exploded"));
        // Merchants saved before rarity existed have no tag: they read back COMMON, i.e. full price.
        setRarity(Rarity.byId(tag.getString("Rarity"), Rarity.COMMON));
    }
}
