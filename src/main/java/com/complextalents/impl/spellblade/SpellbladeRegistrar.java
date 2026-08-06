package com.complextalents.impl.spellblade;

import com.complextalents.TalentsMod;
import com.complextalents.impl.spellblade.origin.SpellbladeOrigin;

/**
 * Central registration point for Spellblade origin and active skills.
 */
public class SpellbladeRegistrar {

    public static void register() {
        SpellbladeOrigin.register();
        TalentsMod.LOGGER.info("Spellblade origin and skills registered");
    }
}
