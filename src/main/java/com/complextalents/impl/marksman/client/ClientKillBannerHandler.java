package com.complextalents.impl.marksman.client;

import com.complextalents.TalentsMod;
import com.complextalents.registry.SoundRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client HUD Renderer for Marksman Adrenaline Mode Kill Banners.
 * Renders kill banners with a pop & fade-in visual animation, followed by fade-out.
 * Instantly clears previous banners and sounds when a new kill banner is triggered.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
public class ClientKillBannerHandler {

    private static final ResourceLocation KILL1_TEX = ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/marksman/kill1.png");
    private static final ResourceLocation KILL2_TEX = ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/marksman/kill2.png");
    private static final ResourceLocation KILL3_TEX = ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/marksman/kill3.png");
    private static final ResourceLocation KILL4_TEX = ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/marksman/kill4.png");
    private static final ResourceLocation OVERKILL_TEX = ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/marksman/overkill.png");

    private static ResourceLocation currentTexture = null;
    private static long bannerStartTime = 0;
    private static final long BANNER_DURATION_MS = 1000; // Total display duration 1.0s

    private static SoundInstance activeKillSound = null;

    public static void triggerKillBanner(int killCount) {
        Minecraft mc = Minecraft.getInstance();

        // 1. Stop previous kill sound instantly
        if (activeKillSound != null) {
            try {
                mc.getSoundManager().stop(activeKillSound);
            } catch (Exception ignored) {}
            activeKillSound = null;
        }

        // 2. Make previous banner disappear INSTANTLY to prepare for the new pop animation
        currentTexture = null;
        bannerStartTime = 0;

        // 3. Select texture & sound based on kill streak
        ResourceLocation newTexture = getTextureForKill(killCount);
        SoundEvent soundEvent = getSoundForKill(killCount);

        // 4. Play new kill audio
        if (soundEvent != null) {
            activeKillSound = SimpleSoundInstance.forUI(soundEvent, 1.0f, 1.0f);
            mc.getSoundManager().play(activeKillSound);
        }

        // 5. Start fresh pop & fade-in animation
        currentTexture = newTexture;
        bannerStartTime = System.currentTimeMillis();
    }

    private static ResourceLocation getTextureForKill(int killCount) {
        return switch (killCount) {
            case 1 -> KILL1_TEX;
            case 2 -> KILL2_TEX;
            case 3 -> KILL3_TEX;
            case 4 -> KILL4_TEX;
            default -> OVERKILL_TEX; // Kill 5+ displays OVERKILL banner
        };
    }

    private static SoundEvent getSoundForKill(int killCount) {
        return switch (killCount) {
            case 1 -> SoundRegistry.MARKSMAN_KILL1.get();
            case 2 -> SoundRegistry.MARKSMAN_KILL2.get();
            case 3 -> SoundRegistry.MARKSMAN_KILL3.get();
            case 4 -> SoundRegistry.MARKSMAN_KILL4.get();
            default -> SoundRegistry.MARKSMAN_KILL5.get(); // Kill 5+ plays kill5 audio
        };
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }

        if (currentTexture == null || bannerStartTime == 0) {
            return;
        }

        long elapsed = System.currentTimeMillis() - bannerStartTime;
        if (elapsed >= BANNER_DURATION_MS) {
            currentTexture = null;
            bannerStartTime = 0;
            return;
        }

        float alpha = 1.0f;
        float scale = 1.0f;

        if (elapsed < 180) { // Pop & Fade In Phase (0ms -> 180ms)
            float t = elapsed / 180.0f;
            alpha = Math.min(1.0f, t * 1.5f);
            
            if (t < 0.70f) { // Scale Pop Overshoot: 0.40 -> 1.20
                float p = t / 0.70f;
                scale = 0.40f + 0.80f * (float) Math.sin(p * Math.PI * 0.5);
            } else { // Settle back: 1.20 -> 1.00
                float p = (t - 0.70f) / 0.30f;
                scale = 1.20f - 0.20f * (float) Math.sin(p * Math.PI * 0.5);
            }
        } else if (elapsed > 750) { // Fade Out Phase (750ms -> 1000ms)
            float t = (elapsed - 750) / 250.0f;
            alpha = Math.max(0.0f, 1.0f - t);
            scale = 1.0f - 0.05f * t;
        } else { // Sustain Phase (180ms -> 750ms)
            alpha = 1.0f;
            scale = 1.0f;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        // Square banner dimensions (80x80 - 2x size increase, 1:1 ratio)
        int bannerWidth = 80;
        int bannerHeight = 80;

        // Position: Centered above hotbar with generous spacing
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight - 95.0f;

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, 0.0f);
        poseStack.scale(scale, scale, 1.0f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

        guiGraphics.blit(currentTexture, -bannerWidth / 2, -bannerHeight / 2, 0, 0, bannerWidth, bannerHeight, bannerWidth, bannerHeight);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();

        poseStack.popPose();
    }
}
