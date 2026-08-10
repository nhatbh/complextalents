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

    // Magic Augment Gems
    public static final RegistryObject<MagicAugmentItem> POWER_GEM = ITEMS.register("power_gem",
            () -> new MagicAugmentItem(new Item.Properties().stacksTo(64), MagicAugmentItem.AugmentType.POWER));

    public static final RegistryObject<MagicAugmentItem> MANA_SAVER_GEM = ITEMS.register("mana_saver_gem",
            () -> new MagicAugmentItem(new Item.Properties().stacksTo(64), MagicAugmentItem.AugmentType.MANA_SAVER));

    public static final RegistryObject<MagicAugmentItem> HASTE_GEM = ITEMS.register("haste_gem",
            () -> new MagicAugmentItem(new Item.Properties().stacksTo(64), MagicAugmentItem.AugmentType.HASTE));

    public static final RegistryObject<MagicAugmentItem> SPEED_GEM = ITEMS.register("speed_gem",
            () -> new MagicAugmentItem(new Item.Properties().stacksTo(64), MagicAugmentItem.AugmentType.SPEED));

    public static final RegistryObject<MagicAugmentItem> PRECISION_GEM = ITEMS.register("precision_gem",
            () -> new MagicAugmentItem(new Item.Properties().stacksTo(64), MagicAugmentItem.AugmentType.PRECISION));

    public static final RegistryObject<MagicAugmentItem> FATAL_GEM = ITEMS.register("fatal_gem",
            () -> new MagicAugmentItem(new Item.Properties().stacksTo(64), MagicAugmentItem.AugmentType.FATAL));

    public static final RegistryObject<MagicAugmentItem> VAMPIRISM_GEM = ITEMS.register("vampirism_gem",
            () -> new MagicAugmentItem(new Item.Properties().stacksTo(64), MagicAugmentItem.AugmentType.VAMPIRISM));

    public static final RegistryObject<MagicAugmentItem> PIERCE_GEM = ITEMS.register("pierce_gem",
            () -> new MagicAugmentItem(new Item.Properties().stacksTo(64), MagicAugmentItem.AugmentType.PIERCE));

    public static final RegistryObject<MagicAugmentItem> OVERCLOCK_GEM = ITEMS.register("overclock_gem",
            () -> new MagicAugmentItem(new Item.Properties().stacksTo(64), MagicAugmentItem.AugmentType.OVERCLOCK));

    public static final RegistryObject<MagicAugmentItem> RECAST_GEM = ITEMS.register("recast_gem",
            () -> new MagicAugmentItem(new Item.Properties().stacksTo(64), MagicAugmentItem.AugmentType.RECAST));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
