package com.complextalents.impl.darkmage.origin;

import com.complextalents.TalentsMod;
import com.complextalents.impl.darkmage.client.DarkMageRenderer;
import com.complextalents.impl.darkmage.skill.VoidReversalSkill;
import com.complextalents.origin.OriginBuilder;
import com.complextalents.origin.OriginManager;
import com.complextalents.passive.PassiveStackDef;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Dark Mage Origin — High-Risk Arcane Risk-Manager.
 * <p>
 * Operates on Arcane Entropy (0%-100%) generated, flushed, and detonated by the
 * 4 Arcane schools.
 * Consumes Entropy via Void Reversal for guaranteed survival or via Eldritch
 * spells for nuclear burst damage.
 * </p>
 */
public class DarkMageOrigin {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "dark_mage");

    // Evocation
    public static final double[] EVOCATION_SHIELD_POISE_BONUS = { 0.10, 0.15, 0.20, 0.25, 0.30 };
    public static final double[] EVOCATION_ENTROPY_GEN = { 10.0, 12.0, 15.0, 18.0, 20.0 };

    // Ender
    public static final double[] VOID_STRIKE_DURATION = { 2.0, 2.5, 3.0, 3.5, 4.0 };
    public static final double[] ENDER_FLANK_DOWNED_MULT = { 1.4, 1.6, 1.8, 2.0, 2.2 };

    // Blood
    public static final double[] BLOOD_ENTROPY_FLUSH = { 20.0, 25.0, 30.0, 35.0, 40.0 };
    public static final double[] BLOOD_DOWNED_LIFESTEAL_MULT = { 1.2, 1.4, 1.6, 1.8, 2.0 };
    public static final double[] BLOOD_HP_COST_PCT = { 0.25, 0.22, 0.20, 0.18, 0.15 };

    // Eldritch
    public static final double[] ELDRITCH_REQUIRED_THRESHOLD = { 85.0, 80.0, 75.0, 70.0, 65.0 };
    public static final double[] ELDRITCH_MAX_NUKE_MULT = { 1.5, 1.75, 2.0, 2.25, 2.5 };
    public static final double[] ELDRITCH_BACKFIRE_SELF_DMG = { 0.25, 0.20, 0.15, 0.10, 0.05 };
    public static final double[] ELDRITCH_BACKFIRE_SILENCE_SEC = { 4.0, 3.5, 3.0, 2.5, 2.0 };

    public static void register() {
        OriginBuilder.create("complextalents", "dark_mage")
                .displayName(Component.translatable("origin.complextalents.dark_mage"))
                .description(Component.translatable("origin.complextalents.dark_mage.desc"))
                .maxLevel(5)
                .baseStat(StatType.AP, 7)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                // Scaling definitions
                .scaledStat("evo_shield_poise", "Lá Chắn/Poise", EVOCATION_SHIELD_POISE_BONUS)
                .scaledStat("evo_entropy_gen", "Tích Hắc Khí", EVOCATION_ENTROPY_GEN)
                .scaledStat("ender_duration", "Thời Gian Hư Không (s)", VOID_STRIKE_DURATION)
                .scaledStat("ender_flank_mult", "ST Đánh Lén Hư Không", ENDER_FLANK_DOWNED_MULT)
                .scaledStat("blood_flush", "Xả Hắc Khí", BLOOD_ENTROPY_FLUSH)
                .scaledStat("blood_lifesteal_mult", "Hút Máu Phép Máu", BLOOD_DOWNED_LIFESTEAL_MULT)
                .scaledStat("blood_hp_cost", "Tiêu Máu (%)", BLOOD_HP_COST_PCT)
                .scaledStat("eldritch_threshold", "Ngưỡng Cổ Thuật (%)", ELDRITCH_REQUIRED_THRESHOLD)
                .scaledStat("eldritch_nuke_mult", "ST Bộc Phát Cổ Thuật", ELDRITCH_MAX_NUKE_MULT)
                .scaledStat("eldritch_backfire_hp", "ST Tự Harm (%)", ELDRITCH_BACKFIRE_SELF_DMG)
                .scaledStat("eldritch_silence_sec", "Câm Lặng Tẩu Hỏa (s)", ELDRITCH_BACKFIRE_SILENCE_SEC)

                // Passive Stacks
                .passiveStack("entropy", PassiveStackDef.create("entropy")
                        .maxStacks(100).displayName("Arcane Entropy").build())

                .passiveSkill("Hắc Thuật & Entropy",
                        "Thi triển phép thuật tích tụ Hắc Khí từ 0 đến 100 điểm và dùng phép Máu tiêu hao 15% đến 25% Máu hiện tại để giải tỏa 20 đến 40 Hắc Khí. Khi Hắc Khí vượt ngưỡng 85% xuống 65%, phép Cổ Thuật sẽ gây 1.5 đến 2.5 lần sát thương bộc phát nhưng nếu thi triển sớm sẽ bị Tẩu Hỏa Nhập Ma nhận sát thương tự hại và câm lặng.")
                .activeSkill("Hư Không Hoán Chuyển",
                        "Tiêu hao toàn bộ Hắc Khí tích tụ để lập tức dịch chuyển lùi về phía sau và tạo lá chắn bảo vệ bằng 1.5 điểm cho mỗi điểm Hắc Khí bị tiêu hao.",
                        ResourceLocation.fromNamespaceAndPath("complextalents",
                                "textures/skill/darkmage/aspectofthewolf.png"))
                .activeSkillId(VoidReversalSkill.ID)
                .levelArmorCalc(lvl -> lvl <= 1 ? 0.0 : Math.round(Math.pow(lvl - 1, 1.15) * 0.65))
                .levelToughnessCalc(lvl -> lvl <= 1 ? 0.0 : Math.min(25.0, Math.pow(lvl - 1, 1.1) * 0.25))
                .levelHealthCalc(lvl -> 0.0)
                .renderer(new DarkMageRenderer())
                .gunConfusionMessages(
                        "origin.complextalents.dark_mage.gun_msg.1",
                        "origin.complextalents.dark_mage.gun_msg.2",
                        "origin.complextalents.dark_mage.gun_msg.3",
                        "origin.complextalents.dark_mage.gun_msg.4",
                        "origin.complextalents.dark_mage.gun_msg.5"
                )
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.FLAT_AD, 3)
                .cost(StatType.PERCENT_AD, 3)
                .cost(StatType.AP, 1)
                .cost(StatType.ARMOR_PEN, 2)
                .cost(StatType.LUCK_CRIT, 2)
                .cost(StatType.MAX_HP, 3)
                .cost(StatType.MAX_MANA, 2)
                .cost(StatType.HEAL_AND_SHIELD, 4)
                .cost(StatType.CDR, 1)
                .spellMasteryCostMultiplier(1.0)
                // Arcane Elements (1x cost)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.EVOCATION, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ENDER, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.BLOOD, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ELDRITCH, 2.0)
                // Primal Elements (3x cost)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.FIRE, 3.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ICE, 3.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.LIGHTNING, 3.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.NATURE, 3.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.AQUA, 3.0)
                // Cannot learn Holy spells (-1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.HOLY, -1.0)
                .weaponMasteryCostMultiplier(2.0);

        // Register Void Reversal Skill
        VoidReversalSkill.register();

        TalentsMod.LOGGER.info("Dark Mage (Arcane Entropy) origin registered");
    }

    public static boolean isDarkMage(ServerPlayer player) {
        return ID.equals(OriginManager.getOriginId(player));
    }
}
