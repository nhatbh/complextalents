package com.complextalents.impl.elementalmage.origin;

import com.complextalents.TalentsMod;
import com.complextalents.origin.OriginBuilder;
import com.complextalents.origin.OriginManager;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.common.Mod;

/**
 * The Elemental Mage Origin - Masters of elemental combo magic.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class ElementalMageOrigin {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents",
            "elemental_mage");

    public static final double[] REACTION_DAMAGE_BONUS = { 0.25, 0.50, 0.75, 1.00, 1.30 };

    /**
     * Register the Elemental Mage origin.
     * Call this during mod initialization.
     */
    public static void register() {
        OriginBuilder.create("complextalents", "elemental_mage")
                .displayName(Component.translatable("origin.complextalents.elemental_mage"))
                .description(Component.translatable("origin.complextalents.elemental_mage.desc"))
                .maxLevel(5)
                .baseStat(StatType.AP, 3)
                .baseStat(StatType.MAX_MANA, 6)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                .scaledStat("reaction_damage_bonus", "ST Phản Ứng (%)", REACTION_DAMAGE_BONUS)
                .passiveSkill("Hài Hoà",
                        "Kích hoạt các phản ứng nguyên tố khác loại để tích tụ tối đa 3 Dấu Ấn Nguyên Tố tồn tại vĩnh viễn và gia tăng từ 25% đến 130% sát thương cho mọi phản ứng nguyên tố.")

                .activeSkill("Hội Tụ",
                        "Bộc phát Dấu Ấn Nguyên Tố tích tụ để tăng từ 1.15 đến 1.5 lần sát thương phép theo số Dấu Ấn, tăng 30% đến 60% tỷ lệ bạo kích và đến 100% sát thương bạo kích phép trong 10 giây.",
                        null)

                .activeSkillId(ResourceLocation.fromNamespaceAndPath("complextalents", "harmonic_convergence"))
                // Level defensive scaling (Elemental Mage = Evocation Caster controlled curve)
                .levelArmorCalc(lvl -> lvl <= 1 ? 0.0 : Math.round(Math.pow(lvl - 1, 1.15) * 0.7))
                .levelToughnessCalc(lvl -> lvl <= 1 ? 0.0 : Math.min(25.0, Math.pow(lvl - 1, 1.1) * 0.2))
                .levelHealthCalc(lvl -> 0.0)
                .renderer(new com.complextalents.impl.elementalmage.client.ElementalMageRenderer())
                .gunConfusionMessages(
                        "origin.complextalents.elemental_mage.gun_msg.1",
                        "origin.complextalents.elemental_mage.gun_msg.2",
                        "origin.complextalents.elemental_mage.gun_msg.3",
                        "origin.complextalents.elemental_mage.gun_msg.4",
                        "origin.complextalents.elemental_mage.gun_msg.5"
                )
                .upgradableStats(
                        StatType.FLAT_AD,
                        StatType.PERCENT_AD,
                        StatType.AP,
                        StatType.MAGIC_EFFECTIVENESS,
                        StatType.LUCK_CRIT,
                        StatType.MAX_HP,
                        StatType.MAX_MANA,
                        StatType.CDR,
                        StatType.SUMMONING_POWER
                )
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.FLAT_AD, 4)
                .cost(StatType.PERCENT_AD, 4)
                .cost(StatType.AP, 1)
                .cost(StatType.MAGIC_EFFECTIVENESS, 2)
                .cost(StatType.LUCK_CRIT, 2)
                .cost(StatType.MAX_HP, 4)
                .cost(StatType.MAX_MANA, 1)
                .cost(StatType.CDR, 1)
                .cost(StatType.SUMMONING_POWER, 1)
                .spellMasteryCostMultiplier(1.0)
                // Primal Elements (Base multiplier: 1x)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.FIRE, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ICE, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.LIGHTNING, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.NATURE, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.AQUA, 1.0)
                // Arcane Elements (Bad affinity: 3x cost)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.EVOCATION, 3.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ENDER, 3.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.BLOOD, 3.0)
                // Holy (No affinity: 5x cost)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.HOLY, 5.0)
                // Eldritch & Abyssal (Dark Mage Exclusive: -1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ELDRITCH, -1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ABYSSAL, -1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.TECHNOMANCY, 2.0)
                .weaponMasteryCostMultiplier(3.0);

        // Register Harmonic Convergence Skill
        com.complextalents.impl.elementalmage.skill.HarmonicConvergenceSkill.register();

        TalentsMod.LOGGER.info("Elemental Mage origin registered");
    }

    /**
     * Get the Elemental Mage origin ID.
     */
    public static ResourceLocation getId() {
        return ID;
    }

    /**
     * Check if a player is an Elemental Mage.
     */
    public static boolean isElementalMage(ServerPlayer player) {
        return ID.equals(OriginManager.getOriginId(player));
    }
}
