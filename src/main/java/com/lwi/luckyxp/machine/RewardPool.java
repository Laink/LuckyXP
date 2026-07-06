package com.lwi.luckyxp.machine;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
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
            case INFUSED_LB -> rollInfusedLb(out, rarity, rng);
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

    /**
     * Infused-block machine (spec user 2026-07-04): 5 RANDOM lucky-block types, each infused to a
     * random Luck inside the machine's tier band — common +10..+30, rare +30..+70, epic +70..+100,
     * legendary flat +100. Values snap to steps of 5. Capped blocks stay in EVERY band (aligned with
     * the lucky events, user 2026-07-06): their offered Luck is clamped to their own cap — a
     * legendary machine sells the Chaos at its full +75 — and the price follows the REAL clamped
     * value, so the tooltip and the cost never over-promise.
     */
    private static void rollInfusedLb(List<Article> out, Rarity rarity, RandomSource rng) {
        int min;
        int max;
        switch (rarity) {
            case RARE -> { min = 30; max = 70; }
            case EPIC -> { min = 70; max = 100; }
            case LEGENDARY -> { min = 100; max = 100; }
            default -> { min = 10; max = 30; }
        }
        // lucky-mod blocks only: a cross-mod lucky-like (the Fuze blockling) cannot be infused
        // through the normal infusion recipes, so a machine-exclusive infusion would be an
        // anomaly (user 2026-07-04 - "si on ne peut pas l'infuser naturellement, pas de machine").
        List<ResourceLocation> pool = new ArrayList<>(LuckyTweaksApi.getLuckyBlockIds());
        pool.removeIf(id -> !"lucky".equals(id.getNamespace()));
        shuffle(pool, rng);
        int n = Math.min(5, pool.size());
        for (int i = 0; i < n; i++) {
            ResourceLocation id = pool.get(i);
            int luck = (min == max) ? max : min + rng.nextInt(max - min + 1);
            luck = Math.round(luck / 5.0F) * 5;             // clean steps of 5 (band bounds are multiples of 5)
            Integer cap = LuckyTweaksApi.getLuckCap(id);
            if (cap != null) {
                luck = Math.min(luck, cap);
            }
            ItemStack s = infusedBlock(id, luck);
            if (!s.isEmpty()) {
                out.add(new Article(s, costForLuck(luck)));
            }
        }
    }

    /** Level cost of an infused block, from its Luck (tunable): +10 -> 3, +30 -> 8, +70 -> 18, +100 -> 25. */
    private static int costForLuck(int luck) {
        return Math.max(3, Math.round(luck * 0.25F));
    }

    /** In-place Fisher-Yates with the world's RandomSource (Collections.shuffle needs java.util.Random). */
    private static void shuffle(List<ResourceLocation> list, RandomSource rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            ResourceLocation tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
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

    private static ItemStack infusedBlock(ResourceLocation blockId, int luck) {
        Item item = ForgeRegistries.ITEMS.getValue(blockId);   // block items share the block's id
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
