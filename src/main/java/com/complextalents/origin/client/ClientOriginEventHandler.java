package com.complextalents.origin.client;

import com.complextalents.TalentsMod;
import com.complextalents.client.screen.OriginSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side event handler for origin data.
 * Handles automatically opening the origin selection screen when a player logs in without an origin.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
public class ClientOriginEventHandler {

    private static boolean hasCheckedOriginOnLogin = false;

    /**
     * Check if player needs to select an origin when they first join the world.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // Check only once per login
        if (!hasCheckedOriginOnLogin && mc.player != null && mc.level != null) {
            // Player has just entered the world
            if (!ClientOriginData.hasOrigin()) {
                // Player has no origin - open selection screen
                mc.setScreen(new OriginSelectionScreen());
            }
            hasCheckedOriginOnLogin = true;
        }

        // Reset flag when player disconnects
        if (mc.player == null) {
            hasCheckedOriginOnLogin = false;
        }
    }
}
