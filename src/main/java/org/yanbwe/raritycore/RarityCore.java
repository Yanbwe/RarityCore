package org.yanbwe.raritycore;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.resources.ResourceLocation;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.network.IncrementalSyncPacket;
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
import org.yanbwe.raritycore.network.RaritySyncPacket;

import java.util.Timer;
import java.util.TimerTask;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(RarityCore.MODID)
public class RarityCore {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "raritycore";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    
    private static Timer syncTimer;

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
        // 初始化网络包
        event.enqueueWork(RaritySyncPacket::initialize);
        
        // 初始化所有配置
        ConfigManager.initializeConfigs();
    }
    
    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 启动定时同步任务，每秒检查一次是否有待处理的变更
        syncTimer = new Timer("RarityCore-Incremental-Sync", true);
        syncTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (RarityRegistry.getPendingChangeCount() > 0) {
                    RarityRegistry.syncIncrementalChangesToClients();
                }
            }
        }, 0, 1000); // 每秒检查一次
    }
    
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        // 服务器停止时取消定时任务
        if (syncTimer != null) {
            syncTimer.cancel();
            syncTimer = null;
        }
        
        // 清空变更缓冲区
        RarityRegistry.clearChangeBuffer();
    }
    
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        RarityCoreCommands.register(event.getDispatcher());
    }
    
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // 玩家登录时发送完整的稀有度表
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // 创建包含当前所有数据的映射
            java.util.Map<ResourceLocation, Integer> currentData = new java.util.HashMap<>(RarityRegistry.ITEM_RARITY_MAP);
            RaritySyncPacket packet = new RaritySyncPacket(currentData);
            
            // 发送完整数据给刚登录的玩家
            RaritySyncPacket.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), packet);
        }
    }
}