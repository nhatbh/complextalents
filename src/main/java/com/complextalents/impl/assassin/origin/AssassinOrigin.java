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
                .displayName("Assassin")
                .description(net.minecraft.network.chat.Component.literal(
                        "Sát thủ tàng hình dồn sát thương. Đánh lén từ phía sau lưng tăng 30%-80% sát thương mục tiêu nhận vào từ cả đội trong 8-16s. Tấn công từ trạng thái Stealth giúp tăng 30%-100% Move Speed để rút lui."))
                .maxLevel(5)
                .baseStat(StatType.FLAT_AD, 4)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                .renderer(new AssassinRenderer())
                // Passive: Expose Weakness
                .scaledStat("exposeDamageAmp", new double[] { 0.30, 0.40, 0.50, 0.60, 0.80 })
                .scaledStat("exposeDuration", new double[] { 8.0, 10.0, 12.0, 14.0, 16.0 })
                .scaledStat("exposeCooldown", new double[] { 45.0, 40.0, 35.0, 30.0, 25.0 })

                // Passive: The Disengage
                .scaledStat("disengageMoveSpeed", new double[] { 0.30, 0.45, 0.60, 0.75, 1.00 })
                .scaledStat("disengageDuration", new double[] { 1.5, 1.5, 2.0, 2.0, 2.5 })
                .passiveSkill("Expose Weakness", "Đánh cận chiến từ phía sau lưng giúp cả đội gây thêm 30%-80% sát thương lên mục tiêu.")
                .passiveSkill("The Disengage", "Tăng mạnh Move Speed để rút lui sau khi tấn công từ trạng thái tàng hình.")
                .activeSkill("Shadow Walk",
                        "Vào trạng thái tàng hình, tăng Move Speed và khiến đòn đánh tiếp theo gây thêm sát thương từ phía sau lưng.",
                        null)
                .activeSkillId(ResourceLocation.fromNamespaceAndPath("complextalents", "shadow_walk"))
                // Level defensive scaling (Assassin = Agile Flanker controlled curve)
                .levelArmorCalc(lvl -> lvl <= 1 ? 0.0 : Math.round(Math.pow(lvl - 1, 1.15) * 0.6))
                .levelToughnessCalc(lvl -> lvl <= 1 ? 0.0 : Math.min(20.0, Math.pow(lvl - 1, 1.1) * 0.15))
                .levelHealthCalc(lvl -> 0.0)
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
