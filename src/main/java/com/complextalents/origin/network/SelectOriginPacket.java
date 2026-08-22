package com.complextalents.origin.network;

import com.complextalents.origin.OriginManager;
import com.complextalents.leveling.handlers.LevelingSyncHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SelectOriginPacket {
    private final ResourceLocation originId;

    public SelectOriginPacket(ResourceLocation originId) {
        this.originId = originId;
    }

    public SelectOriginPacket(FriendlyByteBuf buf) {
        this.originId = buf.readResourceLocation();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.originId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                // Reject origin selection if the player already has an active origin
                if (OriginManager.hasOrigin(player)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou already have an active origin and cannot change it!").withStyle(net.minecraft.ChatFormatting.RED));
                    return;
                }

                OriginManager.setOrigin(player, this.originId);

                // Award 10 SP because it's their first time selecting an origin
                com.complextalents.leveling.data.PlayerLevelingData levelingData = com.complextalents.leveling.data.PlayerLevelingData.get(player.getServer());
                levelingData.addSkillPoints(player.getUUID(), 10);
                
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aYou have been awarded 10 Skill Points!").withStyle(net.minecraft.ChatFormatting.GREEN));
                
                // Sync the new SP to the client immediately
                LevelingSyncHandler.syncPlayerLevelData(player);
            }
        });
        return true;
    }
}
