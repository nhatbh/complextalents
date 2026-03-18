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

public class WeaponMasteryEventHandler {

    @Mod.EventBusSubscriber(modid = TalentsMod.MODID)
    public static class ServerEvents {
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
            if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide && event.player.tickCount % 20 == 0) {
                Player player = event.player;
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
                
                event.getToolTip().add(Component.empty());
                event.getToolTip().add(Component.literal("Weapon Mastery:")
                        .withStyle(ChatFormatting.GOLD));
                event.getToolTip().add(Component.literal(" Path: " + path.name())
                        .withStyle(ChatFormatting.GRAY));
                event.getToolTip().add(Component.literal(" Required Rank: " + rankName)
                        .withStyle(ChatFormatting.RED));
            }
        }

        private static String getRankNameFromLevel(int level) {
            if (level >= 20) return "Master";
            if (level >= 15) return "Expert";
            if (level >= 10) return "Adept";
            if (level >= 5) return "Apprentice";
            return "Novice";
        }
    }
}
