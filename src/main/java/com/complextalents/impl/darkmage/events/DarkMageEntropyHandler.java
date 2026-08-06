package com.complextalents.impl.darkmage.events;

import com.complextalents.TalentsMod;
import com.complextalents.effect.ModEffects;
import com.complextalents.impl.darkmage.origin.DarkMageOrigin;
import com.complextalents.origin.OriginManager;
import com.complextalents.passive.PassiveManager;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class DarkMageEntropyHandler {

    /**
     * Pre-cast handler:
     * 1. Checks if player/entity is Silenced (blocks spell cast).
     * 2. Enforces Possessed restriction (only Eldritch spells).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellPreCast(SpellPreCastEvent event) {
        if (event.getEntity() != null && event.getEntity().hasEffect(ModEffects.SILENCED.get())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("\u00A7cSilenced (Cannot cast)"), true);
            }
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer serverPlayer))
            return;

        if (!DarkMageOrigin.isDarkMage(serverPlayer))
            return;

        try {
            AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
            if (spell != null && spell.getSchoolType() != null) {
                String schoolPath = spell.getSchoolType().getId().getPath();

                // Possessed Restriction: Only Eldritch spells allowed
                if (serverPlayer.hasEffect(ModEffects.POSSESSED.get()) && !"eldritch".equalsIgnoreCase(schoolPath)) {
                    event.setCanceled(true);
                    serverPlayer.sendSystemMessage(Component.literal("\u00A7cPossessed (Eldritch Only)"), true);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * On-cast handler for Dark Mage:
     * 1. Free Casting: Refunds consumed mana so spell costs 0 mana (requires max mana).
     * 2. Blood Spells: Costs % of Max HP = (Spell Mana Cost / Max Mana) and flushes % Entropy.
     * 3. Eldritch Spells: Does NOT fill/flush entropy.
     *    - If current Entropy >= Redline threshold: triggers Possession (15s Eldritch Only, locked entropy).
     *    - If current Entropy < Redline threshold: triggers Eldritch Backfire (self magic damage + Silenced).
     * 4. Other Non-Blood, Non-Eldritch Spells (Evoke, Ender, etc.): Fills Entropy = (Spell Mana Cost / Max Mana).
     *    - Overload Check: If Entropy >= 100, resets to 0 and silences for 15s.
     * 5. Ender Spells: Grants Void Strike buff.
     */
    @SubscribeEvent
    public static void onSpellOnCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer))
            return;

        if (!DarkMageOrigin.isDarkMage(serverPlayer))
            return;

        try {
            AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
            if (spell == null || spell.getSchoolType() == null)
                return;

            // Reset out-of-combat decay timer whenever a spell is cast
            serverPlayer.getPersistentData().putInt("darkmage_decay_ticks", 0);

            int manaCost = spell.getManaCost(event.getSpellLevel());
            double maxMana = serverPlayer.getAttributeValue(AttributeRegistry.MAX_MANA.get());
            if (maxMana <= 0) maxMana = 100.0;

            double manaRatio = Math.max(0.0, manaCost / maxMana);

            // 1. Free Casting: Refund consumed mana
            if (manaCost > 0) {
                MagicData magicData = MagicData.getPlayerMagicData(serverPlayer);
                if (magicData != null) {
                    float newMana = (float) Math.min(maxMana, magicData.getMana() + manaCost);
                    magicData.setMana(newMana);
                    PacketDistributor.sendToPlayer(serverPlayer, new SyncManaPacket(magicData));
                }
            }

            String schoolPath = spell.getSchoolType().getId().getPath();
            boolean isPossessed = serverPlayer.hasEffect(ModEffects.POSSESSED.get());

            // 2. Blood Synergy (HP Cost & Flush Entropy)
            if ("blood".equalsIgnoreCase(schoolPath)) {
                // Get current Blood Exhaustion stack count (0 to 7)
                int stacks = 0;
                MobEffectInstance existingEffect = serverPlayer.getEffect(ModEffects.BLOOD_EXHAUSTION.get());
                if (existingEffect != null) {
                    stacks = Math.min(7, existingEffect.getAmplifier() + 1);
                }

                // Escalating HP Cost: base ratio matches entropy ratio (35% of max HP per 100% mana cost), escalating per stack
                float costMultiplier = 1.0f + (stacks * 0.2f);
                float hpCost = (float) (serverPlayer.getMaxHealth() * (manaRatio * 0.35f) * costMultiplier);
                serverPlayer.setHealth(Math.max(1.0f, serverPlayer.getHealth() - hpCost));

                // Apply/Stack Blood Exhaustion effect for 10s (200 ticks), up to max 7 stacks (amplifier 6)
                int nextAmp = Math.min(6, stacks); // amplifier 0..6 = 1..7 stacks
                serverPlayer.addEffect(new MobEffectInstance(ModEffects.BLOOD_EXHAUSTION.get(), 200, nextAmp));

                if (!isPossessed) {
                    int flushAmt = (int) Math.round(manaRatio * 35.0);
                    PassiveManager.modifyPassiveStacks(serverPlayer, "entropy", -flushAmt);
                }
            }
            // 3. Eldritch Spells (No entropy manipulation; triggers Possession at/above Redline, or Backfire below Redline)
            else if ("eldritch".equalsIgnoreCase(schoolPath)) {
                if (!isPossessed) {
                    int currentEntropy = PassiveManager.getPassiveStacks(serverPlayer, "entropy");
                    int originLevel = Math.min(4, Math.max(0, OriginManager.getOriginLevel(serverPlayer) - 1));
                    double requiredThreshold = DarkMageOrigin.ELDRITCH_REQUIRED_THRESHOLD[originLevel];

                    if (currentEntropy >= requiredThreshold) {
                        // Trigger Possession (15s = 300 ticks)
                        serverPlayer.addEffect(new MobEffectInstance(ModEffects.POSSESSED.get(), 300, 0));
                        serverPlayer.sendSystemMessage(Component.literal("\u00A7dPossessed! (15s Eldritch Only)"), true);
                    } else {
                        // Trigger Eldritch Backfire (Self magic damage & Silenced)
                        float backfireHp = (float) (serverPlayer.getMaxHealth() * DarkMageOrigin.ELDRITCH_BACKFIRE_SELF_DMG[originLevel]);
                        serverPlayer.hurt(serverPlayer.damageSources().magic(), backfireHp);

                        int silenceTicks = (int) (DarkMageOrigin.ELDRITCH_BACKFIRE_SILENCE_SEC[originLevel] * 20);
                        serverPlayer.addEffect(new MobEffectInstance(ModEffects.SILENCED.get(), silenceTicks, 0));
                        serverPlayer.sendSystemMessage(Component.literal("\u00A7cEldritch Backfire! (Silenced)"), true);
                    }
                }
            }
            // 4. Other Non-Blood, Non-Eldritch Spells (Evoke, Ender, Fire, Holy, Ice, etc. -> Gain Entropy & Check Overload)
            else {
                if (!isPossessed) {
                    int gainAmt = (int) Math.round(manaRatio * 35.0);
                    PassiveManager.modifyPassiveStacks(serverPlayer, "entropy", gainAmt);

                    int currentEntropy = PassiveManager.getPassiveStacks(serverPlayer, "entropy");

                    // Overload Check: If entropy >= 100%, reset and silence for 15s (300 ticks)
                    if (currentEntropy >= 100) {
                        PassiveManager.setPassiveStacks(serverPlayer, "entropy", 0);
                        serverPlayer.addEffect(new MobEffectInstance(ModEffects.SILENCED.get(), 300, 0));
                        serverPlayer.sendSystemMessage(Component.literal("\u00A7cEntropy Overload (Silenced 15s)"), true);
                    }
                }
            }

            // 5. Ender Synergy (Void Strike Buff)
            if ("ender".equalsIgnoreCase(schoolPath)) {
                int originLevel = Math.min(4, Math.max(0, OriginManager.getOriginLevel(serverPlayer) - 1));
                int durationTicks = (int) (DarkMageOrigin.VOID_STRIKE_DURATION[originLevel] * 20);
                serverPlayer.addEffect(new MobEffectInstance(ModEffects.VOID_STRIKE.get(), durationTicks, 0));
            }

        } catch (Exception ignored) {
        }
    }

    /**
     * Natural Entropy Decay out of combat:
     * When not ENGAGED and not POSSESSED, entropy decays with an accelerating curve:
     * - 0-3s out of combat: -1 entropy/sec (Grace period)
     * - 4-6s out of combat: -3 entropy/sec (Moderate decay)
     * - 7-10s out of combat: -6 entropy/sec (Fast decay)
     * - >10s out of combat: -10 entropy/sec (Accelerated rapid flush)
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide())
            return;

        if (event.player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.tickCount % 20 != 0)
                return;

            if (!DarkMageOrigin.isDarkMage(serverPlayer))
                return;

            // If Possessed or Engaged in combat, reset decay timer and pause decay
            if (serverPlayer.hasEffect(ModEffects.POSSESSED.get()) || serverPlayer.hasEffect(ModEffects.ENGAGED.get())) {
                serverPlayer.getPersistentData().putInt("darkmage_decay_ticks", 0);
                return;
            }

            int currentEntropy = PassiveManager.getPassiveStacks(serverPlayer, "entropy");
            if (currentEntropy > 0) {
                int decayTicks = serverPlayer.getPersistentData().getInt("darkmage_decay_ticks") + 20;
                serverPlayer.getPersistentData().putInt("darkmage_decay_ticks", decayTicks);

                int decaySec = decayTicks / 20;
                int decayAmount;
                if (decaySec <= 3) {
                    decayAmount = 1;  // Grace period: slow decay (1/s)
                } else if (decaySec <= 6) {
                    decayAmount = 3;  // Moderate decay (3/s)
                } else if (decaySec <= 10) {
                    decayAmount = 6;  // Fast decay (6/s)
                } else {
                    decayAmount = 10; // Accelerated rapid decay (10/s)
                }

                PassiveManager.modifyPassiveStacks(serverPlayer, "entropy", -decayAmount);
            } else {
                serverPlayer.getPersistentData().putInt("darkmage_decay_ticks", 0);
            }
        }
    }

    /**
     * Scaling Spell Damage based on Arcane Entropy:
     * Dark Mages deal lower damage at low Entropy (0% = 75% damage)
     * and higher damage at high Entropy (100% = 130% damage).
     */
    @SubscribeEvent
    public static void onSpellDamage(SpellDamageEvent event) {
        if (event.getSpellDamageSource() != null && event.getSpellDamageSource().getEntity() instanceof ServerPlayer caster) {
            if (DarkMageOrigin.isDarkMage(caster)) {
                int currentEntropy = PassiveManager.getPassiveStacks(caster, "entropy");

                // If Possessed, treat as max 100% entropy
                if (caster.hasEffect(ModEffects.POSSESSED.get())) {
                    currentEntropy = 100;
                }

                // Scaling curve: 0.75x at 0% entropy -> 1.30x at 100% entropy
                double multiplier = 0.75 + (currentEntropy / 100.0) * 0.55;
                event.setAmount((float) (event.getAmount() * multiplier));
            }
        }
    }

    /**
     * Resets Entropy to 0 and applies 15s (300 ticks) of Silenced effect when Possession effect expires or is removed.
     */
    @SubscribeEvent
    public static void onPossessedExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect() == ModEffects.POSSESSED.get()) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                PassiveManager.setPassiveStacks(serverPlayer, "entropy", 0);
                serverPlayer.addEffect(new MobEffectInstance(ModEffects.SILENCED.get(), 300, 0));
                serverPlayer.sendSystemMessage(Component.literal("§cPossession ended! (Silenced 15s)"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onPossessedRemove(MobEffectEvent.Remove event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect() == ModEffects.POSSESSED.get()) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                PassiveManager.setPassiveStacks(serverPlayer, "entropy", 0);
                serverPlayer.addEffect(new MobEffectInstance(ModEffects.SILENCED.get(), 300, 0));
                serverPlayer.sendSystemMessage(Component.literal("§cPossession ended! (Silenced 15s)"), true);
            }
        }
    }
}
