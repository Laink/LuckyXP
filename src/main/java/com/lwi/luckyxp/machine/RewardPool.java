package com.lwi.luckyxp.machine;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls a vending machine's stock from the reward pool, by {@link MachineType} (the category sold) and
 * {@link Rarity} (quality / level cost). Items are looked up by registry id at roll time and skipped if
 * absent, so this never hard-depends on addon/luckytools items.
 *
 * <p>Placeholder economy for now — real balance (exact rolls, level costs, infused potions from
 * Yakurum/Pink, skip-invasion, heal/repair, double-XP buff) is deferred to config. Keep the per-type
 * structure; only the contents/costs will change.
 */
public final class RewardPool {
    private RewardPool() {}

    public static List<Article> roll(MachineType type, Rarity rarity, RandomSource rng) {
        List<Article> out = new ArrayList<>();
        int tier = rarity.ordinal(); // 0 common .. 3 legendary
        switch (type) {
            case POTIONS -> rollPotions(out, tier);
            case INFUSED_LB -> rollInfusedLb(out, rarity);
            case ORES -> rollOres(out, tier);
        }
        // Legendary machines always also offer a lucky tool, whatever the type.
        if (rarity == Rarity.LEGENDARY) {
            ItemStack tool = luckyTool(rng);
            if (!tool.isEmpty()) {
                out.add(new Article(tool, 40));
            }
        }
        return out;
    }

    private static void rollPotions(List<Article> out, int tier) {
        addPotion(out, "minecraft:strong_healing", 3 + tier);
        addPotion(out, "minecraft:long_regeneration", 5 + tier * 2);
        if (tier >= 1) {
            addPotion(out, "minecraft:strong_strength", 5 + tier);
        }
        if (tier >= 2) {
            addItem(out, "minecraft:golden_apple", 1 + tier, 4 + tier * 2);
        }
        if (tier >= 3) {
            addItem(out, "minecraft:enchanted_golden_apple", 1, 30);
        }
    }

    private static void rollInfusedLb(List<Article> out, Rarity rarity) {
        int luck;
        int cost;
        switch (rarity) {
            case RARE -> { luck = 50; cost = 10; }
            case EPIC -> { luck = 75; cost = 18; }
            case LEGENDARY -> { luck = 100; cost = 30; }
            default -> { luck = 25; cost = 5; }
        }
        ItemStack lb = infusedLuckyBlock(luck);
        if (!lb.isEmpty()) {
            out.add(new Article(lb, cost));
        }
        // A cheaper lower-luck block too, so common machines aren't a single line.
        if (rarity != Rarity.COMMON) {
            ItemStack low = infusedLuckyBlock(25);
            if (!low.isEmpty()) {
                out.add(new Article(low, 5));
            }
        }
    }

    private static void rollOres(List<Article> out, int tier) {
        switch (tier) {
            case 0 -> {
                addItem(out, "minecraft:iron_ingot", 4, 3);
                addItem(out, "minecraft:copper_ingot", 8, 2);
            }
            case 1 -> {
                addItem(out, "minecraft:gold_ingot", 4, 5);
                addItem(out, "minecraft:redstone", 16, 3);
                addItem(out, "minecraft:iron_ingot", 8, 4);
            }
            case 2 -> {
                addItem(out, "minecraft:diamond", 2, 10);
                addItem(out, "minecraft:emerald", 4, 8);
                addItem(out, "minecraft:gold_ingot", 8, 6);
            }
            default -> {
                addItem(out, "minecraft:diamond", 4, 14);
                addItem(out, "minecraft:netherite_scrap", 1, 25);
                addItem(out, "minecraft:emerald", 8, 10);
            }
        }
    }

    private static void addItem(List<Article> out, String id, int count, int cost) {
        ItemStack s = stack(id, count);
        if (!s.isEmpty()) {
            out.add(new Article(s, cost));
        }
    }

    private static void addPotion(List<Article> out, String potionId, int cost) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "potion"));
        if (item == null) {
            return;
        }
        ItemStack s = new ItemStack(item);
        s.getOrCreateTag().putString("Potion", potionId);
        out.add(new Article(s, cost));
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
