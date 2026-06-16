package com.lwi.luckyxp.machine;

import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;

/** Vending-machine rarity tiers. Weight = spawn chance; color drives the GUI title (also labelled by name). */
public enum Rarity {
    COMMON("common", ChatFormatting.GREEN, 59),
    RARE("rare", ChatFormatting.BLUE, 30),
    EPIC("epic", ChatFormatting.LIGHT_PURPLE, 10),
    LEGENDARY("legendary", ChatFormatting.GOLD, 1);

    public final String id;
    public final ChatFormatting color;
    public final int weight;

    Rarity(String id, ChatFormatting color, int weight) {
        this.id = id;
        this.color = color;
        this.weight = weight;
    }

    /** ARGB color for GUI text. */
    public int labelColor() {
        Integer c = color.getColor();
        return c == null ? 0xFFFFFFFF : 0xFF000000 | c;
    }

    /** Roll a rarity by weight (used by worldgen to pick which machine to place). */
    public static Rarity roll(RandomSource rng) {
        int total = 0;
        for (Rarity r : values()) {
            total += r.weight;
        }
        int x = rng.nextInt(total);
        for (Rarity r : values()) {
            if (x < r.weight) {
                return r;
            }
            x -= r.weight;
        }
        return COMMON;
    }
}
