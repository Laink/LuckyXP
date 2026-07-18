package com.lwi.luckyxp.machine;

import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/** Stand rarity tiers. Weight = spawn chance; color drives the GUI title (also labelled by name).
 *  Also a block-state property ({@code rarity}) so the machine's external screen model varies by tier.
 *
 *  <p>A stand rolls this TWICE, independently: once for the machine (stock quality + screen) and once for
 *  its merchant (price discount + hat colour). So a plain machine can be tended by a legendary merchant,
 *  whose cheap rerolls are exactly what turns that plain stock around. */
public enum Rarity implements StringRepresentable {
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

    @Override
    public String getSerializedName() {
        return id;
    }

    /** ARGB color for GUI text. */
    public int labelColor() {
        Integer c = color.getColor();
        return c == null ? 0xFFFFFFFF : 0xFF000000 | c;
    }

    /** Vivid RGB used for the rarity pill: the GUI badge AND the external screen LED (shared so they match). */
    public int pillColor() {
        return switch (this) {
            case COMMON -> 0x5CF08A;     // green
            case RARE -> 0x4FA8FF;       // azure
            case EPIC -> 0xC264FF;       // purple
            case LEGENDARY -> 0xFFE21C;  // bright yellow-gold (vivid)
        };
    }

    /**
     * The cut a merchant of this rarity takes off every one of his prices. His hat IS the discount: the
     * whole point of finding a gold-hatted merchant is that he sells the same six services for far less.
     */
    public float merchantDiscount() {
        return switch (this) {
            case COMMON -> 0.0F;
            case RARE -> 0.10F;
            case EPIC -> 0.30F;
            case LEGENDARY -> 0.60F;
        };
    }

    /**
     * A base price, cut by this rarity. Rounds DOWN so the discount is always real money: these prices are
     * small, and rounding to nearest would quietly eat a -10% on the cheap services (5 x 0.9 = 4.5 -> 5,
     * i.e. no discount at all). Never below 1 -- a service is never free.
     */
    public int discountedPrice(int base) {
        return Math.max(1, (int) Math.floor(base * (1.0F - merchantDiscount())));
    }

    /** Look a rarity up by its serialized id, falling back rather than throwing on unknown save data. */
    public static Rarity byId(String id, Rarity fallback) {
        for (Rarity r : values()) {
            if (r.id.equalsIgnoreCase(id)) {
                return r;
            }
        }
        return fallback;
    }

    /** Roll a rarity by the enum's DEFAULT weights (worldgen passes the config weights instead). */
    public static Rarity roll(RandomSource rng) {
        return roll(rng, new int[]{COMMON.weight, RARE.weight, EPIC.weight, LEGENDARY.weight});
    }

    /** Roll a rarity from explicit weights (ordinal order); all-zero falls back to the defaults. */
    public static Rarity roll(RandomSource rng, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return roll(rng);
        }
        int x = rng.nextInt(total);
        for (Rarity r : values()) {
            int w = Math.max(0, weights[r.ordinal()]);
            if (x < w) {
                return r;
            }
            x -= w;
        }
        return COMMON;
    }
}
