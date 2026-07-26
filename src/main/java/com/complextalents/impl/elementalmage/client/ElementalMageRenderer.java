package com.complextalents.impl.elementalmage.client;

import com.complextalents.elemental.ElementType;
import com.complextalents.impl.elementalmage.ElementalMageDataProvider;
import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.origin.client.OriginRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.List;

/**
 * Custom HUD renderer for the Elemental Mage origin.
 * Normal state: Echoes fill top-down (Slot 0 at 120°).
 * Convergence state: Whole 6-slot combo bar fills completely and glows with the Apex Element color.
 */
public class ElementalMageRenderer implements OriginRenderer {

    private static final float ARC_INNER_RADIUS = 25f;
    private static final float ARC_OUTER_RADIUS = 28f;
    private static final float ARC_LENGTH = 120f;
    private static final int ARC_SEGMENTS = 40;

    private static final float RESONANCE_BOTTOM_ANGLE = 300f;
    private static final float ECHO_TOP_ANGLE = 120f; // Top of left arc

    private static final int RESONANCE_BG_COLOR = 0x99000000;
    private static final int RESONANCE_FILL_COLOR = 0x994D96FF;
    private static final int RESONANCE_BORDER_COLOR = 0x99FFFFFF;

    private static final int ECHO_BG_COLOR = 0x99000000;
    private static final int ECHO_DIVIDER_COLOR = 0x99000000;

    @Override
    public void renderHUD(GuiGraphics graphics, int screenWidth, int screenHeight) {
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        RenderSystem.enableBlend();
        renderResonanceArc(graphics, centerX, centerY);
        renderEchoArc(graphics, centerX, centerY);
        RenderSystem.disableBlend();
        renderLabels(graphics, centerX, centerY);
    }

    private void renderResonanceArc(GuiGraphics graphics, int centerX, int centerY) {
        double resonance = ClientOriginData.getResourceValue();
        double max = ClientOriginData.getResourceMax();
        double fillRatio = max > 0 ? Math.min(1.0, Math.max(0.0, resonance / max)) : 0;

        drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                RESONANCE_BOTTOM_ANGLE, RESONANCE_BOTTOM_ANGLE + ARC_LENGTH,
                ARC_SEGMENTS, RESONANCE_BG_COLOR);

        if (fillRatio > 0) {
            float fillAngleLength = ARC_LENGTH * (float) fillRatio;
            drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                    RESONANCE_BOTTOM_ANGLE, RESONANCE_BOTTOM_ANGLE + fillAngleLength,
                    (int) (ARC_SEGMENTS * fillRatio) + 1, RESONANCE_FILL_COLOR);
        }

        drawArcOutline(graphics, centerX, centerY, ARC_OUTER_RADIUS,
                RESONANCE_BOTTOM_ANGLE, RESONANCE_BOTTOM_ANGLE + ARC_LENGTH,
                ARC_SEGMENTS, RESONANCE_BORDER_COLOR);
    }

    /**
     * Render 6 Prismatic Echo slots on the LEFT arc:
     * - Convergence Active: All 6 slots fill and glow brightly with the Apex Element color.
     * - Normal Active: Individual Prismatic Echoes fill top-down from Slot 0 (120°).
     */
    private void renderEchoArc(GuiGraphics graphics, int centerX, int centerY) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        var capOpt = player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).resolve();
        boolean isConvergenceActive = capOpt.map(cap -> cap.getLockedHarmonyMultiplier() > 0.0f).orElse(false);
        ElementType apexElement = capOpt.map(cap -> cap.getApexElement()).orElse(null);

        int maxEchoes = 6;
        float segmentAngleLength = ARC_LENGTH / maxEchoes; // 20° per slot

        if (isConvergenceActive && apexElement != null) {
            // --- CONVERGENCE ACTIVE: Whole combo bar filled and glowing with Apex Element color ---
            int glowColor = getElementColor(apexElement);
            long gameTime = player.level().getGameTime();
            float pulse = 0.75f + 0.25f * (float) Math.sin(gameTime * 0.25);
            int alpha = (int) (0xAA * pulse);
            int vibrantGlowColor = (alpha << 24) | (glowColor & 0x00FFFFFF);

            for (int i = 0; i < maxEchoes; i++) {
                float slotStartAngle = 240f - ((i + 1) * segmentAngleLength);
                float slotEndAngle = 240f - (i * segmentAngleLength);
                drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                        slotStartAngle + 0.8f, slotEndAngle,
                        6, vibrantGlowColor);
            }

            for (int i = 1; i < maxEchoes; i++) {
                float dividerAngle = 240f - (i * segmentAngleLength);
                drawThickLine(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS, dividerAngle, ECHO_DIVIDER_COLOR);
            }
        } else {
            // --- NORMAL STATE: Individual Prismatic Echoes filling top-down ---
            List<ElementType> echoes = capOpt.map(cap -> cap.getEchoes()).orElse(Collections.emptyList());

            for (int i = 0; i < maxEchoes; i++) {
                float slotStartAngle = 240f - ((i + 1) * segmentAngleLength);
                float slotEndAngle = 240f - (i * segmentAngleLength);
                drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                        slotStartAngle + 0.8f, slotEndAngle,
                        6, ECHO_BG_COLOR);
            }

            for (int i = 0; i < echoes.size() && i < maxEchoes; i++) {
                ElementType type = echoes.get(i);
                int fillColor = getElementColor(type);
                float slotStartAngle = 240f - ((i + 1) * segmentAngleLength);
                float slotEndAngle = 240f - (i * segmentAngleLength);
                drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                        slotStartAngle + 0.8f, slotEndAngle,
                        6, fillColor);
            }

            for (int i = 1; i < maxEchoes; i++) {
                float dividerAngle = 240f - (i * segmentAngleLength);
                drawThickLine(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS, dividerAngle, ECHO_DIVIDER_COLOR);
            }
        }
    }

    private int getElementColor(ElementType type) {
        if (type == null) return 0xBBD4B056;
        return switch (type) {
            case FIRE -> 0xBBE05A47;
            case ICE -> 0xBB6BBBC9;
            case LIGHTNING -> 0xBBCFB34A;
            case NATURE -> 0xBB68A378;
            case ENDER -> 0xBB9366BF;
            case AQUA -> 0xBB5592C2;
            default -> 0xBBD4B056;
        };
    }

    private void renderLabels(GuiGraphics graphics, int centerX, int centerY) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        double resonance = ClientOriginData.getResourceValue();
        double resonanceMax = ClientOriginData.getResourceMax();
        String resText = (int) resonance + "/" + (int) resonanceMax;

        int resonanceTextX = (int) (centerX + ARC_OUTER_RADIUS + 6);
        int resonanceTextY = centerY - 3;

        graphics.pose().pushPose();
        graphics.pose().scale(0.7f, 0.7f, 0.7f);
        graphics.drawString(minecraft.font, resText,
                (int) (resonanceTextX / 0.7f), (int) (resonanceTextY / 0.7f), 0x99FFFFFF);
        graphics.pose().popPose();

        var capOpt = player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).resolve();
        float mult = capOpt.map(cap -> cap.getEffectiveHarmonyMultiplier()).orElse(0.5f);
        int echoCount = capOpt.map(cap -> cap.getEchoCount()).orElse(0);
        boolean isLocked = capOpt.map(cap -> cap.getLockedHarmonyMultiplier() > 0.0f).orElse(false);

        String multText = String.format("%.1fx (%d/6)", mult, echoCount);
        if (isLocked) {
            multText = String.format("%.1fx [CONVERGED]", mult);
        }

        int textWidth = minecraft.font.width(multText);
        int echoTextX = (int) (centerX - ARC_OUTER_RADIUS - 6 - textWidth * 0.7f);
        int echoTextY = centerY - 3;
        int echoColor = isLocked ? 0x99FFD93D : (echoCount >= 6 ? 0x99FF6B6B : 0x99FFFFFF);

        graphics.pose().pushPose();
        graphics.pose().scale(0.7f, 0.7f, 0.7f);
        graphics.drawString(minecraft.font, multText,
                (int) (echoTextX / 0.7f), (int) (echoTextY / 0.7f), echoColor);

        // Render locked Apex Element label during Convergence window
        if (isLocked && capOpt.isPresent() && capOpt.get().getApexElement() != null) {
            ElementType apex = capOpt.get().getApexElement();
            String apexText = "APEX: " + apex.name();
            int apexWidth = minecraft.font.width(apexText);
            int apexX = (int) (centerX - ARC_OUTER_RADIUS - 6 - apexWidth * 0.7f);
            graphics.drawString(minecraft.font, apexText,
                    (int) (apexX / 0.7f), (int) ((echoTextY - 9) / 0.7f), getElementColor(apex));
        }
        graphics.pose().popPose();
    }

    private void drawThickLine(GuiGraphics graphics, float cx, float cy, float innerRadius, float outerRadius, float angleDegrees, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();

        Tesselator tesselator = Tesselator.getInstance();
        var buf = tesselator.getBuilder();

        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        double rad = Math.toRadians(angleDegrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        float thickness = 4f;
        float perpCos = -sin * thickness;
        float perpSin = cos * thickness;

        float x1 = cx + cos * innerRadius + perpCos;
        float y1 = cy + sin * innerRadius + perpSin;
        float x2 = cx + cos * outerRadius + perpCos;
        float y2 = cy + sin * outerRadius + perpSin;
        float x3 = cx + cos * innerRadius - perpCos;
        float y3 = cy + sin * innerRadius - perpSin;
        float x4 = cx + cos * outerRadius - perpCos;
        float y4 = cy + sin * outerRadius - perpSin;

        buf.vertex(x1, y1, 0).color(r, g, b, a).endVertex();
        buf.vertex(x3, y3, 0).color(r, g, b, a).endVertex();
        buf.vertex(x2, y2, 0).color(r, g, b, a).endVertex();

        buf.vertex(x3, y3, 0).color(r, g, b, a).endVertex();
        buf.vertex(x4, y4, 0).color(r, g, b, a).endVertex();
        buf.vertex(x2, y2, 0).color(r, g, b, a).endVertex();

        tesselator.end();
    }

    private void drawArcSegment(GuiGraphics graphics, float cx, float cy, float innerRadius, float outerRadius,
                                float startAngle, float endAngle, int segments, int color) {

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();

        Tesselator tesselator = Tesselator.getInstance();
        var buf = tesselator.getBuilder();

        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        float angleStep = (endAngle - startAngle) / segments;

        for (int i = 0; i < segments; i++) {
            float a1 = startAngle + angleStep * i;
            float a2 = startAngle + angleStep * (i + 1);

            double rad1 = Math.toRadians(a1);
            double rad2 = Math.toRadians(a2);

            float cos1 = (float) Math.cos(rad1);
            float sin1 = (float) Math.sin(rad1);
            float cos2 = (float) Math.cos(rad2);
            float sin2 = (float) Math.sin(rad2);

            float outer1x = cx + cos1 * outerRadius;
            float outer1y = cy + sin1 * outerRadius;
            float outer2x = cx + cos2 * outerRadius;
            float outer2y = cy + sin2 * outerRadius;
            float inner1x = cx + cos1 * innerRadius;
            float inner1y = cy + sin1 * innerRadius;
            float inner2x = cx + cos2 * innerRadius;
            float inner2y = cy + sin2 * innerRadius;

            buf.vertex(outer1x, outer1y, 0).color(r, g, b, a).endVertex();
            buf.vertex(inner1x, inner1y, 0).color(r, g, b, a).endVertex();
            buf.vertex(outer2x, outer2y, 0).color(r, g, b, a).endVertex();

            buf.vertex(inner1x, inner1y, 0).color(r, g, b, a).endVertex();
            buf.vertex(inner2x, inner2y, 0).color(r, g, b, a).endVertex();
            buf.vertex(outer2x, outer2y, 0).color(r, g, b, a).endVertex();
        }

        tesselator.end();
    }

    private void drawArcOutline(GuiGraphics graphics, float cx, float cy, float radius,
                                float startAngle, float endAngle, int segments, int color) {

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();

        Tesselator tesselator = Tesselator.getInstance();
        var buf = tesselator.getBuilder();

        buf.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);

        float angleStep = (endAngle - startAngle) / segments;

        for (int i = 0; i < segments; i++) {
            float a1 = startAngle + angleStep * i;
            float a2 = startAngle + angleStep * (i + 1);

            double rad1 = Math.toRadians(a1);
            double rad2 = Math.toRadians(a2);

            float x1 = cx + (float) Math.cos(rad1) * radius;
            float y1 = cy + (float) Math.sin(rad1) * radius;
            float x2 = cx + (float) Math.cos(rad2) * radius;
            float y2 = cy + (float) Math.sin(rad2) * radius;

            buf.vertex(x1, y1, 0).color(r, g, b, a).endVertex();
            buf.vertex(x2, y2, 0).color(r, g, b, a).endVertex();
        }

        tesselator.end();
    }
}
