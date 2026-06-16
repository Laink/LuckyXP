package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.entity.LuckyXpOrb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders {@link LuckyXpOrb} exactly like vanilla's ExperienceOrbRenderer (billboard, value->icon,
 * bobbing), but with our blue-recoloured orb texture and a subtle uniform brightness pulse (so the
 * blue isn't re-tinted by the vanilla green shimmer).
 */
public class LuckyXpOrbRenderer extends EntityRenderer<LuckyXpOrb> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(LuckyXpMod.MODID, "textures/entity/lucky_xp_orb.png");
    private static final RenderType RENDER_TYPE = RenderType.itemEntityTranslucentCull(TEXTURE);

    public LuckyXpOrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.15f;
        this.shadowStrength = 0.75f;
    }

    @Override
    protected int getBlockLightLevel(LuckyXpOrb orb, BlockPos pos) {
        return Mth.clamp(super.getBlockLightLevel(orb, pos) + 7, 0, 15);
    }

    @Override
    public void render(LuckyXpOrb orb, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        int icon = orb.getIcon();
        float u0 = (icon % 4 * 16) / 64.0f;
        float u1 = (icon % 4 * 16 + 16) / 64.0f;
        float v0 = (icon / 4 * 16) / 64.0f;
        float v1 = (icon / 4 * 16 + 16) / 64.0f;
        float t = ((float) orb.tickCount + partialTick) / 2.0f;
        int red = 80;                                                 // light-azure base (like the bar), not navy
        int green = 170 + (int) ((Mth.sin(t) + 1.0f) * 0.5f * 85.0f); // 170..255 -> light blue shimmering to cyan
        pose.translate(0.0f, 0.1f, 0.0f);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0f));
        pose.scale(0.3f, 0.3f, 0.3f);
        VertexConsumer vc = buffer.getBuffer(RENDER_TYPE);
        PoseStack.Pose last = pose.last();
        Matrix4f mat = last.pose();
        Matrix3f norm = last.normal();
        vertex(vc, mat, norm, -0.5f, -0.25f, red, green, u0, v1, packedLight);
        vertex(vc, mat, norm, 0.5f, -0.25f, red, green, u1, v1, packedLight);
        vertex(vc, mat, norm, 0.5f, 0.75f, red, green, u1, v0, packedLight);
        vertex(vc, mat, norm, -0.5f, 0.75f, red, green, u0, v0, packedLight);
        pose.popPose();
        super.render(orb, yaw, partialTick, pose, buffer, packedLight);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, Matrix3f norm, float x, float y, int red, int green, float u, float v, int light) {
        vc.vertex(mat, x, y, 0.0f).color(red, green, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(norm, 0.0f, 1.0f, 0.0f).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(LuckyXpOrb orb) {
        return TEXTURE;
    }
}
