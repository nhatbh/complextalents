package com.complextalents.impl.highpriest.integration;

import com.complextalents.TalentsMod;
import com.complextalents.impl.highpriest.events.HolySpellHealEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Integration with Iron's Spellbooks for holy spell detection.
 * <p>
 * Listens for SpellHealEvent from Iron's Spellbooks and fires
 * HolySpellHealEvent when holy school type heals are detected.
 * </p>
 * <p>
 * This complements the existing HolySpellbooksIntegration which handles
 * SpellDamageEvent for holy spell damage.
 * </p>
 * <p>
 * The actual healing bonus and overheal mechanics are handled in
 * {@link com.complextalents.impl.highpriest.origin.HighPriestOrigin}.
 * </p>
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class HighPriestIntegration {
    private static boolean initialized = false;

    /**
     * Initializes the Iron's Spellbooks integration for holy heal detection.
     */
    public static void init() {
        if (initialized)
            return;
        initialized = true;

        try {
            MinecraftForge.EVENT_BUS.register(HighPriestIntegration.class);
            TalentsMod.LOGGER.info("High Priest: Iron's Spellbooks holy heal integration initialized");
        } catch (Exception e) {
            TalentsMod.LOGGER.warn("Failed to initialize High Priest Iron's Spellbooks integration: {}",
                    e.getMessage());
        }
    }

    /**
     * Handles SpellHealEvent from Iron's Spellbooks.
     * Detects holy heals and fires HolySpellHealEvent.
     * The HighPriestOrigin class handles the actual healing bonuses.
     */
    @SubscribeEvent
    public static void onSpellHeal(io.redspace.ironsspellbooks.api.events.SpellHealEvent event) {
        // Check if Iron's Spellbooks is loaded
        if (!com.complextalents.origin.integration.OriginModIntegrationHandler.isIronSpellbooksLoaded()) {
            return;
        }

        try {
            io.redspace.ironsspellbooks.api.spells.SchoolType schoolType = event.getSchoolType();
            if (schoolType == null)
                return;

            // Check if this is a holy spell
            String schoolPath = schoolType.getId().getPath();
            if (!"holy".equals(schoolPath)) {
                return;
            }

            LivingEntity caster = event.getEntity();
            LivingEntity target = event.getTargetEntity();

            if (caster == null || target == null)
                return;

            // Server-side only
            if (caster.level().isClientSide)
                return;

            float originalHealAmount = event.getHealAmount();

            // Fire holy spell heal event for healing bonuses
            // HighPriestOrigin will handle the actual bonus healing and overheal conversion
            HolySpellHealEvent holyEvent = new HolySpellHealEvent(target, caster, originalHealAmount, schoolType);
            MinecraftForge.EVENT_BUS.post(holyEvent);

        } catch (Exception e) {
            TalentsMod.LOGGER.debug("Error processing holy SpellHealEvent: {}", e.getMessage());
        }
    }

    private static final java.util.Map<java.util.UUID, Float> MANA_ACCUMULATOR = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Handles ChangeManaEvent from Iron's Spellbooks.
     * Spending mana on spells generates Command stacks at a 5:1 ratio (5 Mana = 1 Command, cap 100).
     */
    @SubscribeEvent
    public static void onManaChange(io.redspace.ironsspellbooks.api.events.ChangeManaEvent event) {
        if (!com.complextalents.origin.integration.OriginModIntegrationHandler.isIronSpellbooksLoaded()) {
            return;
        }

        try {
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }

            if (!com.complextalents.impl.highpriest.origin.HighPriestOrigin.isHighPriest(player)) {
                return;
            }

            // Check if mana decreased
            float oldMana = event.getOldMana();
            float newMana = event.getNewMana();
            if (newMana >= oldMana) {
                return;
            }

            float manaCost = oldMana - newMana;

            // 5 Mana = 1 Command stack (capped at 100 Command)
            float totalManaSpent = MANA_ACCUMULATOR.getOrDefault(player.getUUID(), 0.0f) + manaCost;
            int commandGained = (int) (totalManaSpent / 5.0f);
            float remainder = totalManaSpent % 5.0f;
            MANA_ACCUMULATOR.put(player.getUUID(), remainder);

            if (commandGained > 0) {
                int currentCommand = com.complextalents.passive.PassiveManager.getPassiveStacks(player, "command");
                if (currentCommand < 100) {
                    int newCommand = Math.min(100, currentCommand + commandGained);
                    com.complextalents.passive.PassiveManager.setPassiveStacks(player, "command", newCommand);
                }
            }
        } catch (Exception e) {
            TalentsMod.LOGGER.debug("Error processing ChangeManaEvent for High Priest Command generation: {}", e.getMessage());
        }
    }
}
