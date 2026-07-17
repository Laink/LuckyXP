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
        public final ForgeConfigSpec.DoubleValue multiplayerScaling;

        public final ForgeConfigSpec.IntValue standChance;
        public final ForgeConfigSpec.BooleanValue debugFullStock;
        public final ForgeConfigSpec.IntValue weightCommon;
        public final ForgeConfigSpec.IntValue weightRare;
        public final ForgeConfigSpec.IntValue weightEpic;
        public final ForgeConfigSpec.IntValue weightLegendary;
        public final ForgeConfigSpec.IntValue permLuckCap;
        public final ForgeConfigSpec.IntValue standTimerSeconds;

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
            multiplayerScaling = b.comment(
                            "How an event's haul grows with the number of players online. Blocks are placed around",
                            "each player, so the event's TOTAL is count x N^thisValue, and each player is given",
                            "count x N^(thisValue-1) blocks.",
                            "1.0 = every player gets a full solo-sized share, so the total is N times a solo event.",
                            "0.5 (default) = the total grows with the square root of the team: four players find",
                            "twice a solo event, not four times. Players here stay close together and pool what they",
                            "find, so multiplying it outright buries them in blocks.",
                            "0.0 = the whole team shares one solo-sized event.")
                    .defineInRange("multiplayerScaling", 0.5, 0.0, 1.0);
            b.pop();

            b.comment("Vending-machine worldgen (the market stands).")
                    .push("machines");
            standChance = b.comment(
                            "One stand attempted per this many chunks (1 = every chunk, 0/1 disables the filter).",
                            "Terrain may still reject a stand, so the real rate is slightly lower.",
                            "Calibrated on a measured 1467 chunks explored per hour: at 700, a stand shows up every",
                            "~29 minutes and is worth ~279 Lucky XP of exploration -- about level 14, the price of a dear line.",
                            "Lower it while testing (60 puts one almost everywhere); the shipped value is what matters.")
                    .defineInRange("standChance", 700, 1, 100000);
            debugFullStock = b.comment(
                            "TESTING ONLY. When true, every machine lists its ENTIRE rarity pool instead of the",
                            "usual 7-10 random lines — so you can see all items at once. Leave false for real play.")
                    .define("debugFullStock", false);
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
            permLuckCap = b.comment(
                            "Maximum PERMANENT luck (%) a player can accumulate from the merchant's permanent-luck",
                            "service. Each purchase adds +1-3%; once this cap is reached the service refuses (no",
                            "charge). The temporary luck boost is separate and uncapped. 0 = no permanent luck at all.")
                    .defineInRange("permLuckCap", 10, 0, 100);
            standTimerSeconds = b.comment(
                            "Once a stand is first interacted with (machine OR merchant), it stays open for this",
                            "many seconds, then closes FOR GOOD (both go inert). This is the anti-farm: a stand is",
                            "a one-time treasure window, not a shop to come back to after grinding levels. The",
                            "countdown shows on the stand's floating display; the decor burns down as it runs.")
                    .defineInRange("standTimerSeconds", 180, 10, 3600);
            b.pop();

            b.comment("Lucky XP earned for breaking a lucky block -- the vending machines' currency.")
                    .push("xp");
            baseXp = b.comment(
                            "Lucky XP granted by ANY lucky block, whatever it drops and whatever its Luck.",
                            "Silk-touching one grants nothing (it is picked up, not opened).",
                            "This is the ONLY pace control: the level curve is vanilla's, and machine prices are in",
                            "levels, so scaling the curve by K would be exactly the same as dividing baseXp by K.",
                            "Calibrated on a measured session (119 lucky blocks in 49 min, one block per 10.1 chunks):",
                            "at 4, a run reaches level 14 in ~29 min, and a stand (standChance=700) is worth ~279 XP --",
                            "level 14, so the player must CHOOSE a line rather than afford the machine's best every",
                            "time. A lucky-block pack hands out far more XP than vanilla ever does; this is deliberately",
                            "slower than it looks. Raising it makes every machine price cheaper, in proportion.")
                    .defineInRange("baseXp", 4, 0, 1000);
            legendaryMultiplier = b.comment(
                            "Multiplies the break's Lucky XP when the drop it rolled is a LEGENDARY one.",
                            "The reward is the only thing that changes the payout: every lucky block is worth the",
                            "same until its drop turns out legendary. Stacks multiplicatively with an XP event's",
                            "own multiplier. Set to 1 to disable the bonus.",
                            "Measured at ~3.3% of breaks (4 legendaries in 121 blocks), so this is worth about +3% of",
                            "a run's total XP: it is a moment of celebration, not an income. baseXp is calibrated as",
                            "if it did not exist. Luck still pays, but indirectly -- an infused block pushes the roll",
                            "up the drop table, hence towards the legendary entries, hence towards this bonus.")
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
