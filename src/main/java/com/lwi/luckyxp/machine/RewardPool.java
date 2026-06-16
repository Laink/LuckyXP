package com.lwi.luckyxp.machine;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls a vending machine's stock from the reward pool, gated by rarity.
 *
 * <p>MVP pool (placeholders + a few real rewards): every machine offers exactly one infused Lucky
 * Block (Luck + cost scale with rarity), plus a couple of goodies; legendary machines also offer a
 * lucky tool. Items are looked up by registry id at roll time and skipped if absent, so this never
 * hard-depends on addon/luckytools items. TODO: real pool (Yakurum/Pink infused potions, skip
 * invasion, heal, repair, ender eyes, double-XP buff) + balanced level costs, moved to config.
 */
public final class RewardPool {
    private RewardPool() {}

    public static List<Article> roll(Rarity rarity, RandomSource rng) {
        List<Article> out = new ArrayList<>();

        // Exactly one infused Lucky Block per machine.
        int luck;
        int lbCost;
        switch (rarity) {
            case RARE -> { luck = 50; lbCost = 10; }
            case EPIC -> { luck = 75; lbCost = 18; }
            case LEGENDARY -> { luck = 100; lbCost = 30; }
            default -> { luck = 25; lbCost = 5; }
        }
        ItemStack lb = infusedLuckyBlock(luck);
        if (!lb.isEmpty()) {
            out.add(new Article(lb, lbCost));
        }

        // Placeholder "other" goodies until the real pool (potions, etc.) is wired.
        int tier = rarity.ordinal();
        addIfPresent(out, "minecraft:ender_eye", 2 + tier * 2, 2 + tier);
        addIfPresent(out, "minecraft:golden_apple", 1 + tier, 3 + tier * 3);

        // Legendary-exclusive: a lucky tool.
        if (rarity == Rarity.LEGENDARY) {
            ItemStack tool = luckyTool(rng);
            if (!tool.isEmpty()) {
                out.add(new Article(tool, 40));
            }
        }
        return out;
    }

    private static void addIfPresent(List<Article> out, String id, int count, int cost) {
        ItemStack s = stack(id, count);
        if (!s.isEmpty()) {
            out.add(new Article(s, cost));
        }
    }

    private static ItemStack infusedLuckyBlock(int luck) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("lucky", "lucky_block"));
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack s = new ItemStack(item);
        s.getOrCreateTag().putInt("Luck", luck);
        return s;
    }

    private static ItemStack luckyTool(RandomSource rng) {
        String[] tools = {"lucky_radar", "lucky_wand", "lucky_shield", "lucky_spawner", "lucky_totem", "lucky_hammer"};
        String pick = tools[rng.nextInt(tools.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("luckytools", pick));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack stack(String id, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }
}
