package com.complextalents.origin.network;

import com.complextalents.origin.OriginManager;
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
                // Determine if the player is selecting their origin for the first time
                boolean isFirstSelection = !OriginManager.hasOrigin(player);

                OriginManager.setOrigin(player, this.originId);

                // Award 10 SP if it's their first time selecting an origin
                if (isFirstSelection) {
                    com.complextalents.leveling.data.PlayerLevelingData levelingData = com.complextalents.leveling.data.PlayerLevelingData.get(player.serverLevel());
                    levelingData.addSkillPoints(player.getUUID(), 10);
                    
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aYou have been awarded 10 Skill Points!").withStyle(net.minecraft.ChatFormatting.GREEN));
                }
            }
        });
        return true;
    }
}
