package com.complextalents.origin.network;

import com.complextalents.leveling.data.PlayerLevelingData;
import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginManager;
import com.complextalents.skill.capability.IPlayerSkillData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpgradeOriginSkillPacket {

    public UpgradeOriginSkillPacket() {
    }

    public UpgradeOriginSkillPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public static int getCostForNextLevel(int currentLevel) {
        if (currentLevel == 0) return 5;
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
                Origin origin = OriginManager.getOrigin(player);
                if (origin == null || origin.getActiveSkillId() == null) return;
                ResourceLocation skillId = origin.getActiveSkillId();
                
                IPlayerSkillData skillData = player.getCapability(com.complextalents.skill.capability.SkillDataProvider.SKILL_DATA).orElse(null);
                if (skillData == null) return;

                boolean isAssigned = false;
                for (int i = 0; i < IPlayerSkillData.SLOT_COUNT; i++) {
                    if (skillId.equals(skillData.getSkillInSlot(i))) {
                        isAssigned = true;
                        break;
                    }
                }

                int currentLevel = isAssigned ? skillData.getSkillLevel(skillId) : 0;
                if (currentLevel >= 5) return;

                int cost = getCostForNextLevel(currentLevel);
                if (cost <= 0) return;

                PlayerLevelingData levelingData = PlayerLevelingData.get(player.serverLevel());
                int availableSp = levelingData.getAvailableSkillPoints(player.getUUID());

                if (availableSp >= cost) {
                    levelingData.setConsumedSkillPoints(player.getUUID(), levelingData.getConsumedSkillPoints(player.getUUID()) + cost);
                    
                    if (!isAssigned) {
                        skillData.setSkillInSlot(0, skillId);
                    }
                    skillData.setSkillLevel(skillId, currentLevel + 1);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A7aOrigin skill upgraded to level " + (currentLevel + 1) + "!"));
                }
            }
        });
        return true;
    }
}
