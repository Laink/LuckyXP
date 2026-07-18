package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.entity.LuckyMerchant;
import com.lwi.luckyxp.machine.Rarity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.EnumMap;
import java.util.Map;

/**
 * GeckoLib wiring for the Lucky Merchant: geometry, texture and the idle/sale animation file.
 *
 * <p>His skin follows his RARITY (designer textures 2026-07-18 — the scientist's glasses change tint
 * with his tier, the wearable version of his discount), each with its blown-up twin for a ruined stand
 * (see {@link LuckyMerchant#isExploded}). Rarity is synced entity data, so the swap is live: a
 * {@code /luckyevent merchant} reroll re-skins him on the next frame.
 */
public class LuckyMerchantGeoModel extends GeoModel<LuckyMerchant> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(LuckyXpMod.MODID, "geo/lucky_merchant.geo.json");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(LuckyXpMod.MODID, "animations/lucky_merchant.animation.json");

    private static final Map<Rarity, ResourceLocation> TEXTURES = byRarity("");
    private static final Map<Rarity, ResourceLocation> TEXTURES_EXPLODED = byRarity("exploded_");

    private static Map<Rarity, ResourceLocation> byRarity(String prefix) {
        Map<Rarity, ResourceLocation> map = new EnumMap<>(Rarity.class);
        for (Rarity r : Rarity.values()) {
            map.put(r, new ResourceLocation(LuckyXpMod.MODID,
                    "textures/entity/lucky_merchant_" + prefix + r.getSerializedName() + ".png"));
        }
        return map;
    }

    @Override
    public ResourceLocation getModelResource(LuckyMerchant animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LuckyMerchant animatable) {
        return (animatable.isExploded() ? TEXTURES_EXPLODED : TEXTURES).get(animatable.getRarity());
    }

    @Override
    public ResourceLocation getAnimationResource(LuckyMerchant animatable) {
        return ANIMATION;
    }

    // NO head-bone tracking here, on purpose: the IDLE animation keyframes the "head" bone (the
    // scientist's head bobbing), and a setCustomAnimations override fights those keyframes every
    // frame — tried 2026-07-19, the merchant visibly trembled. Facing the player is done by the
    // BODY instead: LookAtPlayerGoal (probability 1.0) turns the entity, and the renderer rotates
    // the whole model smoothly. The head keeps its animated life.
}
