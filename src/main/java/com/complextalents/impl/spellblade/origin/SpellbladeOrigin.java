package com.complextalents.impl.spellblade.origin;

import com.complextalents.TalentsMod;
import com.complextalents.impl.spellblade.client.SpellbladeRenderer;
import com.complextalents.impl.spellblade.skill.SpellbladeOverchargeSkill;
import com.complextalents.origin.OriginBuilder;
import com.complextalents.origin.OriginManager;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SpellbladeOrigin {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "spellblade");

    // Passive & Active Parameters
    // Passive & Active Parameters
    public static final double[] BASE_MANA_PER_HIT = { 0.06, 0.08, 0.10, 0.12, 0.15 };
    public static final double[] ACTIVE_COOLDOWN = { 5.0, 5.0, 5.0, 5.0, 5.0 };
    public static final double[] INITIAL_STANCE_DURATION = { 6.0, 6.0, 6.0, 6.0, 6.0 };
    public static final double[] DURATION_ADDED_PER_CAST = { 2.0, 3.0, 4.0, 5.0, 6.0 };
    public static final double[] MAX_STANCE_CAP = { 30.0, 30.0, 30.0, 30.0, 30.0 };

    // Overcharge Stance Scaling
    public static final double[] ENHANCED_EFFECT_MULT = { 1.25, 1.40, 1.55, 1.70, 1.90 };
    public static final double[] AP_DAMAGE_GAIN_RATIO = { 0.40, 0.55, 0.70, 0.85, 1.00 };
    public static final double[] BASE_MANA_DRAIN_PER_HIT = { 10.0, 10.0, 10.0, 10.0, 10.0 };
    public static final double[] MANA_DRAIN_DAMAGE_SCALING = { 0.15, 0.135, 0.12, 0.105, 0.09 };

    // Fire Imbue
    public static final double[] FIRE_DMG_MULT = { 0.15, 0.20, 0.25, 0.30, 0.40 };
    public static final double[] FIRE_AP_RATIO = { 2.0, 3.0, 4.0, 5.0, 6.0 };

    // Ice Imbue
    public static final double[] ICE_FREEZE_BASE_SEC = { 1.0, 1.25, 1.50, 1.75, 2.00 };
    public static final double[] ICE_FREEZE_AP_SCALING = { 0.5, 0.75, 1.0, 1.25, 1.5 };

    // Lightning Imbue (Multi-target splash damage nerfed to ~30% of Fire single-target damage boost)
    public static final double[] LIGHTNING_SPLASH_BASE_DMG = { 1.0, 1.5, 2.0, 2.5, 3.0 };
    public static final double[] LIGHTNING_AP_RATIO = { 0.5, 0.75, 1.0, 1.25, 1.5 };
    public static final double[] LIGHTNING_HASTE_PCT = { 0.10, 0.15, 0.20, 0.25, 0.30 };

    // Nature Imbue (Tuned as a clutch survival tool rather than a tanking tool)
    public static final double[] NATURE_SHIELD_BASE = { 1.5, 2.5, 3.5, 4.5, 5.5 };
    public static final double[] NATURE_SHIELD_AP_RATIO = { 0.5, 0.75, 1.0, 1.25, 1.5 };

    // Water Imbue
    public static final double[] WATER_MANA_BASE = { 4.0, 6.0, 8.0, 10.0, 12.0 };
    public static final double[] WATER_MANA_AP_RATIO = { 2.0, 3.0, 4.0, 5.0, 6.0 };

    // Evocation Imbue
    public static final double[] EVOCATION_KNOCKBACK_DIST = { 2.0, 2.5, 3.0, 3.5, 4.0 };
    public static final double[] EVOCATION_STANCE_DMG_PCT = { 0.15, 0.20, 0.25, 0.30, 0.40 };

    // Blood Imbue
    public static final double[] BLOOD_BLEED_DOT_BASE = { 3.0, 5.0, 7.0, 9.0, 12.0 };
    public static final double[] BLOOD_BLEED_AP_RATIO = { 2.0, 3.0, 4.0, 5.0, 6.0 };
    public static final double[] BLOOD_ANTI_HEAL_PCT = { 0.40, 0.55, 0.70, 0.85, 1.00 };

    // Ender Imbue (Armor Pierce % + Void damage)
    public static final double[] ENDER_ARMOR_PIERCE_PCT = { 0.30, 0.45, 0.60, 0.75, 1.00 };
    public static final double[] ENDER_VOID_BASE_DMG = { 1.5, 2.0, 2.5, 3.0, 4.0 };
    public static final double[] ENDER_VOID_AP_RATIO = { 0.5, 0.8, 1.0, 1.2, 1.5 };

    // Eldritch Imbue (Scales absorb ratio from 20% to 60% with AP bonus)
    public static final double[] ELDRITCH_ABSORBED_BASE_PCT = { 0.20, 0.30, 0.40, 0.50, 0.60 };
    public static final double[] ELDRITCH_ABSORBED_AP_RATIO = { 0.05, 0.08, 0.10, 0.12, 0.15 };
    public static final double[] ELDRITCH_REFRESH_VOLATILITY = { 0.03, 0.04, 0.05, 0.06, 0.08 };

    public static void register() {
        OriginBuilder.create("complextalents", "spellblade")
                .displayName("Ma Kiếm Sĩ")
                .description(Component.literal(
                        "Bậc thầy dung hợp ma thuật và kiếm thuật theo phong cách Spell Weaver. Đòn chém giúp hồi 6%-15% Mana tối đa và tích lũy Kho Mana Ảo (tối đa 50% Mana) chỉ dùng cho thi triển phép trong Quá Tải. Phép thuật yểm nguyên tố lên vũ khí trong 6s, hiệu ứng nguyên tố tỉ lệ theo Tốc Độ Đánh của vũ khí (Attack Speed Weight). Khi Quá Tải, phép thi triển ≤ 5s trở thành tức thì (0s) và tăng 1.25x-1.90x hiệu ứng nguyên tố."))
                .maxLevel(5)
                .baseStat(StatType.AP, 4)
                .baseStat(StatType.MAX_MANA, 2)
                .baseStat(StatType.FLAT_AD, 2)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                .scaledStat("base_mana_per_hit", "Hồi Mana/Hit (%)", BASE_MANA_PER_HIT)
                .passiveSkill("Dệt Năng Lượng",
                        "Đòn chém cận chiến hồi 6%-15% Mana tối đa. Khi Quá Tải, tích lũy Kho Mana Ảo (tối đa 50% Mana) thi triển phép. Phép thuật yểm nguyên tố lên vũ khí trong 6s (tỉ lệ theo Tốc Đánh & AP).")
                .activeSkill("Quá Tải",
                        "Trạng Thái Quá Tải (hồi chiêu 5s): Phép thuật thi triển ≤ 5s trở thành tức thì (0s). Tăng 1.25x đến 1.90x hiệu ứng nguyên tố yểm trên vũ khí.",
                        ResourceLocation.fromNamespaceAndPath("complextalents",
                                "textures/skill/spellblade/spellblade.png"))
                .activeSkillId(SpellbladeOverchargeSkill.ID)
                .levelArmorCalc(lvl -> Math.round(2.0 + Math.pow(lvl - 1, 1.07) * 1.15))
                .levelToughnessCalc(lvl -> Math.min(30.0, 0.5 + Math.pow(lvl - 1, 1.05) * 0.25))
                .levelHealthCalc(lvl -> 0.0)
                .renderer(new SpellbladeRenderer())
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.FLAT_AD, 2)
                .cost(StatType.PERCENT_AD, 2)
                .cost(StatType.AP, 1)
                .cost(StatType.ARMOR_PEN, 2)
                .cost(StatType.LUCK_CRIT, 2)
                .cost(StatType.MAX_HP, 4)
                .cost(StatType.MAX_MANA, 2)
                .cost(StatType.HEAL_AND_SHIELD, 4)
                .cost(StatType.CDR, 1)
                .spellMasteryCostMultiplier(1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.FIRE, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ICE, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.LIGHTNING, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.NATURE, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.AQUA, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.EVOCATION, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ENDER, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.BLOOD, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ELDRITCH, 2.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.HOLY, -1.0)
                .weaponMasteryCostMultiplier(1);

        SpellbladeOverchargeSkill.register();

        TalentsMod.LOGGER.info("Spellblade origin registered");
    }

    public static boolean isSpellblade(ServerPlayer player) {
        return ID.equals(OriginManager.getOriginId(player));
    }
}
