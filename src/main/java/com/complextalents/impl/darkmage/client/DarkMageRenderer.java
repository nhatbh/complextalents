package com.complextalents.impl.darkmage.client;

import com.complextalents.impl.darkmage.origin.DarkMageOrigin;
import com.complextalents.origin.client.OriginRenderer;
import com.complextalents.passive.client.ClientPassiveStackData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import org.joml.Matrix4f;

/**
 * Minimalist Arc HUD renderer for Dark Mage Arcane Entropy.
 * Renders a clean 240-degree arc around the crosshair.
 * Smoothly lerps decay, and when Possessed, smoothly shows remaining Possession duration (15s -> 0s).
 */
public class DarkMageRenderer implements OriginRenderer {

    private float smoothProgress = 0.0f;

    @Override
    public void renderHUD(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int entropy = ClientPassiveStackData.getStackCount("entropy");
        int originLevel = Math.min(4, Math.max(0, com.complextalents.origin.client.ClientOriginData.getOriginLevel() - 1));
        double threshold = DarkMageOrigin.ELDRITCH_REQUIRED_THRESHOLD[originLevel];

        MobEffectInstance possessedEffect = mc.player.getEffect(com.complextalents.effect.ModEffects.POSSESSED.get());
        boolean isPossessed = (possessedEffect != null);
        boolean isRedline = entropy >= threshold;

        float targetProgress;
        if (isPossessed) {
            // Smoothly show remaining Possession duration (300 ticks max = 15s)
            float maxDuration = 300.0f;
            float currentDuration = (float) possessedEffect.getDuration();
            targetProgress = Math.min(1.0f, Math.max(0.0f, currentDuration / maxDuration));
        } else {
            targetProgress = Math.min(1.0f, Math.max(0.0f, entropy / 100.0f));
        }

        // Smooth HUD lerp animation across frames
        smoothProgress = Mth.lerp(0.12f, smoothProgress, targetProgress);
        if (Math.abs(smoothProgress - targetProgress) < 0.001f) {
            smoothProgress = targetProgress;
        }

        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;

        // Arc Parameters around crosshair
        float radiusInner = 16.0f;
        float radiusOuter = 20.0f;
        float startAngle = -120.0f; // Start top-left
        float totalSweep = 240.0f;  // Total arc angle (deg)

        float filledSweep = totalSweep * smoothProgress;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        // 1. Background Arc Track (Darker when Possessed)
        int trackColor = isPossessed ? 0x88440066 : 0x55220033;
        drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner, radiusOuter, 
                startAngle, totalSweep, trackColor);

        // 2. Filled Progress Arc (Possessed = Glowing Void Magenta Pulse, Redline = Red, Normal = Violet)
        if (smoothProgress > 0.001f) {
            int arcColor;
            long time = System.currentTimeMillis();
            if (isPossessed) {
                // Pulsing Void Magenta/Cyan for Locked Possessed State
                float pulse = (float) ((Math.sin(time / 100.0) + 1.0) / 2.0);
                int r = (int) (160 + pulse * 60);
                int g = (int) (0 + pulse * 40);
                int b = (int) (220 + pulse * 35);
                arcColor = (255 << 24) | (r << 16) | (g << 8) | b;
            } else if (isRedline) {
                // Vibrant Pulsing Red for Redline
                float pulse = (float) ((Math.sin(time / 150.0) + 1.0) / 2.0);
                int r = 255;
                int g = (int) (30 + pulse * 40);
                int b = (int) (30 + pulse * 40);
                arcColor = (240 << 24) | (r << 16) | (g << 8) | b;
            } else {
                // Arcane Violet / Purple
                arcColor = 0xEEA033FF;
            }

            drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner, radiusOuter, 
                    startAngle, filledSweep, arcColor);
        }

        // 3. Threshold Tick Marker (Hidden during Possession for clean countdown arc)
        if (!isPossessed) {
            float thresholdProgress = (float) (threshold / 100.0);
            float thresholdAngle = startAngle + (totalSweep * thresholdProgress);
            drawTickMarker(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner - 2, radiusOuter + 2, thresholdAngle, 0xFFFFFFFF);
        }

        RenderSystem.disableBlend();
    }

    private void drawArcSegment(Tesselator tesselator, BufferBuilder bufferBuilder, Matrix4f matrix, float cx, float cy, 
                                float rInner, float rOuter, float startAngleDeg, float sweepAngleDeg, int color) {
        int segments = Math.max(8, (int) (Math.abs(sweepAngleDeg) / 3.0f));
        float startRad = (float) Math.toRadians(startAngleDeg - 90);
        float sweepRad = (float) Math.toRadians(sweepAngleDeg);

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = startRad + (sweepRad * (i / (float) segments));
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float xOuter = cx + rOuter * cos;
            float yOuter = cy + rOuter * sin;
            float xInner = cx + rInner * cos;
            float yInner = cy + rInner * sin;

            bufferBuilder.vertex(matrix, xOuter, yOuter, 0).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, xInner, yInner, 0).color(r, g, b, a).endVertex();
        }
        tesselator.end();
    }

    private void drawTickMarker(Tesselator tesselator, BufferBuilder bufferBuilder, Matrix4f matrix, float cx, float cy, 
                                float rInner, float rOuter, float angleDeg, int color) {
        float rad = (float) Math.toRadians(angleDeg - 90);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        float xOuter = cx + rOuter * cos;
        float yOuter = cy + rOuter * sin;
        float xInner = cx + rInner * cos;
        float yInner = cy + rInner * sin;

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        bufferBuilder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.vertex(matrix, xInner, yInner, 0).color(r, g, b, a).endVertex();
        bufferBuilder.vertex(matrix, xOuter, yOuter, 0).color(r, g, b, a).endVertex();
        tesselator.end();
    }
}
