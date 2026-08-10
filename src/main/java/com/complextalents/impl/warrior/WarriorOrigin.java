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
                .displayName("Đấu Sĩ")
                .description(
                        "Đấu sĩ tuyến đầu tích lũy từ 0 đến 1000 điểm Đấu Khí qua đòn đánh. Giảm 40% đến 60% sát thương nhận vào, tăng đến 12% sát thương và 35% Tốc Độ Đánh theo cấp Đấu Khí. Khi Đấu Khí đạt 950 điểm trở lên sẽ trở nên không thể cản phá, khi nhận sát thương tử thương sẽ tiêu thụ Đấu Khí về 250 điểm để thoát chết.")
                .resourceType(styleType)
                .maxLevel(5)
                .baseStat(StatType.PERCENT_AD, 4)
                .baseStat(StatType.MAX_HP, 2)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                // Passive Skill: Vanguard's Momentum - Damage Scaling
                .scaledStat("momentumDamage_D", "ST Rank D", new double[] { 0.60, 0.60, 0.60, 0.60, 0.60 })
                .scaledStat("momentumDamage_C", "ST Rank C", new double[] { 0.80, 0.80, 0.80, 0.80, 0.80 })
                .scaledStat("momentumDamage_B", "ST Rank B", new double[] { 0.95, 0.95, 0.95, 0.95, 0.95 })
                .scaledStat("momentumDamage_A", "ST Rank A", new double[] { 1.00, 1.01, 1.02, 1.025, 1.03 })
                .scaledStat("momentumDamage_S", "ST Rank S", new double[] { 1.02, 1.03, 1.035, 1.04, 1.05 })
                .scaledStat("momentumDamage_SS", "ST Rank SS", new double[] { 1.04, 1.05, 1.06, 1.07, 1.08 })
                .scaledStat("momentumDamage_SSS", "ST Rank SSS", new double[] { 1.05, 1.07, 1.09, 1.10, 1.12 })
                // Vanguard's Momentum - Max Damage Reduction per origin level (SSS Rank max DR)
                .scaledStat("maxDamageReduction", "Giảm ST Max (%)", new double[] { 0.40, 0.45, 0.50, 0.55, 0.60 })

                .passiveSkill("Nhịp Độ Tiên Phong",
                        "Tấn công liên tục để tích lũy Đấu Khí, giúp giảm 40% đến 60% sát thương gánh chịu, tăng đến 12% sát thương gây ra và tăng 5% đến 35% Tốc Độ Đánh. Khi Đấu Khí đạt cực hạn, nhận trạng thái không thể cản phá và thoát khỏi một đòn tử thương.")
                .activeSkill("Khiêu Chiến & Phản Trảm",
                        "Giơ khiên hấp thụ sát thương và khiêu khích kẻ địch xung quanh, sau đó nhả khiên để phản lại sát thương diện rộng bằng 100% đến 200% lượng sát thương đã hấp thụ.",
                        null)
                .activeSkillId(ResourceLocation.fromNamespaceAndPath("complextalents", "challengers_retribution"))
                // Level defensive scaling (Warrior = Tank/Frontliner controlled curve)
                .levelArmorCalc(lvl -> lvl <= 1 ? 0.0 : Math.round(Math.pow(lvl - 1, 1.15) * 1.275))
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
