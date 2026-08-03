package com.complextalents.impl.elementalmage.origin;

import com.complextalents.TalentsMod;
import com.complextalents.origin.OriginBuilder;
import com.complextalents.origin.OriginManager;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Elemental Mage Origin - Masters of raw Evocation magic.
 * Scales attributes based on a mathematical framework utilizing the Balance
 * Metric and diminishing returns.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class ElementalMageOrigin {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents",
            "elemental_mage");

    public static final double[] REACTION_DAMAGE_BONUS = { 0.25, 0.50, 0.75, 1.00, 1.30 };

    /**
     * Register the Elemental Mage origin.
     * Call this during mod initialization.
     */
    public static void register() {
        OriginBuilder.create("complextalents", "elemental_mage")
                .displayName("Elemental Mage")
                .description(Component.literal(
                        "Chuyên gia combo phép thuật. Kích hoạt phản ứng để tích lũy tới 6 Prismatic Echoes, tăng tối đa 1.5x sát thương phép. Dùng Harmonic Convergence tiêu hao Echoes để hoàn Mana và dồn sát thương cực đại."))

                .maxLevel(5)
                .baseStat(StatType.AP, 6)
                .baseStat(StatType.MAX_MANA, 10)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                .scaledStat("reaction_damage_bonus", "Reaction Damage Bonus", REACTION_DAMAGE_BONUS)
                .passiveSkill("Prismatic Harmony",
                        "Kích hoạt phản ứng nguyên tố để tích lũy Prismatic Echoes (tăng tối đa 1.5x sát thương). Tăng mạnh sát thương của mọi Phản ứng Nguyên tố (+25% -> +130%).")

                .activeSkill("Harmonic Convergence",
                        "Tiêu hao Prismatic Echoes để hoàn Mana, gia tăng sát thương và khiến mọi phép phản ứng trực tiếp với nguyên tố vừa dùng.",
                        null)

                .activeSkillId(ResourceLocation.fromNamespaceAndPath("complextalents", "harmonic_convergence"))
                // Level defensive scaling (Elemental Mage = Evocation Caster controlled curve)
                .levelArmorCalc(lvl -> lvl <= 1 ? 0.0 : Math.round(Math.pow(lvl - 1, 1.15) * 0.7))
                .levelToughnessCalc(lvl -> lvl <= 1 ? 0.0 : Math.min(25.0, Math.pow(lvl - 1, 1.1) * 0.2))
                .levelHealthCalc(lvl -> 0.0)
                .renderer(new com.complextalents.impl.elementalmage.client.ElementalMageRenderer())
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.FLAT_AD, 4)
                .cost(StatType.PERCENT_AD, 4)
                .cost(StatType.AP, 1)
                .cost(StatType.ARMOR_PEN, 4)
                .cost(StatType.LUCK_CRIT, 2)
                .cost(StatType.MAX_HP, 4)
                .cost(StatType.MAX_MANA, 1)
                .cost(StatType.HEAL_AND_SHIELD, 4)
                .cost(StatType.CDR, 1)
                .spellMasteryCostMultiplier(1.0)
                // Primal Elements (Base multiplier: 1x)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.FIRE, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ICE, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.LIGHTNING, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.NATURE, 1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.AQUA, 1.0)
                // Arcane Elements (Bad affinity: 3x cost)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.EVOCATION, 3.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ENDER, 3.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.BLOOD, 3.0)
                // Holy (No affinity: 5x cost)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.HOLY, 5.0)
                // Eldritch (Dark Mage Exclusive: -1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ELDRITCH, -1.0)
                .weaponMasteryCostMultiplier(3.0);

        // Register Harmonic Convergence Skill
        com.complextalents.impl.elementalmage.skill.HarmonicConvergenceSkill.register();

        TalentsMod.LOGGER.info("Elemental Mage origin registered");
    }

    /**
     * Get the Elemental Mage origin ID.
     */
    public static ResourceLocation getId() {
        return ID;
    }

    /**
     * Check if a player is an Elemental Mage.
     */
    public static boolean isElementalMage(net.minecraft.server.level.ServerPlayer player) {
        return ID.equals(OriginManager.getOriginId(player));
    }

    /**
     * Server tick handler:
     * Decay Prismatic Echoes after 12 seconds (240 ticks) without elemental damage.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() || event.phase != net.minecraftforge.event.TickEvent.Phase.END)
            return;

        if (!(event.player instanceof ServerPlayer serverPlayer))
            return;

        if (!isElementalMage(serverPlayer))
            return;

        long gameTime = serverPlayer.level().getGameTime();

        // Echo Decay Check (every tick)
        serverPlayer.getCapability(com.complextalents.impl.elementalmage.ElementalMageDataProvider.ELEMENTAL_DATA)
                .ifPresent(cap -> {
                    if (cap.getEchoCount() > 0) {
                        if (gameTime - cap.getLastDamageTick() >= 240L) { // 12 seconds = 240 ticks
                            cap.clearEchoes();
                        }
                    }
                });
    }
}
