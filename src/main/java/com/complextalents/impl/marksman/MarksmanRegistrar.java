package com.complextalents.impl.marksman;

import com.complextalents.TalentsMod;
import com.complextalents.impl.marksman.origin.MarksmanOrigin;

/**
 * Central registration point for Marksman origin.
 */
public class MarksmanRegistrar {

    public static void register() {
        com.complextalents.impl.marksman.skill.RelentlessPursuitSkill.register();
        MarksmanOrigin.register();
        TalentsMod.LOGGER.info("Marksman origin registered with Relentless Pursuit skill");
    }

}
