package com.complextalents.impl.marksman.client;

import com.complextalents.TalentsMod;
import com.complextalents.effect.ModEffects;
import com.complextalents.impl.marksman.data.MarksmanAdrenalineData;
import com.complextalents.impl.marksman.origin.MarksmanOrigin;
import com.complextalents.impl.marksman.skill.RelentlessPursuitSkill;
import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.origin.client.OriginRenderer;
import com.complextalents.skill.client.ClientSkillData;
import com.complextalents.tacz.HeartRateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Custom Origin HUD renderer for Marksman origin.
 * 1. Left Arc: Adrenaline Duration.
 * 2. Right Segmented Arcs: 2 Dismiss Charges.
 * 3. Dismissed State: Temporarily enters F1 mode (hides GUI & hands) with a subtle purple screen overlay and suppressed camera bobbing.
 * 4. Minimalist Skill HUD: Numbers only.
 * 5. BPM Monitor: Hidden during Adrenaline mode, original format outside Adrenaline mode.
 */
public class MarksmanRenderer implements OriginRenderer {

    private float smoothAdrenalineProgress = 0.0f;

    @Mod.EventBusSubscriber(modid = TalentsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientTickHandler {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null) return;

            // Suppress movement bobbing while Dismissed is active for a smooth hovering feel
            if (player.hasEffect(ModEffects.DISMISSED.get())) {
                player.walkDist = player.walkDistO;
            }

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

        @SubscribeEvent
        public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player != null && player.hasEffect(ModEffects.DISMISSED.get())) {
                // Render subtle purple screen overlay tint before hiding HUD
                GuiGraphics graphics = event.getGuiGraphics();
                int screenWidth = event.getWindow().getGuiScaledWidth();
                int screenHeight = event.getWindow().getGuiScaledHeight();

                long time = System.currentTimeMillis();
                float pulse = (float) ((Math.sin(time / 100.0) + 1.0) / 2.0);
                int alphaByte = (int) (30 + pulse * 18); // Subtler purple tint (~12% - 18% opacity)
                int purpleOverlay = (alphaByte << 24) | 0x8800FF;
                graphics.fill(0, 0, screenWidth, screenHeight, purpleOverlay);

                // Cancel GUI rendering to force F1 mode HUD hide
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onRenderHand(RenderHandEvent event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player != null && player.hasEffect(ModEffects.DISMISSED.get())) {
                // Cancel first-person hand & item rendering to force F1 mode hand hide
                event.setCanceled(true);
            }
        }
    }

    @Override
    public void renderHUD(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        Player player = mc.player;
        if (player == null || player.isSpectator()) return;

        // Note: Purple overlay & GUI cancellation during Dismissed is handled in onRenderGuiPre

        if (!ClientOriginData.hasOrigin()) return;
        ResourceLocation activeOrigin = ClientOriginData.getOriginId();
        if (activeOrigin == null || (!activeOrigin.equals(MarksmanOrigin.ID) && !"marksman".equals(activeOrigin.getPath()))) {
            return;
        }

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        Font font = mc.font;

        if (ClientAdrenalineFXHandler.isActive()) {
            float maxSecs = ClientAdrenalineFXHandler.getMaxSecs();
            float remSecs = ClientAdrenalineFXHandler.getRemainingSecs();
            float targetProgress = Math.min(1.0f, Math.max(0.0f, remSecs / Math.max(0.1f, maxSecs)));

            smoothAdrenalineProgress = Mth.lerp(0.15f, smoothAdrenalineProgress, targetProgress);

            float cx = centerX;
            float cy = centerY;
            float rInner = 16.0f;
            float rOuter = 20.0f;

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Matrix4f matrix = graphics.pose().last().pose();
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = tesselator.getBuilder();

            // 1. LEFT ARC: Adrenaline Duration (-150° to -40°)
            float leftStart = -150.0f;
            float leftSweep = 110.0f;
            float filledLeftSweep = leftSweep * smoothAdrenalineProgress;

            drawArcSegment(tesselator, bufferBuilder, matrix, cx, cy, rInner, rOuter, leftStart, leftSweep, 0x44440000);

            if (smoothAdrenalineProgress > 0.001f) {
                long time = System.currentTimeMillis();
                int r = 255, g = 30, b = 30;
                if (remSecs <= 3.0f) {
                    float pulse = (float) ((Math.sin(time / 100.0) + 1.0) / 2.0);
                    g = (int) (30 + pulse * 60);
                    b = (int) (30 + pulse * 60);
                }
                int arcColor = (240 << 24) | (r << 16) | (g << 8) | b;
                drawArcSegment(tesselator, bufferBuilder, matrix, cx, cy, rInner, rOuter, leftStart, filledLeftSweep, arcColor);
            }

            // 2. RIGHT SEGMENTED ARCS: 2 Dismiss Charges (Charge 1: 30° to 80°, Charge 2: 95° to 145°)
            float dismissRes = ClientAdrenalineFXHandler.getDismissResource();
            int usedCount = ClientAdrenalineFXHandler.getDismissCount();

            // Segment 1 (Charge 1)
            float seg1Start = 30.0f;
            float seg1Sweep = 50.0f;
            float seg1Progress = usedCount >= 1 ? 0.0f : Math.min(1.0f, dismissRes / 100.0f);

            int seg1Bg = usedCount >= 1 ? 0x33222222 : 0x55002233;
            drawArcSegment(tesselator, bufferBuilder, matrix, cx, cy, rInner, rOuter, seg1Start, seg1Sweep, seg1Bg);

            if (seg1Progress > 0.001f) {
                boolean ready1 = seg1Progress >= 1.0f;
                long time = System.currentTimeMillis();
                int color1;
                if (ready1) {
                    float pulse = (float) ((Math.sin(time / 120.0) + 1.0) / 2.0);
                    int cyanG = (int) (200 + pulse * 55);
                    color1 = (240 << 24) | (0 << 16) | (cyanG << 8) | 255;
                } else {
                    color1 = (200 << 24) | (0 << 16) | (180 << 8) | 220;
                }
                drawArcSegment(tesselator, bufferBuilder, matrix, cx, cy, rInner, rOuter, seg1Start, seg1Sweep * seg1Progress, color1);
            }

            // Segment 2 (Charge 2)
            float seg2Start = 95.0f;
            float seg2Sweep = 50.0f;
            float seg2Progress = usedCount >= 2 ? 0.0f : (usedCount == 1 ? Math.min(1.0f, dismissRes / 100.0f) : Math.min(1.0f, Math.max(0.0f, (dismissRes - 100.0f) / 100.0f)));

            int seg2Bg = usedCount >= 2 ? 0x33222222 : 0x55002233;
            drawArcSegment(tesselator, bufferBuilder, matrix, cx, cy, rInner, rOuter, seg2Start, seg2Sweep, seg2Bg);

            if (seg2Progress > 0.001f) {
                boolean ready2 = seg2Progress >= 1.0f;
                long time = System.currentTimeMillis();
                int color2;
                if (ready2) {
                    float pulse = (float) ((Math.sin(time / 120.0) + 1.0) / 2.0);
                    int cyanG = (int) (200 + pulse * 55);
                    color2 = (240 << 24) | (0 << 16) | (cyanG << 8) | 255;
                } else {
                    color2 = (200 << 24) | (0 << 16) | (180 << 8) | 220;
                }
                drawArcSegment(tesselator, bufferBuilder, matrix, cx, cy, rInner, rOuter, seg2Start, seg2Sweep * seg2Progress, color2);
            }

            RenderSystem.disableBlend();

            // Render ONLY raw remaining duration number below crosshair
            String durNum = String.format("%d", (int) Math.ceil(remSecs));
            int numX = centerX - (font.width(durNum) / 2);
            graphics.drawString(font, durNum, numX, centerY + 16, 0xFFFFFF, true);

            // Hide BPM display entirely while in Adrenaline mode!
            return;

        } else {
            smoothAdrenalineProgress = 0.0f;

            // Outside Adrenaline Mode: Render ONLY raw cooldown number (No text label!)
            double cooldown = ClientSkillData.getCooldownRemaining(RelentlessPursuitSkill.ID);
            if (cooldown > 0) {
                String cdNum = String.format("%d", (int) Math.ceil(cooldown));
                int cdX = centerX - (font.width(cdNum) / 2);
                graphics.drawString(font, cdNum, cdX, centerY + 16, 0xAAAAAA, true);
            }
        }

        // Heart Rate Monitor: Restored original format with "♥" and "BPM" string (Outside Adrenaline Mode)
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
            bpmColor = 0x55FF55; // Calm Green
        } else if (bpm < 135.0f) {
            bpmColor = 0xFFFF55; // Elevated Yellow
        } else {
            bpmColor = 0xFF3333; // Panic Red
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
}
