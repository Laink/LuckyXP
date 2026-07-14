package com.lwi.luckyxp.entity;

import com.lwi.luckyxp.machine.MerchantMenu;
import com.lwi.luckyxp.machine.VendingMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

    public LuckyMerchant(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    /** Play the SALE animation once, then fall back to idle (server-side; GeckoLib syncs it to viewers). */
    public void playSale() {
        triggerAnim(CONTROLLER, ANIM_SALE);
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
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
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
            if (level().getBlockEntity(target) instanceof VendingMachineBlockEntity machine) {
                if (machine.isClosed()) {
                    serverPlayer.displayClientMessage(Component.literal("This stand has closed for good.")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                    return InteractionResult.sidedSuccess(level().isClientSide);
                }
                machine.startTimerIfNeeded(level());
            }
            int merchantId = getId();
            NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("entity.luckyxp.lucky_merchant");
                }

                @Nullable
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new MerchantMenu(id, inv, target, merchantId);
                }
            }, buf -> {
                buf.writeBlockPos(target);
                buf.writeVarInt(merchantId);
            });
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("MachinePos", machinePos.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        machinePos = BlockPos.of(tag.getLong("MachinePos"));
    }
}
