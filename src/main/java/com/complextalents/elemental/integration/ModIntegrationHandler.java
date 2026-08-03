package com.complextalents.elemental.integration;

import com.complextalents.TalentsMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;

public class ModIntegrationHandler {
    private static boolean isIronSpellbooksLoaded = false;
    private static boolean isBaseDefenseV2Loaded = false;

    public static void init() {
        isIronSpellbooksLoaded = false;
        isBaseDefenseV2Loaded = false;

        try {
            isIronSpellbooksLoaded = ModList.get().isLoaded("irons_spellbooks");
        } catch (Exception e) {
            // Mod not loaded or API not available
        }

        try {
            isBaseDefenseV2Loaded = ModList.get().isLoaded("basedefensev2");
        } catch (Exception e) {
            // Mod not loaded or API not available
        }

        if (isIronSpellbooksLoaded) {
            IronSpellbooksIntegration.init();
            TalentsMod.LOGGER.info("Iron's Spellbooks integration enabled");
        }

        if (isBaseDefenseV2Loaded) {
            try {
                MinecraftForge.EVENT_BUS.register(SpellShieldInteractionHandler.class);
                TalentsMod.LOGGER.info("BaseDefenseV2 integration enabled");
            } catch (Throwable t) {
                TalentsMod.LOGGER.warn("Failed to register BaseDefenseV2 integration: {}", t.getMessage());
            }
        }
    }

    public static boolean isIronSpellbooksLoaded() {
        return isIronSpellbooksLoaded;
    }

    public static boolean isBaseDefenseV2Loaded() {
        return isBaseDefenseV2Loaded;
    }
}
