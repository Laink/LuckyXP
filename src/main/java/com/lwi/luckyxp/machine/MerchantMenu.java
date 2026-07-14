package com.lwi.luckyxp.machine;

import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.api.LuckyXpApi;
import com.lwi.luckyxp.xp.LuckBuffs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The merchant's six services, prices fixed by the user (2026-07-09). Slot-less menu like the vending
 * machine's; each service is a menu-button click, validated and executed server-side. Creative pays
 * nothing (same convention as the machines).
 *
 * <p>Services 0/1 target the merchant's associated machine; 2/3 write the player's luck buffs (read at
 * every lucky-block break by {@link LuckBuffs}); 4/5 act on the player directly.
 */
public class MerchantMenu extends AbstractContainerMenu {
    public static final int SERVICE_REROLL = 0;
    public static final int SERVICE_CONVERT = 1;
    public static final int SERVICE_TEMP_LUCK = 2;
    public static final int SERVICE_PERM_LUCK = 3;
    public static final int SERVICE_HEAL = 4;
    public static final int SERVICE_REPAIR = 5;

    public static final int[] PRICES = {15, 18, 20, 25, 5, 13};

    /** The random curse riding along with the temporary luck surge (5 minutes, like the buff). */
    private static final MobEffect[] SURGE_CURSES = {
            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS, MobEffects.HUNGER,
            MobEffects.DIG_SLOWDOWN, MobEffects.CONFUSION
    };

    private final BlockPos machinePos;
    /** The merchant entity that opened this menu, so a sale can trigger his SALE animation. */
    private final int merchantId;

    public MerchantMenu(int id, Inventory inv, BlockPos machinePos, int merchantId) {
        super(Registration.MERCHANT_MENU.get(), id);
        this.machinePos = machinePos;
        this.merchantId = merchantId;
    }

    public MerchantMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), buf.readVarInt());
    }

    /** The machine this merchant is bound to, so the stand's timer can boot open screens on close. */
    public BlockPos machinePos() {
        return machinePos;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId < 0 || buttonId >= PRICES.length || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        // The whole stand goes inert when its window closes — even with the menu still open.
        if (sp.serverLevel().getBlockEntity(machinePos) instanceof VendingMachineBlockEntity machine
                && machine.isClosed()) {
            return fail(sp, "This stand has closed for good.");
        }
        int price = PRICES[buttonId];
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && LuckyXpApi.getLevel(sp) < price) {
            return false;
        }
        // Validate BEFORE charging: a service that cannot apply must not eat the levels.
        if (!executeService(sp, buttonId)) {
            return false;
        }
        if (!creative) {
            LuckyXpApi.spendLevels(sp, price);
        }
        // The "cha-ching" — two amethyst chimes an octave apart + the XP-orb blip (matches the vending
        // machine's client sound; here server-side, so it plays right where the buyer stands).
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9F, 1.2F);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.8F);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.4F);
        if (sp.level().getEntity(merchantId) instanceof com.lwi.luckyxp.entity.LuckyMerchant merchant) {
            merchant.playSale();                        // the shopkeeper reacts to the sale
        }
        return true;
    }

    private boolean executeService(ServerPlayer sp, int service) {
        ServerLevel level = sp.serverLevel();
        switch (service) {
            case SERVICE_REROLL -> {
                if (!(level.getBlockEntity(machinePos) instanceof VendingMachineBlockEntity be)) {
                    return fail(sp, "No machine to reroll!");
                }
                be.devReroll(be.getRarity(), level);            // fresh stock, same rarity
                msg(sp, "Stock rerolled!", ChatFormatting.GREEN);
                return true;
            }
            case SERVICE_CONVERT -> {
                if (!(level.getBlockEntity(machinePos) instanceof VendingMachineBlockEntity be)) {
                    return fail(sp, "No machine to convert!");
                }
                MachineType next = MachineType.values()[(be.getMachineType().ordinal() + 1) % MachineType.values().length];
                Rarity rarity = be.getRarity();
                long closeAt = be.closeAt();            // the stand's window survives the block swap
                boolean wasClosed = be.isClosed();
                Block block = Registration.MACHINES.get(next).get();
                BlockPos upPos = machinePos.above();
                BlockState oldLower = level.getBlockState(machinePos);
                BlockState oldUpper = level.getBlockState(upPos);
                // Each machine type is a DIFFERENT block, and VendingMachineBlock#updateShape turns a
                // half to AIR the instant its partner is "not the same block". Converting one half would
                // make the other self-destruct — so place both with UPDATE_KNOWN_SHAPE (16), which skips
                // updateShape entirely, and no neighbour flag, so nothing reacts mid-swap.
                int flags = 2 | 16;                            // clients + known-shape (no self-destruct)
                level.setBlock(machinePos, block.withPropertiesOf(oldLower), flags);
                if (oldUpper.getBlock() instanceof VendingMachineBlock) {
                    level.setBlock(upPos, block.withPropertiesOf(oldUpper), flags);
                }
                if (level.getBlockEntity(machinePos) instanceof VendingMachineBlockEntity fresh) {
                    fresh.restoreTimer(closeAt, wasClosed);     // the countdown belongs to the STAND
                    fresh.devReroll(rarity, level);             // new type, same rarity, fresh stock
                }
                msg(sp, "Machine converted to " + next.name() + "!", ChatFormatting.GREEN);
                return true;
            }
            case SERVICE_TEMP_LUCK -> {
                int pct = 10 + level.random.nextInt(21);        // +10..30%
                LuckBuffs.setTempLuck(sp, pct, 5 * 60 * 20);
                MobEffect curse = SURGE_CURSES[level.random.nextInt(SURGE_CURSES.length)];
                sp.addEffect(new MobEffectInstance(curse, 5 * 60 * 20, 1));
                msg(sp, "Luck boost: +" + pct + "% for 5 minutes... and a curse.", ChatFormatting.GOLD);
                return true;
            }
            case SERVICE_PERM_LUCK -> {
                if (LuckBuffs.getPermLuck(sp) >= LuckBuffs.permLuckCap()) {
                    return fail(sp, "Already at maximum permanent luck (+" + LuckBuffs.permLuckCap() + "%)!");
                }
                int pct = 1 + level.random.nextInt(3);          // +1..3%
                int total = LuckBuffs.addPermLuck(sp, pct);
                msg(sp, "+" + pct + "% permanent luck (total +" + total + "%)", ChatFormatting.LIGHT_PURPLE);
                return true;
            }
            case SERVICE_HEAL -> {
                if (sp.getHealth() >= sp.getMaxHealth()) {
                    return fail(sp, "Already at full health!");
                }
                sp.setHealth(sp.getMaxHealth());
                msg(sp, "Fully healed!", ChatFormatting.GREEN);
                return true;
            }
            case SERVICE_REPAIR -> {
                ItemStack held = sp.getMainHandItem();
                if (held.isEmpty() || !held.isDamaged()) {
                    return fail(sp, "Hold a damaged item!");
                }
                held.setDamageValue(0);
                msg(sp, "Item repaired!", ChatFormatting.GREEN);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean fail(ServerPlayer sp, String text) {
        msg(sp, text, ChatFormatting.RED);
        return false;
    }

    private static void msg(ServerPlayer sp, String text, ChatFormatting colour) {
        sp.displayClientMessage(Component.literal(text).withStyle(colour), true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;                                    // the merchant stands right there
    }
}
