package org.yanbwe.raritycore;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
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

import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.data.RarityDataLoader;
import org.yanbwe.raritycore.network.RaritySyncPacket;

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

        // 确保在构造函数中初始化配置
        ConfigManager.initializeConfigs();
    }
    
    @SubscribeEvent
    public void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(RarityDataLoader.INSTANCE);
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 初始化网络数据包
        event.enqueueWork(RaritySyncPacket::initialize);
        event.enqueueWork(IncrementalSyncPacket::initialize);
        
        // 初始化所有配置（已在构造函数中处理，这里是为了确保）
        event.enqueueWork(ConfigManager::initializeConfigs);
    }
    
    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 启动智能定时同步任务
        syncScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RarityCore-Incremental-Sync");
            t.setDaemon(true);  // 设置为守护线程
            return t;
        });
        
        syncScheduler.scheduleAtFixedRate(() -> {
            try {
                // 使用批处理管理器检查是否需要同步
                int pendingCount = org.yanbwe.raritycore.network.SyncBatchManager.getPendingOperationCount();
                if (pendingCount > 0) {
                    RarityRegistry.syncIncrementalChangesToClients();
                }
            } catch (Exception e) {
                LOGGER.error("增量同步期间发生错误", e);
            }
        }, 0, 2000, TimeUnit.MILLISECONDS); // 每2秒检查一次，与批处理时间窗口匹配
        
        // 添加缓存清理任务（使用更长的间隔）
        syncScheduler.scheduleAtFixedRate(() -> {
            try {
                // 使用智能清理而非全量清理，间隔延长到10分钟
                org.yanbwe.raritycore.client.ImprovedRenderCacheManager.smartCleanup();
                LOGGER.debug("执行智能缓存清理");
            } catch (Exception e) {
                LOGGER.error("缓存清理期间发生错误", e);
            }
        }, 600000, 600000, TimeUnit.MILLISECONDS); // 每10分钟执行一次
    }
    
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        // 服务器停止时关闭调度器
        if (syncScheduler != null) {
            syncScheduler.shutdown();
            try {
                if (!syncScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    syncScheduler.shutdownNow();
                    if (!syncScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        LOGGER.error("线程池未能正常终止");
                    }
                }
            } catch (InterruptedException e) {
                syncScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            syncScheduler = null;
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
        // 玩家登录时发送完整的稀有度数据
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // 创建包含当前所有数据的映射
            java.util.Map<ResourceLocation, Integer> currentData = new java.util.HashMap<>(RarityRegistry.ITEM_RARITY_MAP);
            RaritySyncPacket packet = new RaritySyncPacket(currentData);
            
            // 发送完整数据给刚登录的玩家
            RaritySyncPacket.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), packet);
        }
    }
}