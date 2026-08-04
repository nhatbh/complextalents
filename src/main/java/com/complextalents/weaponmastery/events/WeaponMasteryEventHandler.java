package com.complextalents.weaponmastery.events;

import com.complextalents.TalentsMod;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class WeaponMasteryEventHandler {

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            if (player.level().isClientSide) return;

            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.isEmpty()) return;

            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(mainHandItem.getItem());
            if (itemId == null) return;

            IWeaponMasteryData.WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(itemId);
            if (path != null) {
                // Accumulate damage, capped at current health of target
                double actualDamage = Math.min(event.getAmount(), event.getEntity().getHealth());
                if (actualDamage <= 0) return;
                
                player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
                    data.addAccumulatedDamage(path, actualDamage);
                });
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;

        // Weapon Rank Level Requirement Check (every 20 ticks)
        if (player.tickCount % 20 == 0) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (!mainHandItem.isEmpty()) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(mainHandItem.getItem());
                if (itemId != null) {
                    IWeaponMasteryData.WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(itemId);
                    if (path != null) {
                        int requiredRankLevel = WeaponMasteryManager.getInstance().getRequiredRankValue(itemId);

                        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
                            if (data.getMasteryLevel(path) < requiredRankLevel) {
                                // Player lacks mastery level to wield this weapon effectively
                                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, false, true));
                                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 4, false, false, true));
                                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 4, false, false, true));
                            }
                        });
                    }
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            ItemStack itemStack = event.getItemStack();
            if (itemStack.isEmpty()) return;

            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
            if (itemId == null) return;

            IWeaponMasteryData.WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(itemId);
            if (path != null) {
                int requiredRankLevel = WeaponMasteryManager.getInstance().getRequiredRankValue(itemId);
                String rankName = getRankNameFromLevel(requiredRankLevel);
                String rankColor = getRankColor(requiredRankLevel);
                String symbol = getRankSymbol(requiredRankLevel);

                // Fetch player current level on client
                int playerLevel = 0;
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    var cap = mc.player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA);
                    if (cap.isPresent()) {
                        playerLevel = cap.orElseThrow(IllegalStateException::new).getMasteryLevel(path);
                    }
                }

                event.getToolTip().add(Component.empty());
                // Header: Symbol + Path
                event.getToolTip().add(Component.literal(symbol + " \u00A7b\u00A7lWeapon Mastery: \u00A7f" + path.getDisplayName()));

                // Requirement: Symbol + Rank Name + Level
                event.getToolTip().add(Component.literal("  " + symbol + " \u00A77Required: " + rankColor + "[" + rankName + "]" + " \u00A77(L." + (requiredRankLevel + 1) + ")"));

                // Player Wield Status
                if (playerLevel >= requiredRankLevel) {
                    event.getToolTip().add(Component.literal("  \u00A7a✔ Mastery Unlocked \u00A77(L." + playerLevel + "/15)"));
                } else {
                    event.getToolTip().add(Component.literal("  \u00A7c✖ Requires Mastery \u00A77(Your Lvl: L." + playerLevel + "/15)"));
                }
            }
        }

        private static String getRankNameFromLevel(int level) {
            if (level >= 14) return "Master";
            if (level >= 9) return "Expert";
            if (level >= 5) return "Adept";
            if (level >= 2) return "Apprentice";
            return "Novice";
        }

        private static String getRankColor(int level) {
            if (level >= 14) return "\u00A76"; // Gold
            if (level >= 9) return "\u00A75";  // Purple
            if (level >= 5) return "\u00A79";  // Indigo
            if (level >= 2) return "\u00A7a";  // Emerald Green
            return "\u00A7f";                 // White
        }

        private static String getRankSymbol(int level) {
            if (level >= 14) return "\u00A76\u00A7l⚜\u00A7r";
            if (level >= 9) return "\u00A75❂\u00A7r";
            if (level >= 5) return "\u00A79❖\u00A7r";
            if (level >= 2) return "\u00A7a✦\u00A7r";
            return "\u00A7f✧\u00A7r";
        }
    }
}
