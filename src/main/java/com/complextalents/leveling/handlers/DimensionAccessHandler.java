package com.complextalents.leveling.handlers;

import com.complextalents.TalentsMod;
import com.complextalents.leveling.data.PlayerLevelingData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Enforces level requirements for dimension travel:
 * - Nether: Requires Level 30+
 * - The End: Requires Level 50+
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class DimensionAccessHandler {

    public static final int NETHER_REQUIRED_LEVEL = 30;
    public static final int END_REQUIRED_LEVEL = 50;

    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Bypass for Creative or Spectator mode
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        ResourceKey<Level> targetDimension = event.getDimension();
        if (player.getServer() == null) return;

        PlayerLevelingData levelingData = PlayerLevelingData.get(player.getServer());
        int currentLevel = levelingData.getLevel(player.getUUID());

        if (targetDimension.equals(Level.NETHER)) {
            if (currentLevel < NETHER_REQUIRED_LEVEL) {
                event.setCanceled(true);
                player.setPortalCooldown(40); // Prevent portal message spam for 2 seconds
                player.sendSystemMessage(Component.literal("\u00A7c[Level Requirement] You must be Level " 
                        + NETHER_REQUIRED_LEVEL + " or higher to enter the Nether! (Current: Level " + currentLevel + ")")
                        .withStyle(ChatFormatting.RED));
            }
        } else if (targetDimension.equals(Level.END)) {
            if (currentLevel < END_REQUIRED_LEVEL) {
                event.setCanceled(true);
                player.setPortalCooldown(40); // Prevent portal message spam for 2 seconds
                player.sendSystemMessage(Component.literal("\u00A7c[Level Requirement] You must be Level " 
                        + END_REQUIRED_LEVEL + " or higher to enter The End! (Current: Level " + currentLevel + ")")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }
}
