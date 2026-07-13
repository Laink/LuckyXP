package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.entity.LuckyMerchant;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** GeckoLib wiring for the Lucky Merchant: geometry, texture and the idle/sale animation file. */
public class LuckyMerchantGeoModel extends GeoModel<LuckyMerchant> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(LuckyXpMod.MODID, "geo/lucky_merchant.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(LuckyXpMod.MODID, "textures/entity/lucky_merchant.png");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(LuckyXpMod.MODID, "animations/lucky_merchant.animation.json");

    @Override
    public ResourceLocation getModelResource(LuckyMerchant animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LuckyMerchant animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LuckyMerchant animatable) {
        return ANIMATION;
    }
}
