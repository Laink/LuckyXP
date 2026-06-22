package com.lwi.luckyxp.event;

import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;

/**
 * The two kinds of Lucky XP event.
 *
 * <p>Colours are only a secondary cue (the user is colourblind): the HUD always pairs them with a
 * distinct text label, so the type is readable without relying on hue.
 */
public enum LuckyEventType {
    /** Multiplies the blue Lucky XP dropped by the boosted block(s) for the duration (magnitude = the multiplier). */
    DOUBLE_XP("double_xp", ChatFormatting.AQUA),
    /** Adds a chance % (percentile points) to the boosted block(s)' drop roll for the duration (magnitude = the %). */
    LUCK("luck", ChatFormatting.GOLD);

    public final String id;
    public final ChatFormatting color;

    LuckyEventType(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    @Nullable
    public static LuckyEventType byId(String id) {
        for (LuckyEventType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
