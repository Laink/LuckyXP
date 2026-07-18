package com.lwi.luckyxp.command;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.LuckyXpCommonConfig;
import com.lwi.luckyxp.event.EventRolls;
import com.lwi.luckyxp.event.LuckyBlockShower;
import com.lwi.luckyxp.event.LuckyEvent;
import com.lwi.luckyxp.event.LuckyEvent.Scope;
import com.lwi.luckyxp.event.LuckyEventManager;
import com.lwi.luckyxp.event.LuckyEventScheduler;
import com.lwi.luckyxp.event.LuckyEventType;
import com.lwi.luckyxp.api.LuckyXpApi;
import com.lwi.luckyxp.machine.MachineType;
import com.lwi.luckyxp.machine.Rarity;
import com.lwi.luckyxp.machine.VendingMachineBlockEntity;
import com.lwi.luckyxp.worldgen.VendingStandFeature;
import com.lwi.luckyxp.xp.LuckyXpData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import com.lwi.luckyxp.Registration;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;

/**
 * DEV/TEST command {@code /luckyevent} (op 2) for the design-v4 events (block apparition). Decides an
 * outcome up front (roll 1 = scope, roll 2 = value), plays the reveal, then the blocks appear.
 *
 * <ul>
 *   <li>{@code start} — fully random outcome (~5% RIEN, ~5% JACKPOT, else single ; value rolled)</li>
 *   <li>{@code start xp [all|&lt;block&gt;] [mult]} · {@code start luck [all|&lt;block&gt;] [percent]}</li>
 *   <li>{@code start nothing} · {@code preview [same args]} (roulette only, no blocks, no gate) · {@code stop} · {@code status}</li>
 *   <li>{@code roll} — force TODAY'S daily auto-roll now (chance + pity, consumes the day; repeatable for testing streaks)</li>
 *   <li>{@code shower …} — spawn blocks directly (skip the roulette), to feel the apparition</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID)
public final class LuckyEventCommand {
    private static final int REVEAL_TICKS = LuckyEventManager.REVEAL_TICKS;
    private static final int JACKPOT_COUNT = EventRolls.JACKPOT_COUNT;

    private static final SuggestionProvider<CommandSourceStack> LUCKY_BLOCKS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    LuckyTweaksApi.getLuckyBlockIds().stream().map(ResourceLocation::toString), builder);

    private static final SuggestionProvider<CommandSourceStack> RARITIES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(Rarity.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), builder);

    private LuckyEventCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // Op level 2 (cheats) gate: /luckyevent is the admin/test interface for the event and stand
        // systems (spawn stands, grant levels, force events). Events fire on their own for players, so
        // it is never needed in normal play. It was briefly left ungated so a non-op LAN designer could
        // drive it during the cosmetic pass; re-gated for release so it stays off a real server.
        event.getDispatcher().register(Commands.literal("luckyevent")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("roll").executes(ctx -> forceRoll(ctx.getSource())))
                // --- machine : force the looked-at vending machine to a rarity + re-roll its stock (test) ---
                .then(Commands.literal("machine")
                        .then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
                                .executes(ctx -> devMachine(ctx.getSource(), StringArgumentType.getString(ctx, "rarity")))))
                // --- merchant : spawn a service merchant bound to the looked-at machine (test). Optional
                // rarity forces his hat + discount; without it he rolls one like a natural merchant. ---
                .then(Commands.literal("merchant")
                        .executes(ctx -> devMerchant(ctx.getSource(), null))
                        .then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
                                .executes(ctx -> devMerchant(ctx.getSource(), StringArgumentType.getString(ctx, "rarity")))))
                // dev: grant whole Lucky levels (the machine/merchant currency), for economy testing
                .then(Commands.literal("levels")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000))
                                .executes(ctx -> devLevels(ctx.getSource(), intArg(ctx, "amount")))))
                // dev: build the whole stand (structure + machine + merchant) in front of the player
                .then(Commands.literal("stand")
                        .executes(ctx -> devStand(ctx.getSource(), null))
                        .then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
                                .executes(ctx -> devStand(ctx.getSource(), StringArgumentType.getString(ctx, "rarity")))))
                .then(outcomeArgs(Commands.literal("start"), false))     // real event (shower + End/dragon gate)
                .then(outcomeArgs(Commands.literal("preview"), true))    // roulette only, no effect, no gate
                // --- shower : test direct de l'apparition (saute la roulette) ---
                .then(Commands.literal("shower")
                        .then(Commands.literal("luck")
                                .then(Commands.literal("all")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> showerLuck(ctx.getSource(), null, intArg(ctx, "amount"), -1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> showerLuck(ctx.getSource(), null, intArg(ctx, "amount"), intArg(ctx, "count"))))))
                                .then(Commands.argument("block", ResourceLocationArgument.id()).suggests(LUCKY_BLOCKS)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> showerLuckBlock(ctx, -1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> showerLuckBlock(ctx, intArg(ctx, "count")))))))
                        .then(Commands.literal("xp")
                                .then(Commands.literal("all")
                                        .then(Commands.argument("mult", DoubleArgumentType.doubleArg(1.0, 10.0))
                                                .executes(ctx -> showerXp(ctx.getSource(), null, dbl(ctx), -1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> showerXp(ctx.getSource(), null, dbl(ctx), intArg(ctx, "count"))))))
                                .then(Commands.argument("block", ResourceLocationArgument.id()).suggests(LUCKY_BLOCKS)
                                        .then(Commands.argument("mult", DoubleArgumentType.doubleArg(1.0, 10.0))
                                                .executes(ctx -> showerXpBlock(ctx, -1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> showerXpBlock(ctx, intArg(ctx, "count")))))))));
    }

    /** The shared outcome argument tree ({@code [nothing | xp … | luck …]}), attached to both {@code start}
     *  (preview=false: real event) and {@code preview} (preview=true: roulette only, no blocks, no gate). */
    private static LiteralArgumentBuilder<CommandSourceStack> outcomeArgs(LiteralArgumentBuilder<CommandSourceStack> node, boolean pv) {
        return node
                .executes(ctx -> startRandom(ctx.getSource(), pv))
                .then(Commands.literal("nothing").executes(ctx -> startNothing(ctx.getSource(), pv)))
                .then(Commands.literal("xp")
                        .then(Commands.literal("all")
                                .executes(ctx -> startXp(ctx.getSource(), Scope.JACKPOT, null, -1.0, pv))
                                .then(Commands.argument("mult", DoubleArgumentType.doubleArg(1.0, 10.0))
                                        .executes(ctx -> startXp(ctx.getSource(), Scope.JACKPOT, null, dbl(ctx), pv))))
                        .then(Commands.argument("block", ResourceLocationArgument.id()).suggests(LUCKY_BLOCKS)
                                .executes(ctx -> startXpBlock(ctx, -1.0, pv))
                                .then(Commands.argument("mult", DoubleArgumentType.doubleArg(1.0, 10.0))
                                        .executes(ctx -> startXpBlock(ctx, dbl(ctx), pv)))))
                .then(Commands.literal("luck")
                        .then(Commands.literal("all")
                                .executes(ctx -> startLuck(ctx.getSource(), Scope.JACKPOT, null, -1, pv))
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> startLuck(ctx.getSource(), Scope.JACKPOT, null, intArg(ctx, "percent"), pv))))
                        .then(Commands.argument("block", ResourceLocationArgument.id()).suggests(LUCKY_BLOCKS)
                                .executes(ctx -> startLuckBlock(ctx, -1, pv))
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> startLuckBlock(ctx, intArg(ctx, "percent"), pv)))));
    }

    // ---- arg helpers ----
    private static int intArg(CommandContext<CommandSourceStack> ctx, String name) {
        return IntegerArgumentType.getInteger(ctx, name);
    }

    private static double dbl(CommandContext<CommandSourceStack> ctx) {
        return DoubleArgumentType.getDouble(ctx, "mult");
    }

    // ---- machine test hook ----
    /** Set the vending machine the player is looking at to {@code rarityName} and re-roll its stock,
     *  so all four rarities can be inspected without worldgen. Dev-only; op-gated with the rest. */
    private static int devMachine(CommandSourceStack src, String rarityName) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("Players only."));
            return 0;
        }
        Rarity rarity;
        try {
            rarity = Rarity.valueOf(rarityName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Unknown rarity '" + rarityName + "' (common/rare/epic/legendary)."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(20.0D));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            src.sendFailure(Component.literal("Look at a vending machine first."));
            return 0;
        }
        BlockEntity be = level.getBlockEntity(hit.getBlockPos());
        if (!(be instanceof VendingMachineBlockEntity)) {
            be = level.getBlockEntity(hit.getBlockPos().below());   // only the lower half holds the BE
        }
        if (!(be instanceof VendingMachineBlockEntity machine)) {
            src.sendFailure(Component.literal("That block is not a vending machine."));
            return 0;
        }
        machine.devReroll(rarity, level);
        final Rarity r = rarity;
        src.sendSuccess(() -> Component.literal("Machine set to " + r.name() + " and re-rolled — reopen it.")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Test helper: spawn a {@link com.lwi.luckyxp.entity.LuckyMerchant} next to the machine the
     *  player is looking at, bound to it (worldgen normally does this at the stand). */
    private static int devMerchant(CommandSourceStack src, @Nullable String rarityName) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(20.0D));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            src.sendFailure(Component.literal("Look at a vending machine first."));
            return 0;
        }
        BlockPos machinePos = hit.getBlockPos();
        if (!(level.getBlockEntity(machinePos) instanceof VendingMachineBlockEntity)) {
            machinePos = machinePos.below();
            if (!(level.getBlockEntity(machinePos) instanceof VendingMachineBlockEntity)) {
                src.sendFailure(Component.literal("That block is not a vending machine."));
                return 0;
            }
        }
        com.lwi.luckyxp.entity.LuckyMerchant merchant = Registration.LUCKY_MERCHANT.get().create(level);
        if (merchant == null) {
            src.sendFailure(Component.literal("Could not create the merchant."));
            return 0;
        }
        // Rarity: forced when asked (so the hat of each tier can be checked on demand), rolled on the
        // worldgen weights otherwise -- exactly what a naturally generated merchant gets.
        Rarity rarity;
        if (rarityName == null) {
            rarity = Rarity.roll(level.random, new int[]{
                    LuckyXpCommonConfig.COMMON.weightCommon.get(),
                    LuckyXpCommonConfig.COMMON.weightRare.get(),
                    LuckyXpCommonConfig.COMMON.weightEpic.get(),
                    LuckyXpCommonConfig.COMMON.weightLegendary.get()});
        } else {
            try {
                rarity = Rarity.valueOf(rarityName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                src.sendFailure(Component.literal("Unknown rarity: " + rarityName));
                return 0;
            }
        }
        BlockPos at = machinePos.relative(player.getDirection().getClockWise());
        merchant.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
        merchant.setMachinePos(machinePos);
        merchant.setRarity(rarity);
        level.addFreshEntity(merchant);
        Rarity shown = rarity;
        int cut = Math.round(shown.merchantDiscount() * 100.0F);
        src.sendSuccess(() -> Component.literal("Merchant spawned, bound to the machine: " + shown
                        + (cut > 0 ? " (-" + cut + "% on every service)" : " (full price)"))
                .withStyle(shown.color), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Test helper: grant exactly {@code levels} whole Lucky levels (tops up the partial progress
     *  first), so the economy can be tested in survival without farming lucky blocks. */
    private static int devLevels(CommandSourceStack src, int levels) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        int cur = LuckyXpData.getLevel(player);
        long needed = -LuckyXpData.getInto(player);
        for (int i = 0; i < levels; i++) {
            needed += LuckyXpData.xpToNext(cur + i);
        }
        LuckyXpApi.addXp(player, (int) Math.max(0, needed));
        final int now = LuckyXpData.getLevel(player);
        src.sendSuccess(() -> Component.literal("+" + levels + " Lucky levels (now level " + now + ")")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Test helper: build the whole vending stand (structure + machine + bound merchant) a few
     *  blocks in front of the player, snapped to the surface, skipping the worldgen density roll.
     *  Rarity optional — without it, the real config weights roll, like worldgen. */
    private static int devStand(CommandSourceStack src, @Nullable String rarityName) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Rarity rarity;
        if (rarityName == null) {
            rarity = Rarity.roll(level.random, new int[]{
                    LuckyXpCommonConfig.COMMON.weightCommon.get(),
                    LuckyXpCommonConfig.COMMON.weightRare.get(),
                    LuckyXpCommonConfig.COMMON.weightEpic.get(),
                    LuckyXpCommonConfig.COMMON.weightLegendary.get()});
        } else {
            try {
                rarity = Rarity.valueOf(rarityName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                src.sendFailure(Component.literal("Unknown rarity '" + rarityName + "' (common/rare/epic/legendary)."));
                return 0;
            }
        }
        MachineType type = MachineType.values()[level.random.nextInt(MachineType.values().length)];
        // centre the 5-wide stall a few blocks ahead, snapped to the surface (the stand always
        // faces north, like worldgen — walk around it if needed)
        BlockPos base = player.blockPosition().relative(player.getDirection(), 5);
        BlockPos o = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, base).offset(-2, 0, 0);
        boolean clean = VendingStandFeature.suitable(level, o);
        VendingStandFeature.build(level, o, rarity, type);
        final Rarity r = rarity;
        final String note = clean ? "" : " — spot would have been REJECTED by worldgen (uneven/wet/canopy), built anyway";
        src.sendSuccess(() -> Component.literal("Stand built: " + r.name() + " " + type.name() + note)
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    // ---- value / count rolls (shared with the daily auto-trigger) ----
    private static float rollXpMult(RandomSource rng) {
        return EventRolls.rollXpMult(rng);
    }

    private static int rollLuckPercent(RandomSource rng) {
        return EventRolls.rollLuckPercent(rng);
    }

    private static int singleCount(RandomSource rng) {
        return EventRolls.singleCount(rng);
    }

    // ---- start (roulette) ----
    private static int startRandom(CommandSourceStack src, boolean pv) {
        return startEvent(src, EventRolls.rollOutcome(src.getLevel()), pv);
    }

    private static int startNothing(CommandSourceStack src, boolean pv) {
        LuckyEventType type = src.getLevel().getRandom().nextBoolean() ? LuckyEventType.DOUBLE_XP : LuckyEventType.LUCK;
        return startEvent(src, LuckyEvent.nothing(type), pv);
    }

    private static int startXp(CommandSourceStack src, Scope scope, @Nullable ResourceLocation block, double mult, boolean pv) {
        RandomSource rng = src.getLevel().getRandom();
        float m = mult > 0 ? (float) mult : rollXpMult(rng);
        int count = scope == Scope.JACKPOT ? JACKPOT_COUNT : singleCount(rng);
        return startEvent(src, LuckyEvent.xp(scope, block, m, count), pv);
    }

    private static int startLuck(CommandSourceStack src, Scope scope, @Nullable ResourceLocation block, int percent, boolean pv) {
        RandomSource rng = src.getLevel().getRandom();
        int p = percent >= 0 ? percent : rollLuckPercent(rng);
        if (p <= 0) {
            return startEvent(src, LuckyEvent.nothing(LuckyEventType.LUCK), pv);   // 0 = miss
        }
        int count = scope == Scope.JACKPOT ? JACKPOT_COUNT : singleCount(rng);
        return startEvent(src, LuckyEvent.luck(scope, block, p, count), pv);
    }

    private static int startXpBlock(CommandContext<CommandSourceStack> ctx, double mult, boolean pv) {
        ResourceLocation block = ResourceLocationArgument.getId(ctx, "block");
        if (!validateBlock(ctx.getSource(), block)) {
            return 0;
        }
        return startXp(ctx.getSource(), Scope.SINGLE, block, mult, pv);
    }

    private static int startLuckBlock(CommandContext<CommandSourceStack> ctx, int percent, boolean pv) {
        ResourceLocation block = ResourceLocationArgument.getId(ctx, "block");
        if (!validateBlock(ctx.getSource(), block)) {
            return 0;
        }
        return startLuck(ctx.getSource(), Scope.SINGLE, block, percent, pv);
    }

    private static int startEvent(CommandSourceStack src, LuckyEvent ev, boolean pv) {
        MinecraftServer server = src.getServer();
        if (!pv) {
            String blocked = LuckyEventManager.startBlockReason(server);
            if (blocked != null) {
                src.sendFailure(Component.literal("Cannot start a Lucky event: " + blocked + "."));
                return 0;
            }
        }
        long seed = src.getLevel().getRandom().nextLong();
        LuckyEventManager.get(server).start(server, ev, REVEAL_TICKS, pv, seed);
        if (pv) {
            src.sendSuccess(() -> Component.literal("Preview (no effect): " + describe(ev)).withStyle(ChatFormatting.GRAY), false);
        } else {
            src.sendSuccess(() -> Component.literal("Lucky event: " + describe(ev)).withStyle(ev.type().color), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int stop(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        LuckyEventManager mgr = LuckyEventManager.get(server);
        if (!mgr.hasActive()) {
            src.sendFailure(Component.literal("No Lucky event is active."));
            return 0;
        }
        mgr.stop(server);
        src.sendSuccess(() -> Component.literal("Lucky event stopped.").withStyle(ChatFormatting.GRAY), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        LuckyEventManager mgr = LuckyEventManager.get(server);
        LuckyEvent ev = mgr.active();
        if (ev == null) {
            src.sendSuccess(() -> Component.literal("No Lucky event is active.").withStyle(ChatFormatting.GRAY), false);
        } else {
            src.sendSuccess(() -> Component.literal(describe(ev) + " — revealing...").withStyle(ev.type().color), false);
        }
        String auto;
        if (!LuckyXpCommonConfig.COMMON.autoEvents.get()) {
            auto = "OFF";
        } else {
            long day = server.overworld().getDayTime() / 24000L;
            auto = (int) Math.round(LuckyXpCommonConfig.COMMON.chancePerDay.get() * 100) + "%/day, pity "
                    + LuckyXpCommonConfig.COMMON.pityDays.get()
                    + (mgr.lastRolledDay() >= day ? ", today: rolled" : ", today: pending")
                    + ", dry days: " + mgr.dryDays();
        }
        String autoLine = "Auto events: " + auto;
        src.sendSuccess(() -> Component.literal(autoLine).withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Force TODAY'S daily auto-roll now (testing): same chance + pity path, consumes the day, repeatable. */
    private static int forceRoll(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        LuckyEventManager mgr = LuckyEventManager.get(server);
        if (mgr.hasActive()) {
            src.sendFailure(Component.literal("An event reveal is already running."));
            return 0;
        }
        String blocked = LuckyEventManager.startBlockReason(server);
        if (blocked != null) {
            src.sendFailure(Component.literal("Cannot start a Lucky event: " + blocked + "."));
            return 0;
        }
        ServerLevel overworld = server.overworld();
        long day = overworld.getDayTime() / 24000L;
        boolean fired = LuckyEventScheduler.rollDay(server, overworld, mgr, day);
        String msg = fired ? "Daily roll: event fired!" : "Daily roll: no event (" + mgr.dryDays() + " dry day(s))";
        src.sendSuccess(() -> Component.literal(msg).withStyle(fired ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        return Command.SINGLE_SUCCESS;
    }

    // ---- shower (direct apparition test) ----
    private static int showerCount(CommandSourceStack src, boolean jackpot, int explicit) {
        if (explicit > 0) {
            return explicit;
        }
        return jackpot ? JACKPOT_COUNT : singleCount(src.getLevel().getRandom());
    }

    private static int showerLuck(CommandSourceStack src, @Nullable ResourceLocation block, int amount, int explicit) {
        int count = showerCount(src, block == null, explicit);
        boolean mega = amount >= 100;
        LuckyBlockShower.shower(src.getServer(), block, false, amount, 0.0F, count, mega);
        String tgt = block == null ? count + " blocs varies" : count + " x " + block;
        src.sendSuccess(() -> Component.literal("Shower LUCK +" + amount + " : " + tgt + (mega ? "  [MEGA]" : ""))
                .withStyle(ChatFormatting.GOLD), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int showerLuckBlock(CommandContext<CommandSourceStack> ctx, int explicit) {
        ResourceLocation block = ResourceLocationArgument.getId(ctx, "block");
        if (!validateBlock(ctx.getSource(), block)) {
            return 0;
        }
        return showerLuck(ctx.getSource(), block, intArg(ctx, "amount"), explicit);
    }

    private static int showerXp(CommandSourceStack src, @Nullable ResourceLocation block, double mult, int explicit) {
        int count = showerCount(src, block == null, explicit);
        boolean mega = mult >= 4.0;
        LuckyBlockShower.shower(src.getServer(), block, true, 0, (float) mult, count, mega);
        String tgt = block == null ? count + " blocs varies" : count + " x " + block;
        src.sendSuccess(() -> Component.literal("Shower XP x" + mult + " : " + tgt + (mega ? "  [MEGA]" : ""))
                .withStyle(ChatFormatting.AQUA), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int showerXpBlock(CommandContext<CommandSourceStack> ctx, int explicit) {
        ResourceLocation block = ResourceLocationArgument.getId(ctx, "block");
        if (!validateBlock(ctx.getSource(), block)) {
            return 0;
        }
        return showerXp(ctx.getSource(), block, dbl(ctx), explicit);
    }

    // ---- shared ----
    private static boolean validateBlock(CommandSourceStack src, ResourceLocation block) {
        if (!LuckyTweaksApi.getLuckyBlockIds().contains(block)) {
            src.sendFailure(Component.literal("Not a lucky block: " + block + " (tab-complete an id, or use 'all')."));
            return false;
        }
        return true;
    }

    private static String describe(LuckyEvent ev) {
        if (ev.isNothing()) {
            return "RIEN (miss)";
        }
        String scope = ev.isJackpot() ? "TOUS (JACKPOT)" : String.valueOf(ev.blockId());
        String val = ev.type() == LuckyEventType.DOUBLE_XP ? ("x" + ev.xpMult() + " XP") : ("+" + ev.luckPercent());
        return val + " sur " + scope + (ev.isMega() ? "  [MEGA JACKPOT]" : "");
    }
}
