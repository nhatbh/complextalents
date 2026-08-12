package com.complextalents.block;

import com.complextalents.TalentsMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TalentsMod.MODID);

    public static final RegistryObject<Block> REFINING_ANVIL = BLOCKS.register("refining_anvil",
            () -> new RefiningAnvilBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.ANVIL)));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
