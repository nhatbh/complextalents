package com.complextalents.origin.integration;

import com.complextalents.TalentsMod;
import com.complextalents.origin.events.HolySpellDamageEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Integration with Iron's Spellbooks for holy spell damage detection.
 * <p>
 * Listens for SpellDamageEvent from Iron's Spellbooks and fires
 * HolySpellDamageEvent when holy school type spells are detected.
 * </p>
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class HolySpellbooksIntegration {
    private static boolean initialized = false;

    /**
     * Initializes the Iron's Spellbooks integration for holy spell detection.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            MinecraftForge.EVENT_BUS.register(HolySpellbooksIntegration.class);
            TalentsMod.LOGGER.info("Origin: Iron's Spellbooks holy spell integration initialized");
        } catch (Exception e) {
            TalentsMod.LOGGER.warn("Failed to initialize origin Iron's Spellbooks integration: {}", e.getMessage());
        }
    }

    /**
     * Handles SpellDamageEvent from Iron's Spellbooks.
     * Detects holy spells and fires HolySpellDamageEvent.
     */
    @SubscribeEvent
    public static void onSpellDamage(io.redspace.ironsspellbooks.api.events.SpellDamageEvent event) {
        if (!OriginModIntegrationHandler.isIronSpellbooksLoaded()) return;

        try {
            io.redspace.ironsspellbooks.damage.SpellDamageSource spellDamageSource = event.getSpellDamageSource();
            io.redspace.ironsspellbooks.api.spells.AbstractSpell spell = spellDamageSource.spell();
            if (spell == null) return;

            io.redspace.ironsspellbooks.api.spells.SchoolType schoolType = spell.getSchoolType();
            if (schoolType == null) return;

            String schoolPath = schoolType.getId().getPath();

            LivingEntity target = event.getEntity();
            if (target == null) return;

            // Server-side only
            if (target.level().isClientSide) return;

            LivingEntity caster = null;
            if (spellDamageSource.getEntity() instanceof LivingEntity) {
                caster = (LivingEntity) spellDamageSource.getEntity();
            } else if (spellDamageSource.getDirectEntity() instanceof LivingEntity) {
                caster = (LivingEntity) spellDamageSource.getDirectEntity();
            }

            if (caster == null) return;

            // High Priest damage scaling: 2x Holy, 0.5x all other schools
            if (caster instanceof net.minecraft.server.level.ServerPlayer player && com.complextalents.impl.highpriest.origin.HighPriestOrigin.isHighPriest(player)) {
                if ("holy".equals(schoolPath)) {
                    event.setAmount(event.getAmount() * 2.0f);
                } else {
                    event.setAmount(event.getAmount() * 0.5f);
                }
            }

            // Check if this is a holy spell to fire the HolySpellDamageEvent
            if ("holy".equals(schoolPath)) {
                float damage = event.getAmount();
                HolySpellDamageEvent holyEvent = new HolySpellDamageEvent(target, caster, damage, spell);
                MinecraftForge.EVENT_BUS.post(holyEvent);
            }

        } catch (Exception e) {
            TalentsMod.LOGGER.debug("Error processing holy SpellDamageEvent: {}", e.getMessage());
        }
    }
}
