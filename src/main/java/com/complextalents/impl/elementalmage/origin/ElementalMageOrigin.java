package com.complextalents.impl.elementalmage.origin;

import com.complextalents.TalentsMod;
import com.complextalents.elemental.events.ElementalDamageEvent;
import com.complextalents.impl.elementalmage.ElementalMageData;
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
    
    // Level scaling arrays (index corresponds to level 1, 2, 3, 4, 5)
    private static final double[] BASE_RES = {40.0, 50.0, 60.0, 70.0, 100.0};
    private static final double[] MULT_RES = {60.0, 70.0, 80.0, 95.0, 120.0};
    private static final double[] BASE_REGEN = {1.0, 1.2, 1.4, 1.6, 2.5}; // Per second
    private static final double[] MULT_REGEN = {1.0, 1.1, 1.2, 1.4, 2.0}; // Per second per mastery point

    /**
     * Register the Elemental Mage origin.
     * Call this during mod initialization.
     */
    public static void register() {
        // Register the Elemental Resonance resource
        com.complextalents.origin.ResourceType resonanceType = com.complextalents.origin.ResourceType.register(
                ResourceLocation.fromNamespaceAndPath("complextalents", "elemental_resonance"),
                "Elemental Resonance",
                0.0,
                100.0, // Default max, overridden dynamically
                0xFF4D96FF // Bright blue color for UI
        );

        OriginBuilder.create("complextalents", "elemental_mage")
                .displayName("Elemental Mage")
                .description(Component.literal("Evocation specialist building power through elemental damage. Generate echoes for mana restore: 10-40 base + (Mastery × 5-25) per echo/level. Resonance regen: 1.0-2.5 + (Mastery × 1.0-2.0)/sec. Combine elements (Fire+Ice=Melt, Water+Nature=Growth) for 25 Resonance/reaction. Massive spell hits (10+/30+/50+) trigger \"OP\" reactions."))
                .resourceType(resonanceType)
                .maxLevel(5) // Max level is now 5
                .scaledStat("base_resonance", "Base Resonance", BASE_RES)
                .scaledStat("mastery_res_mult", "Mastery Res Mult", MULT_RES)
                .scaledStat("base_regen", "Base Regen", BASE_REGEN)
                .scaledStat("mastery_regen_mult", "Mastery Regen Mult", MULT_REGEN)
                .dynamicMaxResource((level, player) -> {
                    int idx = Math.min(Math.max(level - 1, 0), 4);
                    // Get latest elemental mastery value
                    double mastery = com.complextalents.elemental.registry.ReactionRegistry.getInstance().calculateElementalMastery(player);
                    return BASE_RES[idx] + (MULT_RES[idx] * mastery);
                })
                .passiveSkill("Elemental Resonance", "Deal elemental damage to generate echoes and mastery-scaled regeneration.")
                .activeSkill("Harmonic Convergence", "Unleash stored resonance as a devastating blast.", null)
                .activeSkillId(ResourceLocation.fromNamespaceAndPath("complextalents", "harmonic_convergence"))
                .renderer(new com.complextalents.impl.elementalmage.client.ElementalMageRenderer())
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.FLAT_AD, 4)
                .cost(StatType.PERCENT_AD, 4)
                .cost(StatType.AP, 1)
                .cost(StatType.ARMOR_PEN, 4)
                .cost(StatType.LUCK_CRIT, 2)
                .cost(StatType.MAX_HP, 3)
                .cost(StatType.MAX_MANA, 1)
                .cost(StatType.MOBILITY, 2)
                .cost(StatType.CDR, 1)
                .spellMasteryCostMultiplier(1.0) // Elemental Mage normal with spells, 100% cost
                .weaponMasteryCostMultiplier(3.0); // Elemental Mage terrible with weapons, 300% cost

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
     * Regenerate Elemental Resonance over time.
     * Called every tick.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        // Only run on server, and only once per tick (Phase START or END, pick one)
        if (event.side.isClient() || event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        
        if (!(event.player instanceof ServerPlayer serverPlayer)) return;

        // Apply regeneration once per second (every 20 ticks)
        if (serverPlayer.level().getGameTime() % 20L == 0L) {
            if (isElementalMage(serverPlayer)) {
                // Get player origin data capability
                serverPlayer.getCapability(com.complextalents.origin.capability.OriginDataProvider.ORIGIN_DATA).ifPresent(data -> {
                    int level = data.getOriginLevel();
                    int idx = Math.min(Math.max(level - 1, 0), 4);
                    
                    double mastery = com.complextalents.elemental.registry.ReactionRegistry.getInstance().calculateElementalMastery(serverPlayer);
                    double regenAmount = BASE_REGEN[idx] + (MULT_REGEN[idx] * mastery);
                    
                    data.modifyResource(regenAmount);
                    
                    // Force a sync to ensure dynamic max resource (based on mastery) is instantly updated 
                    // on the client, even if current resource is already full and didn't change.
                    data.sync();
                });
            }
        }
    }

    /**
     * Listen for ElementalDamageEvents to feed the math framework.
     */
    @SubscribeEvent
    public static void onElementalDamage(ElementalDamageEvent event) {
        // Ensure the event has a valid caster and target
        if (!(event.getSource() instanceof ServerPlayer player))
            return;

        // Ensure the player is an Elemental Mage
        if (!isElementalMage(player))
            return;

        // Pass the damage event details into the stats system
        ElementalMageData.processElementalDamage(player, event.getElement(), event.getDamage());
    }
}
