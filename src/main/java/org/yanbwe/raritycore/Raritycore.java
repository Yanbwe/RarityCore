package org.yanbwe.raritycore;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.data.RarityDataLoader;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(RarityCore.MODID)
public class RarityCore {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "raritycore";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public RarityCore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
    
    @SubscribeEvent
    public void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(RarityDataLoader.INSTANCE);
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 初始化所有配置
        ConfigManager.initializeConfigs();
    }
    
    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
    }
    
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        RarityCoreCommands.register(event.getDispatcher(), event.getBuildContext());
    }
}