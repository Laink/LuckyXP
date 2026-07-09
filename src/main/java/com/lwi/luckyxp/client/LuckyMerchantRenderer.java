package com.lwi.luckyxp.client;

import com.lwi.luckyxp.entity.LuckyMerchant;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * MVP renderer: the vanilla villager model and base texture (same placeholder approach as the TOOLS
 * machine reusing the ORES art — a bespoke skin can come later). The always-visible name tag is what
 * identifies him at the stand.
 */
public class LuckyMerchantRenderer extends MobRenderer<LuckyMerchant, VillagerModel<LuckyMerchant>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/villager/villager.png");

    public LuckyMerchantRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerModel<>(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(LuckyMerchant entity) {
        return TEXTURE;
    }
}
