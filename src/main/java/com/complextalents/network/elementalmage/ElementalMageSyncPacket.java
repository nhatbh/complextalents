package com.complextalents.network.elementalmage;

import com.complextalents.elemental.ElementType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet to sync Elemental Mage Prismatic Echoes and Convergence status from server to client.
 */
public class ElementalMageSyncPacket {

    private final List<ElementType> echoes;
    private final ElementType apexElement;
    private final float lockedHarmonyMultiplier;
    private final float convergenceCritChance;
    private final float convergenceCritDamage;

    public ElementalMageSyncPacket(List<ElementType> echoes, ElementType apexElement,
                                   float lockedHarmonyMultiplier, float critChance, float critDamage) {
        this.echoes = echoes != null ? echoes : new ArrayList<>();
        this.apexElement = apexElement;
        this.lockedHarmonyMultiplier = lockedHarmonyMultiplier;
        this.convergenceCritChance = critChance;
        this.convergenceCritDamage = critDamage;
    }

    public static void encode(ElementalMageSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.echoes.size());
        for (ElementType type : msg.echoes) {
            buf.writeEnum(type);
        }

        buf.writeBoolean(msg.apexElement != null);
        if (msg.apexElement != null) {
            buf.writeEnum(msg.apexElement);
        }

        buf.writeFloat(msg.lockedHarmonyMultiplier);
        buf.writeFloat(msg.convergenceCritChance);
        buf.writeFloat(msg.convergenceCritDamage);
    }

    public static ElementalMageSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<ElementType> decodedEchoes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            decodedEchoes.add(buf.readEnum(ElementType.class));
        }

        boolean hasApex = buf.readBoolean();
        ElementType apex = hasApex ? buf.readEnum(ElementType.class) : null;

        float lockedMult = buf.readFloat();
        float critChance = buf.readFloat();
        float critDamage = buf.readFloat();

        return new ElementalMageSyncPacket(decodedEchoes, apex, lockedMult, critChance, critDamage);
    }

    public static void handle(ElementalMageSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            @SuppressWarnings("resource")
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return;

            player.getCapability(com.complextalents.impl.elementalmage.ElementalMageDataProvider.ELEMENTAL_DATA).ifPresent(cap -> {
                cap.setEchoes(msg.echoes);
                cap.setApexElement(msg.apexElement);
                cap.setLockedHarmonyMultiplier(msg.lockedHarmonyMultiplier);
                cap.setConvergenceCritChance(msg.convergenceCritChance);
                cap.setConvergenceCritDamage(msg.convergenceCritDamage);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
