package com.lwi.luckyxp.machine;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.api.LuckyXpApi;
import com.lwi.luckyxp.compat.ImprovedMobsDifficulty;
import com.lwi.luckyxp.entity.LuckyMerchant;
import com.lwi.luckyxp.xp.LuckBuffs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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
    public static final int SERVICE_DIFFICULTY = 6;
    public static final int SERVICE_LIFE = 7;

    /** How much of Improved Mobs' difficulty one purchase buys back. */
    private static final float DIFFICULTY_CUT = 0.5F;

    /** Base prices, before the merchant's own rarity discounts them (see {@link #price}). */
    public static final int[] PRICES = {10, 15, 20, 23, 5, 13, 12, 30};

    /** The random curse riding along with the temporary luck surge (5 minutes, like the buff). */
    private static final MobEffect[] SURGE_CURSES = {
            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS, MobEffects.HUNGER,
            MobEffects.DIG_SLOWDOWN, MobEffects.CONFUSION
    };

    private final BlockPos machinePos;
    /** The merchant entity that opened this menu, so a sale can trigger his SALE animation. */
    private final int merchantId;
    /** The stand's close time (game time), snapshotted at open — closeAt never changes once armed, so the
     *  screen can derive a live countdown from it without any per-tick packet (same trick as the machine). */
    private final long closeAt;

    public MerchantMenu(int id, Inventory inv, BlockPos machinePos, int merchantId, long closeAt) {
        super(Registration.MERCHANT_MENU.get(), id);
        this.machinePos = machinePos;
        this.merchantId = merchantId;
        this.closeAt = closeAt;
    }

    public MerchantMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), buf.readVarInt(), buf.readLong());
    }

    /** Game time at which the stand closes for good, or -1 if the countdown was never armed. */
    public long closeAt() {
        return closeAt;
    }

    /** The machine this merchant is bound to, so the stand's timer can boot open screens on close. */
    public BlockPos machinePos() {
        return machinePos;
    }

    /** This menu's merchant, or null if he is somehow gone (he is invulnerable, so: never in practice). */
    @Nullable
    public LuckyMerchant merchant(Level level) {
        return level.getEntity(merchantId) instanceof LuckyMerchant m ? m : null;
    }

    /**
     * What this merchant charges for a service. His rarity discounts every one of them, and his rarity is
     * synced, so the client labels its buttons with the very number the server will charge -- one source of
     * truth, no packet, and no way for the two to drift. Falls back to the base price if he is missing.
     */
    public int price(Level level, int service) {
        LuckyMerchant m = merchant(level);
        return m != null ? m.priceOf(PRICES[service]) : PRICES[service];
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId < 0 || buttonId >= PRICES.length || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        // The whole stand goes inert when its window closes — even with the menu still open.
        if (sp.serverLevel().getBlockEntity(machinePos) instanceof VendingMachineBlockEntity machine
                && machine.isClosed()) {
            return fail(sp, Component.translatable("luckyxp.msg.stand_closed"));
        }
        int price = price(sp.level(), buttonId);
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && LuckyXpApi.getLevel(sp) < price) {
            // Say it — every other refusal of this merchant talks, a silent click reads as a bug.
            return fail(sp, Component.translatable("luckyxp.merchant.msg.not_enough_levels", price));
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
                    return fail(sp, Component.translatable("luckyxp.merchant.msg.no_machine_reroll"));
                }
                be.devReroll(be.getRarity(), level);            // fresh stock, same rarity
                msg(sp, Component.translatable("luckyxp.merchant.msg.rerolled"), ChatFormatting.GREEN);
                return true;
            }
            case SERVICE_CONVERT -> {
                if (!(level.getBlockEntity(machinePos) instanceof VendingMachineBlockEntity be)) {
                    return fail(sp, Component.translatable("luckyxp.merchant.msg.no_machine_convert"));
                }
                // A RANDOM other type, never the one it already is (user 2026-07-21). It used to step
                // through the enum in order, which a player spotted as a fixed Consumables -> Lucky
                // Blocks -> Materials -> Tools loop and reported as a bug. Rolling +1..+3 keeps the
                // "never the same type" guarantee — paying 15 levels for no change would be a robbery —
                // while making the outcome a surprise, which is what this pack is about.
                MachineType[] types = MachineType.values();
                MachineType next = types[(be.getMachineType().ordinal() + 1 + level.random.nextInt(types.length - 1))
                        % types.length];
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
                msg(sp, Component.translatable("luckyxp.merchant.msg.converted",
                        Component.translatable(typeKey(next))), ChatFormatting.GREEN);
                return true;
            }
            case SERVICE_TEMP_LUCK -> {
                int pct = 10 + level.random.nextInt(21);        // +10..30%
                LuckBuffs.setTempLuck(sp, pct, 5 * 60 * 20);
                MobEffect curse = SURGE_CURSES[level.random.nextInt(SURGE_CURSES.length)];
                sp.addEffect(new MobEffectInstance(curse, 5 * 60 * 20, 1));
                msg(sp, Component.translatable("luckyxp.merchant.msg.temp_luck", pct), ChatFormatting.GOLD);
                return true;
            }
            case SERVICE_PERM_LUCK -> {
                int before = LuckBuffs.getPermLuck(sp);
                if (before >= LuckBuffs.permLuckCap()) {
                    return fail(sp, Component.translatable("luckyxp.merchant.msg.perm_luck_max", LuckBuffs.permLuckCap()));
                }
                int pct = 1 + level.random.nextInt(3);          // +1..3%
                int total = LuckBuffs.addPermLuck(sp, pct);
                // Report the GRANTED gain, not the rolled one: near the cap addPermLuck clamps, and
                // "+3%" for an actual +1 would be a lie the player paid for.
                msg(sp, Component.translatable("luckyxp.merchant.msg.perm_luck", total - before, total), ChatFormatting.LIGHT_PURPLE);
                return true;
            }
            case SERVICE_HEAL -> {
                if (sp.getHealth() >= sp.getMaxHealth()) {
                    return fail(sp, Component.translatable("luckyxp.merchant.msg.full_health"));
                }
                sp.setHealth(sp.getMaxHealth());
                msg(sp, Component.translatable("luckyxp.merchant.msg.healed"), ChatFormatting.GREEN);
                return true;
            }
            case SERVICE_REPAIR -> {
                ItemStack held = sp.getMainHandItem();
                if (held.isEmpty() || !held.isDamaged()) {
                    return fail(sp, Component.translatable("luckyxp.merchant.msg.hold_damaged"));
                }
                held.setDamageValue(0);
                msg(sp, Component.translatable("luckyxp.merchant.msg.repaired"), ChatFormatting.GREEN);
                return true;
            }
            case SERVICE_DIFFICULTY -> {
                MinecraftServer server = sp.getServer();
                if (server == null || !ImprovedMobsDifficulty.isAvailable()) {
                    return fail(sp, Component.translatable("luckyxp.merchant.msg.difficulty_unavailable"));
                }
                // Applied to EVERY player online, not just the buyer: this pack scales mobs off the highest
                // difficulty within 256 blocks, so cutting one number while a team-mate stands there with a
                // bigger one would move the HUD and change nothing that actually swings at you.
                float before = ImprovedMobsDifficulty.peak(server);
                if (!ImprovedMobsDifficulty.lower(server, DIFFICULTY_CUT)) {
                    return fail(sp, Component.translatable("luckyxp.merchant.msg.difficulty_min"));
                }
                // Report the ACTUAL cut: lower() clamps at 0, so from a peak below the standard cut
                // the real drop is smaller than DIFFICULTY_CUT.
                float after = ImprovedMobsDifficulty.peak(server);
                msg(sp, Component.translatable("luckyxp.merchant.msg.difficulty_lowered",
                        String.format(java.util.Locale.ROOT, "%.2f", before - after),
                        String.format(java.util.Locale.ROOT, "%.2f", after)),
                        ChatFormatting.AQUA);
                return true;
            }
            case SERVICE_LIFE -> {
                MinecraftServer server = sp.getServer();
                if (server == null || LuckyTweaksApi.getSharedLivesRemaining(server) < 0) {
                    return fail(sp, Component.translatable("luckyxp.merchant.msg.no_lives"));
                }
                // Asked BEFORE charging: the run may buy only so many lives ever, and a merchant who took
                // the levels and shrugged would be worse than one who says no. The cap belongs to the RUN,
                // not to a player -- the pool is shared, so the last life is the team's last life.
                if (!LuckyTweaksApi.canBuySharedLife(server)) {
                    return fail(sp, Component.translatable("luckyxp.merchant.msg.lives_cap", LuckyTweaksApi.getBoughtLivesCap()));
                }
                int left = LuckyTweaksApi.buySharedLife(server);
                msg(sp, Component.translatable("luckyxp.merchant.msg.life_bought", left), ChatFormatting.RED);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean fail(ServerPlayer sp, Component text) {
        msg(sp, text, ChatFormatting.RED);
        return false;
    }

    /** Action-bar feedback. Translatable components resolve on the CLIENT, so every player reads the
     *  merchant in their own language (keys in luckyxp's en_us/fr_fr lang files). */
    private static void msg(ServerPlayer sp, Component text, ChatFormatting colour) {
        sp.displayClientMessage(text.copy().withStyle(colour), true);
    }

    /** The machine type's GUI label key — reused for the convert message so it says "CONSUMABLES",
     *  not the raw enum name. */
    private static String typeKey(MachineType t) {
        return switch (t) {
            case POTIONS -> "luckyxp.machine.type.consumables";
            case INFUSED_LB -> "luckyxp.machine.type.lucky_blocks";
            case ORES -> "luckyxp.machine.type.minerals";
            case TOOLS -> "luckyxp.machine.type.tools";
        };
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
