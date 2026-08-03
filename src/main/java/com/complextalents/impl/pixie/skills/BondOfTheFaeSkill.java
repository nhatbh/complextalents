package com.complextalents.impl.pixie.skills;

import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import net.minecraft.resources.ResourceLocation;

public class BondOfTheFaeSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "bond_of_the_fae");

    public static void register() {
        SkillBuilder.create("complextalents", "bond_of_the_fae")
                .nature(SkillNature.PASSIVE)
                .displayName("Bond of the Fae")
                .description("Attach to a host ally to become invulnerable and grant Adaptive Aura stats based on host's highest stat (Physical or Magical). Ejected and silenced for 3s if host dies.")
                .setMaxLevel(5)
                .scaledStat("adBuff", new double[] { 0.10, 0.15, 0.20, 0.25, 0.35 })
                .scaledStat("defBuff", new double[] { 0.05, 0.10, 0.15, 0.20, 0.25 })
                .scaledStat("apBuff", new double[] { 0.10, 0.15, 0.20, 0.25, 0.35 })
                .scaledStat("manaRegenBuff", new double[] { 0.10, 0.15, 0.20, 0.25, 0.30 })
                .scaledStat("tetherRange", new double[] { 6, 7, 8, 9, 10 })
                .register();
    }
}
