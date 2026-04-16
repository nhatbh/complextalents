package com.complextalents.client;

import com.complextalents.TalentsMod;
import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.skill.client.ClientSkillData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles client-side lifecycle events, such as clearing caches on logout.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
public class ClientLifecycleHandler {

    /**
     * Clear all client-side origin and skill data when the player logs out.
     * This prevents stale data from persisting between different worlds/sessions.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientOriginData.clear();
        ClientSkillData.clear();
        TalentsMod.LOGGER.info("[CLIENT] Cleared origin and skill caches on logout.");
    }
}
