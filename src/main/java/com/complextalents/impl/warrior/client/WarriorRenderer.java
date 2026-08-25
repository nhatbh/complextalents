package com.complextalents.impl.warrior.client;

import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.origin.client.OriginRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Custom HUD renderer for the Warrior origin.
 * Displays Weapon Parry Effect (left) and Shield (right) while charging.
 */
public class WarriorRenderer implements OriginRenderer {

    private static final float ARC_INNER_RADIUS = 25f;
    private static final float ARC_OUTER_RADIUS = 28f;
    private static final float ARC_LENGTH = 120f;

    // Left Arc (Parry Effect) - 240 to 120 (bottom-left to top-left)
    private static final float PARRY_BOTTOM_ANGLE = 240f;

    // Right Arc (Shield) - 300 to 60 (bottom-right to top-right)
    private static final float SHIELD_BOTTOM_ANGLE = 300f;

    // Colors
    private static final int SHIELD_FULL_COLOR = 0x9944AAFF; // Blue
    private static final int SHIELD_EMPTY_COLOR = 0x99FF4444; // Red
    private static final int BG_COLOR = 0x66000000;

    @Override
    public void renderHUD(GuiGraphics graphics, int screenWidth, int screenHeight) {
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        RenderSystem.enableBlend();
        renderParryArc(graphics, centerX, centerY);
        renderShieldArc(graphics, centerX, centerY);
        RenderSystem.disableBlend();

        renderLabels(graphics, centerX, centerY);
    }

    private void renderParryArc(GuiGraphics graphics, int centerX, int centerY) {
        if (!ClientParryData.hasActiveEffect()) return;

        float progress = ClientParryData.getProgress();
        int fillColor = ClientParryData.getColor();

        // Background
        drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                PARRY_BOTTOM_ANGLE - ARC_LENGTH, PARRY_BOTTOM_ANGLE, 16, BG_COLOR);

        // Fill
        if (progress > 0) {
            float fillAngle = progress * ARC_LENGTH;
            drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                    PARRY_BOTTOM_ANGLE - fillAngle, PARRY_BOTTOM_ANGLE, 16, fillColor);
        }
    }

    private void renderShieldArc(GuiGraphics graphics, int centerX, int centerY) {
        double shield = ClientOriginData.getShieldValue();
        double shieldMax = ClientOriginData.getShieldMax();

        if (shield <= 0 || shieldMax <= 0) return;

        float progress = (float) (shield / shieldMax);
        progress = Math.min(1.0f, Math.max(0.0f, progress));

        // Colors for fading
        int r1 = (SHIELD_FULL_COLOR >> 16) & 0xFF;
        int g1 = (SHIELD_FULL_COLOR >> 8) & 0xFF;
        int b1 = SHIELD_FULL_COLOR & 0xFF;
        int a1 = (SHIELD_FULL_COLOR >> 24) & 0xFF;

        int r2 = (SHIELD_EMPTY_COLOR >> 16) & 0xFF;
        int g2 = (SHIELD_EMPTY_COLOR >> 8) & 0xFF;
        int b2 = SHIELD_EMPTY_COLOR & 0xFF;
        int a2 = (SHIELD_EMPTY_COLOR >> 24) & 0xFF;

        // Interpolate color (Blue -> Red)
        int r = (int) (r2 + (r1 - r2) * progress);
        int g = (int) (g2 + (g1 - g2) * progress);
        int b = (int) (b2 + (b1 - b2) * progress);
        int a = (int) (a2 + (a1 - a2) * progress);
        int fillColor = (a << 24) | (r << 16) | (g << 8) | b;

        // Background
        drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                SHIELD_BOTTOM_ANGLE, SHIELD_BOTTOM_ANGLE + ARC_LENGTH, 16, BG_COLOR);

        // Fill
        if (progress > 0) {
            float fillAngle = progress * ARC_LENGTH;
            drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                    SHIELD_BOTTOM_ANGLE, SHIELD_BOTTOM_ANGLE + fillAngle, 16, fillColor);
        }
    }

    private void renderLabels(GuiGraphics graphics, int centerX, int centerY) {
        Minecraft mc = Minecraft.getInstance();

        // 1. Parry Effect Label (Left)
        if (ClientParryData.hasActiveEffect()) {
            String titleText = ClientParryData.getIcon() + " " + ClientParryData.getTitleText();
            int titleColor = ClientParryData.getColor() | 0xFF000000;

            String detailText = String.format("%s (%.1fs)", ClientParryData.getDisplayText(), ClientParryData.getRemainingTicks() / 20.0f);

            int titleX = (int) (centerX - ARC_OUTER_RADIUS - 10 - mc.font.width(titleText) * 0.7f);
            int titleY = centerY - 10;

            graphics.pose().pushPose();
            graphics.pose().scale(0.7f, 0.7f, 0.7f);
            graphics.drawString(mc.font, titleText, (int)(titleX / 0.7f), (int)(titleY / 0.7f), titleColor);
            graphics.pose().popPose();

            int detailX = (int) (centerX - ARC_OUTER_RADIUS - 10 - mc.font.width(detailText) * 0.5f);
            int detailY = centerY + 2;

            graphics.pose().pushPose();
            graphics.pose().scale(0.5f, 0.5f, 0.5f);
            graphics.drawString(mc.font, detailText, (int)(detailX / 0.5f), (int)(detailY / 0.5f), 0xFFEEEEEE);
            graphics.pose().popPose();
        }

        // 2. Shield Value (Right)
        double shield = ClientOriginData.getShieldValue();
        double shieldMax = ClientOriginData.getShieldMax();
        if (shield > 0 && shieldMax > 0) {
            String shieldText = String.format("%.0f HP", shield);
            int shieldX = (int) (centerX + ARC_OUTER_RADIUS + 10);
            int shieldY = centerY - 3;

            graphics.pose().pushPose();
            graphics.pose().scale(0.7f, 0.7f, 0.7f);
            graphics.drawString(mc.font, shieldText, (int)(shieldX / 0.7f), (int)(shieldY / 0.7f), 0xFFAABBFF);
            graphics.pose().popPose();
        }
    }

    private void drawArcSegment(GuiGraphics graphics, float cx, float cy, float innerRadius, float outerRadius,
                               float startAngle, float endAngle, int segments, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);

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
}
