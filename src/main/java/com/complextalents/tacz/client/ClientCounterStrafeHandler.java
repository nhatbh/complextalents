package com.complextalents.tacz.client;

import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side counter-strafe mechanic for TACZ firearms.
 * Instantly cancels horizontal momentum when holding opposing movement keys (A + D or W + S)
 * or counter-tapping opposing strafe keys, bringing the player to a crisp stop for instant ADS accuracy.
 */
@Mod.EventBusSubscriber(modid = "complextalents", value = Dist.CLIENT)
public class ClientCounterStrafeHandler {

    private static float lastXxa = 0.0f;
    private static float lastZza = 0.0f;

    @SubscribeEvent
    public static void onClientPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isClient()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player != event.player || !player.onGround()) return;

        // Only activate counter-strafe when holding a TACZ firearm
        if (IGun.getIGunOrNull(player.getMainHandItem()) == null) {
            return;
        }

        Options options = mc.options;
        boolean keyLeft = options.keyLeft.isDown();
        boolean keyRight = options.keyRight.isDown();
        boolean keyUp = options.keyUp.isDown();
        boolean keyDown = options.keyDown.isDown();

        // 1. Holding opposing keys simultaneously (A + D or W + S)
        boolean opposingStrafe = keyLeft && keyRight;
        boolean opposingForward = keyUp && keyDown;

        // 2. Directional counter-tap detection (e.g. moving left and pressing right)
        float currentXxa = player.input != null ? player.input.leftImpulse : 0.0f;
        float currentZza = player.input != null ? player.input.forwardImpulse : 0.0f;

        boolean directionalCounter = (lastXxa > 0.1f && keyRight) || (lastXxa < -0.1f && keyLeft) ||
                                     (lastZza > 0.1f && keyDown) || (lastZza < -0.1f && keyUp);

        lastXxa = currentXxa;
        lastZza = currentZza;

        if (opposingStrafe || opposingForward || directionalCounter) {
            Vec3 vel = player.getDeltaMovement();
            // Rapidly cancel horizontal momentum (retain 5% residual velocity) to stop gliding
            player.setDeltaMovement(vel.x * 0.05, vel.y, vel.z * 0.05);
        }
    }
}
