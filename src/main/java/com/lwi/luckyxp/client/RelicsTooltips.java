package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Better Relics tooltips: shows a one-line "what does this do" summary on every {@code relics:} item,
 * because the mod hides its real description behind a hold-to-research gesture that most players never
 * discover. This adds a short GENERAL-PURPOSE line (not the per-ability/upgrade details), in front of
 * the mod's own "Hold [X] to research…" prompt — the research mechanic itself is untouched.
 *
 * <p>Namespace-gated, no hard dependency on Relics: if the mod is absent, no {@code relics:} item
 * exists and this never fires. Self-contained on purpose, so it can be lifted into a standalone
 * "Better Relics Tooltips" mod later. English only (pack rule). Every line is condensed from the first
 * OBTAINING sentence of that item's wiki page (minecraft-guides.com/wiki/relics), except Quiver and
 * Spatial Sign — undocumented there — which are derived from the mod's own ability names.
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class RelicsTooltips {
    private RelicsTooltips() {}

    /** id -> short general description, each condensed from the first OBTAINING sentence of the item's
     *  own wiki page (minecraft-guides.com/wiki/relics/&lt;slug&gt;/), except the two undocumented ones
     *  noted below. */
    private static final Map<String, String> DESC = Map.ofEntries(
            // --- condensed from each item's OBTAINING paragraph on the mod's wiki (passive + active) ---
            Map.entry("infinity_ham",        "A meat that regenerates over time for endless food; infuse it with a potion for a bonus each bite."),
            Map.entry("bastion_ring",        "Piglins turn neutral, and nearby Piglins trail particles toward the closest bastion."),
            Map.entry("roller_skates",       "Build momentum to incredible speed while sprinting — every block turns slippery."),
            Map.entry("magma_walker",        "Walk on lava without sinking or taking damage, and shrug off hot blocks like magma."),
            Map.entry("aqua_walker",         "Walk on water for a limited time before the boots waterlog and you sink."),
            Map.entry("leather_belt",        "Adds extra Charm slots, so you can equip more accessories at once."),
            Map.entry("reflection_necklace", "Stores the damage you take, then releases it as an explosive burst of obsidian shards."),
            Map.entry("enders_hand",         "Endermen never turn hostile toward you, and you can swap places with any creature you look at."),
            Map.entry("space_dissector",     "Create temporary portals that let you teleport between two points."),
            Map.entry("holy_locket",         "Steal healing from enemies or weaponise your own to hurt them, with undead-slaying and brief immortality on a kill."),
            Map.entry("magic_mirror",        "Teleports you back to your spawn point from great distances."),
            Map.entry("shadow_glaive",       "Throw a glaive that bounces from enemy to enemy, spawning more clones as it levels."),
            Map.entry("blazing_flask",       "Creates localized flight zones powered by nearby flames."),
            Map.entry("elytra_booster",      "Steadily boosts your elytra speed from an internal fuel buffer — no firework rockets needed."),
            Map.entry("midnight_robe",       "Full invisibility in the dark with extra speed, plus a massive backstab hit from stealth."),
            Map.entry("jellyfish_necklace",  "Float in water and shock enemies on contact, sometimes paralysing them."),
            Map.entry("spore_sack",          "Below half health, automatically releases homing spores that damage nearby enemies."),
            Map.entry("ice_breaker",         "Removes ice sliding, and slams a shockwave into enemies below when you fall from height."),
            Map.entry("horse_flute",         "Stores a tamed horse to summon on demand, and heals it once upgraded."),
            Map.entry("drowned_belt",        "Big damage boost underwater, and lets you use the trident's Riptide anywhere — no water needed."),
            Map.entry("hunter_belt",         "Adds Charm slots and greatly boosts the damage of your tamed pets."),
            Map.entry("rage_glove",          "Chain attacks to build charges for self-healing and mobility, spent on a burst-damage dash."),
            Map.entry("ice_skates",          "Slide across ice to build speed, then ram enemies for momentum-based damage."),
            Map.entry("amphibian_boot",      "Faster movement while swimming or sprinting in rain, plus longer underwater breath."),
            Map.entry("chorus_inhibitor",    "Turns chorus fruit into a precision teleport, with an aim indicator showing where you'll land."),
            Map.entry("wool_mitten",         "Pack snow into hardened snowballs that damage, stun and freeze enemies."),
            // --- not on the wiki: derived from the mod's own ability names ---
            Map.entry("arrow_quiver",        "Stores arrows and looses them in charged shots."),
            Map.entry("spatial_sign",        "Marks a spot and rewinds you back to it after a delay (Time Rift)."),
            Map.entry("relic_experience_bottle", "Throw it to release relic experience.")
    );

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id == null || !"relics".equals(id.getNamespace())) {
            return;
        }
        String desc = DESC.get(id.getPath());
        if (desc == null) {
            return;
        }
        // Insert just under the name (index 1), before the mod's research prompt.
        int at = Math.min(1, event.getToolTip().size());
        event.getToolTip().add(at, Component.literal(desc).withStyle(ChatFormatting.GRAY));
    }
}
