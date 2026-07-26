package com.complextalents.impl.darkmage.client;

import com.complextalents.impl.darkmage.effect.DarkMageEffects;
import com.complextalents.origin.client.OriginRenderer;
import com.complextalents.passive.client.ClientPassiveStackData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Refined HUD renderer for the Dark Mage origin.
 * Displays:
 * - When Soul Stasis is active: A full arc in Blue with remaining Stasis duration.
 * - When Blood Pact is active: A filling arc in Crimson Red showing duration until the next ramping stage.
 */
public class DarkMageRenderer implements OriginRenderer {

    private static final float ARC_INNER_RADIUS = 25f;
    private static final float ARC_OUTER_RADIUS = 28f;
    private static final float ARC_LENGTH = 120f; // degrees

    // Right side arc angles (spans 300° to 420°)
    private static final float ARC_TOP_ANGLE = 300f;
    private static final float ARC_BOTTOM_ANGLE = 420f;

    // Color definitions (ARGB)
    private static final int ARC_BG_COLOR = 0x99000000;

    // Soul Stasis Colors (Blue / Cyan)
    private static final int STASIS_ARC_COLOR = 0xCC00E5FF;
    private static final int STASIS_GLOW_COLOR = 0x4400E5FF;
    private static final int STASIS_TEXT_COLOR = 0x9900E5FF;

    // Ramping Stage Colors (Crimson / Blood Red)
    private static final int RAMP_ARC_COLOR = 0xCCFF0044;
    private static final int RAMP_MAX_COLOR = 0xCCFF2200;
    private static final int RAMP_GLOW_COLOR = 0x44FF0033;
    private static final int RAMP_TEXT_COLOR = 0x99FF3344;

    @Override
    public void renderHUD(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        boolean isStasis = mc.player.hasEffect(DarkMageEffects.SOUL_STASIS.get());
        int isBloodPactActive = ClientPassiveStackData.getStackCount("blood_pact_active");
        int activeTicks = ClientPassiveStackData.getStackCount("blood_pact_ticks");

        if (!isStasis && isBloodPactActive == 0) {
            return; // Don't render anything if inactive
        }

        RenderSystem.enableBlend();

        int dmgPct = ClientPassiveStackData.getStackCount("blood_pact_dmg");

        if (isStasis) {
            // State 1: Soul Stasis Active -> Full Arc in Blue + Stasis Duration + Damage Bonus
            var effect = mc.player.getEffect(DarkMageEffects.SOUL_STASIS.get());
            int durationTicks = effect != null ? effect.getDuration() : 0;
            float seconds = durationTicks / 20.0f;

            // Background Arc
            drawArcSegment(centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS, ARC_TOP_ANGLE, ARC_BOTTOM_ANGLE, 20, ARC_BG_COLOR);

            // Glow Effect
            drawArcSegment(centerX, centerY, ARC_INNER_RADIUS - 1.5f, ARC_OUTER_RADIUS + 1.5f, ARC_TOP_ANGLE, ARC_BOTTOM_ANGLE, 20, STASIS_GLOW_COLOR);

            // Full Blue Arc
            drawArcSegment(centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS, ARC_TOP_ANGLE, ARC_BOTTOM_ANGLE, 20, STASIS_ARC_COLOR);

            // Label next to arc
            renderLabel(graphics, mc, centerX, centerY, String.format("%.1fs", seconds), dmgPct, STASIS_TEXT_COLOR);

        } else {
            // State 2: Blood Pact Active (Ramping) -> Filling Arc in Crimson Red for Stage Duration + Damage Bonus
            float activeSeconds = activeTicks / 20.0f;
            float timeInStage = activeSeconds % 5.0f;
            float timeUntilNextStage = 5.0f - timeInStage;
            float fillRatio = timeInStage / 5.0f;
            boolean isMaxRamp = activeSeconds >= 30.0f;

            if (isMaxRamp) {
                fillRatio = 1.0f;
                timeUntilNextStage = 0.0f;
            }

            // Background Arc
            drawArcSegment(centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS, ARC_TOP_ANGLE, ARC_BOTTOM_ANGLE, 20, ARC_BG_COLOR);

            if (isMaxRamp) {
                drawArcSegment(centerX, centerY, ARC_INNER_RADIUS - 1.5f, ARC_OUTER_RADIUS + 1.5f, ARC_TOP_ANGLE, ARC_BOTTOM_ANGLE, 20, RAMP_GLOW_COLOR);
            }

            // Filling Arc (fills from bottom upwards)
            if (fillRatio > 0f) {
                float fillLength = ARC_LENGTH * fillRatio;
                int fillColor = isMaxRamp ? RAMP_MAX_COLOR : RAMP_ARC_COLOR;
                drawArcSegment(centerX, centerY, ARC_INNER_RADIUS, ARC_OUTER_RADIUS, ARC_BOTTOM_ANGLE - fillLength, ARC_BOTTOM_ANGLE, Math.max(1, (int) (20 * fillRatio)), fillColor);
            }

            // Label next to arc
            String labelText = isMaxRamp ? "MAX" : String.format("%.1fs", timeUntilNextStage);
            renderLabel(graphics, mc, centerX, centerY, labelText, dmgPct, RAMP_TEXT_COLOR);
        }

        RenderSystem.disableBlend();
    }

    private void renderLabel(GuiGraphics graphics, Minecraft mc, int centerX, int centerY, String timeText, int dmgPct, int color) {
        int textX = (int) (centerX + ARC_OUTER_RADIUS + 8);
        int timeY = centerY - 8;
        int dmgY = centerY + 2;

        graphics.pose().pushPose();
        graphics.pose().scale(0.7f, 0.7f, 0.7f);

        // Timer Text
        graphics.drawString(mc.font, timeText, (int) (textX / 0.7f), (int) (timeY / 0.7f), color);

        // Damage Bonus Text (Gold highlight)
        if (dmgPct > 0) {
            String dmgText = "+" + dmgPct + "% Dmg";
            graphics.drawString(mc.font, dmgText, (int) (textX / 0.7f), (int) (dmgY / 0.7f), 0x99FFD700);
        }

        graphics.pose().popPose();
    }

    private void drawArcSegment(float cx, float cy, float innerRadius, float outerRadius, float startAngle, float endAngle, int segments, int color) {
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
