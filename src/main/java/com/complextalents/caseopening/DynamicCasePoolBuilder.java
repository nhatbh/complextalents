package com.complextalents.caseopening;

import com.complextalents.elemental.ElementType;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.util.*;

/**
 * Dynamically builds weighted loot pools for Weapon Cases and Magic Cases.
 * Automatically hooks into Weapon Mastery (weapon_data.json) and Elemental/Spell Mastery.
 * Implements fallback weight redistribution for missing item tiers.
 */
public class DynamicCasePoolBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    public enum CrateRarity {
        COMMON("Common", 0xFFAAAAAA, new int[]{850, 90, 40, 15, 5}),
        UNCOMMON("Uncommon", 0xFF55FF55, new int[]{750, 140, 70, 30, 10}),
        RARE("Rare", 0xFF5555FF, new int[]{650, 160, 110, 60, 20}),
        EPIC("Epic", 0xFFAA00AA, new int[]{550, 180, 140, 90, 40}),
        LEGENDARY("Legendary", 0xFFFFAA00, new int[]{450, 170, 150, 130, 100});

        private final String displayName;
        private final int colorHex;
        private final int[] baseTierWeights; // Weights for [T1, T2, T3, T4, T5]

        CrateRarity(String displayName, int colorHex, int[] baseTierWeights) {
            this.displayName = displayName;
            this.colorHex = colorHex;
            this.baseTierWeights = baseTierWeights;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getColorHex() {
            return colorHex;
        }

        public int[] getBaseTierWeights() {
            return baseTierWeights.clone();
        }
    }

    /**
     * Builds a weighted CaseReward pool for a given WeaponPath and CrateRarity.
     */
    public static List<CaseReward> buildWeaponPool(WeaponPath path, CrateRarity crateRarity) {
        return buildWeaponPool(path, crateRarity, null);
    }

    /**
     * Builds a weighted CaseReward pool for a given WeaponPath and CrateRarity with explicit ResourceManager.
     */
    public static List<CaseReward> buildWeaponPool(WeaponPath path, CrateRarity crateRarity, ResourceManager resourceManager) {
        Map<Integer, List<Item>> tierMap = new HashMap<>();
        for (int t = 1; t <= 5; t++) {
            List<Item> weapons = new ArrayList<>(com.complextalents.weaponmastery.WeaponMasteryManager.getInstance().getWeaponsForPathAndTier(path, t));
            tierMap.put(t, weapons);
        }

        // Add Refinement Gems to their respective item tiers in every weapon case
        tierMap.get(1).add(com.complextalents.item.ModItems.NOVICE_WEAPON_GEM.get());
        tierMap.get(2).add(com.complextalents.item.ModItems.APPRENTICE_WEAPON_GEM.get());
        tierMap.get(3).add(com.complextalents.item.ModItems.ADEPT_WEAPON_GEM.get());
        tierMap.get(4).add(com.complextalents.item.ModItems.EXPERT_WEAPON_GEM.get());
        tierMap.get(5).add(com.complextalents.item.ModItems.MASTER_WEAPON_GEM.get());

        return buildPoolFromTierMap(tierMap, crateRarity, path.name());
    }

    /**
     * Determines which CrateRarity tiers are valid for a given magic school based on the actual spell rarities present.
     * Categories with no low tier items will not have low tier crate rarities.
     */
    public static List<CrateRarity> getValidRaritiesForSchool(ResourceLocation schoolId) {
        List<CrateRarity> validRarities = new ArrayList<>();
        Set<Integer> presentTiers = new HashSet<>();

        try {
            if (io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY != null 
                    && io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get() != null) {
                for (io.redspace.ironsspellbooks.api.spells.AbstractSpell spell : io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get().getValues()) {
                    if (spell == null || spell == io.redspace.ironsspellbooks.api.registry.SpellRegistry.none()) continue;

                    if (spell.getSchoolType() != null && spell.getSchoolType().getId().equals(schoolId)) {
                        for (int lvl = spell.getMinLevel(); lvl <= spell.getMaxLevel(); lvl++) {
                            io.redspace.ironsspellbooks.api.spells.SpellRarity rarity = spell.getRarity(lvl);
                            int tier = mapSpellRarityToTier(rarity);
                            presentTiers.add(tier);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        for (CrateRarity rarity : CrateRarity.values()) {
            int tier = rarity.ordinal() + 1; // 1=COMMON, 2=UNCOMMON, 3=RARE, 4=EPIC, 5=LEGENDARY
            if (presentTiers.contains(tier)) {
                validRarities.add(rarity);
            }
        }

        if (validRarities.isEmpty()) {
            validRarities.add(CrateRarity.COMMON);
        }

        return validRarities;
    }

    /**
     * Determines which CrateRarity tiers are valid for a given WeaponPath based on available weapon tier entries.
     */
    public static List<CrateRarity> getValidRaritiesForWeaponPath(WeaponPath path, ResourceManager resourceManager) {
        List<CrateRarity> validRarities = new ArrayList<>();

        for (CrateRarity rarity : CrateRarity.values()) {
            int tier = rarity.ordinal() + 1;
            List<Item> items = com.complextalents.weaponmastery.WeaponMasteryManager.getInstance().getWeaponsForPathAndTier(path, tier);
            if (items != null && !items.isEmpty()) {
                validRarities.add(rarity);
            }
        }

        if (validRarities.isEmpty()) {
            validRarities.add(CrateRarity.COMMON);
        }

        return validRarities;
    }

    public static List<CrateRarity> getValidRaritiesForWeaponPath(WeaponPath path) {
        return getValidRaritiesForWeaponPath(path, null);
    }

    /**
     * Calculates effective drop probabilities (%) for Tiers 1 through 5 for a given Magic School and CrateRarity.
     * Returns an array of 5 doubles representing percentages [T1%, T2%, T3%, T4%, T5%].
     */
    public static double[] getMagicTierPercentages(ResourceLocation schoolId, CrateRarity crateRarity) {
        Map<Integer, List<ItemStack>> tierMap = getMagicSpellsForSchool(schoolId);
        return calculateTierPercentages(tierMap, crateRarity);
    }

    /**
     * Calculates effective drop probabilities (%) for Tiers 1 through 5 for a given WeaponPath and CrateRarity.
     */
    public static double[] getWeaponTierPercentages(WeaponPath path, CrateRarity crateRarity, ResourceManager resourceManager) {
        Map<Integer, List<ItemStack>> tierMap = new HashMap<>();
        for (int t = 1; t <= 5; t++) {
            List<ItemStack> stacks = new ArrayList<>();
            List<Item> items = com.complextalents.weaponmastery.WeaponMasteryManager.getInstance().getWeaponsForPathAndTier(path, t);
            for (Item item : items) {
                stacks.add(new ItemStack(item));
            }
            tierMap.put(t, stacks);
        }
        ensureNonEmptyPool(tierMap, path.name());
        return calculateTierPercentages(tierMap, crateRarity);
    }

    public static double[] getWeaponTierPercentages(WeaponPath path, CrateRarity crateRarity) {
        return getWeaponTierPercentages(path, crateRarity, null);
    }

    private static double[] calculateTierPercentages(Map<Integer, List<ItemStack>> tierMap, CrateRarity crateRarity) {
        int[] weights = crateRarity.getBaseTierWeights().clone();

        // Dynamic weight redistribution for empty tiers
        for (int t = 1; t <= 5; t++) {
            List<ItemStack> itemsInTier = tierMap.getOrDefault(t, Collections.emptyList());
            if (itemsInTier.isEmpty() && weights[t - 1] > 0) {
                int wToDistribute = weights[t - 1];
                weights[t - 1] = 0;

                int targetTier = findClosestNonEmptyTier(tierMap, t);
                if (targetTier != -1) {
                    weights[targetTier - 1] += wToDistribute;
                }
            }
        }

        int totalWeight = 0;
        for (int w : weights) totalWeight += w;

        double[] percentages = new double[5];
        if (totalWeight > 0) {
            for (int i = 0; i < 5; i++) {
                percentages[i] = (weights[i] / (double) totalWeight) * 100.0;
            }
        }
        return percentages;
    }

    /**
     * Builds a weighted CaseReward pool for a given magic schoolId and CrateRarity.
     */
    public static List<CaseReward> buildMagicPool(ResourceLocation schoolId, CrateRarity crateRarity) {
        Map<Integer, List<ItemStack>> tierMap = getMagicSpellsForSchool(schoolId);
        
        // Inject Magic Augment Gems into respective tiers
        injectMagicAugmentGems(tierMap);

        return buildPoolFromItemStackTierMap(tierMap, crateRarity, schoolId.toString());
    }

    private static void injectMagicAugmentGems(Map<Integer, List<ItemStack>> tierMap) {
        CrateRarity[] rarities = CrateRarity.values();
        com.complextalents.item.MagicAugmentItem[] gems = {
            com.complextalents.item.ModItems.POWER_GEM.get(),
            com.complextalents.item.ModItems.MANA_SAVER_GEM.get(),
            com.complextalents.item.ModItems.HASTE_GEM.get(),
            com.complextalents.item.ModItems.SPEED_GEM.get(),
            com.complextalents.item.ModItems.PRECISION_GEM.get(),
            com.complextalents.item.ModItems.FATAL_GEM.get(),
            com.complextalents.item.ModItems.VAMPIRISM_GEM.get(),
            com.complextalents.item.ModItems.PIERCE_GEM.get(),
            com.complextalents.item.ModItems.OVERCLOCK_GEM.get(),
            com.complextalents.item.ModItems.RECAST_GEM.get()
        };

        for (int t = 1; t <= 5; t++) {
            CrateRarity currentRarity = rarities[t - 1];
            List<ItemStack> list = tierMap.computeIfAbsent(t, k -> new ArrayList<>());
            for (com.complextalents.item.MagicAugmentItem gem : gems) {
                if (currentRarity.ordinal() >= gem.getAugmentType().getMinRarity().ordinal()) {
                    list.add(com.complextalents.item.MagicAugmentItem.createStack(gem, currentRarity, 1));
                }
            }
        }
    }

    /**
     * Dynamic fallback & weight redistribution for item-based tier maps.
     */
    private static List<CaseReward> buildPoolFromTierMap(Map<Integer, List<Item>> tierMap, CrateRarity crateRarity, String debugTag) {
        Map<Integer, List<ItemStack>> stackTierMap = new HashMap<>();
        RandomSource random = RandomSource.create();
        for (int t = 1; t <= 5; t++) {
            List<ItemStack> stacks = new ArrayList<>();
            List<Item> items = tierMap != null ? tierMap.getOrDefault(t, Collections.emptyList()) : Collections.emptyList();
            for (Item item : items) {
                ItemStack stack = new ItemStack(item);
                if (!(item instanceof com.complextalents.item.RefinementGemItem)) {
                    stack = com.complextalents.weaponmastery.WeaponMasteryManager.applyRandomRefinementForLoot(stack, random);
                }
                stacks.add(stack);
            }
            stackTierMap.put(t, stacks);
        }

        // Add fallback items if a path is completely empty
        ensureNonEmptyPool(stackTierMap, debugTag);

        return buildPoolFromItemStackTierMap(stackTierMap, crateRarity, debugTag);
    }

    /**
     * Dynamic weight redistribution algorithm across 5 tiers.
     */
    private static List<CaseReward> buildPoolFromItemStackTierMap(Map<Integer, List<ItemStack>> tierMap, CrateRarity crateRarity, String debugTag) {
        int[] weights = crateRarity.getBaseTierWeights(); // [w1, w2, w3, w4, w5]

        // Redistribute weights for empty tiers to adjacent non-empty tiers
        for (int t = 1; t <= 5; t++) {
            List<ItemStack> itemsInTier = tierMap.getOrDefault(t, Collections.emptyList());
            if (itemsInTier.isEmpty() && weights[t - 1] > 0) {
                int wToDistribute = weights[t - 1];
                weights[t - 1] = 0;

                // Find closest non-empty tier
                int targetTier = findClosestNonEmptyTier(tierMap, t);
                if (targetTier != -1) {
                    weights[targetTier - 1] += wToDistribute;
                }
            }
        }

        List<CaseReward> pool = new ArrayList<>();

        for (int t = 1; t <= 5; t++) {
            List<ItemStack> items = tierMap.getOrDefault(t, Collections.emptyList());
            int totalTierWeight = weights[t - 1];
            if (items.isEmpty() || totalTierWeight <= 0) continue;

            int perItemWeight = Math.max(1, totalTierWeight / items.size());
            CaseRarity rarity = mapTierToCaseRarity(t);

            for (ItemStack stack : items) {
                Component customName = null;
                if (t == 5) {
                    // Special golden emblem item format
                    customName = Component.literal("§6★ " + stack.getHoverName().getString());
                }
                int weight = perItemWeight;
                if (stack.getItem() instanceof com.complextalents.item.RefinementGemItem) {
                    weight = perItemWeight * 3;
                }
                pool.add(new CaseReward(stack.copy(), rarity, weight, customName));
            }
        }

        // Final safety net: if pool is empty, populate default fallback items
        if (pool.isEmpty()) {
            pool.add(new CaseReward(new ItemStack(Items.DIAMOND, 5), CaseRarity.MIL_SPEC, 100));
        }

        return pool;
    }

    private static int findClosestNonEmptyTier(Map<Integer, List<ItemStack>> tierMap, int emptyTier) {
        int bestTier = -1;
        int minDistance = Integer.MAX_VALUE;

        for (int t = 1; t <= 5; t++) {
            List<ItemStack> items = tierMap.getOrDefault(t, Collections.emptyList());
            if (!items.isEmpty()) {
                int dist = Math.abs(t - emptyTier);
                if (dist < minDistance) {
                    minDistance = dist;
                    bestTier = t;
                }
            }
        }
        return bestTier;
    }

    private static void ensureNonEmptyPool(Map<Integer, List<ItemStack>> stackTierMap, String debugTag) {
        boolean hasAnyItem = false;
        for (int t = 1; t <= 5; t++) {
            if (!stackTierMap.get(t).isEmpty()) {
                hasAnyItem = true;
                break;
            }
        }

        if (!hasAnyItem) {
            LOGGER.warn("No items found for path/element '{}', injecting fallback vanilla rewards.", debugTag);
            stackTierMap.get(1).add(new ItemStack(Items.IRON_SWORD));
            stackTierMap.get(2).add(new ItemStack(Items.DIAMOND_SWORD));
            stackTierMap.get(3).add(new ItemStack(Items.NETHERITE_SWORD));
            stackTierMap.get(4).add(new ItemStack(Items.TRIDENT));
            stackTierMap.get(5).add(new ItemStack(Items.NETHER_STAR));
        }
    }

    private static CaseRarity mapTierToCaseRarity(int tier) {
        return switch (tier) {
            case 1 -> CaseRarity.MIL_SPEC;    // Common (Blue)
            case 2 -> CaseRarity.RESTRICTED;  // Uncommon (Purple)
            case 3 -> CaseRarity.CLASSIFIED;  // Rare (Pink)
            case 4 -> CaseRarity.COVERT;      // Epic (Red)
            case 5 -> CaseRarity.SPECIAL;     // Special Master Drop (Gold)
            default -> CaseRarity.MIL_SPEC;
        };
    }

    /**
     * Maps Magic School (schoolId) to Spells (Scrolls) across 5 tiers via SpellRegistry.
     */
    private static Map<Integer, List<ItemStack>> getMagicSpellsForSchool(ResourceLocation schoolId) {
        Map<Integer, List<ItemStack>> map = new HashMap<>();
        for (int t = 1; t <= 5; t++) {
            map.put(t, new ArrayList<>());
        }

        try {
            if (io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY != null 
                    && io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get() != null) {
                for (io.redspace.ironsspellbooks.api.spells.AbstractSpell spell : io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get().getValues()) {
                    if (spell == null || spell == io.redspace.ironsspellbooks.api.registry.SpellRegistry.none()) continue;

                    if (spell.getSchoolType() != null && spell.getSchoolType().getId().equals(schoolId)) {
                        for (int lvl = spell.getMinLevel(); lvl <= spell.getMaxLevel(); lvl++) {
                            io.redspace.ironsspellbooks.api.spells.SpellRarity rarity = spell.getRarity(lvl);
                            int tier = mapSpellRarityToTier(rarity);
                            ItemStack scrollStack = createScrollStack(spell, lvl);
                            map.get(tier).add(scrollStack);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error querying SpellRegistry for Magic Case school {}: ", schoolId, e);
        }

        // Safety fallback: if no school-specific spells were found, pull from ALL registered spells
        boolean empty = map.values().stream().allMatch(List::isEmpty);
        if (empty) {
            populateFallbackSpells(map);
        }

        return map;
    }

    private static int mapSpellRarityToTier(io.redspace.ironsspellbooks.api.spells.SpellRarity rarity) {
        if (rarity == null) return 1;
        return switch (rarity) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 3;
            case EPIC -> 4;
            case LEGENDARY -> 5; // Legendary = Apex Special Drop
            default -> 1;
        };
    }

    public static ItemStack createScrollStack(io.redspace.ironsspellbooks.api.spells.AbstractSpell spell, int level) {
        ItemStack scroll = new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get());
        net.minecraft.nbt.CompoundTag tag = scroll.getOrCreateTag();
        net.minecraft.nbt.CompoundTag isbSpells = new net.minecraft.nbt.CompoundTag();
        isbSpells.putInt("maxSpells", 1);
        isbSpells.putBoolean("mustEquip", false);
        net.minecraft.nbt.ListTag dataList = new net.minecraft.nbt.ListTag();
        net.minecraft.nbt.CompoundTag spellEntry = new net.minecraft.nbt.CompoundTag();
        spellEntry.putInt("index", 0);
        spellEntry.putString("id", spell.getSpellResource().toString());
        spellEntry.putBoolean("locked", true);
        spellEntry.putInt("level", level);
        dataList.add(spellEntry);
        isbSpells.put("data", dataList);
        isbSpells.putBoolean("spellWheel", false);
        tag.put("ISB_Spells", isbSpells);

        tag.putString("SpellId", spell.getSpellResource().toString());
        tag.putInt("SpellLevel", level);

        scroll.setHoverName(Component.literal("§bScroll of " + spell.getDisplayName(null).getString() + " (Lvl " + level + ")"));
        return scroll;
    }

    private static void populateFallbackSpells(Map<Integer, List<ItemStack>> map) {
        try {
            if (io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY != null 
                    && io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get() != null) {
                for (io.redspace.ironsspellbooks.api.spells.AbstractSpell spell : io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get().getValues()) {
                    if (spell == null || spell == io.redspace.ironsspellbooks.api.registry.SpellRegistry.none()) continue;
                    for (int lvl = spell.getMinLevel(); lvl <= spell.getMaxLevel(); lvl++) {
                        io.redspace.ironsspellbooks.api.spells.SpellRarity rarity = spell.getRarity(lvl);
                        int tier = mapSpellRarityToTier(rarity);
                        map.get(tier).add(createScrollStack(spell, lvl));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static CaseReward rollFromPool(List<CaseReward> pool, RandomSource random) {
        int totalWeight = pool.stream().mapToInt(CaseReward::getWeight).sum();
        if (totalWeight <= 0) return pool.get(0);

        int roll = random.nextInt(totalWeight);
        int curr = 0;
        for (CaseReward reward : pool) {
            curr += reward.getWeight();
            if (roll < curr) {
                return reward;
            }
        }
        return pool.get(0);
    }
}
