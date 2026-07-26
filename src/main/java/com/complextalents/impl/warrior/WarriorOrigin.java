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
                        "Đấu sĩ tuyến đầu tích điểm Style (0-1000) từ đòn đánh để tăng từ 0.7x đến 1.5x sát thương. Đạt cấp SSS nhận 1 lớp khiên chặn đòn đánh, vỡ khiên sẽ đưa Style về 250-900 điểm.")
                .resourceType(styleType)
                .maxLevel(5)
                .baseStat(StatType.PERCENT_AD, 4)
                .baseStat(StatType.MAX_HP, 2)
                // Passive Skill: Vanguard's Momentum - Damage Scaling
                .scaledStat("momentumDamage_D", new double[] { 0.7, 0.7, 0.7, 0.7, 0.7 })
                .scaledStat("momentumDamage_C", new double[] { 0.85, 0.85, 0.85, 0.85, 0.85 })
                .scaledStat("momentumDamage_B", new double[] { 1.0, 1.0, 1.0, 1.0, 1.0 })
                .scaledStat("momentumDamage_A", new double[] { 1.05, 1.08, 1.1, 1.12, 1.15 })
                .scaledStat("momentumDamage_S", new double[] { 1.08, 1.12, 1.15, 1.25, 1.3 })
                .scaledStat("momentumDamage_SS", new double[] { 1.09, 1.15, 1.25, 1.35, 1.4 })
                .scaledStat("momentumDamage_SSS", new double[] { 1.1, 1.2, 1.3, 1.4, 1.5 })
                // Vanguard's Momentum - Shield Break Reset (Style Points)
                .scaledStat("shieldBreakReset", new double[] { 250, 450, 700, 850, 900 })

                .passiveStack("sss_shield", com.complextalents.passive.PassiveStackDef.create("SSS Shield")
                        .maxStacks(1)
                        .displayName("SSS Shield")
                        .build())
                .passiveSkill("Vanguard's Momentum",
                        "Tấn công liên tục để tích điểm Style, tăng mạnh sát thương và tạo khiên bảo vệ ở cấp tối đa.")
                .activeSkill("Challenger's Retribution", "Đỡ khiên hấp thụ sát thương, khiêu khích kẻ địch lân cận và nhả phím để phản lại sát thương diện rộng.", null)
                .activeSkillId(ResourceLocation.fromNamespaceAndPath("complextalents", "challengers_retribution"))
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
                .cost(StatType.MOBILITY, 2)
                .cost(StatType.CDR, 3)
                .spellMasteryCostMultiplier(3.0) // Warrior terrible with spells, 300% cost
                .weaponMasteryCostMultiplier(1.0); // Warrior normal with melee weapons, 100% cost
    }
}
