package com.complextalents.impl.warrior;

import com.complextalents.origin.OriginBuilder;
import com.complextalents.origin.ResourceType;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import net.minecraft.resources.ResourceLocation;

/**
 * Warrior Origin implementation.
 */
public class WarriorOrigin {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "warrior");
    public static final ResourceLocation STYLE_RESOURCE_ID = ResourceLocation.fromNamespaceAndPath("complextalents",
            "style");

    public static void register() {
        // Register Style resource type
        ResourceType styleType = ResourceType.register(STYLE_RESOURCE_ID, "Style", 0, 1000, 0xFFFFD700); // Gold color

        // Build and register Warrior origin
        OriginBuilder.create(ID)
                .displayName("Warrior")
                .description(
                        "Đấu sĩ tuyến đầu tích điểm Style (0-1000) từ đòn đánh. Giảm 40%-60% sát thương nhận vào và tăng sát thương theo rank Style. Đạt SSS-Rank (950+) nhận True Hit Immunity; khi nhận sát thương chí mạng sẽ kích hoạt Cheat Death tiêu thụ Style về 250 điểm.")
                .resourceType(styleType)
                .maxLevel(5)
                .baseStat(StatType.PERCENT_AD, 4)
                .baseStat(StatType.MAX_HP, 2)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                // Passive Skill: Vanguard's Momentum - Damage Scaling
                .scaledStat("momentumDamage_D", new double[] { 0.7, 0.7, 0.7, 0.7, 0.7 })
                .scaledStat("momentumDamage_C", new double[] { 0.85, 0.85, 0.85, 0.85, 0.85 })
                .scaledStat("momentumDamage_B", new double[] { 1.0, 1.0, 1.0, 1.0, 1.0 })
                .scaledStat("momentumDamage_A", new double[] { 1.02, 1.04, 1.05, 1.06, 1.08 })
                .scaledStat("momentumDamage_S", new double[] { 1.04, 1.06, 1.08, 1.10, 1.12 })
                .scaledStat("momentumDamage_SS", new double[] { 1.05, 1.08, 1.12, 1.15, 1.18 })
                .scaledStat("momentumDamage_SSS", new double[] { 1.06, 1.10, 1.15, 1.20, 1.25 })
                // Vanguard's Momentum - Max Damage Reduction per origin level (SSS Rank max DR)
                .scaledStat("maxDamageReduction", new double[] { 0.40, 0.45, 0.50, 0.55, 0.60 })

                .passiveSkill("Vanguard's Momentum",
                        "Tấn công liên tục để tích điểm Style, tăng sát thương và giảm sát thương gánh chịu. Ở cấp SSS nhận True Hit Immunity và khả năng vỡ Style thoát chết (Cheat Death).")
                .activeSkill("Challenger's Retribution",
                        "Đỡ khiên hấp thụ sát thương, khiêu khích kẻ địch lân cận và nhả phím (hoặc khi vỡ khiên) để phản lại sát thương diện rộng.",
                        null)
                .activeSkillId(ResourceLocation.fromNamespaceAndPath("complextalents", "challengers_retribution"))
                // Level defensive scaling (Warrior = Tank/Frontliner controlled curve)
                .levelArmorCalc(lvl -> lvl <= 1 ? 0.0 : Math.round(Math.pow(lvl - 1, 1.15) * 1.5))
                .levelToughnessCalc(lvl -> lvl <= 1 ? 0.0 : Math.min(40.0, Math.pow(lvl - 1, 1.1) * 0.4))
                .levelHealthCalc(lvl -> 0.0)
                .renderer(new com.complextalents.impl.warrior.client.WarriorRenderer())
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.FLAT_AD, 2)
                .cost(StatType.PERCENT_AD, 1)
                .cost(StatType.AP, 4)
                .cost(StatType.ARMOR_PEN, 2)
                .cost(StatType.LUCK_CRIT, 3)
                .cost(StatType.MAX_HP, 2)
                .cost(StatType.MAX_MANA, 4)
                .cost(StatType.HEAL_AND_SHIELD, 4)
                .cost(StatType.CDR, 3)
                .spellMasteryCostMultiplier(3.0) // Warrior terrible with spells, 300% cost
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ELDRITCH, -1.0)
                .weaponMasteryCostMultiplier(1.0); // Warrior normal with melee weapons, 100% cost
    }
}
