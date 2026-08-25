package com.complextalents.impl.marksman.client;

import com.complextalents.TalentsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Client Handler for Marksman mob weak point reticle rendering when holding a TACZ gun and Aiming Down Sights (ADS).
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
public class ClientAdrenalineMarkRenderer {

    private static final ResourceLocation RETICLE_TEXTURE = ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/marksman/reticle.png");

    /**
     * Renders reticle.png image on target mobs' heads billboarded facing the camera when holding a TACZ gun and ADS,
     * offset toward player camera by the mob's bounding box to prevent model obstruction.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Check if player is holding a TACZ gun and Aiming Down Sights
        if (IGun.getIGunOrNull(mc.player.getMainHandItem()) == null) {
            return;
        }

        IGunOperator operator = IGunOperator.fromLivingEntity(mc.player);
        boolean isAiming = operator.getSynIsAiming() || operator.getSynAimingProgress() > 0.1f;
        if (!isAiming) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        float partialTick = event.getPartialTick();
        Vec3 camPos = camera.getPosition();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(RETICLE_TEXTURE));

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player || !living.isAlive() || living.isDeadOrDying()) continue;

            Vec3 eyePos = living.getEyePosition(partialTick);
            Vec3 dirToCam = camPos.subtract(eyePos);
            double distSq = dirToCam.lengthSqr();
            if (distSq < 0.0001) continue;

            Vec3 dirNorm = dirToCam.scale(1.0 / Math.sqrt(distSq));

            // Offset position toward player camera by mob bounding box width to prevent model obstruction
            double bbOffset = Math.max(living.getBbWidth() * 0.5f, 0.35f) + 0.1f;
            Vec3 renderPos = eyePos.add(dirNorm.scale(bbOffset));

            poseStack.pushPose();
            poseStack.translate(renderPos.x - camPos.x, renderPos.y - camPos.y, renderPos.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));

            // Render reticle texture quad
            float quadSize = 0.5f;
            drawTexturedQuad(vc, poseStack.last().pose(), poseStack.last().normal(), quadSize);

            poseStack.popPose();
        }

        bufferSource.endBatch();
    }

    private static void drawTexturedQuad(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float size) {
        float half = size * 0.5f;
        int packedLight = 0xF000F0; // Full brightness

        vc.vertex(pose, -half, half, 0.0f).color(255, 255, 255, 255).uv(0.0f, 0.0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(pose, -half, -half, 0.0f).color(255, 255, 255, 255).uv(0.0f, 1.0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(pose, half, -half, 0.0f).color(255, 255, 255, 255).uv(1.0f, 1.0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(pose, half, half, 0.0f).color(255, 255, 255, 255).uv(1.0f, 0.0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0.0f, 0.0f, 1.0f).endVertex();
    }
}
