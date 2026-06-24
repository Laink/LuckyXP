package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the debug "Luck +X" / "×N XP" holograms above lucky blocks (driven by {@code /luckychance debug}).
 * Billboards the text toward the camera and renders it with an 8-direction BLACK OUTLINE via
 * {@link Font#drawInBatch8xOutline} for readability against any background.
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = Dist.CLIENT)
public final class DebugBlockRenderer {
    private static final int LIGHT = 0xF000F0;   // full bright

    private DebugBlockRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ClientDebugBlocks.pos.length == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Font font = mc.font;
        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        for (int i = 0; i < ClientDebugBlocks.pos.length; i++) {
            String label = i < ClientDebugBlocks.labels.length ? ClientDebugBlocks.labels[i] : "";
            if (label.isEmpty()) {
                continue;
            }
            BlockPos bp = BlockPos.of(ClientDebugBlocks.pos[i]);
            int color = label.contains("XP") ? 0xFF55FFFF : 0xFFFFD23D;   // aqua for XP, gold for Luck
            ps.pushPose();
            ps.translate(bp.getX() + 0.5 - camPos.x, bp.getY() + 1.3 - camPos.y, bp.getZ() + 0.5 - camPos.z);
            ps.mulPose(cam.rotation());                 // face the camera (billboard)
            ps.scale(-0.025F, -0.025F, 0.025F);          // flip text right-way + scale to world
            Matrix4f mat = ps.last().pose();
            float x = -font.width(label) / 2.0F;         // centre horizontally
            font.drawInBatch8xOutline(FormattedCharSequence.forward(label, Style.EMPTY),
                    x, 0.0F, color, 0xFF000000, mat, buffer, LIGHT);
            ps.popPose();
        }
        buffer.endBatch();
    }
}
