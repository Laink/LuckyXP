package com.lwi.luckyxp.machine;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.LuckyXpCommonConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rolls a vending machine's stock from the reward pool, by {@link MachineType} (the category sold) and
 * {@link Rarity} (quality / level cost). Items are looked up by registry id at roll time and skipped if
 * absent, so this never hard-depends on addon/luckytools items.
 *
 * <p>All four types are designed: POTIONS (consumables), INFUSED_LB, ORES (materials) and TOOLS.
 * Items are absent-tolerant — a line whose item is not registered is simply left out.
 */
public final class RewardPool {
    private RewardPool() {}

    /** How many lines to show: 7-10 random, or the whole pool when {@code debugFullStock} is on (test). */
    private static int drawCount(int poolSize, RandomSource rng) {
        return drawCount(poolSize, rng, 7, 4);          // the big machines show 7-10 lines
    }

    /** Draw {@code base..base+spread-1} lines (or the whole pool in debug-full-stock review mode). */
    private static int drawCount(int poolSize, RandomSource rng, int base, int spread) {
        if (LuckyXpCommonConfig.COMMON.debugFullStock.get()) {
            return poolSize;
        }
        return Math.min(base + rng.nextInt(spread), poolSize);
    }

    public static List<Article> roll(MachineType type, Rarity rarity, RandomSource rng) {
        List<Article> out = new ArrayList<>();
        switch (type) {
            case POTIONS -> rollConsumables(out, rarity, rng);
            case INFUSED_LB -> rollInfusedLb(out, rarity, rng);
            case ORES -> rollMaterials(out, rarity, rng);
            case TOOLS -> rollTools(out, rarity, rng);
        }
        // (Lucky tools / artifacts / accessories now live in their own TOOLS machine, not as a
        //  legendary bonus on every type — user 2026-07-07.)
        return out;
    }

    // =========================== CONSUMABLES machine (POTIONS) ===========================

    /**
     * Consumables machine ({@link MachineType#POTIONS} — it keeps its potion screen art): food, buff
     * items and the three named potions of the lucky blocks. Same economy as the materials machine —
     * flat per-line price, quantity grows with rarity, 7–10 lines drawn at random from the rarity's pool.
     *
     * <p>{@link #customPotion} rebuilds those three potions exactly as the lucky blocks do — same item,
     * colour, name, and the same effect roll (the mod's {@code #luckyPotionEffects} template and the
     * Water LB's explicit rows were decompiled and reproduced, user 2026-07-08). Legendary sells two of
     * each: a second, independently rolled bottle rides along as the article's bonus stack, since
     * potions do not stack.
     */
    private static void rollConsumables(List<Article> out, Rarity rarity, RandomSource rng) {
        int r = rarity.ordinal();
        List<Cons> pool = new ArrayList<>();
        for (Cons c : CONSUMABLES) {
            if (c.bands()[r] != null && (isPotionKey(c.ids()[0]) || anyPresent(c.ids()))) {
                pool.add(c);
            }
        }
        shuffle(pool, rng);
        int show = drawCount(pool.size(), rng);
        for (int i = 0; i < show; i++) {
            Cons c = pool.get(i);
            int[] band = c.bands()[r];
            int count = band[0] + (band[1] > band[0] ? rng.nextInt(band[1] - band[0] + 1) : 0);
            if (isPotionKey(c.ids()[0])) {
                ItemStack first = customPotion(c.ids()[0], rng);
                if (first.isEmpty()) {
                    continue;
                }
                ItemStack second = count >= 2 ? customPotion(c.ids()[0], rng) : ItemStack.EMPTY;
                out.add(new Article(first, second, c.prices()[r]));  // potions stack to 1: the 2nd rides as bonus
            } else {
                ItemStack s = stack(pickPresentId(c.ids(), rng), count);
                if (!s.isEmpty()) {
                    out.add(new Article(s, c.prices()[r]));
                }
            }
        }
    }

    private static boolean isPotionKey(String id) {
        return id.charAt(0) == '@';
    }

    private static boolean anyPresent(String[] ids) {
        for (String id : ids) {
            if (ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id))) {
                return true;
            }
        }
        return false;
    }

    /** A random id among those actually registered (so a missing addon just narrows the group). */
    private static String pickPresentId(String[] ids, RandomSource rng) {
        List<String> present = new ArrayList<>();
        for (String id : ids) {
            if (ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id))) {
                present.add(id);
            }
        }
        return present.isEmpty() ? ids[0] : present.get(rng.nextInt(present.size()));
    }

    /** One consumables line: an item (or a group, one member picked per roll), its level price PER
     *  RARITY (indexed by {@link Rarity#ordinal()}, 0 where not sold), and its quantity band per
     *  rarity. Prices are per rarity since the tier-list pass (user 2026-07-09): the same article may
     *  cost a little more — or less, deliberately — in a rarer machine. An id starting with {@code @}
     *  is one of the NBT-built custom potions. */
    private record Cons(String[] ids, int[] prices, int[][] bands) {}

    private static final String[] BIC_CANDY = {
        "born_in_chaos_v1:mint_candy", "born_in_chaos_v1:holiday_candy",
        "born_in_chaos_v1:coffee_candy", "born_in_chaos_v1:chocolate_heart"
    };
    private static final String[] COOKED_MEAT = {
        "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken",
        "minecraft:cooked_mutton", "minecraft:cooked_rabbit"
    };

    // Prices from the user's tier-list pass (2026-07-09, ranges C 3-11 / R 5-15 / E 10-17 / L 11-17).
    // Deliberate inversions are design: a rarer machine often sells the same line CHEAPER and bigger
    // (finding it is the reward) — e.g. the named potions at 16 Epic vs 13 Legendary for two bottles.
    private static final Cons[] CONSUMABLES = {
        // ---------- food ----------
        new Cons(new String[]{"minecraft:golden_apple"},           new int[]{ 8, 10,  0,  0}, new int[][]{ {2,4},  {6,8},   null,  null   }),
        new Cons(new String[]{"minecraft:enchanted_golden_apple"}, new int[]{11, 13, 14, 12}, new int[][]{ {1,1},  {2,3},   {4,5}, {6,7}  }),
        new Cons(new String[]{"minecraft:golden_carrot"},          new int[]{ 4,  8,  0,  0}, new int[][]{ {5,10}, {10,20}, null,  null   }),
        new Cons(new String[]{"yakurum:golden_fish"},              new int[]{ 7,  0,  0,  0}, new int[][]{ {1,3},  null,    null,  null   }),
        new Cons(new String[]{"kubejs:cheesecake_a_la_merde"},     new int[]{ 4,  0,  0,  0}, new int[][]{ {1,1},  null,    null,  null   }),
        new Cons(new String[]{"fuze_relics:blue_cord"},            new int[]{ 5,  6,  0,  0}, new int[][]{ {5,10}, {10,15}, null,  null   }),
        new Cons(BIC_CANDY,                                        new int[]{ 6,  7,  0,  0}, new int[][]{ {2,4},  {6,8},   null,  null   }),
        new Cons(new String[]{"born_in_chaos_v1:magical_holiday_candy"},
                                                                   new int[]{10, 10, 10,  0}, new int[][]{ {4,5},  {10,10}, {20,20}, null }),
        new Cons(COOKED_MEAT,                                      new int[]{ 5,  5,  0,  0}, new int[][]{ {5,10}, {10,20}, null,  null   }),
        new Cons(new String[]{"yakurum:spiral_cookie"},            new int[]{ 0,  9, 10,  0}, new int[][]{ null,   {1,2},   {3,5}, null   }),
        new Cons(new String[]{"yakurum:water_apple"},              new int[]{ 0, 14, 10,  0}, new int[][]{ null,   {1,2},   {2,3}, null   }),
        new Cons(new String[]{"yakurum:enchanted.golden_fish"},    new int[]{ 0, 12,  0,  0}, new int[][]{ null,   {1,2},   null,  null   }),
        // the three never-consumed foods: Legendary only, so the 1% machine owns them outright
        new Cons(new String[]{"artifacts:eternal_steak"},          new int[]{ 0,  0,  0, 11}, new int[][]{ null,   null,    null,  {1,1}  }),
        new Cons(new String[]{"relics:infinity_ham"},              new int[]{ 0,  0,  0, 11}, new int[][]{ null,   null,    null,  {1,1}  }),
        new Cons(new String[]{"born_in_chaos_v1:eternal_candy"},   new int[]{ 0,  0,  0, 11}, new int[][]{ null,   null,    null,  {1,1}  }),
        new Cons(new String[]{"yakurum:enchanted.water_apple"},    new int[]{ 0,  0, 12, 12}, new int[][]{ null,   null,    {1,1}, {2,3}  }),
        new Cons(new String[]{"yakurum:diamond_apple"},            new int[]{ 0,  0, 13,  0}, new int[][]{ null,   null,    {1,2}, null   }),
        new Cons(new String[]{"yakurum:enchanted.diamond_apple"},  new int[]{ 0,  0,  0, 14}, new int[][]{ null,   null,    null,  {1,2}  }),
        // ---------- buffs ----------
        new Cons(new String[]{"yakurum:sacred_heart"},             new int[]{ 0, 15, 17, 17}, new int[][]{ null,   {2,2},   {4,4}, {6,7}  }),
        new Cons(new String[]{"yakurum:magic_coral"},              new int[]{ 0,  0, 15, 14}, new int[][]{ null,   null,    {1,1}, {2,2}  }),
        new Cons(new String[]{"yakurum:pink_orb"},                 new int[]{ 0,  0,  0, 16}, new int[][]{ null,   null,    null,  {1,1}  }),
        new Cons(new String[]{"yakurum:dew_gout"},                 new int[]{ 0,  0,  0, 16}, new int[][]{ null,   null,    null,  {1,1}  }),
        // ---------- the lucky blocks' named potions ----------
        new Cons(new String[]{"@lucky_potion"},                    new int[]{ 0,  0, 16, 13}, new int[][]{ null,   null,    {1,1}, {2,2}  }),
        new Cons(new String[]{"@water_potion"},                    new int[]{ 0,  0, 16, 13}, new int[][]{ null,   null,    {1,1}, {2,2}  }),
        new Cons(new String[]{"@hero_potion"},                     new int[]{ 0,  0, 16, 13}, new int[][]{ null,   null,    {1,1}, {2,2}  }),
        // the Water LB's armour potions: one per tier, ordered by the armour they actually grant
        new Cons(new String[]{"@armor_leather"},                   new int[]{10,  0,  0,  0}, new int[][]{ {1,1},  null,    null,  null   }),
        new Cons(new String[]{"@armor_golden"},                    new int[]{ 0, 10,  0,  0}, new int[][]{ null,   {1,1},   null,  null   }),
        new Cons(new String[]{"@armor_iron"},                      new int[]{ 0,  0, 10,  0}, new int[][]{ null,   null,    {1,1}, null   }),
        new Cons(new String[]{"@armor_diamond"},                   new int[]{ 0,  0,  0, 15}, new int[][]{ null,   null,    null,  {1,1}  }),
        // Energy Element
        new Cons(new String[]{"@fighting_energy"},                 new int[]{ 9,  0,  0,  0}, new int[][]{ {1,1},  null,    null,  null   }),
        new Cons(new String[]{"@double_energy"},                   new int[]{ 0, 10,  0,  0}, new int[][]{ null,   {1,1},   null,  null   }),
        new Cons(new String[]{"@rainbow_energy"},                  new int[]{ 0,  0, 12,  0}, new int[][]{ null,   null,    {1,1}, null   }),
        new Cons(new String[]{"@full_heal_energy"},                new int[]{11, 11, 11,  0}, new int[][]{ {1,1},  {1,1},   {2,2}, null   }),
    };

    /**
     * The Water Lucky Block's four armour potions, reproduced from its {@code drops.txt}. Each is a
     * {@code yakurum:yakurum_splash_potion} carrying a single {@code yakurum:armor_boost}, whose effect
     * is {@code ARMOR += 7.0 + amplifier} (ADDITION) for 3 minutes -- so the amplifiers below hand out
     * exactly the vanilla armour values: leather 7, gold 11, iron 15, diamond 20.
     */
    private record ArmorPotion(String name, int amplifier, int colour) {}

    private static final Map<String, ArmorPotion> ARMOR_POTIONS = Map.of(
            "@armor_leather", new ArmorPotion("Leather Armor Potion",  0, 10511680),
            "@armor_golden",  new ArmorPotion("Golden Armor Potion",   4, 15396439),
            "@armor_iron",    new ArmorPotion("Iron Armor Potion",     8, 13027014),
            "@armor_diamond", new ArmorPotion("Diamond Armor Potion", 13,  4910553)
    );

    /** Energy Element's Rainbow pool, verbatim -- {@code 0} is a real entry and resolves to no effect. */
    private static final int[] RAINBOW_EFFECT_IDS = {
        0, 1, 3, 5, 8, 10, 11, 12, 13, 14, 16, 21, 22, 23, 24, 26, 28, 29, 30, 32
    };

    /** "Rainbow Energy Potion", coloured letter by letter exactly as Energy Element writes it. */
    private static final String RAINBOW_NAME =
            "§4R§6a§ei§an§2b§bo§3w §9E§1n§5e§dr§cg§4y §6P§eo§at§2i§bo§3n";

    /**
     * The Lucky Block mod's {@code #luckyPotionEffects} pool, taken from the decompiled mod: its
     * {@code usefulStatusEffectIds} list filtered to the non-HARMFUL ones. {@code glowing} really is
     * listed twice in the mod, so it is here too — the template can hand out both entries.
     */
    private static final String[] LUCKY_POTION_EFFECTS = {
        "speed", "haste", "strength", "instant_health", "jump_boost", "regeneration", "resistance",
        "fire_resistance", "water_breathing", "invisibility", "night_vision", "absorption",
        "saturation", "glowing", "glowing"
    };

    /** The Holy Water Potion's explicit effect rows, exactly as the Water LB drop writes them. */
    private static final String[][] HOLY_WATER_ROWS = {
        {"minecraft:jump_boost", "yakurum:climb"},
        {"minecraft:regeneration", "yakurum:resurrection"},
        {"minecraft:saturation", "minecraft:slow_falling"},
        {"minecraft:strength"},
        {"yakurum:archery", "minecraft:speed"},
        {"minecraft:haste", "minecraft:resistance"},
        {"yakurum:repair", "minecraft:health_boost"},
        {"yakurum:thorns", "minecraft:absorption"},
        {"minecraft:water_breathing", "minecraft:fire_resistance", "yakurum:immovable"},
        {"minecraft:luck", "minecraft:dolphins_grace"},
        {"minecraft:invisibility", "yakurum:step_up"},
        {"minecraft:instant_health", "yakurum:cure"}
    };
    /** Only the first 8 Holy Water rows carry {@code Amplifier=#rand(0,3)}; the rest default to 0. */
    private static final int HOLY_WATER_AMPLIFIED_ROWS = 8;

    /** Rebuilds one of the lucky blocks' named potions, byte-for-byte like the drop that spawns it. */
    private static ItemStack customPotion(String key, RandomSource rng) {
        ArmorPotion armor = ARMOR_POTIONS.get(key);
        if (armor != null) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("yakurum", "yakurum_splash_potion"));
            if (item == null) {
                return ItemStack.EMPTY;
            }
            ItemStack s = new ItemStack(item);
            CompoundTag tag = s.getOrCreateTag();
            tag.putInt("CustomPotionColor", armor.colour());
            ListTag effects = new ListTag();
            addPlainEffect(effects, "yakurum:armor_boost", armor.amplifier(), 3600);
            if (effects.isEmpty()) {
                return ItemStack.EMPTY;                 // Yakurum absent: don't sell an inert bottle
            }
            tag.put("CustomPotionEffects", effects);
            nameTag(s, armor.name(), "green");
            return s;
        }
        if ("@double_energy".equals(key) || "@fighting_energy".equals(key) || "@rainbow_energy".equals(key)
                || "@full_heal_energy".equals(key)) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "potion"));
            if (item == null) {
                return ItemStack.EMPTY;
            }
            ItemStack s = new ItemStack(item);
            CompoundTag tag = s.getOrCreateTag();
            tag.putString("Potion", "minecraft:water");
            ListTag effects = new ListTag();
            String name;
            if ("@double_energy".equals(key)) {
                addRawEffect(effects, 22, 4, 500 + rng.nextInt(501));            // Absorption V, 25-50 s
                name = "§e§lDouble Energy Potion";
            } else if ("@fighting_energy".equals(key)) {
                addRawEffect(effects, 5, 2, 200 + rng.nextInt(301));             // Strength III, 10-25 s
                name = "§c§lFighting Energy Potion";
            } else if ("@full_heal_energy".equals(key)) {
                // Instant Health V -> heal(4 << 4) = 64, clamped to max health: a guaranteed full heal.
                // Duration 6 is what the drops.txt writes; vanilla ignores it for instantaneous effects.
                addRawEffect(effects, 6, 4, 6);
                name = "§d§lFull Heal Energy Potion";
            } else {
                for (int i = 0; i < 4; i++) {                                    // 4 draws, id 0 = nothing
                    addRawEffect(effects, RAINBOW_EFFECT_IDS[rng.nextInt(RAINBOW_EFFECT_IDS.length)],
                            rng.nextInt(4), 300 + rng.nextInt(2701));
                }
                name = RAINBOW_NAME;
            }
            if (!effects.isEmpty()) {
                tag.put("CustomPotionEffects", effects);
            }
            plainNameTag(s, name);
            return s;
        }
        if ("@water_potion".equals(key)) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("yakurum", "yakurum_potion"));
            if (item == null) {
                return ItemStack.EMPTY;
            }
            ItemStack s = new ItemStack(item);
            CompoundTag tag = s.getOrCreateTag();
            tag.putInt("CustomPotionColor", 12318719);
            ListTag effects = new ListTag();
            for (int i = 0; i < HOLY_WATER_ROWS.length; i++) {
                String[] row = HOLY_WATER_ROWS[i];
                int amplifier = i < HOLY_WATER_AMPLIFIED_ROWS ? rng.nextInt(4) : 0;
                int duration = i == HOLY_WATER_ROWS.length - 1 ? 1 : 5000 + rng.nextInt(7001);
                addPlainEffect(effects, row[rng.nextInt(row.length)], amplifier, duration);
            }
            if (!effects.isEmpty()) {
                tag.put("CustomPotionEffects", effects);
            }
            nameTag(s, "Holy Water Potion", "dark_blue");
            return s;
        }

        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "potion"));
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack s = new ItemStack(item);
        s.getOrCreateTag().putString("Potion", "fire_resistance");
        // #luckyPotionEffects == chooseMultiRandomFrom(random, pool, 7..10): 7-10 distinct pool entries
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < LUCKY_POTION_EFFECTS.length; i++) {
            order.add(i);
        }
        shuffle(order, rng);
        int n = Math.min(7 + rng.nextInt(4), order.size());
        ListTag effects = new ListTag();
        for (int i = 0; i < n; i++) {
            addTemplateEffect(effects, "minecraft:" + LUCKY_POTION_EFFECTS[order.get(i)], rng);
        }
        if (!effects.isEmpty()) {
            s.getOrCreateTag().put("CustomPotionEffects", effects);
        }
        boolean hero = "@hero_potion".equals(key);
        nameTag(s, hero ? "Hero's Potion" : "Lucky Potion", hero ? "blue" : "red");
        return s;
    }

    /** One {@code #luckyPotionEffects} entry, as the mod's {@code randEffectInstance} builds it. */
    private static void addTemplateEffect(ListTag list, String effectId, RandomSource rng) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(effectId));
        if (effect == null) {
            return;
        }
        int id = MobEffect.getId(effect);
        if (id < 0 || id > 127) {
            return;                                     // CustomPotionEffects keeps the id as a byte
        }
        int max = effect.isInstantenous() ? 0 : 9600;   // instants get Duration 0
        int min = max / 3;
        CompoundTag t = new CompoundTag();
        t.putByte("Id", (byte) id);
        t.putByte("Amplifier", (byte) rng.nextInt(4));
        t.putInt("Duration", max == 0 ? 0 : min + rng.nextInt(max - min + 1));
        t.putBoolean("Ambient", false);
        t.putBoolean("ShowParticles", true);
        t.putBoolean("ShowIcon", true);
        list.add(t);
    }

    /** An effect row with only Id / Amplifier / Duration — the Holy Water Potion's shape. */
    private static void addPlainEffect(ListTag list, String effectId, int amplifier, int duration) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(effectId));
        if (effect == null) {
            return;                                     // effect from an absent mod: just leave it out
        }
        int id = MobEffect.getId(effect);
        if (id < 0 || id > 127) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putByte("Id", (byte) id);
        t.putByte("Amplifier", (byte) amplifier);
        t.putInt("Duration", duration);
        list.add(t);
    }

    private static void nameTag(ItemStack s, String text, String colour) {
        s.getOrCreateTagElement("display").putString("Name",
                "{\"text\":\"" + text + "\",\"color\":\"" + colour + "\",\"bold\":true}");
    }

    /** {@code display.Name} with no JSON style: the drops.txt names that carry their own § codes. */
    private static void plainNameTag(ItemStack s, String text) {
        s.getOrCreateTagElement("display").putString("Name", "{\"text\":\"" + text + "\"}");
    }

    /** A {@code CustomPotionEffects} entry written with the drops.txt's raw numeric effect id. */
    private static void addRawEffect(ListTag list, int id, int amplifier, int duration) {
        CompoundTag t = new CompoundTag();
        t.putByte("Id", (byte) id);
        t.putByte("Amplifier", (byte) amplifier);
        t.putInt("Duration", duration);
        list.add(t);
    }

    // Block value tiers (user 2026-07-10): Water is the best block of the pack, Chaos second, then
    // Pink/Morbius, then everything else. The tier drives the PRICE and the guaranteed-block rule
    // below, not the Luck band (which is set purely by the machine's rarity).
    private static final String T1_WATER = "lucky:water_lucky_block";
    private static final String T2_CHAOS = "lucky:chaosluckyblock";
    private static final java.util.Set<String> T3_BLOCKS =
            java.util.Set.of("lucky:pink_lucky_block", "lucky:morbius_lucky_block");

    /** 0 = T1 (Water), 1 = T2 (Chaos), 2 = T3 (Pink/Morbius), 3 = T4 (everything else). */
    private static int infusedTier(ResourceLocation id) {
        String s = id.toString();
        if (T1_WATER.equals(s)) return 0;
        if (T2_CHAOS.equals(s)) return 1;
        if (T3_BLOCKS.contains(s)) return 2;
        return 3;
    }

    /**
     * MEDIAN level price [tier 0-3][rarity ordinal 0-3], the user's grid (2026-07-10). The actual
     * price shifts +/-1 with where the rolled Luck lands in the band (see {@link #luckPriceShift}):
     * a low-Luck roll costs median-1, a high-Luck roll median+1. Legendary is flat +100, so it always
     * costs the median.
     */
    private static final int[][] INFUSED_PRICES = {
            // Common Rare Epic Legend
            { 10,   12,  16,  17 },   // T1 Water
            {  9,   11,  15,  16 },   // T2 Chaos
            {  6,    8,  12,  12 },   // T3 Pink / Morbius
            {  5,    7,  11,  11 },   // T4 everything else
    };

    /** How many of each infused block a machine sells per rarity (user 2026-07-10). */
    private static final int[] INFUSED_QTY = { 1, 3, 5, 8 };   // common, rare, epic, legendary

    /**
     * Infused-block machine: 5-8 RANDOM lucky-block types, each infused to a random Luck inside the
     * machine's rarity band — common +10..+30, rare +30..+70, epic +70..+100, legendary flat +100.
     * Values snap to steps of 5. Capped blocks (Water/Chaos) stay in EVERY band (aligned with the
     * lucky events, user 2026-07-06): their offered Luck is clamped to their own cap and the QUANTITY
     * given follows the rarity — a legendary sells 8 of each.
     *
     * <p>Price is a tier x rarity grid (user 2026-07-10): the block's own value tier (Water/Chaos/…)
     * and the machine's rarity together set the level cost, flat within a band. A LEGENDARY machine is
     * guaranteed to stock Water OR Chaos (whichever the shuffle offers; if both are absent from the
     * draw one is forced in).
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
        // anomaly (user 2026-07-04: if it cannot be infused naturally, it gets no machine).
        List<ResourceLocation> pool = new ArrayList<>(LuckyTweaksApi.getLuckyBlockIds());
        pool.removeIf(id -> !"lucky".equals(id.getNamespace()));
        shuffle(pool, rng);
        int n = drawCount(pool.size(), rng, 5, 4);      // this machine shows 5-8 lines

        // Legendary guarantee: make sure Water OR Chaos is inside the first n picked. If neither made
        // the cut, swap one that IS in the pool into a random offered slot.
        if (rarity == Rarity.LEGENDARY) {
            ensurePremiumInDraw(pool, n, rng);
        }

        int qty = INFUSED_QTY[rarity.ordinal()];
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
                s.setCount(qty);
                int price = INFUSED_PRICES[infusedTier(id)][rarity.ordinal()] + luckPriceShift(luck, min, max);
                out.add(new Article(s, price));
            }
        }
    }

    /** Where the rolled Luck sits in the band -> price shift: lower third -1, middle 0, upper third +1
     *  (a flat band, i.e. legendary, always returns 0). Uses the FINAL Luck, so a capped block's
     *  clamped value drives an honest shift. */
    private static int luckPriceShift(int luck, int min, int max) {
        if (max <= min) {
            return 0;
        }
        float t = (float) (luck - min) / (max - min);
        if (t < 1.0F / 3.0F) return -1;
        if (t > 2.0F / 3.0F) return +1;
        return 0;
    }

    /** Force Water or Chaos into the first {@code n} of the (already shuffled) pool, if neither is there. */
    private static void ensurePremiumInDraw(List<ResourceLocation> pool, int n, RandomSource rng) {
        for (int i = 0; i < n && i < pool.size(); i++) {
            int t = infusedTier(pool.get(i));
            if (t == 0 || t == 1) {
                return;                                     // a premium block is already on offer
            }
        }
        // find any premium anywhere in the pool (beyond slot n) and swap it into a random offered slot
        for (int j = n; j < pool.size(); j++) {
            int t = infusedTier(pool.get(j));
            if (t == 0 || t == 1) {
                java.util.Collections.swap(pool, j, rng.nextInt(n));
                return;
            }
        }
        // neither Water nor Chaos installed at all — nothing to guarantee, leave the draw as is
    }

    /** In-place Fisher-Yates with the world's RandomSource (Collections.shuffle needs java.util.Random). */
    private static <T> void shuffle(List<T> list, RandomSource rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    /**
     * Materials machine ({@link MachineType#ORES}) — four shelves: three infusion-ingredient families
     * (Chaos / Born-in-Chaos, vanilla, Yakurum) plus gear/progression materials. It draws a random
     * 7–10 of the lines available at the machine's rarity (pure random: no guaranteed family mix, but
     * with a 9–15-line pool per rarity most machines still show a spread; user 2026-07-07).
     *
     * <p>Design principles (user, iterated 2026-07-07): the machine's RARITY is the reward — a rarer
     * machine is just harder to find — so good goods appear early and higher-rarity machines give MORE
     * of them at a FLAT per-line price (a legendary sells 5–6 krampus for the same price a rare sells
     * 1–2). Each line's quantity is rolled inside its per-rarity band at generation and then frozen, so
     * two same-rarity machines differ (the same line can come out mediocre or good). Infusion mats are sold
     * raw (no NBT — the player infuses the block of their choice); prices are flat starting values, easy
     * to tune. A line whose item is absent (e.g. BiC/Yakurum not installed) is filtered out first, so
     * the 7–10 shown are always real items.
     */
    private static void rollMaterials(List<Article> out, Rarity rarity, RandomSource rng) {
        int r = rarity.ordinal();
        List<Line> eligible = new ArrayList<>();
        for (Line line : MATERIALS_POOL) {
            if (line.bands()[r] != null && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(line.id()))) {
                eligible.add(line);
            }
        }
        shuffle(eligible, rng);
        int show = drawCount(eligible.size(), rng);
        for (int i = 0; i < show; i++) {
            Line line = eligible.get(i);
            int[] band = line.bands()[r];
            int count = band[0] + (band[1] > band[0] ? rng.nextInt(band[1] - band[0] + 1) : 0);
            ItemStack s = stack(line.id(), count);
            if (!s.isEmpty()) {
                out.add(new Article(s, line.prices()[r]));
            }
        }
    }

    /**
     * One materials line: an item, its level price PER RARITY (indexed by {@link Rarity#ordinal()},
     * 0 where not sold), and its quantity band per rarity ({@code null} = not sold at that rarity,
     * {@code {min,max}} = quantity range rolled at generation). Per-rarity prices since the tier-list
     * pass (user 2026-07-09).
     */
    private record Line(String id, int[] prices, int[][] bands) {}

    // Prices from the user's tier-list pass (2026-07-09). Prestige ingredients (water diamond, nether
    // star, krampus) are deliberately dearer per infusion point than generic ones (gold, diamond).
    private static final Line[] MATERIALS_POOL = {
        // --- Chaos infusion (Born in Chaos) ---
        // COMMON quantities trimmed alongside the Water lines (2026-07-09): both premium families now
        // yield ~4.4-5.5 infusion points per level at Common, half the old rate.
        new Line("born_in_chaos_v1:phantom_powder",    new int[]{10,  0,  0,  0}, new int[][]{ {8, 14},  null,     null,     null    }),
        new Line("born_in_chaos_v1:dark_rod",          new int[]{ 9, 13,  0,  0}, new int[][]{ {3, 5},   {7, 12},  null,     null    }),
        new Line("born_in_chaos_v1:fire_dust",         new int[]{ 9,  0,  0,  0}, new int[][]{ {8, 12},  null,     null,     null    }),
        new Line("born_in_chaos_v1:seedof_chaos",      new int[]{ 0, 10, 11,  0}, new int[][]{ null,     {2, 4},   {5, 9},   null    }),
        new Line("born_in_chaos_v1:krampus_horn",      new int[]{ 0, 13, 14, 14}, new int[][]{ null,     {1, 2},   {2, 4},   {7, 8}  }),
        new Line("born_in_chaos_v1:orbofthe_summoner", new int[]{ 0,  0,  0, 12}, new int[][]{ null,     null,     null,     {8, 9}  }),
        // --- Vanilla infusion ---
        new Line("minecraft:gold_ingot",               new int[]{ 4,  0,  0,  0}, new int[][]{ {10, 18}, null,     null,     null    }),
        new Line("minecraft:diamond",                  new int[]{ 5,  9,  0,  0}, new int[][]{ {4, 6},   {7, 12},  null,     null    }),
        new Line("minecraft:gold_block",               new int[]{ 7,  0,  0,  0}, new int[][]{ {1, 2},   null,     null,     null    }),
        new Line("minecraft:nether_star",              new int[]{ 0, 14, 16, 16}, new int[][]{ null,     {1, 2},   {2, 4},   {8, 9}  }),
        new Line("minecraft:golden_apple",             new int[]{ 0,  7,  0,  0}, new int[][]{ null,     {2, 4},   null,     null    }),
        new Line("minecraft:diamond_block",            new int[]{ 0,  0, 12,  0}, new int[][]{ null,     null,     {2, 3},   null    }),
        // every rarity, aligned with the Consumables machine (user 2026-07-09)
        new Line("minecraft:enchanted_golden_apple",   new int[]{11, 13, 14, 13}, new int[][]{ {1, 1},   {2, 3},   {4, 5},   {7, 8}  }),
        // --- Yakurum infusion (Water LB) ---
        // COMMON quantities halved (2026-07-09: a common machine is not meant to reward much)
        // -- pearl and aquamarine aligned on the same infusion yield (~4.4 pts/level).
        new Line("yakurum:pearl",                      new int[]{11,  0,  0,  0}, new int[][]{ {6, 10},  null,     null,     null    }),
        new Line("yakurum:aquamarine",                 new int[]{11, 14,  0,  0}, new int[][]{ {5, 7},   {10, 16}, null,     null    }),
        new Line("yakurum:pearl_block",                new int[]{11,  0,  0,  0}, new int[][]{ {1, 1},   null,     null,     null    }),
        new Line("yakurum:water_diamond",              new int[]{ 0, 15, 17, 17}, new int[][]{ null,     {1, 2},   {2, 4},   {8, 9}  }),
        new Line("yakurum:coral_crystal_block",        new int[]{ 0, 14,  0,  0}, new int[][]{ null,     {2, 3},   null,     null    }),
        new Line("yakurum:prismarine_gem_block",       new int[]{ 0,  0, 13,  0}, new int[][]{ null,     null,     {3, 4},   null    }),
        new Line("yakurum:aquamarine_block",           new int[]{ 0,  0,  0, 15}, new int[][]{ null,     null,     null,     {7, 8}  }),
        // --- Gear / progression materials ---
        new Line("minecraft:end_portal_frame",         new int[]{10, 13, 11, 11}, new int[][]{ {2, 2},   {6, 7},   {11, 12}, {12, 12} }),
        new Line("minecraft:ender_eye",                new int[]{ 6, 11, 11, 11}, new int[][]{ {1, 1},   {2, 3},   {7, 9},   {12, 12} }),
        new Line("minecraft:ender_pearl",              new int[]{ 6,  5, 10,  0}, new int[][]{ {5, 5},   {6, 7},   {12, 12}, null    }),
        new Line("minecraft:blaze_rod",                new int[]{ 6,  7, 10,  0}, new int[][]{ {1, 2},   {3, 4},   {6, 6},   null    }),
        new Line("minecraft:netherite_ingot",          new int[]{ 9, 12,  0,  0}, new int[][]{ {1, 3},   {6, 12},  null,     null    }),
        new Line("minecraft:netherite_block",          new int[]{ 0,  0, 15, 15}, new int[][]{ null,     null,     {2, 3},   {4, 5}  }),
        new Line("minecraft:netherite_upgrade_smithing_template", new int[]{ 8,  8,  0,  0}, new int[][]{ {1, 1}, {1, 2}, null, null }),
    };

    private static ItemStack infusedBlock(ResourceLocation blockId, int luck) {
        Item item = ForgeRegistries.ITEMS.getValue(blockId);   // block items share the block's id
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack s = new ItemStack(item);
        s.getOrCreateTag().putInt("Luck", luck);
        return s;
    }

    // =========================== TOOLS machine ===========================

    /**
     * Tools machine ({@link MachineType#TOOLS}): a boutique of UNIQUE gear — lucky tools, artifacts &
     * relics (Artifacts / Relics / Confluence-Terraria), totem-style accessories (Yakurum), mobility
     * pieces, Sophisticated Backpacks + upgrade modules. Unlike the materials machine, quantity is
     * always 1 and the machine's RARITY gates the QUALITY of what's offered, not the amount. A machine
     * shows 7–10 items drawn at random from its rarity's pool.
     *
     * <p>Lucky tools are a capped slot (user 2026-07-07): EPIC offers exactly ONE random lucky tool,
     * LEGENDARY offers TWO distinct ones among Radar / Hammer / Ring / Belt — the rest of the 7–10 are
     * filled from the rarity's non-tool pool. Backpacks come dyed a random cloth colour; the XP-pump
     * module is bundled with its tank (Article.extra). Prices are the user's tier-list pass
     * (2026-07-09, ranges C 3-11 / R 5-15 / E 10-17 / L 11-17).
     */
    private static void rollTools(List<Article> out, Rarity rarity, RandomSource rng) {
        int total = LuckyXpCommonConfig.COMMON.debugFullStock.get() ? Integer.MAX_VALUE : 7 + rng.nextInt(4);
        int toolsAdded = 0;

        // Capped lucky-tool slot. 16/17 = the top of the Epic/Legendary tier-list ranges, alongside
        // the totems and the crystal heart (suggested 2026-07-09, pending the user's confirmation).
        if (rarity == Rarity.EPIC) {
            ItemStack t = toolStack(ALL_LUCKY_TOOLS[rng.nextInt(ALL_LUCKY_TOOLS.length)]);
            if (!t.isEmpty()) { out.add(new Article(t, 16)); toolsAdded++; }
        } else if (rarity == Rarity.LEGENDARY) {
            List<String> pick = new ArrayList<>(List.of(LEGENDARY_TOOLS));
            shuffle(pick, rng);
            for (int i = 0; i < Math.min(2, pick.size()); i++) {
                ItemStack t = toolStack(pick.get(i));
                if (!t.isEmpty()) { out.add(new Article(t, 17)); toolsAdded++; }
            }
        }

        // Fill the rest from the rarity's non-tool pool.
        List<ToolLine> pool = new ArrayList<>();
        for (ToolLine line : TOOLS_POOL) {
            if (line.rarity() == rarity && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(line.id()))) {
                pool.add(line);
            }
        }
        shuffle(pool, rng);
        int fill = Math.min(total - toolsAdded, pool.size());
        for (int i = 0; i < fill; i++) {
            ToolLine line = pool.get(i);
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(line.id())));
            if (line.randomColor()) {
                applyRandomBackpackColor(stack, rng);
            }
            applyTooltipNbtFix(stack, line.id());
            ItemStack extra = ItemStack.EMPTY;
            if (line.extra() != null && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(line.extra()))) {
                extra = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(line.extra())));
            }
            out.add(new Article(stack, extra, line.price()));
        }
    }

    /**
     * Correct cosmetic NBT so the machine's preview matches what the item really does in this pack.
     * Right now the only offender is Yakurum's Pandilla's Totem: its tooltip reads a raw {@code uses}
     * tag (defaulting to 5), but the pack's real reuse count comes from TotemBeforePlayerRevive
     * ({@code multiUseTotems = ["yakurum:pandilla_totem=2"]}). Writing {@code uses=2} up front stops the
     * "5 remaining" false hope — the item behaves as 2 either way, only the label was lying.
     */
    private static void applyTooltipNbtFix(ItemStack stack, String id) {
        if ("yakurum:pandilla_totem".equals(id)) {
            stack.getOrCreateTag().putInt("uses", 2);
        }
    }

    private static ItemStack toolStack(String name) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("luckytools", name));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** Tint a Sophisticated Backpack a random cloth colour (harmless no-op if the NBT keys differ). */
    private static void applyRandomBackpackColor(ItemStack stack, RandomSource rng) {
        int colour = rng.nextInt(0xFFFFFF + 1);
        stack.getOrCreateTag().putInt("clothColor", colour);
        stack.getOrCreateTag().putInt("borderColor", colour);
    }

    private static final String[] ALL_LUCKY_TOOLS = {
        "lucky_radar", "lucky_wand", "lucky_shield", "lucky_spawner",
        "lucky_idol", "lucky_hammer", "lucky_ring", "lucky_belt"
    };
    private static final String[] LEGENDARY_TOOLS = {"lucky_radar", "lucky_hammer", "lucky_ring", "lucky_belt"};

    /** One tools-machine line: item, the rarity it is stocked at, price, an optional bundled bonus item
     *  ({@code extra}), and whether it is a backpack that should get a random cloth colour. Quantity is
     *  always 1. */
    private record ToolLine(String id, Rarity rarity, int price, String extra, boolean randomColor) {}

    private static final ToolLine[] TOOLS_POOL = {
        // Prices from the user's tier-list pass (2026-07-09). Tools run deliberately dearer than the
        // other machines ("elles donnent du concret") — hence e.g. anvil/crafting upgrades back at 8.
        // ---------- COMMON ----------
        new ToolLine("relics:bastion_ring",              Rarity.COMMON,  9, null, false),
        new ToolLine("relics:roller_skates",             Rarity.COMMON, 10, null, false),
        new ToolLine("relics:magma_walker",              Rarity.COMMON, 10, null, false),
        new ToolLine("relics:aqua_walker",               Rarity.COMMON, 10, null, false),
        new ToolLine("confluence:hand_drill",            Rarity.COMMON,  9, null, false),
        new ToolLine("confluence:hand_warmer",           Rarity.COMMON,  7, null, false),
        new ToolLine("confluence:flower_boots",          Rarity.COMMON,  4, null, false),
        new ToolLine("artifacts:panic_necklace",         Rarity.COMMON, 11, null, false),
        new ToolLine("artifacts:feral_claws",            Rarity.COMMON, 11, null, false),
        new ToolLine("artifacts:whoopee_cushion",        Rarity.COMMON,  4, null, false),
        new ToolLine("sophisticatedbackpacks:backpack",  Rarity.COMMON,  9, null, true),
        new ToolLine("sophisticatedbackpacks:anvil_upgrade",    Rarity.COMMON, 8, null, false),
        new ToolLine("sophisticatedbackpacks:crafting_upgrade", Rarity.COMMON, 8, null, false),
        new ToolLine("artifacts:villager_hat",           Rarity.COMMON,  8, null, false),
        new ToolLine("artifacts:lucky_scarf",            Rarity.COMMON, 10, null, false),
        new ToolLine("artifacts:night_vision_goggles",   Rarity.COMMON,  9, null, false),
        new ToolLine("confluence:aglet",                 Rarity.COMMON,  9, null, false),
        new ToolLine("confluence:flashlight",            Rarity.COMMON,  9, null, false),
        new ToolLine("confluence:fast_clock",            Rarity.COMMON,  9, null, false),
        new ToolLine("artifacts:snowshoes",              Rarity.COMMON,  7, null, false),
        // ---------- RARE ----------
        new ToolLine("luckyxp:lucky_glasses",            Rarity.RARE, 13, null, false),
        new ToolLine("artifacts:kitty_slippers",         Rarity.RARE, 13, null, false),
        new ToolLine("artifacts:flame_pendant",          Rarity.RARE, 12, null, false),
        new ToolLine("artifacts:charm_of_sinking",       Rarity.RARE, 11, null, false),
        new ToolLine("artifacts:cloud_in_a_bottle",      Rarity.RARE, 12, null, false),
        new ToolLine("relics:leather_belt",              Rarity.RARE, 15, null, false),
        new ToolLine("relics:reflection_necklace",       Rarity.RARE, 13, null, false),
        new ToolLine("confluence:sun_stone",             Rarity.RARE, 14, null, false),
        new ToolLine("confluence:moon_stone",            Rarity.RARE, 14, null, false),
        new ToolLine("confluence:magma_stone",           Rarity.RARE, 14, null, false),
        new ToolLine("confluence:toolbox",               Rarity.RARE, 15, null, false),
        new ToolLine("confluence:magiluminescence",      Rarity.RARE, 12, null, false),
        new ToolLine("sophisticatedbackpacks:gold_backpack", Rarity.RARE, 10, null, true),
        new ToolLine("sophisticatedbackpacks:advanced_feeding_upgrade", Rarity.RARE, 9, null, false),
        new ToolLine("sophisticatedbackpacks:stack_upgrade_tier_3",     Rarity.RARE, 9, null, false),
        new ToolLine("confluence:flipper",               Rarity.RARE, 11, null, false),
        new ToolLine("confluence:lightning_boots",       Rarity.RARE, 15, null, false),
        new ToolLine("artifacts:pickaxe_heater",         Rarity.RARE, 11, null, false),
        new ToolLine("artifacts:rooted_boots",           Rarity.RARE, 11, null, false),
        new ToolLine("artifacts:helium_flamingo",        Rarity.RARE, 11, null, false),
        // ---------- EPIC ----------
        new ToolLine("minecraft:totem_of_undying",       Rarity.EPIC, 17, null, false),
        new ToolLine("artifacts:chorus_totem",           Rarity.EPIC, 17, null, false),
        new ToolLine("artifacts:bunny_hoppers",          Rarity.EPIC, 14, null, false),
        new ToolLine("artifacts:golden_hook",            Rarity.EPIC, 12, null, false),
        new ToolLine("artifacts:cross_necklace",         Rarity.EPIC, 12, null, false),
        new ToolLine("confluence:extendo_grip",          Rarity.EPIC, 14, null, false),
        new ToolLine("confluence:warrior_emblem",        Rarity.EPIC, 15, null, false),
        new ToolLine("confluence:putrid_scent",          Rarity.EPIC, 12, null, false),
        new ToolLine("relics:enders_hand",               Rarity.EPIC, 14, null, false),
        new ToolLine("minecraft:elytra",                 Rarity.EPIC, 13, null, false),
        new ToolLine("fuze_relics:jetpack_playbutton_chestplate", Rarity.EPIC, 13, null, false),
        new ToolLine("fuze_relics:grapplin_hook",        Rarity.EPIC, 12, null, false),
        new ToolLine("relics:space_dissector",           Rarity.EPIC, 16, null, false),
        new ToolLine("confluence:band_of_regeneration",  Rarity.EPIC, 13, null, false),
        new ToolLine("sophisticatedbackpacks:diamond_backpack", Rarity.EPIC, 10, null, true),
        new ToolLine("sophisticatedbackpacks:xp_pump_upgrade",  Rarity.EPIC, 10, "sophisticatedbackpacks:tank_upgrade", false),
        new ToolLine("artifacts:antidote_vessel",        Rarity.EPIC, 12, null, false),
        new ToolLine("confluence:ranger_emblem",         Rarity.EPIC, 15, null, false),
        new ToolLine("confluence:brain_of_confusion",    Rarity.EPIC, 12, null, false),
        new ToolLine("confluence:shark_tooth_necklace",  Rarity.EPIC, 12, null, false),
        new ToolLine("confluence:terraspark_boots",      Rarity.EPIC, 15, null, false),
        new ToolLine("artifacts:snorkel",                Rarity.EPIC, 11, null, false),
        // ---------- LEGENDARY ----------
        new ToolLine("yakurum:pandilla_totem",           Rarity.LEGENDARY, 16, null, false),
        new ToolLine("yakurum:pearl_necklace",           Rarity.LEGENDARY, 17, null, false),
        new ToolLine("artifacts:crystal_heart",          Rarity.LEGENDARY, 17, null, false),
        new ToolLine("artifacts:vampiric_glove",         Rarity.LEGENDARY, 12, null, false),
        new ToolLine("confluence:frozen_turtle_shell",   Rarity.LEGENDARY, 14, null, false),
        new ToolLine("confluence:bundle_of_horseshoe_balloons", Rarity.LEGENDARY, 13, null, false),
        new ToolLine("confluence:demon_heart",           Rarity.LEGENDARY, 12, null, false),
        new ToolLine("yakurum:angel_wings",              Rarity.LEGENDARY, 16, null, false),
        new ToolLine("sophisticatedbackpacks:netherite_backpack", Rarity.LEGENDARY, 11, null, true),
        new ToolLine("confluence:ankh_shield",           Rarity.LEGENDARY, 16, null, false),
        new ToolLine("confluence:worm_scarf",            Rarity.LEGENDARY, 12, null, false),
        new ToolLine("confluence:celestial_stone",       Rarity.LEGENDARY, 15, null, false),
        new ToolLine("yakurum:king_triton_amulet",       Rarity.LEGENDARY, 17, null, false),
        new ToolLine("sophisticatedbackpacks:advanced_alchemy_upgrade", Rarity.LEGENDARY, 11, null, false),
    };

    private static ItemStack stack(String id, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }
}
