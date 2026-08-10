package com.complextalents.loot;

import com.complextalents.caseopening.DynamicCasePoolBuilder;
import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.item.MysteriousLootItem;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.ChunkPos;
import com.complextalents.leveling.events.xp.XPContext;
import com.complextalents.leveling.events.xp.XPSource;
import com.complextalents.leveling.service.LevelingService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Global Loot Modifier that dynamically injects Mysterious Loot Crates into all chest loot tables
 * across vanilla and modded structures. Crate drop chance and crate rarity scale with horizontal
 * distance from world spawn (0, 0).
 */
public class ChestCaseLootModifier extends LootModifier {

    public static final Supplier<Codec<ChestCaseLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, ChestCaseLootModifier::new)));

    public static final double MAX_DISTANCE_BLOCKS = 50000.0;
    public static final double BASE_DROP_CHANCE = 0.10; // 10% at spawn / close area
    public static final double MAX_DROP_CHANCE = 0.35;  // 35% at 50,000+ blocks

    public ChestCaseLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // 0. Apply Weapon Refinement Algorithm to any compatible weapon present in generated loot
        RandomSource random = context.getRandom();
        for (ItemStack stack : generatedLoot) {
            if (stack != null && !stack.isEmpty()) {
                com.complextalents.weaponmastery.WeaponMasteryManager.applyRandomRefinementForLoot(stack, random);
            }
        }

        // 1. Verify this is a chest / container loot context
        ResourceLocation tableId = context.getQueriedLootTableId();
        if (tableId == null) return generatedLoot;

        String pathStr = tableId.getPath().toLowerCase();
        boolean isChestLoot = pathStr.contains("chest") || pathStr.contains("dungeon") || pathStr.contains("vault") || pathStr.contains("crate") || pathStr.contains("storage");
        if (!isChestLoot) return generatedLoot;

        // 2. Get 3D position of chest/container
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) return generatedLoot;

        // 3. Compute 2D horizontal distance from world spawn (0, 0)
        double distance = Math.sqrt(origin.x * origin.x + origin.z * origin.z);
        double distanceRatio = Math.min(1.0, distance / MAX_DISTANCE_BLOCKS);

        // 4. Award Chest Loot XP to the player who opened the container
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (entity instanceof ServerPlayer player) {
            double xpAmount = 100.0 + (7400.0 * distanceRatio); // 100 XP at spawn -> 7500 XP at 50k blocks
            ChunkPos chunkPos = new ChunkPos((int) Math.floor(origin.x) >> 4, (int) Math.floor(origin.z) >> 4);
            XPContext xpContext = XPContext.builder()
                    .source(XPSource.CHEST_LOOT)
                    .chunkPos(chunkPos)
                    .rawAmount(xpAmount)
                    .metadata("lootTable", tableId.toString())
                    .metadata("distance", distance)
                    .build();
            LevelingService.getInstance().awardXP(player, xpAmount, XPSource.CHEST_LOOT, xpContext);
        }

        // 5. Calculate Distance-Based Drop Chance (10% at spawn -> 35% at 50,000+ blocks out)
        double dropChance = BASE_DROP_CHANCE + ((MAX_DROP_CHANCE - BASE_DROP_CHANCE) * distanceRatio);

        if (random.nextDouble() >= dropChance) {
            return generatedLoot;
        }

        // 5. Calculate Distance-Based CrateRarity
        CrateRarity chosenRarity = sampleRarityForDistance(distanceRatio, random);

        // 6. 50/50 Roll between Weapon Case and Magic Case
        boolean isWeaponCase = random.nextBoolean();

        if (isWeaponCase) {
            WeaponPath[] paths = WeaponPath.values();
            List<WeaponPath> validPaths = new ArrayList<>();
            for (WeaponPath p : paths) {
                List<CrateRarity> valid = DynamicCasePoolBuilder.getValidRaritiesForWeaponPath(p, null);
                if (valid.contains(chosenRarity)) {
                    validPaths.add(p);
                }
            }
            if (validPaths.isEmpty()) {
                validPaths.add(paths[random.nextInt(paths.length)]);
            }
            WeaponPath selectedPath = validPaths.get(random.nextInt(validPaths.size()));
            generatedLoot.add(MysteriousLootItem.createWeaponCase(selectedPath, chosenRarity));
        } else {
            // Magic Case
            List<ResourceLocation> validSchoolIds = new ArrayList<>();
            try {
                if (io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY != null 
                        && io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get() != null) {
                    for (SchoolType school : io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get().getValues()) {
                        if (school != null && school.getId() != null) {
                            List<CrateRarity> valid = DynamicCasePoolBuilder.getValidRaritiesForSchool(school.getId());
                            if (valid.contains(chosenRarity)) {
                                validSchoolIds.add(school.getId());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (validSchoolIds.isEmpty()) {
                validSchoolIds.add(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fire"));
            }
            ResourceLocation selectedSchoolId = validSchoolIds.get(random.nextInt(validSchoolIds.size()));
            generatedLoot.add(MysteriousLootItem.createMagicCase(selectedSchoolId, chosenRarity));
        }

        return generatedLoot;
    }

    private CrateRarity sampleRarityForDistance(double distanceRatio, RandomSource random) {
        // Interpolate weights between Spawn (ratio = 0.0) and Far (ratio = 1.0)
        // Order: Common, Uncommon, Rare, Epic, Legendary
        double wCommon    = lerp(65.0, 15.0, distanceRatio);
        double wUncommon  = lerp(25.0, 25.0, distanceRatio);
        double wRare      = lerp( 8.0, 30.0, distanceRatio);
        double wEpic      = lerp( 2.0, 20.0, distanceRatio);
        double wLegendary = lerp( 0.0, 10.0, distanceRatio);

        double totalWeight = wCommon + wUncommon + wRare + wEpic + wLegendary;
        double roll = random.nextDouble() * totalWeight;

        if ((roll -= wCommon) < 0) return CrateRarity.COMMON;
        if ((roll -= wUncommon) < 0) return CrateRarity.UNCOMMON;
        if ((roll -= wRare) < 0) return CrateRarity.RARE;
        if ((roll -= wEpic) < 0) return CrateRarity.EPIC;
        return CrateRarity.LEGENDARY;
    }

    private double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
