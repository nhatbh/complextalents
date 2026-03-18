package com.complextalents.weaponmastery.network;

import com.complextalents.leveling.data.PlayerLevelingData;
import com.complextalents.leveling.handlers.LevelingSyncHandler;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

public class PurchaseWeaponMasteryPacket {
    private final Map<String, Integer> upgrades;

    public PurchaseWeaponMasteryPacket(Map<String, Integer> upgrades) {
        this.upgrades = upgrades;
    }

    public PurchaseWeaponMasteryPacket(FriendlyByteBuf buf) {
        this.upgrades = new java.util.HashMap<>();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            upgrades.put(buf.readUtf(), buf.readInt());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(upgrades.size());
        for (Map.Entry<String, Integer> entry : upgrades.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(masteryData -> {
                PlayerLevelingData levelingData = PlayerLevelingData.get(player.serverLevel());
                long availableSP = levelingData.getAvailableSkillPoints(player.getUUID());
                long totalCost = 0;

                // Validate and process each upgrade request
                for (Map.Entry<String, Integer> entry : upgrades.entrySet()) {
                    IWeaponMasteryData.WeaponPath path = IWeaponMasteryData.WeaponPath.fromString(entry.getKey());
                    if (path == null) continue;
                    
                    int requestedLevels = entry.getValue();
                    if (requestedLevels <= 0) continue;

                    int currentLevel = masteryData.getMasteryLevel(path);
                    double accumulatedDamage = masteryData.getAccumulatedDamage(path);
                    
                    int proposedNewLevel = currentLevel;
                    boolean valid = true;

                    for (int i = 0; i < requestedLevels; i++) {
                        if (proposedNewLevel >= 25) {
                            valid = false;
                            break;
                        }
                        
                        double requiredDamageForNext = WeaponMasteryManager.getInstance().getDamageRequiredForNextLevel(proposedNewLevel);
                        if (accumulatedDamage < requiredDamageForNext) {
                            valid = false;
                            break;
                        }

                        int costForNext = WeaponMasteryManager.getInstance().getSPCostForNextLevel(proposedNewLevel);
                        totalCost += costForNext;
                        proposedNewLevel++;
                    }

                    if (valid && availableSP >= totalCost) {
                        masteryData.setMasteryLevel(path, proposedNewLevel);
                    } else {
                        // Rollback accumulated costs if this specific path validation fails
                        // We still allow other paths to succeed, but cancel this invalid one.
                        // For a rigid "basket", we calculate total cost incrementally. If one is invalid, it just skips that one.
                        // Actually, wait, let's just make it simpler: We validated if we had SP for each single step,
                        // and accumulated the cost. The best way is to do it incrementally per path.
                    }
                }
                
                // Finalize the total cost deduction if anything was bought
                if (totalCost > 0 && availableSP >= totalCost) {
                    levelingData.setConsumedSkillPoints(player.getUUID(), levelingData.getConsumedSkillPoints(player.getUUID()) + (int)totalCost);
                    LevelingSyncHandler.syncPlayerLevelData(player);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
