package com.lwi.luckyxp.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * The outcome of a Lucky XP event (design v4). Two independent rolls:
 * <ul>
 *   <li>{@link Scope} (roll 1 = SCOPE): {@code NOTHING} (a miss), {@code SINGLE} (one block type),
 *       or {@code JACKPOT} (all blocks = an assortment).</li>
 *   <li>the VALUE (roll 2): {@link #luckPercent} for a LUCK event (10..100; 100 = mega) or
 *       {@link #xpMult} for an XP event (1.5/2/4; 4 = mega).</li>
 * </ul>
 * The event makes {@link #count} lucky blocks appear (hidden 5–10 for single, ~20 for jackpot); there is
 * NO temporal phase. Immutable.
 */
public final class LuckyEvent {
    public enum Scope { NOTHING, SINGLE, JACKPOT }

    private final LuckyEventType type;
    private final Scope scope;
    @Nullable private final ResourceLocation blockId; // SINGLE only
    private final int luckPercent;                    // LUCK value (0 otherwise)
    private final float xpMult;                       // XP value (0 otherwise)
    private final int count;                          // blocks to shower (0 for NOTHING)

    public LuckyEvent(LuckyEventType type, Scope scope, @Nullable ResourceLocation blockId,
                      int luckPercent, float xpMult, int count) {
        this.type = type;
        this.scope = scope;
        this.blockId = blockId;
        this.luckPercent = luckPercent;
        this.xpMult = xpMult;
        this.count = count;
    }

    public static LuckyEvent nothing(LuckyEventType type) {
        return new LuckyEvent(type, Scope.NOTHING, null, 0, 0.0F, 0);
    }

    public static LuckyEvent luck(Scope scope, @Nullable ResourceLocation block, int percent, int count) {
        return new LuckyEvent(LuckyEventType.LUCK, scope, scope == Scope.SINGLE ? block : null, percent, 0.0F, count);
    }

    public static LuckyEvent xp(Scope scope, @Nullable ResourceLocation block, float mult, int count) {
        return new LuckyEvent(LuckyEventType.DOUBLE_XP, scope, scope == Scope.SINGLE ? block : null, 0, mult, count);
    }

    public LuckyEventType type() {
        return type;
    }

    public Scope scope() {
        return scope;
    }

    @Nullable
    public ResourceLocation blockId() {
        return blockId;
    }

    public int luckPercent() {
        return luckPercent;
    }

    public float xpMult() {
        return xpMult;
    }

    public int count() {
        return count;
    }

    public boolean isNothing() {
        return scope == Scope.NOTHING;
    }

    public boolean isJackpot() {
        return scope == Scope.JACKPOT;
    }

    public boolean isSingle() {
        return scope == Scope.SINGLE;
    }

    /** The value roll hit its maximum (Luck +100% or XP ×4): the MEGA JACKPOT (fireworks). */
    public boolean isMega() {
        if (scope == Scope.NOTHING) {
            return false;
        }
        return type == LuckyEventType.LUCK ? luckPercent >= 100 : xpMult >= 4.0F;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.id);
        tag.putInt("Scope", scope.ordinal());
        if (blockId != null) {
            tag.putString("Block", blockId.toString());
        }
        tag.putInt("Luck", luckPercent);
        tag.putFloat("XpMult", xpMult);
        tag.putInt("Count", count);
        return tag;
    }

    @Nullable
    public static LuckyEvent load(CompoundTag tag) {
        LuckyEventType type = LuckyEventType.byId(tag.getString("Type"));
        if (type == null) {
            return null;
        }
        Scope[] scopes = Scope.values();
        int si = tag.getInt("Scope");
        Scope scope = si >= 0 && si < scopes.length ? scopes[si] : Scope.NOTHING;
        ResourceLocation block = tag.contains("Block") ? ResourceLocation.tryParse(tag.getString("Block")) : null;
        return new LuckyEvent(type, scope, block, tag.getInt("Luck"), tag.getFloat("XpMult"), tag.getInt("Count"));
    }
}
