package com.complextalents.summoning;

import com.complextalents.TalentsMod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Main initialization class for the ComplexTalents Summoning Module.
 */
public class SummoningModule {

    public static void init(IEventBus modEventBus) {
        // Register event handlers for summoning behaviors
        MinecraftForge.EVENT_BUS.register(SummoningEventHandler.class);
        TalentsMod.LOGGER.info("Summoning Module initialized successfully");
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        SummoningCommand.register(dispatcher);
        TalentsMod.LOGGER.info("Summoning commands registered");
    }
}
