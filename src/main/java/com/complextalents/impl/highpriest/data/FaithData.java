package com.complextalents.impl.highpriest.data;

import com.complextalents.network.PacketHandler;
import com.complextalents.network.highpriest.FaithSyncPacket;
import com.complextalents.persistence.PlayerPersistentData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Server-side tracking for High Priest Faith stacks.
 * Wrapped around the IPlayerFaithData capability for robust persistence and sync.
 */
public class FaithData {

    public static double getFaith(Player player) {
        return player.getCapability(FaithDataProvider.FAITH_DATA)
                .map(IPlayerFaithData::getFaith)
                .orElse(0.0);
    }

    public static void setFaith(Player player, double faith) {
        player.getCapability(FaithDataProvider.FAITH_DATA).ifPresent(cap -> {
            cap.setFaith(faith);
        });
    }

    public static void addFaith(Player player, double amount) {
        player.getCapability(FaithDataProvider.FAITH_DATA).ifPresent(cap -> {
            cap.addFaith(amount);
        });
    }

    /**
     * Sync faith to client.
     */
    public static void syncToClient(ServerPlayer player) {
        double currentFaith = getFaith(player);
        PacketHandler.sendTo(new FaithSyncPacket(currentFaith), player);
    }

    // --- Legacy / Context-less helpers ---

    public static double getFaith(UUID playerUuid) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 0.0;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) return getFaith(player);
        
        return PlayerPersistentData.get(server).getFaithDataObj(playerUuid).getFaith();
    }

    public static void setFaith(UUID playerUuid, double faith) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            setFaith(player, faith);
        } else {
            var data = PlayerPersistentData.get(server);
            data.getFaithDataObj(playerUuid).setFaith(faith);
            data.setDirty();
        }
    }
}
