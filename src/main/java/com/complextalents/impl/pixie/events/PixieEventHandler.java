package com.complextalents.impl.pixie.events;

import com.complextalents.TalentsMod;
import com.complextalents.effect.ModEffects;
import com.complextalents.impl.pixie.data.PixieData;
import com.complextalents.impl.pixie.data.PixieDataManager;
import com.complextalents.impl.pixie.origin.PixieOrigin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class PixieEventHandler {

    private static final UUID AD_MODIFIER_UUID = UUID.fromString("f48b8120-7f28-4f9e-991c-fa328b90a120");
    private static final UUID AP_MODIFIER_UUID = UUID.fromString("f48b8120-7f28-4f9e-991c-fa328b90a121");
    private static final UUID DR_MODIFIER_UUID = UUID.fromString("f48b8120-7f28-4f9e-991c-fa328b90a122");

    @SubscribeEvent
    public static void onEntitySize(net.minecraftforge.event.entity.EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player) {
            PixieData pixieData = PixieDataManager.get(player);
            if (pixieData.isPixie() && pixieData.isAttached()) {
                event.setNewSize(net.minecraft.world.entity.EntityDimensions.scalable(0.5f, 0.5f));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        PixieData pixieData = PixieDataManager.get(player);

        if (player.level().isClientSide()) {
            if (pixieData.isPixie() && pixieData.isAttached()) {
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
            }
            return;
        }

        if (!(player instanceof ServerPlayer pixiePlayer)) return;

        pixieData.tickSilence();

        boolean isPixieOrigin = PixieOrigin.isPixie(pixiePlayer);
        if (isPixieOrigin) {
            pixieData.setPixie(true);
        }

        if (!pixieData.isPixie()) {
            return;
        }

        // Refresh entity dimensions when attached state changes
        pixiePlayer.refreshDimensions();

        // Host Resolution
        LivingEntity host = null;
        if (pixieData.getHostUUID().isPresent()) {
            host = pixiePlayer.getServer().getPlayerList().getPlayer(pixieData.getHostUUID().get());
        } else if (pixieData.getHostEntityId().isPresent()) {
            Entity ent = pixiePlayer.level().getEntity(pixieData.getHostEntityId().get());
            if (ent instanceof LivingEntity living) {
                host = living;
            }
        }

        // Flight, Invulnerability & Tethering Capabilities (Active ONLY when bound/attached to host)
        if (pixieData.isAttached()) {
            if (host == null || !host.isAlive() || host.level() != pixiePlayer.level()) {
                // Unbind if host is absent or dead
                detachPixie(pixiePlayer, pixieData, "Host unavailable");
            } else {
                // Grant flight and invulnerability while bound to host
                pixiePlayer.getAbilities().mayfly = true;
                pixiePlayer.getAbilities().flying = true;
                pixiePlayer.onUpdateAbilities();

                pixiePlayer.setInvulnerable(true);
                pixiePlayer.fallDistance = 0;

                // Adaptive Aura Application to host (if player)
                if (host instanceof ServerPlayer hostPlayer) {
                    applyAdaptiveAura(pixiePlayer, hostPlayer);
                }

                // Passive Pixie Dust Regeneration (+2 per tick -> 50 ticks to full)
                pixieData.regenPixieDust(2.0);

                // In-Combat Mana Handling
                pixieData.tickCombatTimer();
                if (pixieData.getHostCombatTimer() > 0) {
                    restoreInCombatMana(pixiePlayer);
                }

                // Tether Range Clamping (Allows free movement within range, gently pulls if exceeding tether range)
                double tetherRange = com.complextalents.origin.OriginManager.getOriginStat(pixiePlayer, "tetherRange");
                if (tetherRange <= 0) tetherRange = 6.0;

                double dist = pixiePlayer.distanceTo(host);
                if (dist > tetherRange) {
                    Vec3 pullDir = host.position().subtract(pixiePlayer.position()).normalize();
                    double excessDist = dist - tetherRange;
                    double pullStrength = Math.min(0.8, 0.15 + excessDist * 0.1);

                    Vec3 currentVel = pixiePlayer.getDeltaMovement();
                    Vec3 targetVel = currentVel.add(pullDir.scale(pullStrength));
                    pixiePlayer.setDeltaMovement(targetVel);
                    pixiePlayer.hurtMarked = true; // Force network sync
                }
            }
        } else {
            // Unbound / Detached state: Disable flight and remove invulnerability
            if (!pixiePlayer.isCreative() && !pixiePlayer.isSpectator()) {
                pixiePlayer.getAbilities().mayfly = false;
                pixiePlayer.getAbilities().flying = false;
                pixiePlayer.onUpdateAbilities();
            }

            removeAuraModifiers(pixiePlayer);
            pixiePlayer.setInvulnerable(false);
        }
    }

    private static void applyAdaptiveAura(ServerPlayer pixie, ServerPlayer host) {
        double adBuff = com.complextalents.origin.OriginManager.getOriginStat(pixie, "physicalAdBuff");
        double defBuff = com.complextalents.origin.OriginManager.getOriginStat(pixie, "physicalDefBuff");
        double apBuff = com.complextalents.origin.OriginManager.getOriginStat(pixie, "magicalApBuff");
        double manaRegenBuff = com.complextalents.origin.OriginManager.getOriginStat(pixie, "magicalManaRegenBuff");

        if (adBuff <= 0) adBuff = 0.10;
        if (defBuff <= 0) defBuff = 0.05;
        if (apBuff <= 0) apBuff = 0.10;
        if (manaRegenBuff <= 0) manaRegenBuff = 0.10;

        double attackDamage = host.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double spellPower = 1.0;
        var apAttr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power"));
        if (apAttr != null) {
            spellPower = host.getAttributeValue(apAttr);
        }

        AttributeInstance adAttrInst = host.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance armorAttrInst = host.getAttribute(Attributes.ARMOR);

        if (attackDamage >= spellPower * 10.0) {
            // Physical host
            if (adAttrInst != null) {
                adAttrInst.removeModifier(AD_MODIFIER_UUID);
                adAttrInst.addTransientModifier(new AttributeModifier(AD_MODIFIER_UUID, "Pixie AD Buff", adBuff, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            if (armorAttrInst != null) {
                armorAttrInst.removeModifier(DR_MODIFIER_UUID);
                armorAttrInst.addTransientModifier(new AttributeModifier(DR_MODIFIER_UUID, "Pixie Defense Buff", defBuff, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        } else {
            // Magical host
            if (apAttr != null) {
                AttributeInstance apAttrInst = host.getAttribute(apAttr);
                if (apAttrInst != null) {
                    apAttrInst.removeModifier(AP_MODIFIER_UUID);
                    apAttrInst.addTransientModifier(new AttributeModifier(AP_MODIFIER_UUID, "Pixie AP Buff", apBuff, AttributeModifier.Operation.MULTIPLY_BASE));
                }
            }
            // Mana regen buff via status effect tick or Iron's API if loaded
            if (com.complextalents.origin.integration.OriginModIntegrationHandler.isIronSpellbooksLoaded()) {
                try {
                    io.redspace.ironsspellbooks.api.magic.MagicData magicData = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(host);
                    magicData.setMana(magicData.getMana() + (float)(manaRegenBuff * 0.5f));
                } catch (Exception ignored) {}
            }
        }
    }

    private static void removeAuraModifiers(ServerPlayer pixie) {
        PixieData data = PixieDataManager.get(pixie);
        data.getHostUUID().ifPresent(hostUUID -> {
            ServerPlayer host = pixie.getServer().getPlayerList().getPlayer(hostUUID);
            if (host != null) {
                AttributeInstance adInst = host.getAttribute(Attributes.ATTACK_DAMAGE);
                if (adInst != null) adInst.removeModifier(AD_MODIFIER_UUID);

                AttributeInstance drInst = host.getAttribute(Attributes.ARMOR);
                if (drInst != null) drInst.removeModifier(DR_MODIFIER_UUID);

                var apAttr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power"));
                if (apAttr != null) {
                    AttributeInstance apInst = host.getAttribute(apAttr);
                    if (apInst != null) apInst.removeModifier(AP_MODIFIER_UUID);
                }
            }
        });
    }

    private static void restoreInCombatMana(ServerPlayer pixiePlayer) {
        if (com.complextalents.origin.integration.OriginModIntegrationHandler.isIronSpellbooksLoaded()) {
            try {
                io.redspace.ironsspellbooks.api.magic.MagicData magicData = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(pixiePlayer);
                magicData.setMana(magicData.getMana() + 2.0f);
            } catch (Exception ignored) {}
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer pixiePlayer) {
            PixieData pixieData = PixieDataManager.get(pixiePlayer);
            if (pixieData.isPixie() && pixieData.isAttached()) {
                event.setCanceled(true);
            }
        }
        if (event.getSource().getEntity() instanceof ServerPlayer pixieAttacker) {
            PixieData pixieData = PixieDataManager.get(pixieAttacker);
            if (pixieData.isPixie() && pixieData.isAttached()) {
                boolean isSpellOrMagic = event.getSource().is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)
                        || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
                        || event.getSource().isIndirect()
                        || "magic".equals(event.getSource().getMsgId())
                        || "indirectMagic".equals(event.getSource().getMsgId());
                if (!isSpellOrMagic) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer pixiePlayer) {
            PixieData pixieData = PixieDataManager.get(pixiePlayer);
            if (pixieData.isPixie() && pixieData.isAttached()) {
                event.setCanceled(true);
            }
        }
        if (event.getSource().getEntity() instanceof ServerPlayer pixieAttacker) {
            PixieData pixieData = PixieDataManager.get(pixieAttacker);
            if (pixieData.isPixie() && pixieData.isAttached()) {
                boolean isSpellOrMagic = event.getSource().is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)
                        || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
                        || event.getSource().isIndirect()
                        || "magic".equals(event.getSource().getMsgId())
                        || "indirectMagic".equals(event.getSource().getMsgId());
                if (!isSpellOrMagic) {
                    event.setCanceled(true);
                }
            }
        }

        // Combat activity tracking for host
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            updateHostCombat(attacker);
        }
        if (event.getEntity() instanceof ServerPlayer targetPlayer) {
            updateHostCombat(targetPlayer);
        }
    }

    private static void updateHostCombat(ServerPlayer player) {
        // Find any pixie attached to this player
        for (PixieData data : PixieDataManager.getAllData().values()) {
            if (data.isAttached() && data.getHostUUID().map(uuid -> uuid.equals(player.getUUID())).orElse(false)) {
                data.setHostCombatTimer(200); // 10s out of combat timer
            }
        }
    }

    @SubscribeEvent
    public static void onHostDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer host) {
            for (ServerPlayer pixiePlayer : host.getServer().getPlayerList().getPlayers()) {
                PixieData pixieData = PixieDataManager.get(pixiePlayer);
                if (pixieData.isPixie() && pixieData.isAttached() && pixieData.getHostUUID().map(uuid -> uuid.equals(host.getUUID())).orElse(false)) {
                    detachPixie(pixiePlayer, pixieData, "Host died");
                    pixieData.setSilenceTicks(60); // 3 seconds silence
                    pixiePlayer.displayClientMessage(Component.literal("§cYour host died! You are thrown off and silenced for 3s."), true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onSpellDamage(io.redspace.ironsspellbooks.api.events.SpellDamageEvent event) {
        if (event.getSpellDamageSource().getEntity() instanceof ServerPlayer pixieAttacker) {
            PixieData pixieData = PixieDataManager.get(pixieAttacker);
            if (pixieData.isPixie()) {
                LivingEntity target = event.getEntity();
                if (target != null && target.isAlive()) {
                    target.addEffect(new MobEffectInstance(ModEffects.PIXIE_MARK.get(), 200, 0, false, true));
                    target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                            SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 1.0f, 1.4f);
                    pixieAttacker.displayClientMessage(Component.literal("§dMarked " + target.getName().getString() + " with Pixie Mark!"), true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onHostAttackMarked(LivingDamageEvent event) {
        if (event.getEntity() != null) {
            LivingEntity target = event.getEntity();
            // Case 2: Damage caused BY THE HOST on a target ALREADY marked -> Host Detonates it!
            if (event.getSource().getEntity() instanceof ServerPlayer hostAttacker) {
                if (target.hasEffect(ModEffects.PIXIE_MARK.get())) {
                    // Find Pixie attached to this host
                    for (ServerPlayer pixie : hostAttacker.getServer().getPlayerList().getPlayers()) {
                        PixieData data = PixieDataManager.get(pixie);
                        if (data.isPixie() && data.getHostUUID().map(uuid -> uuid.equals(hostAttacker.getUUID())).orElse(false)) {
                            target.removeEffect(ModEffects.PIXIE_MARK.get());

                            int skillLevel = com.complextalents.skill.SkillManager.getSkillLevel(pixie,
                                    com.complextalents.impl.pixie.skills.FaeSurgeSkill.ID);
                            if (skillLevel <= 0) skillLevel = 1;

                            double[] baseDmg = {40, 60, 80, 110, 150};
                            int idx = Math.min(4, Math.max(0, skillLevel - 1));

                            double ap = 1.0;
                            var apAttr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power"));
                            if (apAttr != null) {
                                ap = pixie.getAttributeValue(apAttr);
                            }

                            double totalDmg = baseDmg[idx] + (ap * 0.40);
                            target.hurt(pixie.damageSources().magic(), (float) totalDmg);

                            target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.5f);
                            break;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PixieData playerPixieData = PixieDataManager.get(player);
            if (playerPixieData.isAttached()) {
                detachPixie(player, playerPixieData, "Logged off");
            }
            // If host logged out, detach any attached pixie
            for (ServerPlayer pixie : player.getServer().getPlayerList().getPlayers()) {
                PixieData data = PixieDataManager.get(pixie);
                if (data.isAttached() && data.getHostUUID().map(uuid -> uuid.equals(player.getUUID())).orElse(false)) {
                    detachPixie(pixie, data, "Host logged off");
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PixieData pixieData = PixieDataManager.get(player);
            pixieData.resetAll();
            removeAuraModifiers(player);

            player.refreshDimensions();
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            PixieData pixieData = PixieDataManager.get(player);
            if (pixieData.isPixie() && pixieData.isAttached()) {
                event.setCanceled(true);
            }
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            PixieData pixieData = PixieDataManager.get(mc.player);
            if (pixieData.isPixie() && pixieData.isAttached()) {
                event.setCanceled(true);
            }
        }
    }

    public static void detachPixie(ServerPlayer pixiePlayer, PixieData pixieData, String reason) {
        removeAuraModifiers(pixiePlayer);
        pixieData.resetAttachment();
        pixiePlayer.setInvulnerable(false);
        pixiePlayer.refreshDimensions();

        if (!pixiePlayer.isCreative() && !pixiePlayer.isSpectator()) {
            pixiePlayer.getAbilities().mayfly = false;
            pixiePlayer.getAbilities().flying = false;
            pixiePlayer.onUpdateAbilities();
        }

        pixiePlayer.displayClientMessage(Component.literal("§cDetached from host (" + reason + ")!"), true);
        pixiePlayer.level().playSound(null, pixiePlayer.getX(), pixiePlayer.getY(), pixiePlayer.getZ(),
                SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
    }
}
