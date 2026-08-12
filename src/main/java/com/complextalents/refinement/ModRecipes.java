package com.complextalents.refinement;

import com.complextalents.TalentsMod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TalentsMod.MODID);

    public static final RegistryObject<RecipeSerializer<WeaponRefinementRecipe>> WEAPON_REFINEMENT_SERIALIZER =
            SERIALIZERS.register("weapon_refinement", WeaponRefinementRecipe.Serializer::new);

    public static final RegistryObject<RecipeSerializer<GunRefinementRecipe>> GUN_REFINEMENT_SERIALIZER =
            SERIALIZERS.register("gun_refinement", GunRefinementRecipe.Serializer::new);

    public static final RegistryObject<RecipeSerializer<SpellAugmentRecipe>> SPELL_AUGMENT_SERIALIZER =
            SERIALIZERS.register("spell_augment", SpellAugmentRecipe.Serializer::new);

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
