package com.lwi.luckyxp.machine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One purchasable article in a vending machine: the reward stack, an optional bonus stack handed over
 * with it (e.g. an XP-pump upgrade that also gives its tank), the cost in Lucky levels, and whether it
 * has already been bought. The screen only shows {@link #stack}; the {@link #extra} is granted
 * silently on purchase.
 *
 * <p>{@code sold} — every line is a SINGLE purchase, permanently (user 2026-07-09): once bought it
 * shows as SOLD forever. There is no restock — a machine sells its one rolled stock and that is all
 * (a re-rollable legendary would hand out its lucky tools in a loop). Without the flag, a machine
 * could be wrung dry by farming lucky blocks in front of it.
 */
public record Article(ItemStack stack, ItemStack extra, int costLevels, boolean sold) {
    public Article(ItemStack stack, ItemStack extra, int costLevels) {
        this(stack, extra, costLevels, false);
    }

    /** Single-item article (no bonus). */
    public Article(ItemStack stack, int costLevels) {
        this(stack, ItemStack.EMPTY, costLevels, false);
    }

    /** This article, marked as bought. */
    public Article asSold() {
        return new Article(stack, extra, costLevels, true);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeItem(stack);
        buf.writeItem(extra);
        buf.writeVarInt(costLevels);
        buf.writeBoolean(sold);
    }

    public static Article read(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        ItemStack extra = buf.readItem();
        int cost = buf.readVarInt();
        boolean sold = buf.readBoolean();
        return new Article(stack, extra, cost, sold);
    }
}
