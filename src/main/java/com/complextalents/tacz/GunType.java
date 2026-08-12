package com.complextalents.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Represents TACZ weapon archetypes and global scope for attribute targeting.
 * Uses exact mapping to TACZ's native {@link CommonGunIndex#getType()} classification strings.
 */
public enum GunType {
    GLOBAL("", "Global"),
    PISTOL("pistol", "Pistol"),
    SNIPER("sniper", "Sniper"),
    RIFLE("rifle", "Rifle"),
    SHOTGUN("shotgun", "Shotgun"),
    SMG("smg", "SMG"),
    RPG("rpg", "RPG"),
    MG("mg", "Machine Gun");

    private static final Map<String, GunType> TYPE_REGISTRY = new HashMap<>();

    static {
        // Register default TACZ type mappings strictly by exact ID
        for (GunType type : values()) {
            if (!type.isGlobal()) {
                TYPE_REGISTRY.put(type.getId(), type);
            }
        }
        // Register standard clean aliases
        TYPE_REGISTRY.put("handgun", PISTOL);
        TYPE_REGISTRY.put("revolver", PISTOL);
        TYPE_REGISTRY.put("marksman", SNIPER);
        TYPE_REGISTRY.put("dmr", SNIPER);
        TYPE_REGISTRY.put("assault_rifle", RIFLE);
        TYPE_REGISTRY.put("submachine_gun", SMG);
        TYPE_REGISTRY.put("launcher", RPG);
        TYPE_REGISTRY.put("machine_gun", MG);
        TYPE_REGISTRY.put("lmg", MG);
    }

    private final String id;
    private final String displayName;

    GunType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isGlobal() {
        return this == GLOBAL;
    }

    /**
     * Dynamically registers a custom type alias mapping string to a GunType.
     */
    public static void registerTypeAlias(String rawId, GunType type) {
        if (rawId != null && !rawId.isBlank() && type != null) {
            TYPE_REGISTRY.put(rawId.toLowerCase(Locale.ROOT).trim(), type);
        }
    }

    /**
     * Resolves a GunType directly from an ItemStack using TACZ's native CommonGunIndex.
     */
    public static GunType fromItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return GLOBAL;
        }
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) {
            return GLOBAL;
        }
        Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(iGun.getGunId(stack));
        return index.map(gunIndex -> fromId(gunIndex.getType())).orElse(GLOBAL);
    }

    /**
     * Resolves a GunType directly from a TACZ gun ResourceLocation ID.
     */
    public static GunType fromGunId(ResourceLocation gunId) {
        if (gunId == null) {
            return GLOBAL;
        }
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(gunIndex -> fromId(gunIndex.getType()))
                .orElse(GLOBAL);
    }

    /**
     * Resolves a TACZ raw gun index type string to a GunType enum via exact registry lookup.
     */
    public static GunType fromId(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return GLOBAL;
        }
        String key = rawType.toLowerCase(Locale.ROOT).trim();
        return TYPE_REGISTRY.getOrDefault(key, GLOBAL);
    }
}
