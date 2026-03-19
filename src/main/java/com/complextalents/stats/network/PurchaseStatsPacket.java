package com.complextalents.stats.network;

import com.complextalents.origin.capability.OriginDataProvider;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import com.complextalents.stats.capability.GeneralStatsDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PurchaseStatsPacket {
    private final Map<String, Integer> purchases;

    public PurchaseStatsPacket(Map<String, Integer> purchases) {
        this.purchases = purchases;
    }

    public PurchaseStatsPacket(FriendlyByteBuf buffer) {
        this.purchases = new HashMap<>();
        int size = buffer.readInt();
        for (int i = 0; i < size; i++) {
            purchases.put(buffer.readUtf(), buffer.readInt());
        }
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeInt(purchases.size());
        for (Map.Entry<String, Integer> entry : purchases.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeInt(entry.getValue());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(GeneralStatsDataProvider.STATS_DATA).ifPresent(stats -> {
                player.getCapability(OriginDataProvider.ORIGIN_DATA).ifPresent(originData -> {
                    ResourceLocation originId = originData.getActiveOrigin();
                    
                    int totalCost = 0;
                    Map<StatType, Integer> validatedPurchases = new HashMap<>();

                    for (Map.Entry<String, Integer> entry : purchases.entrySet()) {
                        try {
                            StatType type = StatType.valueOf(entry.getKey());
                            int amount = entry.getValue();
                            if (amount <= 0) continue;

                            int costPerRank = ClassCostMatrix.getCost(originId, type);
                            totalCost += costPerRank * amount;
                            validatedPurchases.put(type, amount);
                            
                        } catch (IllegalArgumentException e) {
                            // Invalid stat type, ignore
                        }
                    }

                    if (totalCost > 0 && stats.getSkillPoints() >= totalCost) {
                        stats.setSkillPoints(stats.getSkillPoints() - totalCost);
                        
                        for (Map.Entry<StatType, Integer> entry : validatedPurchases.entrySet()) {
                            StatType type = entry.getKey();
                            int currentRank = stats.getStatRank(type);
                            stats.setStatRank(type, currentRank + entry.getValue());
                        }
                        
                        stats.sync();
                    }
                });
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
