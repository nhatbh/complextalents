package com.complextalents.item;

import com.complextalents.TalentsMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TalentsMod.MODID);

    public static final RegistryObject<Item> MYSTERIOUS_LOOT = ITEMS.register("mysterious_loot",
            () -> new MysteriousLootItem(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> NOVICE_WEAPON_GEM = ITEMS.register("novice_weapon_gem",
            () -> new RefinementGemItem(new Item.Properties().stacksTo(64), com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity.COMMON));

    public static final RegistryObject<Item> APPRENTICE_WEAPON_GEM = ITEMS.register("apprentice_weapon_gem",
            () -> new RefinementGemItem(new Item.Properties().stacksTo(64), com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity.UNCOMMON));

    public static final RegistryObject<Item> ADEPT_WEAPON_GEM = ITEMS.register("adept_weapon_gem",
            () -> new RefinementGemItem(new Item.Properties().stacksTo(64), com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity.RARE));

    public static final RegistryObject<Item> EXPERT_WEAPON_GEM = ITEMS.register("expert_weapon_gem",
            () -> new RefinementGemItem(new Item.Properties().stacksTo(64), com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity.EPIC));

    public static final RegistryObject<Item> MASTER_WEAPON_GEM = ITEMS.register("master_weapon_gem",
            () -> new RefinementGemItem(new Item.Properties().stacksTo(64), com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity.LEGENDARY));

    public static final RegistryObject<Item> REFINING_ANVIL = ITEMS.register("refining_anvil",
            () -> new net.minecraft.world.item.BlockItem(com.complextalents.block.ModBlocks.REFINING_ANVIL.get(), new Item.Properties()));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
