package org.yanbwe.raritycore.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.config.TagRarityConfigLoader;
import org.yanbwe.raritycore.itemdatamatching.ItemDataConfigLoader;
import org.yanbwe.raritycore.network.DelayedSyncManager;
import org.yanbwe.raritycore.network.ItemDataSyncManager;
import org.yanbwe.raritycore.network.NetworkRetryManager;
import org.yanbwe.raritycore.network.SyncBatchManager;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.service.ConfigReloadService;
import org.yanbwe.raritycore.service.ServiceFactory;

/**
 * RarityCore 事件处理器
 * 负责处理所有事件监听
 */
public class RarityCoreEventHandler {
    
    /**
     * 注册资源重载监听器
     * @param event 资源重载监听器事件
     */
    @SubscribeEvent
    public void addReloadListeners(AddReloadListenerEvent event) {
        ServiceFactory factory = ServiceFactory.getInstance();
        event.addListener(factory.getRarityDataLoader());
        
        // 注册物品数据匹配配置加载器(支持数据包加载)
        event.addListener(factory.createItemDataConfigLoader());
        
        // CacheInvalidationListener 已通过 @EventBusSubscriber 注解自动注册
        // 此处不再手动注册，避免重复订阅导致每个事件触发多次
        // ItemDataConfigLoader.loadAllConfigs() 已移至 ConfigReloadService.reloadOnStartup() 统一管理
    }
    
    /**
     * 服务器启动事件
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ServiceFactory factory = ServiceFactory.getInstance();
        // 服务器启动时执行完整配置重载（此时所有模组物品已注册，保证模组物品的稀有度配置正确加载）
        ConfigReloadService.reloadOnStartup();
        // 启动调度器服务
        factory.getSchedulerService().startScheduledTasks();
    }
    
    /**
     * 服务器停止事件
     * @param event 服务器停止事件
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        try {
            ServiceFactory factory = ServiceFactory.getInstance();
            // 停止调度器服务
            factory.getSchedulerService().stopScheduledTasks();
            
            // 清空变更缓冲区
            SyncManager.clearChangeBuffer();
            
            // 清空批处理队列中的待处理操作（防止脏数据跨会话）
            SyncBatchManager.clearAllOperations();
            
            // 关闭延迟同步管理器
            DelayedSyncManager.shutdown();

            // 关闭网络重试管理器
            NetworkRetryManager.shutdown();
            
            // 关闭物品数据同步管理器
            ItemDataSyncManager.shutdown();
        } catch (NoClassDefFoundError | Exception e) {
            RarityCore.LOGGER.debug("Error during server stop cleanup: {}", e.getMessage());
        }
    }
    
    /**
     * 注册命令
     * @param event 命令注册事件
     */
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        RarityCoreCommands.register(event.getDispatcher());
    }
    
    /**
     * 玩家登录事件
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // 只向登录的玩家同步稀有度数据，避免全服广播
            SyncManager.syncRarityToPlayer(serverPlayer,
                RarityRegistry.getItemRarityMap(),
                RarityRegistry.getAutoRarityMap(),
                TagRarityConfigLoader.getSyncedRules());

            // 发送物品数据匹配规则
            ItemDataSyncManager.syncItemDataRulesToPlayer(serverPlayer);
        }
    }
}