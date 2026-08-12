package com.complextalents.impl.marksman.client;

import com.complextalents.TalentsMod;
import com.complextalents.tacz.HeartRateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Client Handler for Marksman Adrenaline state shader & mob head reticle image rendering.
 * Native glowing red mob model outlines are handled strictly via EntityMixin while active.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
public class ClientAdrenalineMarkRenderer {

    private static final ResourceLocation RED_MONO_SHADER = ResourceLocation.fromNamespaceAndPath("complextalents", "shaders/post/red_monochromatic.json");
    private static final ResourceLocation FALLBACK_SHADER = ResourceLocation.fromNamespaceAndPath("minecraft", "shaders/post/desaturate.json");
    private static final ResourceLocation RETICLE_TEXTURE = ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/marksman/reticle.png");

    private static boolean shaderActive = false;
    private static float shaderProgress = 0.0f;
    private static Field passesField = null;

    static {
        try {
            passesField = PostChain.class.getDeclaredField("passes");
            passesField.setAccessible(true);
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (shaderActive) {
                clearShader(mc);
            }
            return;
        }

        boolean active = ClientAdrenalineFXHandler.isActive();

        if (active) {
            shaderProgress = Math.min(1.0f, shaderProgress + 0.15f);
            if (!shaderActive || mc.gameRenderer.currentEffect() == null) {
                enableShader(mc);
            }
        } else {
            shaderProgress = Math.max(0.0f, shaderProgress - 0.15f);

            if (shaderProgress <= 0.0f && (shaderActive || mc.gameRenderer.currentEffect() != null)) {
                clearShader(mc);
            }
        }

        float cubicEase = shaderProgress * shaderProgress * (3.0f - 2.0f * shaderProgress);
        updateShaderUniform(mc, cubicEase);

        if (active) {
            HeartRateManager.setHeartRate(mc.player, HeartRateManager.RESTING_BPM);
        }
    }

    /**
     * Renders reticle.png image on target mobs' heads billboarded facing the camera during Adrenaline,
     * offset toward player camera by the mob's bounding box to prevent model obstruction.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (!ClientAdrenalineFXHandler.isActive()) {
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

    private static void enableShader(Minecraft mc) {
        try {
            mc.gameRenderer.loadEffect(RED_MONO_SHADER);
            shaderActive = true;
        } catch (Exception e) {
            try {
                mc.gameRenderer.loadEffect(FALLBACK_SHADER);
                shaderActive = true;
            } catch (Exception ex) {
                shaderActive = false;
            }
        }
    }

    private static void clearShader(Minecraft mc) {
        try {
            if (mc.gameRenderer != null) {
                mc.gameRenderer.shutdownEffect();
            }
        } catch (Exception ignored) {}
        shaderActive = false;
        shaderProgress = 0.0f;
    }

    private static void updateShaderUniform(Minecraft mc, float value) {
        if (mc.gameRenderer != null && mc.gameRenderer.currentEffect() != null && passesField != null) {
            try {
                PostChain chain = mc.gameRenderer.currentEffect();
                @SuppressWarnings("unchecked")
                List<PostPass> passes = (List<PostPass>) passesField.get(chain);
                if (passes != null) {
                    for (PostPass pass : passes) {
                        Uniform uniform = pass.getEffect().getUniform("Progress");
                        if (uniform != null) {
                            uniform.set(value);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
