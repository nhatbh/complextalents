package com.complextalents.impl.marksman.client;

import com.complextalents.TalentsMod;
import com.complextalents.impl.marksman.origin.MarksmanOrigin;
import com.complextalents.impl.marksman.skill.RelentlessPursuitSkill;
import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.origin.client.OriginRenderer;
import com.complextalents.skill.client.ClientSkillData;
import com.complextalents.tacz.HeartRateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Custom Origin HUD renderer for Marksman origin.
 * Low-profile design matching High Priest & Warrior HUD style:
 * 1. Low-profile Mobility Arc on left side of crosshair (25px-28px radius, 120° sweep).
 * 2. Scaled text label ("Mobility: X/100") & skill cooldown timer.
 * 3. Dynamic Heart Rate (BPM) Monitor when holding a TACZ gun.
 */
public class MarksmanRenderer implements OriginRenderer {

    private static final float ARC_INNER_RADIUS = 25.0f;
    private static final float ARC_OUTER_RADIUS = 28.0f;
    private static final float ARC_LENGTH = 80f;
    private static final float MOBILITY_BOTTOM_ANGLE = 220f;

    private static final int MOBILITY_BG_COLOR = 0x33000000;     // ~20% Opacity Background
    private static final int MOBILITY_LOW_COLOR = 0x558899A6;    // ~33% Opacity Slate Gray (< 50 Mobility)
    private static final int MOBILITY_READY_COLOR = 0x5500E5FF;  // ~33% Opacity Cyan (50+ Mobility)
    private static final int MOBILITY_FULL_COLOR = 0x5500FFCC;   // ~33% Opacity Bright Cyan (100 Mobility)
    private static final int MOBILITY_DIVIDER_COLOR = 0x44000000; // ~27% Opacity Divider

    private float smoothMobility = 0.0f;
    private static String cachedMobilityText = "";
    private static int lastMobilityValue = -1;
    private static int lastCooldownSeconds = -1;
    private static String cachedCooldownText = "";

    @Mod.EventBusSubscriber(modid = TalentsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientTickHandler {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null) return;

            if (!ClientOriginData.hasOrigin()) return;
            ResourceLocation activeOrigin = ClientOriginData.getOriginId();
            if (activeOrigin == null || (!activeOrigin.equals(MarksmanOrigin.ID) && !"marksman".equals(activeOrigin.getPath()))) {
                return;
            }

            ItemStack mainStack = player.getMainHandItem();
            ItemStack offStack = player.getOffhandItem();
            if (IGun.getIGunOrNull(mainStack) != null || IGun.getIGunOrNull(offStack) != null) {
                HeartRateManager.tickHeartRate(player);
            }
        }
    }

    @Override
    public void renderHUD(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        Player player = mc.player;
        if (player == null || player.isSpectator()) return;

        if (!ClientOriginData.hasOrigin()) return;
        ResourceLocation activeOrigin = ClientOriginData.getOriginId();
        if (activeOrigin == null || (!activeOrigin.equals(MarksmanOrigin.ID) && !"marksman".equals(activeOrigin.getPath()))) {
            return;
        }

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        Font font = mc.font;

        float targetMobility = ClientAdrenalineFXHandler.getDismissResource();
        smoothMobility = Mth.lerp(0.2f, smoothMobility, targetMobility);

        RenderSystem.enableBlend();
        renderMobilityArc(graphics, centerX, centerY);
        RenderSystem.disableBlend();

        renderLabels(graphics, font, centerX, centerY);
        renderHeartRate(graphics, font, player, mc, centerX, centerY);
    }

    private void renderMobilityArc(GuiGraphics graphics, int centerX, int centerY) {
        float mobility = Math.min(100.0f, Math.max(0.0f, smoothMobility));
        int numSegments = 2;
        float segmentAngleLength = ARC_LENGTH / numSegments;

        // Draw empty background arc
        for (int i = 0; i < numSegments; i++) {
            float startAngle = MOBILITY_BOTTOM_ANGLE - (i * segmentAngleLength);
            drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                    startAngle - segmentAngleLength + 0.8f, startAngle,
                    6, MOBILITY_BG_COLOR);
        }

        // Fill color based on mobility level
        int fillColor;
        if (mobility >= 100.0f) {
            fillColor = MOBILITY_FULL_COLOR;
        } else if (mobility >= 50.0f) {
            fillColor = MOBILITY_READY_COLOR;
        } else {
            fillColor = MOBILITY_LOW_COLOR;
        }

        // Draw filled segments
        for (int i = 0; i < numSegments; i++) {
            int segmentStartVal = i * 50;
            int segmentEndVal = (i + 1) * 50;

            if (mobility <= segmentStartVal) break;

            float fillFraction = 1.0f;
            if (mobility < segmentEndVal) {
                fillFraction = (mobility - segmentStartVal) / 50.0f;
            }

            float startAngle = MOBILITY_BOTTOM_ANGLE - (i * segmentAngleLength);
            float endAngle = startAngle - (segmentAngleLength * fillFraction) + (fillFraction >= 1.0f ? 0.8f : 0.0f);

            drawArcSegment(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS,
                    endAngle, startAngle,
                    6, fillColor);
        }

        // Draw single divider at 50 Mobility mark (i = 1)
        for (int i = 1; i < numSegments; i++) {
            float dividerAngle = MOBILITY_BOTTOM_ANGLE - (i * segmentAngleLength);
            drawThickLine(graphics, centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS, dividerAngle,
                    MOBILITY_DIVIDER_COLOR);
        }
    }

    private void renderLabels(GuiGraphics graphics, Font font, int centerX, int centerY) {
        int mobilityInt = (int) smoothMobility;
        if (mobilityInt != lastMobilityValue) {
            lastMobilityValue = mobilityInt;
            cachedMobilityText = mobilityInt + "/100";
        }

        int textWidth = font.width(cachedMobilityText);
        int textX = (int) (centerX - ARC_OUTER_RADIUS - 8 - textWidth * 0.7f);
        int textY = centerY - 3;

        int textColor = smoothMobility >= 50.0f ? 0x7700E5FF : 0x77AAAAAA;

        graphics.pose().pushPose();
        graphics.pose().scale(0.7f, 0.7f, 0.7f);
        graphics.drawString(font, cachedMobilityText, (int) (textX / 0.7f), (int) (textY / 0.7f), textColor);
        graphics.pose().popPose();

        // Skill Cooldown Timer
        double cooldown = ClientSkillData.getCooldownRemaining(RelentlessPursuitSkill.ID);
        if (cooldown > 0) {
            int cdSecInt = (int) (cooldown * 10);
            if (cdSecInt != lastCooldownSeconds) {
                lastCooldownSeconds = cdSecInt;
                cachedCooldownText = String.format("%.1fs", cooldown);
            }

            int cdWidth = font.width(cachedCooldownText);
            int cdX = (int) (centerX - ARC_OUTER_RADIUS - 8 - cdWidth * 0.6f);
            int cdY = centerY + 5;

            graphics.pose().pushPose();
            graphics.pose().scale(0.6f, 0.6f, 0.6f);
            graphics.drawString(font, cachedCooldownText, (int) (cdX / 0.6f), (int) (cdY / 0.6f), 0x77AAAAAA);
            graphics.pose().popPose();
        }
    }

    private void renderHeartRate(GuiGraphics graphics, Font font, Player player, Minecraft mc, int centerX, int centerY) {
        ItemStack mainStack = player.getMainHandItem();
        ItemStack offStack = player.getOffhandItem();
        if (IGun.getIGunOrNull(mainStack) == null && IGun.getIGunOrNull(offStack) == null) {
            return;
        }

        float bpm = HeartRateManager.getHeartRate(player);
        float healthRatio = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
        boolean isCriticalHealth = healthRatio <= 0.30f;

        long gameTime = player.level().getGameTime();
        float timeInSeconds = (gameTime + mc.getFrameTime()) / 20.0f;

        float beatsPerSecond = bpm / 60.0f;
        float cycleProgress = (timeInSeconds * beatsPerSecond) % 1.0f;
        float beatPulse = cycleProgress < 0.25f ? (float) Math.sin(cycleProgress * (Math.PI / 0.25f)) : 0.0f;

        int x = centerX + 14;
        int y = centerY - 4;

        int bpmColor;
        if (bpm < 90.0f) {
            bpmColor = 0x55FF55;
        } else if (bpm < 135.0f) {
            bpmColor = 0xFFFF55;
        } else {
            bpmColor = 0xFF3333;
        }

        String bpmText = String.format("%d BPM", (int) bpm);

        graphics.pose().pushPose();
        float heartScale = 1.0f + (0.35f * beatPulse);
        graphics.pose().translate(x + 3, y + 4, 0);
        graphics.pose().scale(heartScale, heartScale, 1.0f);
        int heartColor = isCriticalHealth ? (beatPulse > 0.5f ? 0xFF0000 : 0xAA0000) : bpmColor;
        graphics.drawString(font, "♥", -3, -4, heartColor, true);
        graphics.pose().popPose();

        graphics.drawString(font, bpmText, x + 12, y + 1, bpmColor, true);
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

        float thickness = 3.0f;

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
