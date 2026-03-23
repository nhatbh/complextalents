package com.complextalents.network.elementalmage;

import com.complextalents.elemental.ElementType;
import com.complextalents.impl.elementalmage.ElementalMageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Packet to sync Elemental Mage attribute stats from server to client.
 */
public class ElementalMageSyncPacket {
    
    private final Map<ElementType, Float> stats;

    public ElementalMageSyncPacket(Map<ElementType, Float> stats) {
        this.stats = stats;
    }

    public static void encode(ElementalMageSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.stats.size());
        for (Map.Entry<ElementType, Float> entry : msg.stats.entrySet()) {
            buf.writeEnum(entry.getKey());
            buf.writeFloat(entry.getValue());
        }
    }

    public static ElementalMageSyncPacket decode(FriendlyByteBuf buf) {
        Map<ElementType, Float> decodedStats = new HashMap<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            ElementType element = buf.readEnum(ElementType.class);
            float value = buf.readFloat();
            decodedStats.put(element, value);
        }
        return new ElementalMageSyncPacket(decodedStats);
    }

    public static void handle(ElementalMageSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Check if we are on the client thread and have a player
            @SuppressWarnings("resource")
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return;
            
            // Update stats on the client version of ElementalMageData
            for (Map.Entry<ElementType, Float> entry : msg.stats.entrySet()) {
                ElementalMageData.setStat(player.getUUID(), entry.getKey(), entry.getValue());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
