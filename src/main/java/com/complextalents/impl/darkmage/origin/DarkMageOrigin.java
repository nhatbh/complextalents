package com.complextalents.impl.darkmage.origin;

import com.complextalents.TalentsMod;
import com.complextalents.impl.darkmage.client.DarkMageRenderer;
import com.complextalents.origin.OriginBuilder;
import com.complextalents.origin.OriginManager;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dark Mage Origin - Infinite Scaling Soul Harvester.
 * <p>
 * A high-risk, high-reward playstyle that rewards sustained combat.
 * The longer you fight and kill, the stronger you become.
 * Death is punishing but not permanent - Phylactery saves you at the cost of
 * souls.
 * </p>
 * <p>
 * Offers exponential HP-for-Power scaling. Use Blood Pact to tap into your soul
 * reserves,
 * granting increased cast speed and soul-scaled mana regeneration.
 * </p>
 *
 * <h3>Passive: Soul Siphon</h3>
 * <ul>
 * <li>Gain souls from killed enemies (amount = enemy max health / 40)</li>
 * <li>Souls are UNCAPPED - can grow indefinitely</li>
 * <li>Souls provide damage bonus ONLY during Blood Pact</li>
 * </ul>
 *
 * <h3>Passive: Phylactery (Death-Defy)</h3>
 * <ul>
 * <li>Auto-triggers on fatal damage if souls > 0</li>
 * <li>Sets HP to 1, loses 50% of souls</li>
 * <li>5-minute internal cooldown</li>
 * </ul>
 *
 * <h3>Active: Blood Pact (Toggle)</h3>
 * <ul>
 * <li>HP drain per second: 8%/7%/6%/5%/4% (by level)</li>
 * <li>Cast Speed bonus: +10%/20%/30%/40%/50% (by level)</li>
 * <li>Soul-scaled Mana Regeneration while active: 1.0 + (souls / 200.0)</li>
 * <li>Soul damage bonus: +0.05%/0.1%/0.15%/0.2%/0.25% per soul (by level)</li>
 * <li>30 second cooldown after toggling off</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class DarkMageOrigin {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "dark_mage");

    /**
     * Register the Dark Mage origin.
     * Call this during mod initialization.
     */
    public static void register() {
        OriginBuilder.create("complextalents", "dark_mage")
                .displayName("Dark Mage")
                .description(Component.literal(
                        "Pháp sư Huyết Thuật. Hạ gục kẻ địch rơi ra Soul Orbs dựa trên Max HP mục tiêu. Bật Blood Pact tự đốt Current HP để tăng Shadow Spell Power và chuyển sát thương phép nhận vào thành Bleed 3s. Tắt Blood Pact sẽ kích nổ các Soul Orbs tạo sóng sát thương diện rộng."))
                .maxLevel(5)
                .baseStat(StatType.AP, 4)
                .baseStat(StatType.MAX_MANA, 2)
                // HP drain rates for Blood Pact: 8%/7%/6%/5%/4% Current HP per second
                .scaledStat("bloodPactHpDrainPercent", "HP Drain/sec", new double[] { 0.08, 0.07, 0.06, 0.05, 0.04 })
                // Shadow Spell Power bonus: +10%/20%/30%/40%/50%
                .scaledStat("bloodPactSpellPowerBonus", "Spell Power Bonus",
                        new double[] { 0.10, 0.20, 0.30, 0.40, 0.50 })
                // Harvest Heal Base Percent: 2%/3%/4%/5%/6%
                .scaledStat("harvestHealPercent", "Harvest Heal",
                        new double[] { 0.02, 0.03, 0.04, 0.05, 0.06 })
                // Harvest Frenzy Cast Speed: +10%/15%/20%/25%/30%
                .scaledStat("harvestFrenzyCastSpeed", "Frenzy Cast Speed",
                        new double[] { 0.10, 0.15, 0.20, 0.25, 0.30 })
                // Detonation Base Damage per Density V: 10/15/20/25/35
                .scaledStat("detonationBaseDamage", "Detonation Base Dmg",
                        new double[] { 10.0, 15.0, 20.0, 25.0, 35.0 })
                // Rebound Heal Percent: 30%/35%/40%/45%/50%
                .scaledStat("reboundHealPercent", "Rebound Heal",
                        new double[] { 0.30, 0.35, 0.40, 0.45, 0.50 })
                .passiveStack("blood_pact_active", com.complextalents.passive.PassiveStackDef.create("blood_pact_active")
                        .maxStacks(1).displayName("Blood Pact Active").build())
                .passiveStack("blood_pact_ticks", com.complextalents.passive.PassiveStackDef.create("blood_pact_ticks")
                        .maxStacks(72000).displayName("Blood Pact Ticks").build())
                .passiveStack("blood_pact_dmg", com.complextalents.passive.PassiveStackDef.create("blood_pact_dmg")
                        .maxStacks(500).displayName("Blood Pact Dmg Bonus").build())
                .passiveSkill("Soul Siphon",
                        "Hạ gục kẻ địch rơi ra Soul Orbs tích trữ sức mạnh dựa trên Max HP kẻ địch.")
                .passiveSkill("Blood Magic",
                        "Chuyển sát thương phép nhận vào thành Bleed giảm dần trong 3s khi đang bật Blood Pact.")
                .activeSkill("Blood Pact",
                        "Bật/Tắt: Đốt Current HP để tăng liên tục Shadow Spell Power. Tắt kỹ năng để kích nổ Soul Orbs tạo sóng sát thương diện rộng.",
                        ResourceLocation.fromNamespaceAndPath("complextalents",
                                "textures/skill/darkmage/bloodpact.png"))
                .activeSkillId(ResourceLocation.fromNamespaceAndPath("complextalents", "blood_pact"))
                .renderer(new DarkMageRenderer())
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.FLAT_AD, 3)
                .cost(StatType.PERCENT_AD, 3)
                .cost(StatType.AP, 1)
                .cost(StatType.ARMOR_PEN, 2)
                .cost(StatType.LUCK_CRIT, 2)
                .cost(StatType.MAX_HP, 2)
                .cost(StatType.MAX_MANA, 2)
                .cost(StatType.MOBILITY, 3)
                .cost(StatType.CDR, 2)
                .spellMasteryCostMultiplier(1.0) // Dark Mage normal with spells, 100% cost
                .weaponMasteryCostMultiplier(2.0); // Dark Mage weak with weapons, 200% cost

        TalentsMod.LOGGER.info("Dark Mage origin registered");
    }

    /**
     * Check if a player is a Dark Mage.
     */
    public static boolean isDarkMage(ServerPlayer player) {
        return ID.equals(OriginManager.getOriginId(player));
    }
}
