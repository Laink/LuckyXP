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
 *       generates (relative weights, not percents; 0 disables a rarity).</li>
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

        public final ForgeConfigSpec.IntValue weightCommon;
        public final ForgeConfigSpec.IntValue weightRare;
        public final ForgeConfigSpec.IntValue weightEpic;
        public final ForgeConfigSpec.IntValue weightLegendary;

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
            weightCommon = b.comment("Relative weight of a COMMON machine when a stand generates (weights, not percents; 0 disables the rarity).")
                    .defineInRange("weightCommon", 59, 0, 1000);
            weightRare = b.comment("Relative weight of a RARE machine.")
                    .defineInRange("weightRare", 30, 0, 1000);
            weightEpic = b.comment("Relative weight of an EPIC machine.")
                    .defineInRange("weightEpic", 10, 0, 1000);
            weightLegendary = b.comment("Relative weight of a LEGENDARY machine.")
                    .defineInRange("weightLegendary", 1, 0, 1000);
            b.pop();
        }
    }
}
