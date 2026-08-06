package com.complextalents.stats;

import com.complextalents.util.UUIDHelper;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Defines the core stats in the General Stats system.
 */
public enum StatType {
    FLAT_AD("Flat Attack Damage", 0.5, () -> Attributes.ATTACK_DAMAGE),
    PERCENT_AD("% Attack Damage", 0.03, () -> Attributes.ATTACK_DAMAGE),
    AP("Ability Power (AP)", 0.05,
            () -> ForgeRegistries.ATTRIBUTES
                    .getValue(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power"))),
    ARMOR_PEN("Armor Penetration", 0.5,
            () -> ForgeRegistries.ATTRIBUTES
                    .getValue(ResourceLocation.fromNamespaceAndPath("attributeslib", "armor_pierce"))),
    LUCK_CRIT("Luck & Crit", 1.0, () -> Attributes.LUCK), // Multi-attribute: handled specifically
    MAX_HP("Max HP", 2.0, () -> Attributes.MAX_HEALTH),
    MAX_MANA("Max Mana", 50.0,
            () -> ForgeRegistries.ATTRIBUTES
                    .getValue(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "max_mana"))),
    HEAL_AND_SHIELD("Heal & Shield", 0.10, () -> com.complextalents.registry.ModAttributes.HEAL_AND_SHIELD_POWER.get()),
    CDR("Ability Haste", 5.0, () -> ForgeRegistries.ATTRIBUTES
            .getValue(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "cooldown_reduction")));

    private final String displayName;
    private final double yieldPerRank;
    private final Supplier<Attribute> attributeSupplier;
    private final UUID modifierUuid;

    StatType(String displayName, double yieldPerRank, Supplier<Attribute> attributeSupplier) {
        this.displayName = displayName;
        this.yieldPerRank = yieldPerRank;
        this.attributeSupplier = attributeSupplier;
        this.modifierUuid = UUIDHelper.generateAttributeModifierUUID("stats", this.name().toLowerCase());
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getYieldPerRank() {
        return yieldPerRank;
    }

    public Attribute getAttribute() {
        return attributeSupplier.get();
    }

    public UUID getModifierUuid() {
        return modifierUuid;
    }
}
