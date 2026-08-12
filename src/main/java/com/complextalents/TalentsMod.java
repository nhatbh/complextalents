package com.complextalents;

import com.complextalents.config.ElementalReactionConfig;
import com.complextalents.elemental.effects.ElementalEffects;
import com.complextalents.elemental.entity.ModEntities;
import com.complextalents.elemental.integration.ModIntegrationHandler;
import com.complextalents.elemental.registry.ReactionRegistry;
import com.complextalents.impl.darkmage.DarkMageRegistrar;
import com.complextalents.impl.elementalmage.origin.ElementalMageOrigin;
import com.complextalents.impl.highpriest.effect.HighPriestEffects;
import com.complextalents.impl.highpriest.entity.HighPriestEntities;
import com.complextalents.impl.highpriest.item.HighPriestItems;
import com.complextalents.item.ModItems;
import com.complextalents.item.ModCreativeTabs;
import com.complextalents.impl.highpriest.origin.HighPriestOrigin;
import com.complextalents.impl.assassin.effect.AssassinEffects;
import com.complextalents.impl.assassin.AssassinRegistrar;
import com.complextalents.network.PacketHandler;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.origin.command.OriginCommand;
import com.complextalents.origin.integration.OriginModIntegrationHandler;
import com.complextalents.registry.ModAttributes;
import com.complextalents.registry.SoundRegistry;
import com.complextalents.spellmastery.command.SpellMasteryCommand;
import com.complextalents.stats.command.StatsCommand;
import com.complextalents.skill.SkillRegistry;
import com.complextalents.skill.command.SkillCommand;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.command.WeaponMasteryCommand;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TalentsMod.MODID)
public class TalentsMod {
    public static final String MODID = "complextalents";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TalentsMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // Register common setup
        modEventBus.addListener(this::commonSetup);

        // Register custom status effects
        ElementalEffects.register(modEventBus);
        HighPriestEffects.register(modEventBus);
        AssassinEffects.register(modEventBus);
        com.complextalents.effect.ModEffects.register(modEventBus);

        // Register common attributes
        ModAttributes.register(modEventBus);

        // Register TACZ Gun Module
        com.complextalents.tacz.TACZModule.init(modEventBus);

        // Register custom entities
        ModEntities.register(modEventBus);
        HighPriestEntities.register(modEventBus);

        // Register blocks & menus
        com.complextalents.block.ModBlocks.register(modEventBus);
        com.complextalents.menu.ModMenuTypes.register(modEventBus);

        // Register items
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        HighPriestItems.register(modEventBus);

        // Register loot modifiers
        com.complextalents.loot.ModLootModifiers.register(modEventBus);

        // Register custom recipes
        com.complextalents.refinement.ModRecipes.register(modEventBus);

        // Register network packets
        PacketHandler.register();

        // Register custom sounds
        SoundRegistry.register(modEventBus);

        // Register ourselves for server and other game events
        MinecraftForge.EVENT_BUS.register(this);

        // Register configurations
        context.registerConfig(ModConfig.Type.COMMON, ElementalReactionConfig.SPEC, "complextalents-reactions.toml");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Complex Talents mod initializing...");

        // Initialize mod integration (Iron's Spellbooks only - addons use main mod)
        ModIntegrationHandler.init();
        LOGGER.info("Mod integration initialized");

        // Initialize origin module mod integration
        OriginModIntegrationHandler.init();
        LOGGER.info("Origin mod integration initialized");

        // Initialize skill registry
        SkillRegistry.getInstance().initialize();
        LOGGER.info("Skill registry initialized");

        // Initialize origin registry
        OriginRegistry.getInstance().initialize();
        LOGGER.info("Origin registry initialized");

        // Initialize weapon mastery manager
        WeaponMasteryManager.getInstance().initialize();
        LOGGER.info("Weapon Mastery Manager initialized");

        // Register example origins
        HighPriestOrigin.register();
        HighPriestOrigin.initIntegration();
        DarkMageRegistrar.register();
        AssassinRegistrar.register();
        ElementalMageOrigin.register();
        com.complextalents.impl.warrior.WarriorRegistrar.register();
        com.complextalents.impl.spellblade.SpellbladeRegistrar.register();
        com.complextalents.impl.marksman.MarksmanRegistrar.register();
        // com.complextalents.impl.pixie.origin.PixieOrigin.register(); // Temporarily
        // disabled
        LOGGER.info("Example origins registered");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Complex Talents server starting");

        // Initialize reaction registry with all registered reactions
        ReactionRegistry.getInstance().initialize();
        LOGGER.info("Reaction registry initialized");

        // Register skill commands
        SkillCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Skill commands registered");

        // Register origin commands
        OriginCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Origin commands registered");

        // Register dev UI command
        com.complextalents.command.DevUICommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Dev UI command registered");

        // Register dummy player command
        com.complextalents.command.DummyPlayerCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Dummy player command registered");

        // Register stats commands
        StatsCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Stats commands registered");

        // Register spell mastery commands
        SpellMasteryCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Spell mastery commands registered");

        // Register weapon mastery commands
        WeaponMasteryCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Weapon mastery commands registered");

        // Register gun mastery commands
        com.complextalents.gunmastery.command.GunMasteryCommand.register(event.getServer().getCommands().getDispatcher());
        com.complextalents.tacz.TacZGunDumpCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Gun mastery & TACZ dump commands registered");



        // Register leveling commands
        com.complextalents.leveling.command.LevelingCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Leveling commands registered");

        // Register crate commands
        com.complextalents.command.CrateCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Crate commands registered");

        // Register DPS commands
        com.complextalents.dps.command.DPSCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("DPS commands registered");

        // Register Damage Test command
        com.complextalents.command.DamageTestCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Damage Test command registered");

        // Register Live Damage Tracker command
        com.complextalents.command.DamageTrackerCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Damage Tracker command registered");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void buildCreativeTabContents(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                event.accept(ModItems.REFINING_ANVIL.get());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                net.minecraft.client.gui.screens.MenuScreens.register(
                        com.complextalents.menu.ModMenuTypes.REFINING_ANVIL_MENU.get(),
                        com.complextalents.client.gui.RefiningAnvilScreen::new
                );
            });
            LOGGER.info("Complex Talents client setup complete");
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
            com.complextalents.command.WeaponClassifyCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onAddReloadListeners(net.minecraftforge.event.AddReloadListenerEvent event) {
            event.addListener(com.complextalents.weaponmastery.WeaponMasteryManager.getInstance());
            LOGGER.info("Registered WeaponMasteryManager as a reload listener");
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onRegisterClientCommands(net.minecraftforge.client.event.RegisterClientCommandsEvent event) {
            com.complextalents.command.WeaponDumpCommand.register(event.getDispatcher());
            com.complextalents.command.WeaponIconDumpCommand.register(event.getDispatcher());
            LOGGER.info("Client commands registered: weapon_dump, weapon_icons");
        }
    }
}