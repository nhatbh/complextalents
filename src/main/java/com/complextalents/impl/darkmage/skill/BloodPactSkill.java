package com.complextalents.impl.darkmage.skill;

import com.complextalents.impl.darkmage.util.BloodParticleHelper;
import com.complextalents.origin.OriginManager;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.targeting.TargetType;
import com.complextalents.util.UUIDHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * Blood Pact - Dark Mage's core toggle skill.
 * Volatile trance trading Current HP to ramp up Shadow Spell Power.
 * Deactivating detonates all nearby owned Soul Orbs in a Soul Wave.
 */
public class BloodPactSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "blood_pact");

    private static final UUID SPELL_POWER_UUID = UUIDHelper.generateAttributeModifierUUID("dark_mage", "blood_pact_spell_power");

    public static void register() {
        SkillBuilder.create("complextalents", "blood_pact")
                .nature(SkillNature.ACTIVE)
                .displayName("Blood Pact")
                .description("Kỹ năng duy trì (Toggle) đốt 8%-4% Current HP/s để tăng liên tục Shadow Spell Power và phân tán sát thương phép nhận vào thành Bleed 3s. Khi tắt, kích nổ tất cả Soul Orbs lân cận tạo Soul Wave (sóng xung kích gây sát thương diện rộng).")
                .targeting(TargetType.NONE)
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/darkmage/bloodpact.png"))
                .toggleable(true)
                .scaledCooldown(new double[]{10.0, 10.0, 10.0, 10.0, 10.0})
                .setMaxLevel(5)
                .scaledStat("hp_drain", "HP Drain/sec", new double[]{0.08, 0.07, 0.06, 0.05, 0.04})
                .scaledStat("spell_power", "Spell Power Bonus", new double[]{0.10, 0.20, 0.30, 0.40, 0.50})
                .validate((context, player) -> {
                    ServerPlayer serverPlayer = (ServerPlayer) player;
                    if (serverPlayer.getHealth() < serverPlayer.getMaxHealth() * 0.2f) {
                        serverPlayer.sendSystemMessage(Component.literal(
                                "\u00A7cBlood Pact requires at least 20% HP to activate!"
                        ));
                        return false;
                    }
                    return true;
                })
                .onActive((context, player) -> {
                    ServerPlayer serverPlayer = (ServerPlayer) player;
                    ServerLevel level = serverPlayer.serverLevel();

                    updateRampedSpellPower(serverPlayer, 1.0);

                    level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5f, 1.5f);
                    level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 0.8f);

                    Vec3 center = serverPlayer.position().add(0, serverPlayer.getBbHeight() / 2.0, 0);
                    BloodParticleHelper.sendParticleCircle(level, center, 1.5, BloodParticleHelper.BLOOD_MIST, 40);
                    BloodParticleHelper.sendParticleVerticalCircle(level, center, 1.5, BloodParticleHelper.BLOOD_MIST, 40);

                    serverPlayer.sendSystemMessage(Component.literal(
                            "\u00A75\u00A7lBLOOD PACT ACTIVATED!\u00A7r \u00A7dYour life force fuels your magic!"
                    ));
                })
                .onToggleOff(player -> {
                    ServerPlayer serverPlayer = (ServerPlayer) player;
                    ServerLevel level = serverPlayer.serverLevel();

                    removeSpellPowerBonus(serverPlayer);

                    // Detonate all owned Soul Orbs within 20m radius on manual toggle off
                    com.complextalents.impl.darkmage.manager.BloodOrbManager.detonateOwnerOrbs(serverPlayer, 20.0);

                    level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.8f, 1.2f);

                    serverPlayer.sendSystemMessage(Component.literal(
                            "\u00A7cBlood Pact deactivated."
                    ));
                })
                .register();
    }

    /**
     * Update Shadow Spell Power attribute modifier scaled by ramping multiplier.
     */
    public static void updateRampedSpellPower(ServerPlayer player, double rampMultiplier) {
        double baseBonus = OriginManager.getOriginStat(player, "bloodPactSpellPowerBonus");
        double totalBonus = baseBonus * rampMultiplier;

        ResourceLocation spellPowerAttrId = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power");
        Attribute spellPowerAttr = ForgeRegistries.ATTRIBUTES.getValue(spellPowerAttrId);

        if (spellPowerAttr != null) {
            var attributeInstance = player.getAttributes().getInstance(spellPowerAttr);
            if (attributeInstance != null) {
                attributeInstance.removeModifier(SPELL_POWER_UUID);
                if (totalBonus > 0.0) {
                    AttributeModifier modifier = new AttributeModifier(
                            SPELL_POWER_UUID,
                            "Blood Pact Spell Power",
                            totalBonus,
                            AttributeModifier.Operation.ADDITION
                    );
                    attributeInstance.addTransientModifier(modifier);
                }
            }
        }
    }

    public static void removeSpellPowerBonus(ServerPlayer player) {
        ResourceLocation spellPowerAttrId = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power");
        Attribute spellPowerAttr = ForgeRegistries.ATTRIBUTES.getValue(spellPowerAttrId);

        if (spellPowerAttr != null) {
            var attributeInstance = player.getAttributes().getInstance(spellPowerAttr);
            if (attributeInstance != null) {
                attributeInstance.removeModifier(SPELL_POWER_UUID);
            }
        }
    }
}
