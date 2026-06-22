package com.lwi.luckyxp.command;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.event.LuckyEvent;
import com.lwi.luckyxp.event.LuckyEventManager;
import com.lwi.luckyxp.event.LuckyEventType;
import com.mojang.brigadier.Command;
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
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;

/**
 * DEV/TEST command {@code /luckyevent} (op 2): start, stop or inspect the single global Lucky XP event,
 * bypassing the day/night auto-trigger (added in a later tranche). {@code all} = mega jackpot (every
 * lucky block). Default durations mirror the design: 6 min for a single-block event, 4 min for a jackpot.
 *
 * <ul>
 *   <li>{@code /luckyevent start} — random type, random block</li>
 *   <li>{@code /luckyevent start xp [all|<block>] [seconds] [multiplier]}</li>
 *   <li>{@code /luckyevent start luck [<amount> [all|<block>] [seconds]]}</li>
 *   <li>{@code /luckyevent stop} · {@code /luckyevent status}</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID)
public final class LuckyEventCommand {
    private static final int NORMAL_TICKS = 6 * 60 * 20;   // 6 min (single-block event)
    private static final int JACKPOT_TICKS = 4 * 60 * 20;  // 4 min (mega jackpot)
    private static final int DEFAULT_XP_MULTIPLIER = 2;
    private static final int[] LUCK_TIERS = {10, 30, 50, 70, 90, 100};
    private static final int MAX_SECONDS = 24 * 60 * 60;

    /** Tab-completes the {@code <block>} argument with the registered lucky block ids. */
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
                .then(Commands.literal("start")
                        .executes(ctx -> startRandom(ctx.getSource()))
                        // --- start xp [all|<block>] [seconds] [multiplier] ---
                        .then(Commands.literal("xp")
                                .executes(ctx -> startRandomBlock(ctx.getSource(), LuckyEventType.DOUBLE_XP, DEFAULT_XP_MULTIPLIER, 0))
                                .then(Commands.literal("all")
                                        .executes(ctx -> start(ctx.getSource(), LuckyEventType.DOUBLE_XP, null, DEFAULT_XP_MULTIPLIER, 0))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_SECONDS))
                                                .executes(ctx -> start(ctx.getSource(), LuckyEventType.DOUBLE_XP, null, DEFAULT_XP_MULTIPLIER, secs(ctx)))
                                                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, 100))
                                                        .executes(ctx -> start(ctx.getSource(), LuckyEventType.DOUBLE_XP, null, mult(ctx), secs(ctx))))))
                                .then(Commands.argument("block", ResourceLocationArgument.id()).suggests(LUCKY_BLOCKS)
                                        .executes(ctx -> startXpBlock(ctx, DEFAULT_XP_MULTIPLIER, 0))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_SECONDS))
                                                .executes(ctx -> startXpBlock(ctx, DEFAULT_XP_MULTIPLIER, secs(ctx)))
                                                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, 100))
                                                        .executes(ctx -> startXpBlock(ctx, mult(ctx), secs(ctx)))))))
                        // --- start luck [<amount> [all|<block>] [seconds]] ---
                        .then(Commands.literal("luck")
                                .executes(ctx -> startRandomBlock(ctx.getSource(), LuckyEventType.LUCK, randomLuckTier(ctx.getSource()), 0))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> startRandomBlock(ctx.getSource(), LuckyEventType.LUCK, amount(ctx), 0))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> start(ctx.getSource(), LuckyEventType.LUCK, null, amount(ctx), 0))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_SECONDS))
                                                        .executes(ctx -> start(ctx.getSource(), LuckyEventType.LUCK, null, amount(ctx), secs(ctx)))))
                                        .then(Commands.argument("block", ResourceLocationArgument.id()).suggests(LUCKY_BLOCKS)
                                                .executes(ctx -> startLuckBlock(ctx, 0))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_SECONDS))
                                                        .executes(ctx -> startLuckBlock(ctx, secs(ctx)))))))));
    }

    // ---- argument shorthands ----
    private static int secs(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "seconds");
    }

    private static int mult(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "multiplier");
    }

    private static int amount(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "amount");
    }

    // ---- block-targeted starts (validate the id is a real lucky block) ----
    private static int startXpBlock(CommandContext<CommandSourceStack> ctx, int multiplier, int seconds) {
        ResourceLocation block = ResourceLocationArgument.getId(ctx, "block");
        if (!validateBlock(ctx.getSource(), block)) {
            return 0;
        }
        return start(ctx.getSource(), LuckyEventType.DOUBLE_XP, block, multiplier, seconds);
    }

    private static int startLuckBlock(CommandContext<CommandSourceStack> ctx, int seconds) {
        ResourceLocation block = ResourceLocationArgument.getId(ctx, "block");
        if (!validateBlock(ctx.getSource(), block)) {
            return 0;
        }
        return start(ctx.getSource(), LuckyEventType.LUCK, block, amount(ctx), seconds);
    }

    // ---- random helpers ----
    private static int startRandom(CommandSourceStack src) {
        boolean xp = src.getLevel().getRandom().nextBoolean();
        LuckyEventType type = xp ? LuckyEventType.DOUBLE_XP : LuckyEventType.LUCK;
        int magnitude = xp ? DEFAULT_XP_MULTIPLIER : randomLuckTier(src);
        return startRandomBlock(src, type, magnitude, 0);
    }

    private static int startRandomBlock(CommandSourceStack src, LuckyEventType type, int magnitude, int seconds) {
        ResourceLocation block = randomBlock(src);
        if (block == null) {
            src.sendFailure(Component.literal("No lucky blocks are registered."));
            return 0;
        }
        return start(src, type, block, magnitude, seconds);
    }

    @Nullable
    private static ResourceLocation randomBlock(CommandSourceStack src) {
        List<ResourceLocation> ids = LuckyTweaksApi.getLuckyBlockIds();
        if (ids.isEmpty()) {
            return null;
        }
        return ids.get(src.getLevel().getRandom().nextInt(ids.size()));
    }

    private static int randomLuckTier(CommandSourceStack src) {
        return LUCK_TIERS[src.getLevel().getRandom().nextInt(LUCK_TIERS.length)];
    }

    // ---- core ----
    private static int start(CommandSourceStack src, LuckyEventType type, @Nullable ResourceLocation block,
                             int magnitude, int seconds) {
        MinecraftServer server = src.getServer();
        int ticks = seconds > 0 ? seconds * 20 : (block == null ? JACKPOT_TICKS : NORMAL_TICKS);
        LuckyEvent ev = new LuckyEvent(type, block, magnitude);
        LuckyEventManager.get(server).start(server, ev, ticks);
        src.sendSuccess(() -> Component.literal("Lucky event started: " + describe(ev) + " (" + mmss(ticks) + ")")
                .withStyle(type.color), true);
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
        src.sendSuccess(() -> Component.literal(describe(ev) + " — " + mmss(mgr.ticksRemaining()) + " left")
                .withStyle(ev.type().color), false);
        return Command.SINGLE_SUCCESS;
    }

    // ---- formatting ----
    private static String describe(LuckyEvent ev) {
        String target = ev.isJackpot() ? "ALL blocks (MEGA JACKPOT)" : ev.blockId().toString();
        if (ev.type() == LuckyEventType.DOUBLE_XP) {
            return "Double XP x" + ev.magnitude() + " on " + target;
        }
        return "Luck +" + ev.magnitude() + "% on " + target;
    }

    private static boolean validateBlock(CommandSourceStack src, ResourceLocation block) {
        if (!LuckyTweaksApi.getLuckyBlockIds().contains(block)) {
            src.sendFailure(Component.literal("Not a lucky block: " + block + " (tab-complete an id, or use 'all')."));
            return false;
        }
        return true;
    }

    private static String mmss(int ticks) {
        int total = Math.max(0, ticks) / 20;
        int m = total / 60;
        int s = total % 60;
        return m + ":" + (s < 10 ? "0" + s : s);
    }
}
