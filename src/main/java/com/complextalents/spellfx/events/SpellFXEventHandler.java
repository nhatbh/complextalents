package com.complextalents.spellfx.events;

import com.complextalents.TalentsMod;
import com.complextalents.network.PacketHandler;
import com.complextalents.spellfx.client.ClientSpellFXHandler;
import com.complextalents.spellfx.network.S2CSpellBackSplatterPacket;
import com.complextalents.spellfx.network.S2CSpellFXPacket;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Event handler for spellcasting FX.
 * Intercepts Iron's Spells SpellOnCastEvent & SpellDamageEvent to trigger:
 * 1. Screen shake, camera recoil kick, FOV impulse, school-colored muzzle flash overlay, and backward physical push on cast.
 * 2. Directional back splatter vector particles scaled by damage dealt on spell impact.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SpellFXEventHandler {

    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        if (event.getEntity() == null)
            return;

        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null)
            return;

        int spellLevel = event.getSpellLevel();

        // 1. Accurately check if spell is a damaging spell using Iron's Spells tooltip unique info analysis
        if (!isDamagingSpell(spell, spellLevel, event.getEntity())) {
            return;
        }

        int manaCost = 20;
        try {
            manaCost = spell.getManaCost(spellLevel);
        } catch (Exception ignored) {
        }

        int schoolColor = getSchoolColorHex(spell);

        // 2. Physically push player backward based on mana cost / spell force
        applyPhysicalRecoil(event.getEntity(), manaCost);

        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // Send feedback packet exclusively to the caster
            PacketHandler.sendTo(new S2CSpellFXPacket(manaCost, schoolColor), serverPlayer);
        } else if (event.getEntity().level().isClientSide()) {
            // Direct client trigger if event is handled client-side for local player
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getUUID().equals(event.getEntity().getUUID())) {
                ClientSpellFXHandler.triggerFeedback(manaCost, schoolColor);
            }
        }
    }

    /**
     * Intercepts spell damage events to spawn directional back splatter vector particles.
     */
    @SubscribeEvent
    public static void onSpellDamage(SpellDamageEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide()) return;
        if (event.getAmount() <= 0.1f) return;

        LivingEntity target = event.getEntity();
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        Vec3 attackDir = new Vec3(0, 0, 1);
        if (event.getSpellDamageSource() != null) {
            if (event.getSpellDamageSource().getDirectEntity() != null) {
                attackDir = targetPos.subtract(event.getSpellDamageSource().getDirectEntity().position());
            } else if (event.getSpellDamageSource().getEntity() != null) {
                attackDir = targetPos.subtract(event.getSpellDamageSource().getEntity().position());
            }
        }

        if (attackDir.lengthSqr() < 0.001) {
            attackDir = target.getLookAngle().reverse();
        } else {
            attackDir = attackDir.normalize();
        }

        String schoolPath = "default";
        if (event.getSpellDamageSource() != null && event.getSpellDamageSource().spell() != null) {
            AbstractSpell spell = event.getSpellDamageSource().spell();
            if (spell.getSchoolType() != null) {
                schoolPath = spell.getSchoolType().getId().getPath().toLowerCase();
            }
        }

        if (target.level() instanceof ServerLevel serverLevel) {
            S2CSpellBackSplatterPacket packet = new S2CSpellBackSplatterPacket(
                    targetPos.x, targetPos.y, targetPos.z,
                    attackDir.x, attackDir.y, attackDir.z,
                    event.getAmount(), schoolPath
            );
            PacketHandler.sendToNearby(packet, serverLevel, targetPos);
        }
    }

    /**
     * Applies physical backward push force to the caster to demonstrate the impact of the attack.
     */
    private static void applyPhysicalRecoil(LivingEntity entity, int manaCost) {
        if (entity == null) return;

        Vec3 look = entity.getLookAngle();
        // Calculate backward push strength (0.12 to 0.38)
        double pushStrength = Math.min(0.38, 0.12 + (manaCost / 100.0) * 0.15);

        // Horizontal backward vector + tiny upward lift so player slides/hops backward cleanly
        Vec3 recoilVector = new Vec3(-look.x * pushStrength, 0.06, -look.z * pushStrength);

        entity.setDeltaMovement(entity.getDeltaMovement().add(recoilVector));

        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.hurtMarked = true; // Sync velocity modification to client immediately
        }
    }

    /**
     * Checks whether a spell is a damaging/attack spell by inspecting Iron's Spells unique info tooltip output.
     */
    public static boolean isDamagingSpell(AbstractSpell spell, int spellLevel, LivingEntity caster) {
        if (spell == null) return false;

        try {
            List<MutableComponent> uniqueInfo = spell.getUniqueInfo(spellLevel, caster);
            if (uniqueInfo != null) {
                for (Component component : uniqueInfo) {
                    if (component.getContents() instanceof TranslatableContents translatable) {
                        String key = translatable.getKey().toLowerCase();
                        if (key.contains("damage")) {
                            return true;
                        }
                    }
                    String text = component.getString().toLowerCase();
                    if (text.contains("damage") || text.contains("dmg")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    public static int getSchoolColorHex(AbstractSpell spell) {
        if (spell == null || spell.getSchoolType() == null)
            return 0xFFFFFF;

        try {
            String schoolPath = spell.getSchoolType().getId().getPath().toLowerCase();
            return switch (schoolPath) {
                case "fire" -> 0xFF6A00;       // Vibrant Orange
                case "ice" -> 0xE0F7FF;        // Icy Frost White
                case "lightning" -> 0x00BFFF;  // Electric Blue
                case "holy" -> 0xFFF066;       // Light Yellow
                case "ender" -> 0xAA00FF;      // Void Purple
                case "blood" -> 0xFF0022;      // Crimson Red
                case "evocation" -> 0xD0D8E0;  // Pale Gray
                case "eldritch" -> 0x00FFFF;   // Cyan
                case "nature" -> 0x22FF55;     // Verdant Green
                default -> 0xFFFFFF;           // Pure White
            };
        } catch (Exception ignored) {
            return 0xFFFFFF;
        }
    }
}
