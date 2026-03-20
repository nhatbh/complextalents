package com.complextalents.network;

import net.minecraft.resources.ResourceLocation;

/**
 * Common data structures for the upgrade system.
 */
public class UpgradeData {
    public static record MasteryUpgrade(ResourceLocation schoolId, int tier) {}
    public static record SpellPurchase(ResourceLocation spellId, int level) {}
}
