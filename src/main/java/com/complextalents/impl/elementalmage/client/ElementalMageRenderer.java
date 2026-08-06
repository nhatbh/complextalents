package com.complextalents.impl.elementalmage.client;

import com.complextalents.elemental.ElementType;
import com.complextalents.impl.elementalmage.ElementalMageDataProvider;
import com.complextalents.origin.client.OriginRenderer;
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
 * Custom 3-Segment Arc HUD renderer for the Elemental Mage origin.
 * Renders 3 distinct curved arc segments framing the right side of the crosshair.
 * Segments light up individually according to Echo count with Apex Element colors.
 */
public class ElementalMageRenderer implements OriginRenderer {

    private static final float[] SEGMENT_STARTS = { -60.0f, -18.0f, 24.0f };
    private static final float SEGMENT_SWEEP = 36.0f;

    @Override
    public void renderHUD(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || player.isSpectator()) return;

        var capOpt = player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).resolve();
        if (capOpt.isEmpty()) return;

        var cap = capOpt.get();
        int echoCount = cap.getEchoCount();
        ElementType apexElement = cap.getApexElement();
        float lockedMult = cap.getLockedHarmonyMultiplier();

        var effect = player.getEffect(com.complextalents.elemental.effects.ElementalEffects.HARMONIC_CONVERGENCE.get());
        boolean isConvergenceActive = lockedMult > 0.0f || effect != null;

        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;

        // Arc Radii
        float radiusInner = 17.0f;
        float radiusOuter = 21.0f;

        long gameTime = player.level().getGameTime();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        // Render 3 Distinct Curved Arc Segments
        for (int i = 0; i < 3; i++) {
            float segStart = SEGMENT_STARTS[i];
            boolean isCharged = i < echoCount;

            int baseColor = getElementColor(apexElement);

            if (isConvergenceActive) {
                // Convergence Active: All 3 segments glow with the active Apex Element color
                float pulse = 0.75f + 0.25f * (float) Math.sin(gameTime * 0.35 + i * 0.8);
                int alpha = (int) (0xEE * pulse);
                int elemFill = (alpha << 24) | (baseColor & 0x00FFFFFF);

                drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner, radiusOuter, segStart, SEGMENT_SWEEP, elemFill);

            } else if (isCharged) {
                // Charged Segment: Illuminated Apex Element color
                int elemFill = (0xEE << 24) | (baseColor & 0x00FFFFFF);

                drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner, radiusOuter, segStart, SEGMENT_SWEEP, elemFill);

            } else {
                // Uncharged Segment: Subtle dark slate outline segment
                int darkTrack = 0x4410121A;
                drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner, radiusOuter, segStart, SEGMENT_SWEEP, darkTrack);
            }
        }

        // Convergence Countdown Bar (Thin 1px inner arc draining during Convergence)
        if (isConvergenceActive) {
            float durationProgress = effect != null ? (float) effect.getDuration() / 200.0f : 1.0f;
            durationProgress = Math.max(0.0f, Math.min(1.0f, durationProgress));

            float fullStart = SEGMENT_STARTS[0];
            float fullSweep = (SEGMENT_STARTS[2] + SEGMENT_SWEEP) - fullStart;
            float drainSweep = fullSweep * durationProgress;

            drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner - 3, radiusInner - 2, fullStart, drainSweep, 0xDDFFD700);
        }

        RenderSystem.disableBlend();
    }

    private void drawArcSegment(Tesselator tesselator, BufferBuilder bufferBuilder, Matrix4f matrix, float cx, float cy,
                                float rInner, float rOuter, float startAngleDeg, float sweepAngleDeg, int color) {
        int segments = Math.max(6, (int) (Math.abs(sweepAngleDeg) / 3.0f));
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

    private int getElementColor(ElementType type) {
        if (type == null) return 0xFFFFD700;
        return switch (type) {
            case FIRE -> 0xFFE05A47;
            case ICE -> 0xFF6BBBC9;
            case LIGHTNING -> 0xFFCFB34A;
            case NATURE -> 0xFF68A378;
            case ENDER -> 0xFF9366BF;
            case AQUA -> 0xFF5592C2;
            default -> 0xFFFFD700;
        };
    }
}
