package com.lwi.luckyxp.client;

import com.lwi.luckyxp.entity.LuckyMerchant;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The merchant's GeckoLib renderer (designer's model + idle/sale animations). */
public class LuckyMerchantRenderer extends GeoEntityRenderer<LuckyMerchant> {
    public LuckyMerchantRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new LuckyMerchantGeoModel());
    }
}
