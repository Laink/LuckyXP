package com.lwi.luckyxp.machine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** One purchasable article in a vending machine: the reward stack and its cost in Lucky levels. */
public record Article(ItemStack stack, int costLevels) {
    public void write(FriendlyByteBuf buf) {
        buf.writeItem(stack);
        buf.writeVarInt(costLevels);
    }

    public static Article read(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int cost = buf.readVarInt();
        return new Article(stack, cost);
    }
}
