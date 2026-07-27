package com.complextalents.origin.integration;

import com.complextalents.TalentsMod;
import com.complextalents.util.IronParticleHelper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handles spell critical hit logic for all players.
 * <p>
 * Hooks into Iron's Spellbooks' SpellDamageEvent at HIGH priority.
 * All players have a base spell crit chance of 0% and crit damage of 150%.
 * These can be modified via the attribute system.
 * </p>
 */
public class SpellCritHandler {

    private static boolean initialized = false;

    /**
     * Initialize the spell crit handler. Must only be called when Iron's Spellbooks
     * is loaded.
     */
    public static void init() {
        if (initialized)
            return;
        initialized = true;

        try {
            MinecraftForge.EVENT_BUS.register(SpellCritHandler.class);
            TalentsMod.LOGGER.info("Origin: Spell crit handler initialized");
        } catch (Exception e) {
            TalentsMod.LOGGER.warn("Failed to initialize spell crit handler: {}", e.getMessage());
        }
    }

    /**
     * Intercept SpellDamageEvent to apply spell crit multiplier.
     * Runs at HIGH priority so the critted amount feeds into subsequent handlers.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSpellDamage(io.redspace.ironsspellbooks.api.events.SpellDamageEvent event) {
        if (!OriginModIntegrationHandler.isIronSpellbooksLoaded())
            return;

        try {
            if (event.getEntity().level().isClientSide())
                return;

            // Caster must be a player
            io.redspace.ironsspellbooks.damage.SpellDamageSource source = event.getSpellDamageSource();
            if (!(source.getEntity() instanceof ServerPlayer caster))
                return;

            // Read spell crit chance
            AttributeInstance critChanceInst = caster.getAttribute(SpellCritAttributes.SPELL_CRIT_CHANCE.get());
            if (critChanceInst == null)
                return;

            double critChance = critChanceInst.getValue();

            // Read casting item stack for Precision & Fatal augments
            io.redspace.ironsspellbooks.api.spells.AbstractSpell spell = event.getSpellDamageSource().spell();
            net.minecraft.world.item.ItemStack castingStack = com.complextalents.refinement.SpellAugmentEventHandler.getCastingItemStack(caster, spell);
            if (!castingStack.isEmpty()) {
                java.util.List<net.minecraft.nbt.CompoundTag> augments = com.complextalents.refinement.SpellAugmentEventHandler.getAugmentsForSpell(castingStack, spell);
                for (net.minecraft.nbt.CompoundTag aug : augments) {
                    try {
                        com.complextalents.item.MagicAugmentItem.AugmentType type = com.complextalents.item.MagicAugmentItem.AugmentType.valueOf(aug.getString("Type"));
                        com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity rarity = com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity.values()[Math.min(4, Math.max(0, aug.getInt("Tier")))];
                        int tLvl = rarity.ordinal() + 1;
                        if (type == com.complextalents.item.MagicAugmentItem.AugmentType.PRECISION) {
                            critChance += switch (tLvl) { case 2 -> 0.06; case 3 -> 0.10; case 4 -> 0.15; default -> 0.20; };
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Inject Harmonic Convergence Buffs
            double convergenceDamBonus = 0.0;
            if (caster.hasEffect(com.complextalents.elemental.effects.ElementalEffects.HARMONIC_CONVERGENCE.get())) {
                var cap = caster.getCapability(com.complextalents.impl.elementalmage.ElementalMageDataProvider.ELEMENTAL_DATA).resolve();
                if (cap.isPresent()) {
                    critChance += cap.get().getConvergenceCritChance();
                    convergenceDamBonus = cap.get().getConvergenceCritDamage();
                }
            }

            if (critChance <= 0.0)
                return;

            // Roll for crit
            double roll = caster.getRandom().nextDouble();
            if (roll >= critChance)
                return;

            // Crit! Apply damage multiplier
            AttributeInstance critDamageInst = caster.getAttribute(SpellCritAttributes.SPELL_CRIT_DAMAGE.get());
            double critDamage = (critDamageInst != null) ? critDamageInst.getValue() : 1.5;
            critDamage += convergenceDamBonus;

            if (!castingStack.isEmpty()) {
                java.util.List<net.minecraft.nbt.CompoundTag> augments = com.complextalents.refinement.SpellAugmentRecipe.getAugments(castingStack);
                for (net.minecraft.nbt.CompoundTag aug : augments) {
                    try {
                        com.complextalents.item.MagicAugmentItem.AugmentType type = com.complextalents.item.MagicAugmentItem.AugmentType.valueOf(aug.getString("Type"));
                        com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity rarity = com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity.values()[Math.min(4, Math.max(0, aug.getInt("Tier")))];
                        int tLvl = rarity.ordinal() + 1;
                        if (type == com.complextalents.item.MagicAugmentItem.AugmentType.FATAL) {
                            critDamage += switch (tLvl) { case 2 -> 0.20; case 3 -> 0.32; case 4 -> 0.45; default -> 0.60; };
                        }
                    } catch (Exception ignored) {}
                }
            }

            float originalDamage = event.getAmount();
            float newDamage = (float) (originalDamage * critDamage);
            event.setAmount(newDamage);

            // Spawn school-themed particles on the target
            LivingEntity target = event.getEntity();
            spawnCritParticles(source, target);

            // Notify the caster
            caster.sendSystemMessage(Component.literal(
                    String.format("\u00A76\u2736 Spell Critical! \u00A7f%.1f damage", newDamage)));

            TalentsMod.LOGGER.debug(
                    "Spell crit! Player={} roll={} vs chance={}, multiplier={}x ({} -> {})",
                    caster.getName().getString(),
                    String.format("%.4f", roll),
                    String.format("%.4f", critChance),
                    String.format("%.2f", critDamage),
                    originalDamage, newDamage);

        } catch (Exception e) {
            TalentsMod.LOGGER.debug("Error processing SpellDamageEvent for spell crit: {}", e.getMessage());
        }
    }

    private static void spawnCritParticles(io.redspace.ironsspellbooks.damage.SpellDamageSource source,
            LivingEntity target) {
        if (!(target.level() instanceof ServerLevel serverLevel))
            return;

        String particleName = "magic"; // default fallback
        try {
            io.redspace.ironsspellbooks.api.spells.AbstractSpell spell = source.spell();
            if (spell != null) {
                io.redspace.ironsspellbooks.api.spells.SchoolType school = spell.getSchoolType();
                if (school != null) {
                    String schoolFull = school.getId().toString();
                    String schoolPath = school.getId().getPath();
                    if (schoolFull.equals("traveloptics:aqua")) {
                        particleName = "acid_bubble";
                    } else {
                        particleName = switch (schoolPath) {
                            case "fire" -> "fire";
                            case "ice" -> "snowflake";
                            case "lightning" -> "lightning";
                            case "ender" -> "unstable_ender";
                            case "nature" -> "nature";
                            case "blood" -> "acid_bubble"; // blood maps to AQUA
                            default -> "magic";
                        };
                    }
                }
            }
        } catch (Exception ignored) {
        }

        ParticleOptions particle = IronParticleHelper.getIronParticle(particleName);
        if (particle == null)
            return;

        serverLevel.sendParticles(particle,
                target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(),
                20, 0.4, 0.4, 0.4, 0.1);
    }
}
