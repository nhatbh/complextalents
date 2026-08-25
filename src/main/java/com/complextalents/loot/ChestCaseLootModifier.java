package com.complextalents.loot;

import com.complextalents.caseopening.DynamicCasePoolBuilder;
import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.item.MysteriousLootItem;
import com.complextalents.leveling.events.xp.XPContext;
import com.complextalents.leveling.events.xp.XPSource;
import com.complextalents.leveling.service.LevelingService;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
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
        // 0. Apply Weapon & Gun Refinement Algorithm to any compatible items present in generated loot
        RandomSource random = context.getRandom();
        for (ItemStack stack : generatedLoot) {
            if (stack != null && !stack.isEmpty()) {
                com.complextalents.weaponmastery.WeaponMasteryManager.applyRandomRefinementForLoot(stack, random);
                com.complextalents.gunmastery.GunRefinementManager.applyRandomRefinementForLoot(stack, random);
            }
        }

        // 1. Exclude non-container loot contexts (block drops have BLOCK_STATE, mob kills have DAMAGE_SOURCE)
        if (context.hasParam(LootContextParams.BLOCK_STATE) || context.hasParam(LootContextParams.DAMAGE_SOURCE)) {
            return generatedLoot;
        }

        // 2. Get 3D position of chest/container
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) return generatedLoot;

        // 3. Compute 2D horizontal distance from world spawn (0, 0)
        double distance = Math.sqrt(origin.x * origin.x + origin.z * origin.z);
        double distanceRatio = Math.min(1.0, distance / MAX_DISTANCE_BLOCKS);

        // 4. Award Chest Loot XP to the player who opened the container (at most once per block entity)
        BlockEntity blockEntity = context.getParamOrNull(LootContextParams.BLOCK_ENTITY);
        boolean shouldAwardXP = true;
        if (blockEntity != null) {
            if (blockEntity.getPersistentData().getBoolean("ct_xp_claimed")) {
                shouldAwardXP = false;
            } else {
                blockEntity.getPersistentData().putBoolean("ct_xp_claimed", true);
            }
        }

        if (shouldAwardXP) {
            ServerPlayer player = null;
            Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
            if (entity instanceof ServerPlayer sp) {
                player = sp;
            } else if (context.getLevel() != null) {
                player = (ServerPlayer) context.getLevel().getNearestPlayer(origin.x, origin.y, origin.z, 16.0, false);
            }

            if (player != null) {
                double xpAmount = 100.0 + (7400.0 * distanceRatio); // 100 XP at spawn -> 7500 XP at 50k blocks
                ChunkPos chunkPos = new ChunkPos((int) Math.floor(origin.x) >> 4, (int) Math.floor(origin.z) >> 4);
                XPContext xpContext = XPContext.builder()
                        .source(XPSource.CHEST_LOOT)
                        .chunkPos(chunkPos)
                        .rawAmount(xpAmount)
                        .metadata("distance", distance)
                        .build();
                LevelingService.getInstance().awardXP(player, xpAmount, XPSource.CHEST_LOOT, xpContext);
            }
        }

        // 5. Calculate Distance-Based Drop Chance (10% at spawn -> 35% at 50,000+ blocks out)
        double dropChance = BASE_DROP_CHANCE + ((MAX_DROP_CHANCE - BASE_DROP_CHANCE) * distanceRatio);
        if (random.nextDouble() >= dropChance) {
            return generatedLoot;
        }

        // 6. Calculate Distance-Based CrateRarity
        CrateRarity chosenRarity = sampleRarityForDistance(distanceRatio, random);

        // 7. 3-Way Roll between Weapon Case, Magic Case, and Gun Case
        int crateCategoryChoice = random.nextInt(3);

        if (crateCategoryChoice == 0) {
            // Weapon Case: All Weapon Case (null) has 2x chance (added twice) and comes in all rarities
            List<WeaponPath> candidates = new ArrayList<>();
            candidates.add(null);
            candidates.add(null);

            for (WeaponPath p : WeaponPath.values()) {
                List<CrateRarity> valid = DynamicCasePoolBuilder.getValidRaritiesForWeaponPath(p, null);
                if (valid.contains(chosenRarity)) {
                    candidates.add(p);
                }
            }
            WeaponPath selectedPath = candidates.get(random.nextInt(candidates.size()));
            generatedLoot.add(MysteriousLootItem.createWeaponCase(selectedPath, chosenRarity));
        } else if (crateCategoryChoice == 1) {
            // Magic Case: All Magic Case (null) has 2x chance (added twice) and comes in all rarities
            List<ResourceLocation> candidates = new ArrayList<>();
            candidates.add(null);
            candidates.add(null);

            try {
                if (io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY != null 
                        && io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get() != null) {
                    for (SchoolType school : io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get().getValues()) {
                        if (school != null && school.getId() != null) {
                            List<CrateRarity> valid = DynamicCasePoolBuilder.getValidRaritiesForSchool(school.getId());
                            if (valid.contains(chosenRarity)) {
                                candidates.add(school.getId());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            ResourceLocation selectedSchoolId = candidates.get(random.nextInt(candidates.size()));
            generatedLoot.add(MysteriousLootItem.createMagicCase(selectedSchoolId, chosenRarity));
        } else {
            // Gun Case: All Firearm Case (null) has 2x chance (added twice) and comes in all rarities
            List<com.complextalents.tacz.GunType> candidates = new ArrayList<>();
            candidates.add(null);
            candidates.add(null);

            for (com.complextalents.tacz.GunType gt : com.complextalents.tacz.GunType.values()) {
                List<CrateRarity> valid = DynamicCasePoolBuilder.getValidRaritiesForGunType(gt);
                if (valid.contains(chosenRarity)) {
                    candidates.add(gt);
                }
            }

            com.complextalents.tacz.GunType selectedGunType = candidates.get(random.nextInt(candidates.size()));
            generatedLoot.add(MysteriousLootItem.createGunCase(selectedGunType, chosenRarity));
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
