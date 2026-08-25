package com.complextalents.tacz;

import com.complextalents.TalentsMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * TACZ Gun Integration Module entry point.
 */
public class TACZModule {

    public static void init(IEventBus modEventBus) {
        TalentsMod.LOGGER.info("Initializing TACZ Gun Integration Module (264 Gun Attributes)...");

        // Attribute registration is managed by tacz_attributes

        // Register Forge event handlers
        MinecraftForge.EVENT_BUS.register(TACZGunEventHandler.class);

        TalentsMod.LOGGER.info("TACZ Gun Integration Module initialized successfully.");
    }
}
