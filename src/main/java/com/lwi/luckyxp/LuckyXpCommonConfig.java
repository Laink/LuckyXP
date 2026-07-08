package com.lwi.luckyxp;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * COMMON config ({@code config/luckyxp-common.toml}), read live through the spec:
 * <ul>
 *   <li>{@code [events]} — the daily automatic Lucky events: whether they run, the per-day chance,
 *       the pity counter and the morning trigger window (in day-time ticks; dawn = 0, 1200 ticks =
 *       1 in-game minute);</li>
 *   <li>{@code [machines]} — vending-machine worldgen: the rarity weights rolled when a stand
 *       generates (relative weights, not percents; 0 disables a rarity);</li>
 *   <li>{@code [xp]} — how much Lucky XP a broken lucky block pays, the machines' currency.</li>
 * </ul>
 */
public final class LuckyXpCommonConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    private LuckyXpCommonConfig() {}

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue autoEvents;
        public final ForgeConfigSpec.IntValue firstEventDay;
        public final ForgeConfigSpec.DoubleValue chancePerDay;
        public final ForgeConfigSpec.IntValue pityDays;
        public final ForgeConfigSpec.IntValue windowStart;
        public final ForgeConfigSpec.IntValue windowEnd;

        public final ForgeConfigSpec.IntValue standChance;
        public final ForgeConfigSpec.IntValue weightCommon;
        public final ForgeConfigSpec.IntValue weightRare;
        public final ForgeConfigSpec.IntValue weightEpic;
        public final ForgeConfigSpec.IntValue weightLegendary;

        public final ForgeConfigSpec.IntValue baseXp;
        public final ForgeConfigSpec.IntValue legendaryMultiplier;
        public final ForgeConfigSpec.IntValue legendaryXpDelayTicks;

        Common(ForgeConfigSpec.Builder b) {
            b.comment("Daily automatic Lucky events (the case-opening roulette that makes lucky blocks appear).")
                    .push("events");
            autoEvents = b.comment("Roll once per in-game day for a Lucky event, at a random moment inside the morning window below.")
                    .define("autoEvents", true);
            firstEventDay = b.comment("First in-game day on which a Lucky event may trigger (0 = the world's very first day). Earlier days are simply skipped: no roll, no pity credit - lets new players find their feet first.")
                    .defineInRange("firstEventDay", 1, 0, 1000);
            chancePerDay = b.comment("Chance that a given day gets an event (0.333 = one day in three).")
                    .defineInRange("chancePerDay", 0.333, 0.0, 1.0);
            pityDays = b.comment("After this many consecutive event-less days, the next day's event is guaranteed (0 = an event every day).")
                    .defineInRange("pityDays", 3, 0, 100);
            windowStart = b.comment("Earliest moment of the day the roulette may start, in day-time ticks after dawn (1200 = 1 in-game minute after dawn).")
                    .defineInRange("windowStart", 1200, 0, 23999);
            windowEnd = b.comment("Latest moment of the day the roulette may start, in day-time ticks after dawn. Kept in the morning so the spawned blocks can be opened before night.")
                    .defineInRange("windowEnd", 4800, 1, 24000);
            b.pop();

            b.comment("Vending-machine worldgen (the market stands).")
                    .push("machines");
            standChance = b.comment(
                            "One stand attempted per this many chunks (1 = every chunk, 0/1 disables the filter).",
                            "Terrain may still reject a stand, so the real rate is slightly lower.",
                            "Calibrated on a measured 1467 chunks explored per hour: at 500, a stand shows up every",
                            "20 minutes and is worth ~283 Lucky XP of exploration -- exactly the price of a dear line.",
                            "Lower it while testing (60 puts one almost everywhere); the shipped value is what matters.")
                    .defineInRange("standChance", 500, 1, 100000);
            weightCommon = b.comment("Relative weight of a COMMON machine when a stand generates (weights, not percents; 0 disables the rarity).")
                    .defineInRange("weightCommon", 59, 0, 1000);
            weightRare = b.comment("Relative weight of a RARE machine.")
                    .defineInRange("weightRare", 30, 0, 1000);
            weightEpic = b.comment("Relative weight of an EPIC machine.")
                    .defineInRange("weightEpic", 10, 0, 1000);
            weightLegendary = b.comment(
                            "Relative weight of a LEGENDARY machine. A stand also rolls one of the four machine",
                            "TYPES uniformly, so a legendary of a given type is four times rarer still -- that is",
                            "accepted: every type's legendary tier is worth finding on its own.",
                            "At 4 (3.9% of stands) a long game meets ~2.8 of them and only 6% of players meet none;",
                            "at the old value of 1, half the players never saw a single one.")
                    .defineInRange("weightLegendary", 4, 0, 1000);
            b.pop();

            b.comment("Lucky XP earned for breaking a lucky block -- the vending machines' currency.")
                    .push("xp");
            baseXp = b.comment(
                            "Lucky XP granted by ANY lucky block, whatever it drops and whatever its Luck.",
                            "Silk-touching one grants nothing (it is picked up, not opened).",
                            "Reference point: at 4 XP, with the pack's natural spawn of one lucky block per 10.1",
                            "chunks, a stand at standChance=500 is worth ~200 XP of exploration. Raising this makes",
                            "every machine price cheaper, in proportion.")
                    .defineInRange("baseXp", 4, 0, 1000);
            legendaryMultiplier = b.comment(
                            "Multiplies the break's Lucky XP when the drop it rolled is a LEGENDARY one.",
                            "The reward is the only thing that changes the payout: every lucky block is worth the",
                            "same until its drop turns out legendary. Stacks multiplicatively with an XP event's",
                            "own multiplier. Set to 1 to disable the bonus.")
                    .defineInRange("legendaryMultiplier", 2, 1, 100);
            legendaryXpDelayTicks = b.comment(
                            "Ticks to wait before dropping the legendary bonus XP. The legendary suspense wrap in",
                            "the lucky blocks' drops.txt reveals the marked item at delay=2.2, i.e. 44 ticks after",
                            "the break, once its sound-and-particle fanfare has played out. Paying the bonus at that",
                            "exact moment makes the doubling visible: a second clump of orbs bursts with the reward.",
                            "0 = pay immediately, together with the break's own XP.")
                    .defineInRange("legendaryXpDelayTicks", 44, 0, 200);
            b.pop();
        }
    }
}
