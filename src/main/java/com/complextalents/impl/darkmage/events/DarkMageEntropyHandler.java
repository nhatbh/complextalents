package com.complextalents.impl.darkmage.events;

import com.complextalents.TalentsMod;
import com.complextalents.effect.ModEffects;
import com.complextalents.impl.darkmage.origin.DarkMageOrigin;
import com.complextalents.origin.OriginManager;
import com.complextalents.passive.PassiveManager;
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
                // Get current Blood Exhaustion stack count (0, 1, 2, or 3)
                int stacks = 0;
                MobEffectInstance existingEffect = serverPlayer.getEffect(ModEffects.BLOOD_EXHAUSTION.get());
                if (existingEffect != null) {
                    stacks = Math.min(3, existingEffect.getAmplifier() + 1);
                }

                // Escalating HP Cost: 1.0x at 0 stacks, 1.5x at 1 stack, 2.0x at 2 stacks, 2.5x at 3 stacks
                float costMultiplier = 1.0f + (stacks * 0.5f);
                float hpCost = (float) (serverPlayer.getMaxHealth() * manaRatio * costMultiplier);
                serverPlayer.setHealth(Math.max(1.0f, serverPlayer.getHealth() - hpCost));

                // Apply/Stack Blood Exhaustion effect for 10s (200 ticks), up to max 3 stacks (amp 2)
                int nextAmp = Math.min(2, stacks); // amplifier 0 = 1 stack, amp 1 = 2 stacks, amp 2 = 3 stacks
                serverPlayer.addEffect(new MobEffectInstance(ModEffects.BLOOD_EXHAUSTION.get(), 200, nextAmp));

                if (!isPossessed) {
                    int flushAmt = (int) Math.round(manaRatio * 100.0);
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
                    int gainAmt = (int) Math.round(manaRatio * 100.0);
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
     * When not ENGAGED and not POSSESSED, entropy decays naturally over time (5 entropy per second).
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

            // If Possessed or Engaged, entropy cannot go down naturally
            if (serverPlayer.hasEffect(ModEffects.POSSESSED.get()) || serverPlayer.hasEffect(ModEffects.ENGAGED.get()))
                return;

            int currentEntropy = PassiveManager.getPassiveStacks(serverPlayer, "entropy");
            if (currentEntropy > 0) {
                PassiveManager.modifyPassiveStacks(serverPlayer, "entropy", -5);
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
