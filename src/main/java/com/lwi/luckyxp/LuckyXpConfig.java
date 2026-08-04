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
        public final ForgeConfigSpec.IntValue luckyXpBarY;
        public final ForgeConfigSpec.IntValue luckyNumberDy;
        public final ForgeConfigSpec.IntValue reserve;
        public final ForgeConfigSpec.IntValue gap;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("Lucky XP HUD layout, in GUI pixels.",
                            "Forge caches these values and only re-reads them when its file watcher sees the write, which",
                            "many editors dodge by saving to a temp file and renaming. Leaving and re-entering the world does",
                            "NOT re-read a client config either: restart the game to be sure an edit applied.")
                    .push("hud");
            luckyXpBarY = b.comment("Blue Lucky XP bar height above the bottom of the screen. Must clear the vanilla level number,",
                            "which vanilla draws at 35.")
                    .defineInRange("luckyXpBarY", 42, 0, 256);
            luckyNumberDy = b.comment("Vertical nudge of the BLUE level number relative to its bar (negative = up). It is centered",
                            "horizontally, like vanilla's own number.")
                    .defineInRange("luckyNumberDy", -6, -200, 200);
            reserve = b.comment("Cluster lift when the blue Lucky XP bar is shown (room opened for the blue bar and its number).")
                    .defineInRange("reserve", 21, 0, 100);
            gap = b.comment("Cluster lift when the Lucky XP bar is hidden -- creative and spectator, where vanilla",
                            "hides its own XP bar too.")
                    .defineInRange("gap", 2, 0, 100);
            b.pop();
        }
    }
}
