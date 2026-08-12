package com.complextalents.item;

import com.complextalents.TalentsMod;
import com.complextalents.caseopening.DynamicCasePoolBuilder;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, TalentsMod.MODID);

    public static final RegistryObject<CreativeModeTab> COMPLEX_TALENTS_TAB = CREATIVE_MODE_TABS.register(
            "complex_talents_tab",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> new ItemStack(ModItems.MYSTERIOUS_LOOT.get()))
                    .title(Component.translatable("creativetab.complextalents.tab"))
                    .displayItems((parameters, output) -> {
                        // Default Mysterious Loot Box
                        output.accept(new ItemStack(ModItems.MYSTERIOUS_LOOT.get()));
                        output.accept(new ItemStack(ModItems.REFINING_ANVIL.get()));

                        // Refinement Gems
                        output.accept(ModItems.NOVICE_WEAPON_GEM.get());
                        output.accept(ModItems.APPRENTICE_WEAPON_GEM.get());
                        output.accept(ModItems.ADEPT_WEAPON_GEM.get());
                        output.accept(ModItems.EXPERT_WEAPON_GEM.get());
                        output.accept(ModItems.MASTER_WEAPON_GEM.get());

                        // Magic Augment Gems
                        output.accept(ModItems.POWER_GEM.get());
                        output.accept(ModItems.MANA_SAVER_GEM.get());
                        output.accept(ModItems.HASTE_GEM.get());
                        output.accept(ModItems.SPEED_GEM.get());
                        output.accept(ModItems.PRECISION_GEM.get());
                        output.accept(ModItems.FATAL_GEM.get());
                        output.accept(ModItems.VAMPIRISM_GEM.get());
                        output.accept(ModItems.PIERCE_GEM.get());
                        output.accept(ModItems.OVERCLOCK_GEM.get());
                        output.accept(ModItems.RECAST_GEM.get());

                        // Populate Weapon Case NBT variants (All weapons case for all rarities, then specific path cases)
                        for (DynamicCasePoolBuilder.CrateRarity rarity : DynamicCasePoolBuilder.CrateRarity.values()) {
                            output.accept(MysteriousLootItem.createWeaponCase(null, rarity));
                        }
                        for (IWeaponMasteryData.WeaponPath path : IWeaponMasteryData.WeaponPath.values()) {
                            for (DynamicCasePoolBuilder.CrateRarity rarity : DynamicCasePoolBuilder.getValidRaritiesForWeaponPath(path)) {
                                output.accept(MysteriousLootItem.createWeaponCase(path, rarity));
                            }
                        }

                        // Populate Magic Case NBT variants (All magic case for all rarities, then specific school cases)
                        for (DynamicCasePoolBuilder.CrateRarity rarity : DynamicCasePoolBuilder.CrateRarity.values()) {
                            output.accept(MysteriousLootItem.createMagicCase(null, rarity));
                        }
                        try {
                            if (SchoolRegistry.REGISTRY != null && SchoolRegistry.REGISTRY.get() != null) {
                                for (SchoolType school : SchoolRegistry.REGISTRY.get().getValues()) {
                                    for (DynamicCasePoolBuilder.CrateRarity rarity : DynamicCasePoolBuilder.getValidRaritiesForSchool(school.getId())) {
                                        output.accept(MysteriousLootItem.createMagicCase(school.getId(), rarity));
                                    }
                                }
                            }
                        } catch (Exception ignored) {}

                        // Populate Gun Case NBT variants (All gun case for all rarities, then specific archetype cases)
                        for (DynamicCasePoolBuilder.CrateRarity rarity : DynamicCasePoolBuilder.CrateRarity.values()) {
                            output.accept(MysteriousLootItem.createGunCase(null, rarity));
                        }
                        for (com.complextalents.tacz.GunType gunType : com.complextalents.tacz.GunType.values()) {
                            for (DynamicCasePoolBuilder.CrateRarity rarity : DynamicCasePoolBuilder.getValidRaritiesForGunType(gunType)) {
                                output.accept(MysteriousLootItem.createGunCase(gunType, rarity));
                            }
                        }
                    }).build());

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
