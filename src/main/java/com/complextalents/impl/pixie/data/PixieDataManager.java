package com.complextalents.impl.pixie.data;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PixieDataManager {

    private static final Map<UUID, PixieData> DATA_MAP = new ConcurrentHashMap<>();

    public static PixieData get(Player player) {
        return get(player.getUUID());
    }

    public static PixieData get(UUID uuid) {
        return DATA_MAP.computeIfAbsent(uuid, k -> new PixieData());
    }

    public static void remove(UUID uuid) {
        DATA_MAP.remove(uuid);
    }

    public static Map<UUID, PixieData> getAllData() {
        return DATA_MAP;
    }
}
