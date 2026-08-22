package com.complextalents.impl.spellblade.client;

import com.complextalents.impl.spellblade.SpellbladeDataProvider;
import com.complextalents.origin.client.OriginRenderer;
import com.complextalents.spellmastery.SpellSchool;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * Single-Arc HUD renderer for the Spellblade origin matching the Elemental Mage design pattern.
 * - Main Arc: Tracks active elemental imbue charge / 6s enhanced duration countdown on top with active element colors.
 * - Thin Inner Sub-Bar: Tracks 30s Overcharge stance remaining duration with an electric gold countdown bar.
 */
public class SpellbladeRenderer implements OriginRenderer {

    private float smoothImbueProgress = 0.0f;
    private float smoothSubBarFade = 0.0f;

    @Override
    public void renderHUD(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || player.isSpectator()) return;

        var capOpt = player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA).resolve();
        if (capOpt.isEmpty()) return;

        var cap = capOpt.get();
        SpellSchool activeElement = cap.getActiveElement();
        int enhancedTicks = cap.getEnhancedAttackTicks();
        boolean hasCharge = cap.hasImbueCharge();

        boolean isOvercharge = cap.isOverchargeStance();
        boolean hasFreeCast = cap.hasFreeCast();

        // Calculate Target Progress Values (6-second imbue duration countdown)
        float targetImbue = 0.0f;
        if (enhancedTicks > 0) {
            targetImbue = Math.min(1.0f, Math.max(0.0f, enhancedTicks / 120.0f));
        } else if (hasCharge) {
            targetImbue = 1.0f;
        }

        // Smooth Lerp Animations
        smoothImbueProgress = Mth.lerp(0.15f, smoothImbueProgress, targetImbue);
        if (Math.abs(smoothImbueProgress - targetImbue) < 0.001f) {
            smoothImbueProgress = targetImbue;
        }

        float targetSubBarFade = isOvercharge ? 1.0f : 0.0f;
        smoothSubBarFade = Mth.lerp(0.15f, smoothSubBarFade, targetSubBarFade);
        if (Math.abs(smoothSubBarFade - targetSubBarFade) < 0.001f) {
            smoothSubBarFade = targetSubBarFade;
        }

        // Only return if stance is off and both HUD elements have fully faded out
        if (!isOvercharge && smoothImbueProgress <= 0.001f && smoothSubBarFade <= 0.001f) {
            return;
        }

        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;

        // Main Arc Radii framing the right side of the crosshair
        float radiusInner = 17.0f;
        float radiusOuter = 21.0f;
        float startAngle = -60.0f;
        float totalSweep = 120.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        int elementColor = getSchoolColor(activeElement);
        long gameTime = player.level().getGameTime();

        // 1. Main Background Track (Dark Slate)
        int darkTrack = 0x4410121A;
        drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner, radiusOuter, startAngle, totalSweep, darkTrack);

        // 2. Main Imbue Progress Arc (Tracks Imbue Charge & 6s Enhanced Time Left)
        if (smoothImbueProgress > 0.001f) {
            boolean isAboveHalf = isOvercharge && enhancedTicks > 60;
            float pulseRate = isAboveHalf ? 0.35f : 0.25f;
            float pulseMin = isAboveHalf ? 0.88f : 0.85f;
            float pulseMax = isAboveHalf ? 1.00f : 0.95f;

            float pulse = pulseMin + (pulseMax - pulseMin) * (float) Math.sin(gameTime * pulseRate);
            int alpha = (int) (0xEE * pulse);
            int filledColor = (alpha << 24) | (elementColor & 0x00FFFFFF);

            float fillSweep = totalSweep * smoothImbueProgress;
            drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner, radiusOuter, startAngle, fillSweep, filledColor);

            // Draw 50% threshold marker during Overcharge
            if (isOvercharge) {
                float halfSweep = totalSweep * 0.5f;
                int markerColor = isAboveHalf ? 0xFFFFFFFF : 0xAAFFFFFF;
                drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner - 0.5f, radiusOuter + 0.5f, startAngle + halfSweep - 1.0f, 2.0f, markerColor);
            }
        }

        // 3. Thin Inner Sub-Bar (Replaces Temp Mana: Glows Gold when Free Cast ready, Glows Blue in Overcharge)
        if (smoothSubBarFade > 0.001f) {
            int alphaBase = (int) (0xDD * smoothSubBarFade);
            int subBarBg = (int) (0x33 * smoothSubBarFade) << 24 | 0x10121A;
            drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner - 3.5f, radiusInner - 1.5f, startAngle, totalSweep, subBarBg);

            int subBarColor;
            if (hasFreeCast) {
                // Gold light up when Free Cast is ready
                float pulse = 0.80f + 0.20f * (float) Math.sin(gameTime * 0.3);
                int alpha = (int) (alphaBase * pulse);
                subBarColor = (alpha << 24) | 0xFFD700; // Bright Warm Gold (#FFD700)
            } else {
                // Electric Cyan-Blue in Overcharge
                int alpha = (int) (alphaBase * 0.9f);
                subBarColor = (alpha << 24) | 0x00BFFF; // Electric Deep Sky Blue (#00BFFF)
            }
            drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner - 3.5f, radiusInner - 1.5f, startAngle, totalSweep, subBarColor);
        }

        RenderSystem.disableBlend();

        // 4. "FREE CAST" Gold Label (Displayed when Free Cast is active during Overcharge Stance)
        if (isOvercharge && hasFreeCast) {
            String labelText = "FREE CAST";
            int textColor = 0xFFFFD700; // Gold

            float scale = 0.55f;
            float textWidth = minecraft.font.width(labelText) * scale;

            float textX = centerX + 12.55f - (textWidth * 0.5f);
            float textY = centerY + 10.0f;

            graphics.pose().pushPose();
            graphics.pose().scale(scale, scale, scale);
            graphics.drawString(minecraft.font, labelText, (int) (textX / scale), (int) (textY / scale), textColor, true);
            graphics.pose().popPose();
        }
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

    private int getSchoolColor(SpellSchool school) {
        if (school == null) return 0xFFFFFFFF;
        return switch (school) {
            case FIRE -> 0xFFE05A47;
            case ICE -> 0xFF6BBBC9;
            case LIGHTNING -> 0xFFCFB34A;
            case NATURE -> 0xFF68A378;
            case AQUA -> 0xFF5592C2;
            case EVOCATION -> 0xFFE0E0E0;
            case BLOOD -> 0xFFB22222;
            case ENDER -> 0xFF9366BF;
            case ELDRITCH -> 0xFF8A2BE2;
            case HOLY -> 0xFFFFFFE0;
        };
    }
}
