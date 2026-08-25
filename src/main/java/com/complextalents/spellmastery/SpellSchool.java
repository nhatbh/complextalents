package com.complextalents.spellmastery;

import net.minecraft.resources.ResourceLocation;

/**
 * Enumeration of the 10 magic schools supported for cost multipliers and mastery calculations.
 */
public enum SpellSchool {
    // Elemental Schools (Primal Cycle)
    FIRE("irons_spellbooks", "fire"),
    ICE("irons_spellbooks", "ice"),
    LIGHTNING("irons_spellbooks", "lightning"),
    NATURE("irons_spellbooks", "nature"),
    AQUA("traveloptics", "aqua"),

    // Arcane Schools (Unique Mechanics)
    HOLY("irons_spellbooks", "holy"),
    EVOCATION("irons_spellbooks", "evocation"),
    ENDER("irons_spellbooks", "ender"),
    ELDRITCH("irons_spellbooks", "eldritch"),
    BLOOD("irons_spellbooks", "blood"),
    ABYSSAL("irons_spellbooks", "abyssal"),
    TECHNOMANCY("irons_spellbooks", "technomancy");

    private final ResourceLocation location;

    SpellSchool(String namespace, String path) {
        this.location = ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public ResourceLocation getLocation() {
        return location;
    }

    public String getPath() {
        return location.getPath();
    }

    public static SpellSchool fromString(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String cleanName = name.trim().toUpperCase();
        for (SpellSchool school : values()) {
            if (school.name().equals(cleanName) || school.getPath().equalsIgnoreCase(cleanName)) {
                return school;
            }
        }
        return null;
    }

    public static SpellSchool fromResourceLocation(ResourceLocation loc) {
        if (loc == null) return null;
        for (SpellSchool school : values()) {
            if (school.location.equals(loc) || school.location.getPath().equalsIgnoreCase(loc.getPath())) {
                return school;
            }
        }
        return null;
    }
}
