package com.lwi.luckyxp.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * An active Lucky XP event: a {@link LuckyEventType type}, the boosted block ({@code null} = ALL lucky
 * blocks = the "mega jackpot"), and a magnitude. The magnitude is the XP multiplier for
 * {@link LuckyEventType#DOUBLE_XP}, or the chance % (percentile points) for {@link LuckyEventType#LUCK}.
 *
 * <p>Immutable. The remaining time is held by {@link LuckyEventManager}, not here.
 */
public final class LuckyEvent {
    private final LuckyEventType type;
    @Nullable private final ResourceLocation blockId;
    private final int magnitude;

    public LuckyEvent(LuckyEventType type, @Nullable ResourceLocation blockId, int magnitude) {
        this.type = type;
        this.blockId = blockId;
        this.magnitude = magnitude;
    }

    public LuckyEventType type() {
        return type;
    }

    /** The single boosted block, or {@code null} for a mega jackpot (all lucky blocks). */
    @Nullable
    public ResourceLocation blockId() {
        return blockId;
    }

    public int magnitude() {
        return magnitude;
    }

    public boolean isJackpot() {
        return blockId == null;
    }

    /** Whether this event boosts the given broken block (always true for a mega jackpot). */
    public boolean appliesTo(@Nullable ResourceLocation broken) {
        return blockId == null || blockId.equals(broken);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.id);
        if (blockId != null) {
            tag.putString("Block", blockId.toString());
        }
        tag.putInt("Magnitude", magnitude);
        return tag;
    }

    @Nullable
    public static LuckyEvent load(CompoundTag tag) {
        LuckyEventType type = LuckyEventType.byId(tag.getString("Type"));
        if (type == null) {
            return null;
        }
        ResourceLocation block = tag.contains("Block") ? ResourceLocation.tryParse(tag.getString("Block")) : null;
        return new LuckyEvent(type, block, tag.getInt("Magnitude"));
    }
}
