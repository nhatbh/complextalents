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

    // Level scaling arrays (index corresponds to level 1, 2, 3, 4, 5)
    private static final double[] BASE_RES = { 40.0, 50.0, 60.0, 70.0, 100.0 };
    private static final double[] MANA_RES_MULT = { 0.20, 0.25, 0.30, 0.35, 0.50 };
    private static final double[] BASE_REGEN = { 1.0, 1.2, 1.4, 1.6, 2.5 }; // Per second
    private static final double[] MANA_REGEN_MULT = { 0.005, 0.008, 0.010, 0.012, 0.020 }; // Per second per Max Mana

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
                .description(Component.literal(
                        "Chuyên gia combo phép thuật. Kích hoạt phản ứng tiêu 25 Resonance để tích lũy tới 6 Prismatic Echoes, tăng tối đa 1.5x sát thương phép. Dùng Harmonic Convergence tiêu hao Echoes để hoàn Mana & Resonance và dồn sát thương cực đại."))

                .resourceType(resonanceType)
                .maxLevel(5)
                .baseStat(StatType.AP, 2)
                .baseStat(StatType.MAX_MANA, 4)
                .scaledStat("base_resonance", "Base Resonance", BASE_RES)
                .scaledStat("mana_res_mult", "Mana Res Mult", MANA_RES_MULT)
                .scaledStat("base_regen", "Base Regen", BASE_REGEN)
                .scaledStat("mana_regen_mult", "Mana Regen Mult", MANA_REGEN_MULT)
                .dynamicMaxResource((level, player) -> {
                    int idx = Math.min(Math.max(level - 1, 0), 4);
                    double maxMana = player.getAttributeValue(
                            io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get()
                    );
                    return BASE_RES[idx] + (MANA_RES_MULT[idx] * maxMana);
                })
                .passiveSkill("Prismatic Harmony",
                        "Kích hoạt phản ứng nguyên tố để tích lũy Prismatic Echoes, tăng tối đa 1.5x sát thương.")

                .activeSkill("Harmonic Convergence", "Tiêu hao Prismatic Echoes để hoàn Mana & Resonance, gia tăng sát thương và khiến mọi phép phản ứng trực tiếp với nguyên tố vừa dùng.", null)

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
                .spellMasteryCostMultiplier(1.0)
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
     * 1. Regenerate Elemental Resonance over time (every 20 ticks).
     * 2. Decay Prismatic Echoes after 12 seconds (240 ticks) without elemental damage.
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

        // 1. Echo Decay Check (every tick)
        serverPlayer.getCapability(com.complextalents.impl.elementalmage.ElementalMageDataProvider.ELEMENTAL_DATA).ifPresent(cap -> {
            if (cap.getEchoCount() > 0) {
                if (gameTime - cap.getLastDamageTick() >= 240L) { // 12 seconds = 240 ticks
                    cap.clearEchoes();
                }
            }
        });

        // 2. Regeneration once per second (every 20 ticks)
        if (gameTime % 20L == 0L) {
            serverPlayer.getCapability(com.complextalents.origin.capability.OriginDataProvider.ORIGIN_DATA)
                    .ifPresent(data -> {
                        int level = data.getOriginLevel();
                        int idx = Math.min(Math.max(level - 1, 0), 4);

                        double maxMana = serverPlayer.getAttributeValue(
                                io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get()
                        );
                        double regenAmount = BASE_REGEN[idx] + (MANA_REGEN_MULT[idx] * maxMana);

                        data.modifyResource(regenAmount);
                        data.sync();
                    });
        }
    }


}
