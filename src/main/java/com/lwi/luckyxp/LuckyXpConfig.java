package com.lwi.luckyxp;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Client-side, HOT-RELOADABLE HUD layout config ({@code config/luckyxp-client.toml}). Forge re-reads
 * the file when you edit + save it on disk, so the XP bars / level numbers reposition live (if a given
 * edit does not take immediately, re-enter the world). All values are in GUI pixels.
 */
public final class LuckyXpConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Client, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = pair.getLeft();
        CLIENT_SPEC = pair.getRight();
    }

    private LuckyXpConfig() {}

    public static final class Client {
        public final ForgeConfigSpec.IntValue vanillaXpBarY;
        public final ForgeConfigSpec.IntValue luckyXpBarY;
        public final ForgeConfigSpec.IntValue numberGapX;
        public final ForgeConfigSpec.IntValue vanillaNumberDy;
        public final ForgeConfigSpec.IntValue luckyNumberDy;
        public final ForgeConfigSpec.IntValue reserve;
        public final ForgeConfigSpec.IntValue gap;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("Lucky XP HUD layout, in GUI pixels. Forge reloads this file live when you save it (re-enter the world if an edit does not show right away).")
                    .push("hud");
            vanillaXpBarY = b.comment("Vanilla XP bar height above the bottom of the screen (vanilla default = 29).")
                    .defineInRange("vanillaXpBarY", 29, 0, 256);
            luckyXpBarY = b.comment("Blue Lucky XP bar height above the bottom of the screen.")
                    .defineInRange("luckyXpBarY", 38, 0, 256);
            numberGapX = b.comment("Horizontal gap between a level number's right edge and the bar's left edge (bigger = number further left).")
                    .defineInRange("numberGapX", 2, -300, 300);
            vanillaNumberDy = b.comment("Vertical nudge of the GREEN vanilla XP level number relative to its bar (negative = up).")
                    .defineInRange("vanillaNumberDy", -2, -200, 200);
            luckyNumberDy = b.comment("Vertical nudge of the BLUE Lucky XP level number relative to its bar (negative = up).")
                    .defineInRange("luckyNumberDy", -2, -200, 200);
            reserve = b.comment("Cluster lift when the blue Lucky XP bar is shown (room opened for the blue bar).")
                    .defineInRange("reserve", 11, 0, 100);
            gap = b.comment("Cluster lift when the Lucky XP bar is hidden (small gap kept above the vanilla XP bar).")
                    .defineInRange("gap", 2, 0, 100);
            b.pop();
        }
    }
}
