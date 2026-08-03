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
                .displayName("Dark Mage")
                .description(Component.literal(
                        "Pháp sư thao túng Hắc Thuật không tiêu tốn Mana mà tích tụ Entropy. Thi triển phép thuật thường làm tăng Entropy, phép Máu dùng Máu để giải tỏa Entropy. Khi Entropy vượt vạch đỏ, dùng phép Eldritch sẽ bị Chiếm Hữu (tăng mạnh Chí Mạng Phép nhưng chỉ dùng được phép Eldritch). Dùng phép Eldritch quá sớm sẽ Tẩu Hoả Nhập Ma, gây sát thương lên bản thân và bị Câm Lặng."))
                .maxLevel(5)
                .baseStat(StatType.AP, 5)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                // Scaling definitions
                .scaledStat("evo_shield_poise", "Evocation Shield/Poise Bonus", EVOCATION_SHIELD_POISE_BONUS)
                .scaledStat("evo_entropy_gen", "Evocation Entropy Gen", EVOCATION_ENTROPY_GEN)
                .scaledStat("ender_duration", "Void Strike Duration", VOID_STRIKE_DURATION)
                .scaledStat("ender_flank_mult", "Void Strike Flank/Downed Mult", ENDER_FLANK_DOWNED_MULT)
                .scaledStat("blood_flush", "Blood Entropy Flush", BLOOD_ENTROPY_FLUSH)
                .scaledStat("blood_lifesteal_mult", "Blood Downed Lifesteal Mult", BLOOD_DOWNED_LIFESTEAL_MULT)
                .scaledStat("blood_hp_cost", "Blood HP Cost (% Current)", BLOOD_HP_COST_PCT)
                .scaledStat("eldritch_threshold", "Eldritch Threshold (%)", ELDRITCH_REQUIRED_THRESHOLD)
                .scaledStat("eldritch_nuke_mult", "Eldritch Nuke Mult", ELDRITCH_MAX_NUKE_MULT)
                .scaledStat("eldritch_backfire_hp", "Eldritch Backfire HP (% Max)", ELDRITCH_BACKFIRE_SELF_DMG)
                .scaledStat("eldritch_silence_sec", "Eldritch Silence (s)", ELDRITCH_BACKFIRE_SILENCE_SEC)

                // Passive Stacks
                .passiveStack("entropy", PassiveStackDef.create("entropy")
                        .maxStacks(100).displayName("Arcane Entropy").build())

                .passiveSkill("Hắc Thuật & Entropy",
                        "Phép thuật thường làm tăng Entropy, phép Máu dùng Máu để giảm Entropy. Khi Entropy vượt mốc vạch đỏ, dùng phép Eldritch sẽ bị Linh Hồn Nhập (tăng mạnh Phép Chí Mạng). Nếu dùng phép Eldritch khi Entropy chưa đủ vạch đỏ sẽ bị Hắc Thuật Quật Khấu.")
                .activeSkill("Hư Không Hoán Chuyển",
                        "Xóa sạch toàn bộ thanh Entropy tích tụ để tạo Lá Chắn Hư Không bảo vệ bản thân và lập tức dịch chuyển lùi về phía sau.",
                        ResourceLocation.fromNamespaceAndPath("complextalents",
                                "textures/skill/darkmage/aspectofthewolf.png"))
                .activeSkillId(VoidReversalSkill.ID)
                .levelArmorCalc(lvl -> lvl <= 1 ? 0.0 : Math.round(Math.pow(lvl - 1, 1.15) * 0.8))
                .levelToughnessCalc(lvl -> lvl <= 1 ? 0.0 : Math.min(25.0, Math.pow(lvl - 1, 1.1) * 0.25))
                .levelHealthCalc(lvl -> 0.0)
                .renderer(new DarkMageRenderer())
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
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ELDRITCH, 1.0)
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
