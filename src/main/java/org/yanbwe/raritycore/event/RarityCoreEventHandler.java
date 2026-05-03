package org.yanbwe.raritycore.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
        
        // 注册稀有度数据加载器(支持数据包加载基础ID配置)
        event.addListener(factory.getRarityDataLoader());
        
        // 注册NBT匹配配置加载器(支持数据包加载)
        event.addListener(factory.createNbtConfigLoader());
    }
    
    /**
     * 服务器启动事件
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ServiceFactory factory = ServiceFactory.getInstance();
        // 启动调度器服务
        factory.getSchedulerService().startScheduledTasks();
        
        // 在服务器启动时加载配置,此时物品注册表已完全填充
        org.yanbwe.raritycore.service.ConfigReloadService.reloadOnStartup();
    }
    
    /**
     * 服务器停止事件
     * @param event 服务器停止事件
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ServiceFactory factory = ServiceFactory.getInstance();
        // 停止调度器服务
        factory.getSchedulerService().stopScheduledTasks();
        
        // 清空变更缓冲区
        org.yanbwe.raritycore.network.SyncManager.clearChangeBuffer();
        
        // 关闭延迟同步管理器
        org.yanbwe.raritycore.network.DelayedSyncManager.shutdown();

        // 关闭网络重试管理器线程池
        org.yanbwe.raritycore.network.NetworkRetryManager.shutdown();
    }
    
    /**
     * 注册命令
     * @param event 命令注册事件
     */
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        org.yanbwe.raritycore.command.RarityCoreCommands.register(event.getDispatcher());
    }
    
    /**
     * 玩家登录事件
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // 登录时向玩家发送完整稀有度数据(版本感知: 客户端版本匹配时自动跳过)
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // 发送版本感知的稀有度数据——客户端自动检测版本号跳过重复同步
            org.yanbwe.raritycore.network.SyncManager.syncRarityToPlayer(serverPlayer, 
                org.yanbwe.raritycore.registry.RarityRegistry.getItemRarityMap());
            
            // 发送NBT匹配规则
            org.yanbwe.raritycore.network.NbtSyncManager.syncNbtRulesToPlayer(serverPlayer);
        }
    }
}