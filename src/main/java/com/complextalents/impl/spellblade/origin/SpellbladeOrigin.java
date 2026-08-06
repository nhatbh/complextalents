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
    public static final double[] BASE_MANA_PER_HIT = { 0.02, 0.03, 0.04, 0.05, 0.06 };
    public static final double[] ACTIVE_COOLDOWN = { 150.0, 140.0, 130.0, 120.0, 100.0 };
    public static final double[] INITIAL_STANCE_DURATION = { 4.0, 5.0, 6.0, 7.0, 8.0 };
    public static final double[] DURATION_ADDED_PER_CAST = { 2.0, 3.0, 4.0, 5.0, 6.0 };
    public static final double[] MAX_STANCE_CAP = { 30.0, 30.0, 30.0, 30.0, 30.0 };

    // Fire Imbue
    public static final double[] FIRE_DMG_MULT = { 0.15, 0.20, 0.25, 0.30, 0.40 };
    public static final double[] FIRE_AP_RATIO = { 2.0, 3.0, 4.0, 5.0, 6.0 };

    // Ice Imbue
    public static final double[] ICE_FREEZE_BASE_SEC = { 1.0, 1.25, 1.50, 1.75, 2.00 };
    public static final double[] ICE_FREEZE_AP_SCALING = { 0.5, 0.75, 1.0, 1.25, 1.5 };

    // Lightning Imbue
    public static final double[] LIGHTNING_SPLASH_BASE_DMG = { 4.0, 6.0, 8.0, 10.0, 12.0 };
    public static final double[] LIGHTNING_AP_RATIO = { 2.0, 3.0, 4.0, 5.0, 6.0 };
    public static final double[] LIGHTNING_HASTE_PCT = { 0.10, 0.15, 0.20, 0.25, 0.30 };

    // Nature Imbue (Cut in half by user request)
    public static final double[] NATURE_SHIELD_BASE = { 4.0, 7.0, 10.0, 13.0, 16.0 };
    public static final double[] NATURE_SHIELD_AP_RATIO = { 2.0, 3.0, 4.0, 5.0, 6.0 };

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
                .displayName("Spellblade")
                .description(Component.literal(
                        "Bậc thầy kết hợp giữa vật lý và pháp thuật. Thi triển phép để yểm ma lực lên vũ khí, biến đòn chém thành đòn bộc phá nguyên tố và hồi Mana trên đòn đánh."))
                .maxLevel(5)
                .baseStat(StatType.AP, 2)
                .baseStat(StatType.FLAT_AD, 2)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                .scaledStat("base_mana_per_hit", "Base Mana per Hit (% Max)", BASE_MANA_PER_HIT)
                .passiveSkill("Dệt Năng Lượng",
                        "Mỗi nhát chém cận chiến giúp hồi lại % Mana tối đa (vũ khí vung càng chậm, Mana hồi càng nhiều). Khi thi triển phép thuật, lưỡi kiếm được nạp Năng Lượng Nguyên Tố, giúp đòn chém kế tiếp bộc phá hiệu ứng nguyên tố tương ứng.")
                .activeSkill("Ma Kiếm Quá Tải",
                        "Bộc phát trạng thái Quá Tải trong 30s: Chuyển hóa Sức Mạnh Ma Thuật (AP) thành Sức Mạnh Cận Chiến (AD). Mỗi phép thuật thi triển sẽ duy trì Cường Hóa Nguyên Tố liên tục trong 6s trên mọi đòn đánh.",
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
                .cost(StatType.MAX_MANA, 3)
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
