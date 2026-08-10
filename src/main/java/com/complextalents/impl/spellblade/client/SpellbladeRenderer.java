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
    private float smoothVirtualManaProgress = 0.0f;

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

        // Calculate Target Progress Values (6-second imbue duration countdown, finite regardless of stance)
        float targetImbue = 0.0f;
        if (enhancedTicks > 0) {
            targetImbue = Math.min(1.0f, Math.max(0.0f, enhancedTicks / 120.0f));
        } else if (hasCharge) {
            targetImbue = 1.0f;
        }

        // Calculate Virtual Mana ratio (up to 50% max mana cap)
        float virtualMana = cap.getVirtualMana();
        double maxMana = player.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
        if (maxMana <= 0) maxMana = 100.0;
        float maxVirtualMana = (float) (maxMana * 0.5);

        float targetVirtualMana = (maxVirtualMana > 0) ? Math.min(1.0f, Math.max(0.0f, virtualMana / maxVirtualMana)) : 0.0f;

        // Smooth Lerp Animations for fluid UI response
        smoothImbueProgress = Mth.lerp(0.15f, smoothImbueProgress, targetImbue);
        if (Math.abs(smoothImbueProgress - targetImbue) < 0.001f) {
            smoothImbueProgress = targetImbue;
        }

        smoothVirtualManaProgress = Mth.lerp(0.15f, smoothVirtualManaProgress, targetVirtualMana);
        if (Math.abs(smoothVirtualManaProgress - targetVirtualMana) < 0.001f) {
            smoothVirtualManaProgress = targetVirtualMana;
        }

        // Only return if stance is off and both HUD bars have fully faded out
        if (!isOvercharge && smoothImbueProgress <= 0.001f && smoothVirtualManaProgress <= 0.001f) {
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
            float pulse = 0.85f + 0.15f * (float) Math.sin(gameTime * 0.25);
            int alpha = (int) (0xEE * pulse);
            int filledColor = (alpha << 24) | (elementColor & 0x00FFFFFF);

            float fillSweep = totalSweep * smoothImbueProgress;
            drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner, radiusOuter, startAngle, fillSweep, filledColor);
        }

        // 3. Thin Inner Sub-Bar (Tracks Virtual Mana Pool up to 50% max mana, ONLY in Overcharge Stance)
        if (isOvercharge) {
            // Background track for inner arc
            drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner - 3.0f, radiusInner - 2.0f, startAngle, totalSweep, 0x3310121A);

            if (smoothVirtualManaProgress > 0.001f) {
                float virtualSweep = totalSweep * smoothVirtualManaProgress;
                int goldColor = 0xFFFFA500; // Bright Warm Gold during Overcharge stance
                drawArcSegment(tesselator, bufferBuilder, matrix, centerX, centerY, radiusInner - 3.0f, radiusInner - 2.0f, startAngle, virtualSweep, goldColor);
            }
        }

        RenderSystem.disableBlend();

        // 4. Small Gold Text for Virtual Mana Amount (Displayed ONLY when Overcharge Stance is active, flush at bottom of mana bar)
        if (isOvercharge) {
            String manaText = String.format("%.0f", virtualMana);
            int textColor = 0xFFFFA500;

            float scale = 0.60f;
            float textWidth = minecraft.font.width(manaText) * scale;

            // Positioned right at the bottom of the mana bar tip, flush with its curved bottom
            float textX = centerX + 12.55f - (textWidth * 0.5f);
            float textY = centerY + 9.5f;

            graphics.pose().pushPose();
            graphics.pose().scale(scale, scale, scale);
            graphics.drawString(minecraft.font, manaText, (int) (textX / scale), (int) (textY / scale), textColor, true);
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
