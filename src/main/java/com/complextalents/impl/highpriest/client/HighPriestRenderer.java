package com.complextalents.impl.highpriest.client;

import com.complextalents.origin.client.OriginRenderer;
import com.complextalents.passive.client.ClientPassiveStackData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Custom HUD renderer for the High Priest origin.
 * Displays a 10-segment Command arc (max 100 stacks) with fractional stack rendering.
 */
public class HighPriestRenderer implements OriginRenderer {

    private static final float ARC_INNER_RADIUS = 25f;
    private static final float ARC_OUTER_RADIUS = 28f;
    private static final float ARC_LENGTH = 120f; // degrees

    // Command arc (left side) - spans from 240° to 120° (bottom-left to top-left)
    private static final float COMMAND_BOTTOM_ANGLE = 240f;

    // Color definitions (ARGB) - 60% opacity (0x99)
    private static final int COMMAND_BG_COLOR = 0x99000000;
    private static final int COMMAND_FILL_COLOR = 0x99FFD700; // Gold
    private static final int COMMAND_PULL_READY_COLOR = 0x9900E5FF; // Celestial Cyan when >= 50 Command (Pull Ready)
    private static final int COMMAND_FULL_COLOR = 0x9900FFCC; // Bright Celestial Cyan when maxed (100)
    private static final int COMMAND_DIVIDER_COLOR = 0x99000000;

    // Cache
    private static String cachedCommandText = "";
    private static int lastCommandValue = -1;
    private static int lastGraceValue = -1;
    private static int lastGraceCooldown = -1;
    private static String cachedGraceCooldownText = "";

    @Override
    public void renderHUD(GuiGraphics graphics, int screenWidth, int screenHeight) {
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        RenderSystem.enableBlend();
        renderCommandArc(graphics, centerX, centerY);
        RenderSystem.disableBlend();
        renderLabels(graphics, centerX, centerY);
    }

    private void renderCommandArc(GuiGraphics graphics, int centerX, int centerY) {
        int command = ClientPassiveStackData.getStackCount("command");
        int maxCommand = 100;
        int numSegments = 10;
        boolean hasGrace = ClientPassiveStackData.getStackCount("grace") > 0;

        float segmentAngleLength = ARC_LENGTH / numSegments; // 12° per segment

        // Draw empty background segments
        for (int i = 0; i < numSegments; i++) {
            float startAngle = COMMAND_BOTTOM_ANGLE - (i * segmentAngleLength);
            drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                    startAngle - segmentAngleLength + 0.8f, startAngle,
                    3, COMMAND_BG_COLOR);
        }

        // Grace active glow
        if (hasGrace) {
            drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS - 1.5f, ARC_OUTER_RADIUS + 1.5f,
                    COMMAND_BOTTOM_ANGLE - ARC_LENGTH, COMMAND_BOTTOM_ANGLE,
                    16, 0x33E6F0FF);
        }

        // Fill color based on command level
        int fillColor;
        if (command >= maxCommand) {
            fillColor = COMMAND_FULL_COLOR;
        } else if (command >= 50) {
            fillColor = COMMAND_PULL_READY_COLOR;
        } else {
            fillColor = COMMAND_FILL_COLOR;
        }

        // Draw filled segments (including fractional filling for partial segment)
        for (int i = 0; i < numSegments; i++) {
            int segmentStartVal = i * 10;
            int segmentEndVal = (i + 1) * 10;

            if (command <= segmentStartVal) break;

            float fillFraction = 1.0f;
            if (command < segmentEndVal) {
                fillFraction = (command - segmentStartVal) / 10.0f;
            }

            float startAngle = COMMAND_BOTTOM_ANGLE - (i * segmentAngleLength);
            float endAngle = startAngle - (segmentAngleLength * fillFraction) + (fillFraction >= 1.0f ? 0.8f : 0.0f);

            drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                    endAngle, startAngle,
                    3, fillColor);
        }

        // Draw dividers between the 10 segments
        for (int i = 1; i < numSegments; i++) {
            float dividerAngle = COMMAND_BOTTOM_ANGLE - (i * segmentAngleLength);
            drawThickLine(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS, dividerAngle,
                    COMMAND_DIVIDER_COLOR);
        }
    }

    private void renderLabels(GuiGraphics graphics, int centerX, int centerY) {
        Minecraft minecraft = Minecraft.getInstance();

        int command = ClientPassiveStackData.getStackCount("command");
        int grace = ClientPassiveStackData.getStackCount("grace");

        if (command != lastCommandValue || grace != lastGraceValue) {
            lastCommandValue = command;
            lastGraceValue = grace;
            cachedCommandText = "Command: " + command + "/100";
        }

        int commandTextWidth = minecraft.font.width(cachedCommandText);
        int commandTextX = (int) (centerX - ARC_OUTER_RADIUS - 8 - commandTextWidth * 0.7f);
        int commandTextY = centerY - 3;

        int textColor = command >= 50 ? 0x9900E5FF : (grace > 0 ? 0x99E6F0FF : 0x99AAAAAA);

        graphics.pose().pushPose();
        graphics.pose().scale(0.7f, 0.7f, 0.7f);
        graphics.drawString(minecraft.font, cachedCommandText,
                (int) (commandTextX / 0.7f), (int) (commandTextY / 0.7f), textColor);
        graphics.pose().popPose();

        int graceCooldown = ClientPassiveStackData.getStackCount("grace_cooldown");
        if (graceCooldown > 0) {
            if (graceCooldown != lastGraceCooldown) {
                lastGraceCooldown = graceCooldown;
                cachedGraceCooldownText = String.format("%.1fs", graceCooldown / 20.0f);
            }

            int cooldownWidth = minecraft.font.width(cachedGraceCooldownText);
            int cooldownX = (int) (centerX - ARC_OUTER_RADIUS - 8 - cooldownWidth * 0.6f);
            int cooldownY = centerY + 5;

            graphics.pose().pushPose();
            graphics.pose().scale(0.6f, 0.6f, 0.6f);
            graphics.drawString(minecraft.font, cachedGraceCooldownText,
                    (int) (cooldownX / 0.6f), (int) (cooldownY / 0.6f), 0x99AAAAAA);
            graphics.pose().popPose();
        }
    }

    private void drawThickLine(GuiGraphics graphics, float cx, float cy, float innerRadius, float outerRadius,
            float angleDegrees, int color) {
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
}
