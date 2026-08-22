package com.complextalents.combatpower.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientNameFormatHelper {
    public static boolean isLocalPlayer(Player player) {
        return player == Minecraft.getInstance().player;
    }

    public static int getClientLevel() {
        return com.complextalents.leveling.client.ClientLevelingData.getLevel();
    }
}
