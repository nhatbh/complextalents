package com.complextalents.leveling.handlers;

import com.complextalents.TalentsMod;
import com.complextalents.leveling.events.level.PlayerLevelUpEvent;
import com.complextalents.leveling.service.LevelingService;
import com.complextalents.util.UUIDHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Automatically applies passive Armor and Armor Toughness to players based on their ComplexTalents level.
 * 
 * <p>This counteracts the severe diminishing returns of Apotheosis armor scaling when fighting
 * high-level mobs whose attack damage scales linearly/exponentially with distance.</p>
 * 
 * <ul>
 *   <li><b>Armor Bonus:</b> +1.2 Armor per level above 1.</li>
 *   <li><b>Armor Toughness Bonus:</b> +0.3 Toughness per level above 1 (Capped at 30.0 for 60% armor shred resistance).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class LevelArmorHandler {

    private static final UUID LEVEL_ARMOR_UUID = UUIDHelper.generateAttributeModifierUUID("leveling", "player_level_armor");
    private static final UUID LEVEL_TOUGHNESS_UUID = UUIDHelper.generateAttributeModifierUUID("leveling", "player_level_toughness");
    private static final UUID LEVEL_HEALTH_UUID = UUIDHelper.generateAttributeModifierUUID("leveling", "player_level_health");
    private static final UUID MARKSMAN_HEALTH_PENALTY_UUID = UUIDHelper.generateAttributeModifierUUID("origin", "marksman_health_penalty");
    private static final UUID MARKSMAN_AMMO_YIELD_UUID = UUIDHelper.generateAttributeModifierUUID("origin", "marksman_ammo_crafting_yield");

    private static final double ARMOR_PER_LEVEL = 1.2;
    private static final double TOUGHNESS_PER_LEVEL = 0.3;
    private static final double MAX_TOUGHNESS_BONUS = 30.0;

    /**
     * Updates player armor when leveling up.
     */
    @SubscribeEvent
    public static void onPlayerLevelUp(PlayerLevelUpEvent event) {
        updatePlayerLevelArmor(event.getPlayer());
    }

    /**
     * Updates player armor when joining the level.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.getLevel().isClientSide) {
            updatePlayerLevelArmor(player);
        }
    }

    /**
     * Updates player armor on respawn.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            updatePlayerLevelArmor(player);
        }
    }

    /**
     * Updates player armor on login.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            updatePlayerLevelArmor(player);
        }
    }

    /**
     * Updates player armor on origin change.
     */
    @SubscribeEvent
    public static void onOriginChange(com.complextalents.origin.events.OriginChangeEvent event) {
        if (event.getPlayer() != null) {
            updatePlayerLevelArmor(event.getPlayer());
        }
    }

    /**
     * Recalculates and applies the level-based Armor and Armor Toughness modifiers for the player based on their origin.
     *
     * @param player The ServerPlayer whose attributes should be updated
     */
    public static void updatePlayerLevelArmor(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return;

        int level = LevelingService.getInstance().getLevel(player);

        net.minecraft.resources.ResourceLocation originId = com.complextalents.origin.OriginManager.getOriginId(player);
        com.complextalents.origin.Origin origin = originId != null ? com.complextalents.origin.OriginRegistry.getInstance().getOrigin(originId) : null;

        double armorAmount = Math.round(origin != null ? origin.getLevelArmorBonus(level, player) : (level <= 1 ? 0.0 : Math.pow(level - 1, 1.15) * 1.0));
        double toughnessAmount = origin != null ? origin.getLevelToughnessBonus(level) : (level <= 1 ? 0.0 : Math.min(35.0, Math.pow(level - 1, 1.1) * 0.25));
        double healthAmount = Math.round(origin != null ? origin.getLevelHealthBonus(level) : (level <= 1 ? 0.0 : Math.pow(level - 1, 1.15) * 0.2696));

        applyOrUpdateModifier(player, Attributes.ARMOR, LEVEL_ARMOR_UUID, "Level Armor Bonus", armorAmount);
        applyOrUpdateModifier(player, Attributes.ARMOR_TOUGHNESS, LEVEL_TOUGHNESS_UUID, "Level Toughness Bonus", toughnessAmount);
        applyOrUpdateModifier(player, Attributes.MAX_HEALTH, LEVEL_HEALTH_UUID, "Level Max Health Bonus", healthAmount);

        // Apply 50% Max HP total reduction & Ammo Crafting Yield scaling for Marksman origin
        if (com.complextalents.impl.marksman.origin.MarksmanOrigin.ID.equals(originId)) {
            applyOrUpdateModifier(player, Attributes.MAX_HEALTH, MARKSMAN_HEALTH_PENALTY_UUID, "Marksman Health Penalty", -0.50, AttributeModifier.Operation.MULTIPLY_TOTAL);
            int originLevel = com.complextalents.origin.OriginManager.getOriginLevel(player);
            double yieldBonus = origin != null ? origin.getScaledStat("ammoCraftingYield", originLevel) : (0.20 * originLevel);
            applyOrUpdateModifier(player, com.complextalents.registry.ModAttributes.AMMO_CRAFTING_YIELD.get(), MARKSMAN_AMMO_YIELD_UUID, "Marksman Ammo Yield Bonus", yieldBonus, AttributeModifier.Operation.ADDITION);
        } else {
            removeAttributeModifier(player, Attributes.MAX_HEALTH, MARKSMAN_HEALTH_PENALTY_UUID);
            removeAttributeModifier(player, com.complextalents.registry.ModAttributes.AMMO_CRAFTING_YIELD.get(), MARKSMAN_AMMO_YIELD_UUID);
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * Removes level armor, toughness, and max health modifiers from the player.
     */
    public static void removeModifiers(Player player) {
        if (player == null) return;
        removeAttributeModifier(player, Attributes.ARMOR, LEVEL_ARMOR_UUID);
        removeAttributeModifier(player, Attributes.ARMOR_TOUGHNESS, LEVEL_TOUGHNESS_UUID);
        removeAttributeModifier(player, Attributes.MAX_HEALTH, LEVEL_HEALTH_UUID);
        removeAttributeModifier(player, Attributes.MAX_HEALTH, MARKSMAN_HEALTH_PENALTY_UUID);
        removeAttributeModifier(player, com.complextalents.registry.ModAttributes.AMMO_CRAFTING_YIELD.get(), MARKSMAN_AMMO_YIELD_UUID);
    }

    private static void applyOrUpdateModifier(Player player, Attribute attribute, UUID uuid, String name, double amount) {
        applyOrUpdateModifier(player, attribute, uuid, name, amount, AttributeModifier.Operation.ADDITION);
    }

    private static void applyOrUpdateModifier(Player player, Attribute attribute, UUID uuid, String name, double amount, AttributeModifier.Operation operation) {
        if (attribute == null) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        instance.removeModifier(uuid);
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
        }
    }

    private static void removeAttributeModifier(Player player, Attribute attribute, UUID uuid) {
        if (attribute == null) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(uuid);
        }
    }
}
