package com.lwi.luckyxp.machine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One purchasable article in a vending machine: the reward stack, an optional bonus stack handed over
 * with it (e.g. an XP-pump upgrade that also gives its tank), and the cost in Lucky levels. The screen
 * only shows {@link #stack}; the {@link #extra} is granted silently on purchase.
 */
public record Article(ItemStack stack, ItemStack extra, int costLevels) {
    /** Single-item article (no bonus). */
    public Article(ItemStack stack, int costLevels) {
        this(stack, ItemStack.EMPTY, costLevels);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeItem(stack);
        buf.writeItem(extra);
        buf.writeVarInt(costLevels);
    }

    public static Article read(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        ItemStack extra = buf.readItem();
        int cost = buf.readVarInt();
        return new Article(stack, extra, cost);
    }
}
