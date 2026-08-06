package com.complextalents.spellfx.client;

import com.complextalents.TalentsMod;
import com.complextalents.util.IronParticleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side feedback handler for spellcasting effects.
 * Manages directional camera screen shake, pitch recoil kick, FOV kick impulse,
 * elemental school muzzle flash overlay, and damage-scaled back splatter vector particles.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
public class ClientSpellFXHandler {

    // Screen Shake variables
    private static int shakeTicks = 0;
    private static int maxShakeTicks = 1;
    private static float currentIntensity = 0.0f;
    private static float randomPhase = 0.0f;

    // Recoil Pitch Kick variables
    private static int recoilTicks = 0;
    private static int maxRecoilTicks = 1;
    private static float recoilPitch = 0.0f;

    // Muzzle Flash Overlay variables
    private static float flashAlpha = 0.0f;
    private static int flashColorRGB = 0xFFFFFF;

    /**
     * Triggers complete spellcasting feedback (screen shake, camera recoil kick, FOV impulse, and muzzle flash overlay).
     *
     * @param manaCost Mana required for the spell.
     * @param colorHex RGB color matching the spell's school.
     */
    public static void triggerFeedback(int manaCost, int colorHex) {
        int effectiveMana = Math.max(15, manaCost);

        // 1. Screen Shake setup (intensity 0.8 to 3.5, duration 6 to 20 ticks)
        float baseIntensity = (float) Math.min(3.5, 0.8 + (effectiveMana / 100.0) * 1.5);
        int baseDuration = Math.min(20, Math.max(6, 5 + (int) (effectiveMana / 20.0)));

        if (baseIntensity > currentIntensity || shakeTicks <= 2) {
            currentIntensity = baseIntensity;
            shakeTicks = baseDuration;
            maxShakeTicks = baseDuration;
            randomPhase = (float) (Math.random() * 100.0);
        } else {
            shakeTicks = Math.max(shakeTicks, baseDuration);
        }

        // 2. Camera Recoil Pitch Kick setup (1.2 to 4.5 degrees pitch kick)
        float newRecoil = (float) Math.min(4.5, 1.2 + (effectiveMana / 60.0) * 1.5);
        if (newRecoil > recoilPitch || recoilTicks <= 1) {
            recoilPitch = newRecoil;
            recoilTicks = Math.min(10, Math.max(4, 4 + (int) (effectiveMana / 35.0)));
            maxRecoilTicks = recoilTicks;
        }

        // 3. Muzzle Flash Overlay setup (starting alpha ~0.25 to ~0.50)
        flashColorRGB = colorHex;
        flashAlpha = (float) Math.min(0.50, 0.25 + (effectiveMana / 150.0) * 0.20);
    }

    /**
     * Spawns back splatter vector particles and a flash particle when a spell hits an entity, scaled by damage dealt.
     */
    public static void spawnBackSplatter(double posX, double posY, double posZ, double dirX, double dirY, double dirZ, float damage, String schoolPath) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        net.minecraft.util.RandomSource rand = mc.level.getRandom();

        // 1. Spawn single instant vanilla flash particle at point of impact
        mc.level.addParticle(ParticleTypes.FLASH, posX, posY, posZ, 0, 0, 0);

        // 2. Calculate reduced particle count scaled off damage dealt (minimum 4, max 18)
        int particleCount = Math.min(18, Math.max(4, (int)(damage * 0.8f)));

        Vec3 dir = new Vec3(dirX, dirY, dirZ);
        if (dir.lengthSqr() < 0.001) {
            dir = new Vec3(0, 0, 1);
        } else {
            dir = dir.normalize();
        }

        for (int i = 0; i < particleCount; i++) {
            double speed = 0.22 + rand.nextDouble() * 0.35;

            // Cone spray deviation around impact vector
            double spreadX = (rand.nextDouble() - 0.5) * 0.45;
            double spreadY = (rand.nextDouble() - 0.5) * 0.45 + 0.10;
            double spreadZ = (rand.nextDouble() - 0.5) * 0.45;

            double vx = (dir.x * speed) + spreadX;
            double vy = (dir.y * speed) + spreadY;
            double vz = (dir.z * speed) + spreadZ;

            double spawnX = posX + dir.x * 0.2 + (rand.nextDouble() - 0.5) * 0.25;
            double spawnY = posY + dir.y * 0.2 + (rand.nextDouble() - 0.5) * 0.25;
            double spawnZ = posZ + dir.z * 0.2 + (rand.nextDouble() - 0.5) * 0.25;

            ParticleOptions particle = getSplatterParticle(schoolPath, i);
            if (particle != null) {
                mc.level.addParticle(particle, spawnX, spawnY, spawnZ, vx, vy, vz);
            }
        }
    }

    private static ParticleOptions getSplatterParticle(String schoolPath, int index) {
        if (schoolPath == null) schoolPath = "default";
        return switch (schoolPath.toLowerCase()) {
            case "blood" -> (index % 2 == 0) ?
                    IronParticleHelper.getIronParticle("blood") :
                    IronParticleHelper.getIronParticle("acid_bubble");
            case "fire" -> (index % 2 == 0) ?
                    IronParticleHelper.getIronParticle("fire") :
                    IronParticleHelper.getIronParticle("ember");
            case "ice" -> (index % 2 == 0) ?
                    IronParticleHelper.getIronParticle("ice") :
                    IronParticleHelper.getIronParticle("snowflake");
            case "lightning" -> (index % 2 == 0) ?
                    IronParticleHelper.getIronParticle("lightning") :
                    IronParticleHelper.getIronParticle("electricity");
            case "ender" -> (index % 2 == 0) ?
                    IronParticleHelper.getIronParticle("unstable_ender") :
                    IronParticleHelper.getIronParticle("portal");
            case "eldritch" -> (index % 2 == 0) ?
                    IronParticleHelper.getIronParticle("unstable_ender") :
                    IronParticleHelper.getIronParticle("smoke");
            case "holy" -> (index % 2 == 0) ?
                    ParticleTypes.END_ROD :
                    ParticleTypes.GLOW;
            case "evocation" -> (index % 2 == 0) ?
                    ParticleTypes.CRIT :
                    ParticleTypes.ENCHANT;
            case "nature" -> (index % 2 == 0) ?
                    IronParticleHelper.getIronParticle("nature") :
                    IronParticleHelper.getIronParticle("firefly");
            default -> (index % 2 == 0) ?
                    ParticleTypes.CRIT :
                    IronParticleHelper.getIronParticle("blood");
        };
    }

    /**
     * 1. Render HUD Muzzle Flash Overlay with decaying transparency
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.VIGNETTE.type() || flashAlpha <= 0.0f) {
            return;
        }

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        int alphaByte = Math.min(255, Math.max(0, (int) (flashAlpha * 255)));
        int argbColor = (alphaByte << 24) | (flashColorRGB & 0x00FFFFFF);

        event.getGuiGraphics().fill(0, 0, width, height, argbColor);
    }

    /**
     * 2. Recoil Camera Angles (Multi-frequency vibration shake + upward recoil kick)
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float partialTick = (float) event.getPartialTick();
        float addedPitch = 0.0f;
        float addedYaw = 0.0f;
        float addedRoll = 0.0f;

        // Recoil Pitch Kick calculation (snaps camera upward and recovers)
        if (recoilTicks > 0) {
            float currentRecoilTicks = recoilTicks - partialTick;
            float recoilProgress = 1.0f - (currentRecoilTicks / (float) maxRecoilTicks);
            recoilProgress = Math.max(0.0f, Math.min(1.0f, recoilProgress));
            float recoilDecay = (1.0f - recoilProgress) * (1.0f - recoilProgress);

            // Upward recoil pitch kick (negative pitch in MC camera)
            addedPitch -= recoilPitch * recoilDecay;
        }

        // Multi-frequency directional vibration screen shake
        if (shakeTicks > 0 && currentIntensity > 0.001f) {
            float currentShakeTicks = shakeTicks - partialTick;
            float shakeProgress = 1.0f - (currentShakeTicks / (float) maxShakeTicks);
            shakeProgress = Math.max(0.0f, Math.min(1.0f, shakeProgress));
            float shakeDecay = (1.0f - shakeProgress) * (1.0f - shakeProgress);
            float time = (float) (shakeTicks - partialTick) + randomPhase;

            addedPitch += (float) (Math.sin(time * 2.5f) * 0.7f + Math.cos(time * 5.1f) * 0.3f) * currentIntensity * shakeDecay * 1.20f;
            addedYaw   += (float) (Math.cos(time * 3.2f) * 0.7f + Math.sin(time * 6.3f) * 0.3f) * currentIntensity * shakeDecay * 0.90f;
            addedRoll  += (float) (Math.sin(time * 1.9f) * 0.8f + Math.cos(time * 4.4f) * 0.2f) * currentIntensity * shakeDecay * 1.00f;
        }

        if (addedPitch != 0.0f || addedYaw != 0.0f || addedRoll != 0.0f) {
            event.setPitch(event.getPitch() + addedPitch);
            event.setYaw(event.getYaw() + addedYaw);
            event.setRoll(event.getRoll() + addedRoll);
        }
    }

    /**
     * 3. FOV Recoil Kick Impulse
     */
    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (flashAlpha > 0.0f || recoilTicks > 0) {
            float fovBoost = (flashAlpha * 0.15f) + (recoilPitch * 0.015f);
            event.setNewFovModifier(event.getFovModifier() + fovBoost);
        }
    }

    /**
     * 4. Rapidly decay overlay alpha, recoil, and shake ticks on client tick
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (flashAlpha > 0.0f) {
            flashAlpha = Math.max(0.0f, flashAlpha - 0.08f);
        }

        if (recoilTicks > 0) {
            recoilTicks--;
            if (recoilTicks <= 0) {
                recoilPitch = 0.0f;
            }
        }

        if (shakeTicks > 0) {
            shakeTicks--;
            if (shakeTicks <= 0) {
                currentIntensity = 0.0f;
            }
        }
    }
}
