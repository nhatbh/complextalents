package com.complextalents.spellmastery.network;

import com.complextalents.leveling.data.PlayerLevelingData;
import com.complextalents.spellmastery.SpellMasteryManager;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PurchaseMasteryPacket {
    private final ResourceLocation schoolId;
    private final int targetTier;

    public PurchaseMasteryPacket(ResourceLocation schoolId, int targetTier) {
        this.schoolId = schoolId;
        this.targetTier = targetTier;
    }

    public PurchaseMasteryPacket(FriendlyByteBuf buffer) {
        this.schoolId = buffer.readResourceLocation();
        this.targetTier = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(schoolId);
        buffer.writeInt(targetTier);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(mastery -> {
                int currentMastery = mastery.getMasteryLevel(schoolId);
                if (targetTier != currentMastery + 1) return; // Can only buy next tier

                int cost = SpellMasteryManager.getMasteryBuyUpCost(targetTier);
                PlayerLevelingData levelingData = PlayerLevelingData.get(player.serverLevel());
                int availableSP = levelingData.getAvailableSkillPoints(player.getUUID());

                if (availableSP >= cost) {
                    levelingData.setConsumedSkillPoints(player.getUUID(), levelingData.getConsumedSkillPoints(player.getUUID()) + cost);
                    mastery.purchaseMastery(schoolId, targetTier, cost);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
