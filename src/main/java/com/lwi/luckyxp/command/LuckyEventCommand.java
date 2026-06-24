package com.lwi.luckyxp.command;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.event.LuckyBlockShower;
import com.lwi.luckyxp.event.LuckyEvent;
import com.lwi.luckyxp.event.LuckyEvent.Scope;
import com.lwi.luckyxp.event.LuckyEventManager;
import com.lwi.luckyxp.event.LuckyEventType;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;

/**
 * DEV/TEST command {@code /luckyevent} (op 2) for the design-v4 events (block apparition). Decides an
 * outcome up front (roll 1 = scope, roll 2 = value), plays the reveal, then the blocks appear.
 *
 * <ul>
 *   <li>{@code start} — fully random outcome (~5% RIEN, ~5% JACKPOT, else single ; value rolled)</li>
 *   <li>{@code start xp [all|&lt;block&gt;] [mult]} · {@code start luck [all|&lt;block&gt;] [percent]}</li>
 *   <li>{@code start nothing} · {@code preview [same args]} (roulette only, no blocks, no gate) · {@code stop} · {@code status}</li>
 *   <li>{@code shower …} — spawn blocks directly (skip the roulette), to feel the apparition</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID)
public final class LuckyEventCommand {
    private static final int REVEAL_TICKS = 410;     // ~20.5s : 5s hype + 2 rolls, each with a read pause, + held result
    private static final int JACKPOT_COUNT = 20;

    private static final SuggestionProvider<CommandSourceStack> LUCKY_BLOCKS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    LuckyTweaksApi.getLuckyBlockIds().stream().map(ResourceLocation::toString), builder);

    private LuckyEventCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("luckyevent")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
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

    // ---- value / count rolls ----
    private static float rollXpMult(RandomSource rng) {
        int r = rng.nextInt(100);
        return r < 30 ? 1.5F : (r < 90 ? 2.0F : 4.0F);          // 30% / 60% / 10%
    }

    private static int rollLuckPercent(RandomSource rng) {
        int r = rng.nextInt(100);
        if (r < 20) return 10;
        if (r < 45) return 30;
        if (r < 65) return 50;
        if (r < 80) return 70;
        if (r < 90) return 90;
        return 100;                                             // ~10% mega
    }

    private static int singleCount(RandomSource rng) {
        return 5 + rng.nextInt(6);                              // 5-10 (hidden)
    }

    // ---- start (roulette) ----
    private static int startRandom(CommandSourceStack src, boolean pv) {
        RandomSource rng = src.getLevel().getRandom();
        boolean xp = rng.nextBoolean();
        LuckyEventType type = xp ? LuckyEventType.DOUBLE_XP : LuckyEventType.LUCK;
        int roll = rng.nextInt(100);
        if (roll < 5) {
            return startEvent(src, LuckyEvent.nothing(type), pv);
        }
        Scope scope = roll < 10 ? Scope.JACKPOT : Scope.SINGLE;
        ResourceLocation block = scope == Scope.SINGLE ? randomBlock(src) : null;
        if (scope == Scope.SINGLE && block == null) {
            scope = Scope.JACKPOT;                              // no lucky block here -> jackpot
        }
        int count = scope == Scope.JACKPOT ? JACKPOT_COUNT : singleCount(rng);
        LuckyEvent ev = xp ? LuckyEvent.xp(scope, block, rollXpMult(rng), count)
                : LuckyEvent.luck(scope, block, rollLuckPercent(rng), count);
        return startEvent(src, ev, pv);
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
            return startEvent(src, LuckyEvent.nothing(LuckyEventType.LUCK), pv);   // 0% = miss
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
        LuckyEventManager mgr = LuckyEventManager.get(src.getServer());
        LuckyEvent ev = mgr.active();
        if (ev == null) {
            src.sendSuccess(() -> Component.literal("No Lucky event is active.").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        src.sendSuccess(() -> Component.literal(describe(ev) + " — revealing...").withStyle(ev.type().color), false);
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
        src.sendSuccess(() -> Component.literal("Shower LUCK +" + amount + "% : " + tgt + (mega ? "  [MEGA]" : ""))
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
    @Nullable
    private static ResourceLocation randomBlock(CommandSourceStack src) {
        ResourceLocation dim = src.getLevel().dimension().location();
        List<ResourceLocation> ids = LuckyTweaksApi.getLuckyBlockIds(dim);
        if (ids.isEmpty()) {
            ids = LuckyTweaksApi.getLuckyBlockIds();
        }
        if (ids.isEmpty()) {
            return null;
        }
        return ids.get(src.getLevel().getRandom().nextInt(ids.size()));
    }

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
        String val = ev.type() == LuckyEventType.DOUBLE_XP ? ("x" + ev.xpMult() + " XP") : ("+" + ev.luckPercent() + "%");
        return val + " sur " + scope + (ev.isMega() ? "  [MEGA JACKPOT]" : "");
    }
}
