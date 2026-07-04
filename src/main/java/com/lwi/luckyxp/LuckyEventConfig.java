package com.lwi.luckyxp;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * COMMON config ({@code config/luckyxp-common.toml}) for the daily automatic Lucky events: whether
 * they run, the per-day chance, the pity counter and the morning trigger window (in day-time ticks;
 * dawn = 0, 1200 ticks = 1 in-game minute). Read live through the spec each roll.
 */
public final class LuckyEventConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    private LuckyEventConfig() {}

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue autoEvents;
        public final ForgeConfigSpec.DoubleValue chancePerDay;
        public final ForgeConfigSpec.IntValue pityDays;
        public final ForgeConfigSpec.IntValue windowStart;
        public final ForgeConfigSpec.IntValue windowEnd;

        Common(ForgeConfigSpec.Builder b) {
            b.comment("Daily automatic Lucky events (the case-opening roulette that makes lucky blocks appear).")
                    .push("events");
            autoEvents = b.comment("Roll once per in-game day for a Lucky event, at a random moment inside the morning window below.")
                    .define("autoEvents", true);
            chancePerDay = b.comment("Chance that a given day gets an event (0.333 = one day in three).")
                    .defineInRange("chancePerDay", 0.333, 0.0, 1.0);
            pityDays = b.comment("After this many consecutive event-less days, the next day's event is guaranteed (0 = an event every day).")
                    .defineInRange("pityDays", 3, 0, 100);
            windowStart = b.comment("Earliest moment of the day the roulette may start, in day-time ticks after dawn (1200 = 1 in-game minute after dawn).")
                    .defineInRange("windowStart", 1200, 0, 23999);
            windowEnd = b.comment("Latest moment of the day the roulette may start, in day-time ticks after dawn. Kept in the morning so the spawned blocks can be opened before night.")
                    .defineInRange("windowEnd", 4800, 1, 24000);
            b.pop();
        }
    }
}
