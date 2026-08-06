package com.complextalents.impl.mage;

import com.complextalents.TalentsMod;
import com.complextalents.effect.ModEffects;
import com.complextalents.origin.OriginManager;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class MageManaRegenHandler {

    // Engaged effect duration: 12 seconds (240 ticks)
    private static final int ENGAGED_DURATION_TICKS = 240;

    /**
     * Check if a player active origin is a Mage origin (Elemental Mage or Dark Mage).
     */
    public static boolean isMage(ServerPlayer player) {
        ResourceLocation originId = OriginManager.getOriginId(player);
        if (originId == null) {
            return false;
        }
        String path = originId.getPath();
        return "elemental_mage".equals(path) || "dark_mage".equals(path);
    }

    /**
     * When a Mage casts a spell, enter combat (inflict 12s Engaged effect).
     */
    @SubscribeEvent
    public static void onSpellOnCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer && isMage(serverPlayer)) {
            serverPlayer.addEffect(new MobEffectInstance(
                    ModEffects.ENGAGED.get(),
                    ENGAGED_DURATION_TICKS,
                    0,
                    false, // ambient
                    true,  // visible particles
                    true   // icon
            ));

            if (com.complextalents.impl.elementalmage.origin.ElementalMageOrigin.isElementalMage(serverPlayer)) {
                io.redspace.ironsspellbooks.api.spells.AbstractSpell spell =
                        io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(event.getSpellId());
                if (spell != null && spell.getSchoolType() != null) {
                    com.complextalents.elemental.ElementType element = mapSchoolToElement(spell.getSchoolType());
                    if (element != null) {
                        serverPlayer.getCapability(com.complextalents.impl.elementalmage.ElementalMageDataProvider.ELEMENTAL_DATA)
                                .ifPresent(cap -> cap.setApexElement(element));
                    }
                }
            }
        }
    }

    private static com.complextalents.elemental.ElementType mapSchoolToElement(io.redspace.ironsspellbooks.api.spells.SchoolType schoolType) {
        if (schoolType == null) return null;
        String schoolPath = schoolType.getId().getPath();
        String schoolFull = schoolType.getId().toString();

        if ("traveloptics:aqua".equals(schoolFull)) return com.complextalents.elemental.ElementType.AQUA;

        return switch (schoolPath) {
            case "fire" -> com.complextalents.elemental.ElementType.FIRE;
            case "ice" -> com.complextalents.elemental.ElementType.ICE;
            case "lightning" -> com.complextalents.elemental.ElementType.LIGHTNING;
            case "nature" -> com.complextalents.elemental.ElementType.NATURE;
            case "aqua" -> com.complextalents.elemental.ElementType.AQUA;
            case "holy" -> com.complextalents.elemental.ElementType.HOLY;
            case "evocation" -> com.complextalents.elemental.ElementType.EVOCATION;
            case "ender" -> com.complextalents.elemental.ElementType.ENDER;
            case "eldritch" -> com.complextalents.elemental.ElementType.ELDRITCH;
            case "blood" -> com.complextalents.elemental.ElementType.BLOOD;
            default -> null;
        };
    }

    /**
     * Server tick handler for Mage out-of-combat mana recovery:
     * - Fast recovery up to 50% max mana
     * - Slower recovery from 50% to 100% max mana
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }

        if (!(event.player instanceof ServerPlayer serverPlayer) || !isMage(serverPlayer)) {
            return;
        }

        // Tick every 10 ticks (0.5s) for smooth mana restoration
        if (serverPlayer.tickCount % 10 == 0) {
            // Out of Combat check: player does NOT have Engaged effect
            if (!serverPlayer.hasEffect(ModEffects.ENGAGED.get())) {
                try {
                    MagicData magicData = MagicData.getPlayerMagicData(serverPlayer);
                    double maxMana = serverPlayer.getAttributeValue(AttributeRegistry.MAX_MANA.get());
                    float currentMana = magicData.getMana();
                    float halfMana = (float) (maxMana * 0.5);

                    if (currentMana < maxMana) {
                        double increment;
                        if (currentMana < halfMana) {
                            // FAST RECOVERY below 50% mana:
                            // ~15% Max Mana + 4.0 flat Mana per second (halved per 0.5s tick)
                            increment = (maxMana * 0.075) + 2.0;
                        } else {
                            // SLOWER RECOVERY from 50% to 100% mana:
                            // ~3% Max Mana + 1.0 flat Mana per second (halved per 0.5s tick)
                            increment = (maxMana * 0.015) + 0.5;
                        }

                        float newMana = (float) Math.min(maxMana, currentMana + increment);
                        magicData.setMana(newMana);

                        PacketDistributor.sendToPlayer(serverPlayer, new SyncManaPacket(magicData));
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
