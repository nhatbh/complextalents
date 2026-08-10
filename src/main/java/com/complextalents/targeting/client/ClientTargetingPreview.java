package com.complextalents.targeting.client;

import com.complextalents.skill.Skill;
import com.complextalents.skill.client.ChannelManager;
import com.complextalents.targeting.*;
import com.complextalents.util.KeyHelper;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side preview system for targeting visuals while channeling.
 *
 * <p>Renders different indicators based on targeting type:</p>
 * <ul>
 *   <li><b>ENTITY:</b> Box outline around targeted entity</li>
 *   <li><b>POSITION:</b> Crosshair and circle at ground position</li>
 *   <li><b>DIRECTION:</b> Direction arrow with range circle</li>
 * </ul>
 *
 * <p><b>Behavior:</b></p>
 * <ul>
 *   <li>Only renders when actively channeling a skill</li>
 *   <li>Uses the channeling skill's targeting configuration (range, allowed types)</li>
 *   <li>Updates in real-time as the player moves their crosshair</li>
 *   <li>Color indicates ally (green) vs enemy (red) for entity targeting</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "complextalents")
public class ClientTargetingPreview {

    private static final Minecraft MC = Minecraft.getInstance();
    private static TargetingSnapshot snapshot;
    private static TargetType currentTargetingType;

    /**
     * Update the targeting preview based on the channeling skill's configuration.
     * Only updates when actively channeling a skill.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Only render preview when channeling
        if (!ChannelManager.isChanneling() || MC.player == null) {
            snapshot = null;
            currentTargetingType = null;
            return;
        }

        Player player = MC.player;

        // Get the skill currently being channeled
        Skill skill = ChannelManager.getCurrentChannelingSkill();
        if (skill == null) {
            snapshot = null;
            currentTargetingType = null;
            return;
        }

        currentTargetingType = skill.getTargetingType();

        // Build targeting request using skill's configuration
        TargetingRequest.Builder requestBuilder = TargetingRequest.builder(player)
                .maxRange(skill.getMaxRange());

        boolean isShiftDown = KeyHelper.isShiftDown();
        boolean disableSmartCast = !SmartCastManager.isSmartCastEnabled();
        boolean forcePlayerOnly = isShiftDown && skill.canTargetPlayer();

        // Set allowed types and shared filters
        requestBuilder.allowTargetSelf(skill.allowsSelfTarget() && isShiftDown)
                .targetAllyOnly(skill.targetsAllyOnly())
                .targetPlayerOnly(skill.targetsPlayerOnly() || forcePlayerOnly)
                .disableSmartCast(disableSmartCast);

        switch (currentTargetingType) {
            case NONE -> {
                // No preview for self-target skills
                snapshot = null;
                currentTargetingType = null;
                return;
            }
            case DIRECTION -> {
                requestBuilder.allowedTypes(TargetType.DIRECTION, TargetType.POSITION);
            }
            case POSITION -> {
                requestBuilder.allowedTypes(TargetType.POSITION);
            }
            case ENTITY -> {
                requestBuilder.allowedTypes(TargetType.ENTITY, TargetType.POSITION);
            }
        }

        snapshot = ClientTargetingResolver.getInstance().resolve(requestBuilder.build());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }
        if (snapshot == null || currentTargetingType == null) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();

        MultiBufferSource.BufferSource buffer =
                MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        // Render based on targeting type
        switch (currentTargetingType) {
            case ENTITY -> renderEntityReticle(pose, buffer);
            case POSITION -> {
                if (snapshot.hasEntity()) {
                    renderEntityReticle(pose, buffer);
                } else {
                    renderPositionReticle(pose, buffer);
                }
            }
            case DIRECTION -> renderDirectionReticle(pose, buffer);
            case NONE -> {
                // No preview for self-target skills
            }
        }

        pose.popPose();
        buffer.endBatch();
    }

    /**
     * Render reticle for entity targeting.
     * Shows a box outline around the targeted entity.
     * Color indicates ally (green) or enemy (red).
     */
    private static void renderEntityReticle(PoseStack pose, MultiBufferSource buffer) {
        if (!snapshot.hasEntity()) {
            // No entity targeted, fall back to position reticle
            renderPositionReticle(pose, buffer);
            return;
        }

        Entity entity = MC.player.level().getEntity(snapshot.getTargetEntityId());
        if (entity == null || !entity.isAlive()) {
            return;
        }

        // Determine color based on ally status
        float r = snapshot.isAlly() ? 0.0f : 1.0f;
        float g = snapshot.isAlly() ? 1.0f : 0.0f;
        float b = 0.0f;

        AABB box = entity.getBoundingBox().inflate(0.1);
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        VertexConsumer vc = buffer.getBuffer(RenderType.lines());

        // Draw 3D box around entity bounding box
        box(vc, pose, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 0.5f);
    }

    /**
     * Render reticle for position targeting.
     * Shows a bright, high-visibility dual circle and crosshair at the target position.
     */
    private static void renderPositionReticle(PoseStack pose, MultiBufferSource buffer) {
        VertexConsumer vc = buffer.getBuffer(RenderType.lines());
        Vec3 p = snapshot.getTargetPosition();

        float x = (float) p.x;
        float y = (float) p.y + 0.03f; // prevent z-fighting
        float z = (float) p.z;
        float size = 0.8f;

        // Vibrant Cyan / Gold color for position target
        float r = 0.0f;
        float g = 0.9f;
        float b = 1.0f;
        float a = 1.0f;

        // Bold Crosshair lines
        line(vc, pose, x - size, y, z, x + size, y, z, r, g, b, a);
        line(vc, pose, x, y, z - size, x, y, z + size, r, g, b, a);

        // Dual Concentric Circles for high visibility
        circle(vc, pose, x, y, z, size * 1.2f, 32, r, g, b, 1.0f);
        circle(vc, pose, x, y, z, size * 1.6f, 32, r, g, b, 0.8f);

        // Corner tick accents
        float corner = size * 0.6f;
        line(vc, pose, x - corner, y, z - corner, x + corner, y, z + corner, r, g, b, 0.7f);
        line(vc, pose, x + corner, y, z - corner, x - corner, y, z + corner, r, g, b, 0.7f);
    }

    /**
     * Render reticle for direction targeting.
     * Shows a bright crosshair and target circle.
     */
    private static void renderDirectionReticle(PoseStack pose, MultiBufferSource buffer) {
        Vec3 p = snapshot.getTargetPosition();

        float x = (float) p.x;
        float y = (float) p.y + 0.03f;
        float z = (float) p.z;
        float size = 0.8f;

        VertexConsumer vc = buffer.getBuffer(RenderType.lines());
        // Bright Orange / Amber glow for direction target
        float r = 1.0f;
        float g = 0.7f;
        float b = 0.1f;

        line(vc, pose, x - size, y, z, x + size, y, z, r, g, b, 1.0f);
        line(vc, pose, x, y, z - size, x, y, z + size, r, g, b, 1.0f);

        circle(vc, pose, x, y, z, size * 1.2f, 32, r, g, b, 1.0f);
        circle(vc, pose, x, y, z, size * 1.5f, 32, r, g, b, 0.8f);
    }
    /* ================= HELPERS ================= */

    /**
     * Draw a 3D box outline.
     */
    private static void box(VertexConsumer vc, PoseStack pose,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float r, float g, float b, float a) {
        // Bottom face
        line(vc, pose, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(vc, pose, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(vc, pose, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(vc, pose, x1, y1, z2, x1, y1, z1, r, g, b, a);

        // Top face
        line(vc, pose, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(vc, pose, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(vc, pose, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(vc, pose, x1, y2, z2, x1, y2, z1, r, g, b, a);

        // Vertical edges
        line(vc, pose, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(vc, pose, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(vc, pose, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(vc, pose, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(VertexConsumer vc, PoseStack pose,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        PoseStack.Pose p = pose.last();
        int packedLight = 0xF000F0; // Full brightness
        vc.vertex(p.pose(), x1, y1, z1)
                .color(r, g, b, a)
                .uv(0, 0)
                .uv2(packedLight)
                .normal(p.normal(), 0, 1, 0)
                .endVertex();
        vc.vertex(p.pose(), x2, y2, z2)
                .color(r, g, b, a)
                .uv(0, 0)
                .uv2(packedLight)
                .normal(p.normal(), 0, 1, 0)
                .endVertex();
    }

    private static void circle(VertexConsumer vc, PoseStack pose,
                               float x, float y, float z,
                               float radius, int segments,
                               float r, float g, float b, float a) {
        float step = (float) (2 * Math.PI / segments);

        for (int i = 0; i < segments; i++) {
            float a1 = i * step;
            float a2 = (i + 1) * step;

            line(vc, pose,
                    x + (float) Math.cos(a1) * radius, y, z + (float) Math.sin(a1) * radius,
                    x + (float) Math.cos(a2) * radius, y, z + (float) Math.sin(a2) * radius,
                    r, g, b, a);
        }
    }
}
