package com.complextalents.impl.assassin.origin;

import com.complextalents.TalentsMod;
import com.complextalents.impl.assassin.client.AssassinRenderer;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import com.complextalents.origin.OriginBuilder;
import com.complextalents.origin.OriginManager;
import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.util.UUIDHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/**
 * Assassin Origin - Stealth-based burst damage dealer.
 * <p>
 * Focuses on backstabbing enemies to apply team-wide damage amplification
 * and gain personal buffs to escape combat.
 * </p>
 */
public class AssassinOrigin {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "assassin");
    public static final UUID STEALTH_SPEED_UUID = UUIDHelper.generateAttributeModifierUUID("origin",
            "assassin_stealth_speed");

    public static void register() {
        OriginBuilder.create("complextalents", "assassin")
                .displayName(Component.translatable("origin.complextalents.assassin"))
                .description(Component.translatable("origin.complextalents.assassin.desc"))
                .maxLevel(5)
                .baseStat(StatType.FLAT_AD, 4)
                .baseStat(StatType.LUCK_CRIT, 10)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                .renderer(new AssassinRenderer())
                // Passive: Expose Weakness
                .scaledStat("exposeDamageAmp", "Tăng ST Đánh Lén (%)", new double[] { 0.30, 0.40, 0.50, 0.60, 0.80 })
                .scaledStat("exposeDuration", "Thời Gian Vạch Trần (s)", new double[] { 8.0, 10.0, 12.0, 14.0, 16.0 })
                .scaledStat("exposeCooldown", "Hồi Chiêu Vạch Trần (s)", new double[] { 45.0, 40.0, 35.0, 30.0, 25.0 })

                // Passive: The Disengage
                .scaledStat("disengageMoveSpeed", "Tốc Độ Rút Lực (%)", new double[] { 0.30, 0.45, 0.60, 0.75, 1.00 })
                .scaledStat("disengageDuration", "Thời Gian Rút Lực (s)", new double[] { 1.5, 1.5, 2.0, 2.0, 2.5 })
                .passiveSkill("Vạch Trần Điểm Yếu", "Đánh cận chiến từ phía sau lưng giúp toàn đội gây thêm 30% đến 80% sát thương lên mục tiêu trong 8 đến 16 giây.")
                .passiveSkill("Né Tránh Rút Lực", "Tăng 30% đến 100% tốc độ di chuyển trong 1.5 đến 2.5 giây sau khi tấn công từ trạng thái tàng hình.")
                .activeSkill("Dạ Hành",
                        "Tiến vào trạng thái tàng hình cho tới khi tấn công hoặc bị phát hiện, tăng tốc độ di chuyển và khiến đòn đánh tiếp theo từ phía sau lưng gây thêm sát thương.",
                        null)
                .activeSkillId(ResourceLocation.fromNamespaceAndPath("complextalents", "shadow_walk"))
                // Level defensive scaling (Assassin = Agile Flanker controlled curve)
                .levelArmorCalc(lvl -> lvl <= 1 ? 0.0 : Math.round(Math.pow(lvl - 1, 1.15) * 0.6))
                .levelToughnessCalc(lvl -> lvl <= 1 ? 0.0 : Math.min(20.0, Math.pow(lvl - 1, 1.1) * 0.15))
                .levelHealthCalc(lvl -> 0.0)
                .gunConfusionMessages(
                        "origin.complextalents.assassin.gun_msg.1",
                        "origin.complextalents.assassin.gun_msg.2",
                        "origin.complextalents.assassin.gun_msg.3",
                        "origin.complextalents.assassin.gun_msg.4",
                        "origin.complextalents.assassin.gun_msg.5"
                )
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.FLAT_AD, 1)
                .cost(StatType.PERCENT_AD, 2)
                .cost(StatType.AP, 4)
                .cost(StatType.ARMOR_PEN, 1)
                .cost(StatType.LUCK_CRIT, 1)
                .cost(StatType.MAX_HP, 5)
                .cost(StatType.MAX_MANA, 4)
                .cost(StatType.HEAL_AND_SHIELD, 4)
                .cost(StatType.CDR, 3)
                .spellMasteryCostMultiplier(3.0) // Assassin terrible with spells, 300% cost
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ELDRITCH, -1.0)
                .weaponMasteryCostMultiplier(1.0); // Assassin normal with melee weapons, 100% cost

        TalentsMod.LOGGER.info("Assassin origin registered");
    }

    public static double getExposeAmp(int level) {
        Origin origin = OriginRegistry.getInstance().getOrigin(ID);
        if (origin == null)
            return 0.15;
        return origin.getScaledStat("exposeDamageAmp", level);
    }

    public static boolean isAssassin(ServerPlayer player) {
        return ID.equals(OriginManager.getOriginId(player));
    }
}
