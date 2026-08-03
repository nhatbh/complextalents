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
     * Check if a player active origin is a Mage origin (Elemental Mage or Dark
     * Mage).
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
     * When a Mage casts a spell, inflict the Engaged effect on themselves for 12
     * seconds (240 ticks).
     */
    @SubscribeEvent
    public static void onSpellOnCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            if (isMage(serverPlayer)) {
                // Inflict 12s Engaged effect on self (refreshes if already present)
                serverPlayer.addEffect(new MobEffectInstance(
                        ModEffects.ENGAGED.get(),
                        ENGAGED_DURATION_TICKS,
                        0,
                        false, // ambient
                        true, // visible particles
                        true // icon
                ));
            }
        }
    }

    /**
     * Server tick handler for fast out-of-combat mana recovery.
     * Prevents mana recovery while Engaged effect is active.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }

        if (!(event.player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!isMage(serverPlayer)) {
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

                    if (currentMana < maxMana) {
                        // Rate: 10% Max Mana + 4 flat Mana per second (halved per 0.5s tick)
                        double increment = (maxMana * 0.05) + 2.0;
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
