package org.yanbwe.raritycore;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.compat.CompatibilityManager;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.data.RarityDataLoader;
import org.yanbwe.raritycore.network.IncrementalSyncPacket;
import org.yanbwe.raritycore.network.RaritySyncPacket;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(RarityCore.MODID)
public class RarityCore {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "raritycore";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    
    private static ScheduledExecutorService syncScheduler;

    public RarityCore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);

        // Ensure configuration initialization in constructor
        ConfigManager.initializeConfigs();
        org.yanbwe.raritycore.config.ServerConfigManager.initializeServerConfigs();
        
        // 初始化双缓存系统
        org.yanbwe.raritycore.cache.DualCacheManager.initialize();
    }
    
    @SubscribeEvent
    public void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(RarityDataLoader.INSTANCE);
        
        // 注册NBT匹配配置加载器（支持数据包加载）
        event.addListener(new org.yanbwe.raritycore.nbtmatching.NbtConfigLoader());
        
        // 注册缓存失效监听器到事件总线
        MinecraftForge.EVENT_BUS.register(org.yanbwe.raritycore.client.CacheInvalidationListener.class);
        
        // 加载本地NBT匹配配置
        org.yanbwe.raritycore.nbtmatching.NbtConfigLoader.loadAllConfigs();
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        // Perform compatibility diagnostics early
        event.enqueueWork(RarityRegistry::performCompatibilityCheck);
        
        // Initialize network packets
        event.enqueueWork(RaritySyncPacket::initialize);
        event.enqueueWork(IncrementalSyncPacket::initialize);
        event.enqueueWork(org.yanbwe.raritycore.network.NbtSyncPacket::initialize);
        event.enqueueWork(org.yanbwe.raritycore.network.EditModeRequestPacket::initialize);
        
        // Initialize all configurations (already handled in constructor, just to be safe)
        event.enqueueWork(ConfigManager::initializeConfigs);
        
        // Initialize compatibility adapters
        event.enqueueWork(CompatibilityManager::initializeCompatibilityAdapters);
    }
    
    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Start smart scheduled sync task
        syncScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RarityCore-Incremental-Sync");
            t.setDaemon(true);  // Set as daemon thread
            return t;
        });
            
        // 延时发送兼容性提示（等待世界完全加载）
        syncScheduler.schedule(() -> {
            try {
                RarityRegistry.notifyPlayersOfCompatibilityIssue();
            } catch (Exception e) {
                LOGGER.debug("Failed to send compatibility notification", e);
            }
        }, 5, TimeUnit.SECONDS); // 5 秒后发送提示
            
        // 延时启动自动稀有度计算（世界启动 5 秒后检测）
        syncScheduler.schedule(() -> {
            try {
                checkAndStartAutoCalculation();
            } catch (Exception e) {
                LOGGER.error("Failed to start auto rarity calculation", e);
            }
        }, 5, TimeUnit.SECONDS);
            
        syncScheduler.scheduleAtFixedRate(() -> {
            try {
                // Check if sync is needed using batch manager
                int pendingCount = org.yanbwe.raritycore.network.SyncBatchManager.getPendingOperationCount();
                if (pendingCount > 0) {
                    RarityRegistry.syncIncrementalChangesToClients();
                }
            } catch (Exception e) {
                LOGGER.error("Error occurred during incremental sync", e);
            }
        }, 0, 2000, TimeUnit.MILLISECONDS); // Check every 2 seconds, matching batch processing window
            
        // 添加自动稀有度计算的 tick 任务
        syncScheduler.scheduleAtFixedRate(() -> {
            try {
                // 每 tick 调用计算器
                org.yanbwe.raritycore.calc.AutoRarityCalculator.tick();
            } catch (Exception e) {
                LOGGER.error("Error occurred during auto rarity calculation tick", e);
            }
        }, 100, 50, TimeUnit.MILLISECONDS); // 100ms 后开始，每 50ms(1tick) 执行一次
            
        // Add cache cleanup task (using longer interval)
        syncScheduler.scheduleAtFixedRate(() -> {
            try {
                // Use smart cleanup instead of full cleanup, interval extended to 10 minutes
                // 新缓存系统不需要手动清理
                // 根据调试日志管理规范，注释掉高频触发的调试信息
                // LOGGER.debug("Executing smart cache cleanup");
            } catch (Exception e) {
                LOGGER.error("Error occurred during cache cleanup", e);
            }
        }, 600000, 600000, TimeUnit.MILLISECONDS); // Execute every 10 minutes
    }
    
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        // Shutdown scheduler when server stops
        if (syncScheduler != null) {
            syncScheduler.shutdown();
            try {
                if (!syncScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    syncScheduler.shutdownNow();
                    if (!syncScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        LOGGER.error("Thread pool failed to terminate properly");
                    }
                }
            } catch (InterruptedException e) {
                syncScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            syncScheduler = null;
        }
        
        // Clear change buffer
        RarityRegistry.clearChangeBuffer();
        
        // Shutdown delayed sync manager
        org.yanbwe.raritycore.network.DelayedSyncManager.shutdown();
    }
    
    /**
     * 检查并启动自动稀有度计算
     */
    private void checkAndStartAutoCalculation() {
        // 检查 auto_rarity.json 是否存在
        java.nio.file.Path autoRarityFile = org.yanbwe.raritycore.calc.AutoRarityConfigManager.getAutoRarityFilePath();
        if (!java.nio.file.Files.exists(autoRarityFile)) {
            // 文件不存在，开始自动计算
            org.yanbwe.raritycore.calc.AutoRarityCalculator.startAutoCalculation();
        } else {
            LOGGER.debug("Auto rarity config already exists, skipping calculation: {}", autoRarityFile);
        }
    }
    
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        RarityCoreCommands.register(event.getDispatcher());
    }
    
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // Send full rarity data to player on login
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // Send full rarity data
            java.util.Map<ResourceLocation, Integer> currentData = new java.util.HashMap<>(RarityRegistry.ITEM_RARITY_MAP);
            RaritySyncPacket packet = new RaritySyncPacket(currentData);
            RaritySyncPacket.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), packet);
            
            // Send NBT matching rules
            org.yanbwe.raritycore.network.NbtSyncManager.syncNbtRulesToPlayer(serverPlayer);
        }
    }
}