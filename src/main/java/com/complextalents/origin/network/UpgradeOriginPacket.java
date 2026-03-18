package com.complextalents.origin.network;

import com.complextalents.leveling.data.PlayerLevelingData;
import com.complextalents.origin.OriginManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpgradeOriginPacket {

    public UpgradeOriginPacket() {
    }

    public UpgradeOriginPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public static int getCostForNextLevel(int currentLevel) {
        if (currentLevel == 1) return 10;
        if (currentLevel == 2) return 15;
        if (currentLevel == 3) return 20;
        if (currentLevel == 4) return 30;
        return -1; // Max level or invalid
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                int currentLevel = OriginManager.getOriginLevel(player);
                if (currentLevel >= 5) return; // Max level reached

                int cost = getCostForNextLevel(currentLevel);
                if (cost <= 0) return;

                PlayerLevelingData levelingData = PlayerLevelingData.get(player.serverLevel());
                int availableSp = levelingData.getAvailableSkillPoints(player.getUUID());

                if (availableSp >= cost) {
                    levelingData.setConsumedSkillPoints(player.getUUID(), levelingData.getConsumedSkillPoints(player.getUUID()) + cost);
                    OriginManager.setOriginLevel(player, currentLevel + 1);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A7aOrigin upgraded to level " + (currentLevel + 1) + "!"));
                }
            }
        });
        return true;
    }
}
