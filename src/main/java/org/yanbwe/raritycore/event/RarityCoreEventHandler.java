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
        event.addListener(factory.getRarityDataLoader());
        
        // 注册NBT匹配配置加载器(支持数据包加载)
        event.addListener(factory.createNbtConfigLoader());
        
        // 注册缓存失效监听器到事件总线
        try {
            Class<?> listenerClass = Class.forName("org.yanbwe.raritycore.client.CacheInvalidationListener");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listenerClass);
        } catch (ClassNotFoundException e) {
            org.yanbwe.raritycore.RarityCore.LOGGER.warn("CacheInvalidationListener class not found, skipping registration");
        }
        
        // 加载本地NBT匹配配置
        org.yanbwe.raritycore.nbtmatching.NbtConfigLoader.loadAllConfigs();
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
        factory.getSyncManager().clearChangeBuffer();
        
        // 关闭延迟同步管理器
        factory.getDelayedSyncManager().shutdown();
    }
    
    /**
     * 注册命令
     * @param event 命令注册事件
     */
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        ServiceFactory factory = ServiceFactory.getInstance();
        factory.getRarityCoreCommands().register(event.getDispatcher());
    }
    
    /**
     * 玩家登录事件
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ServiceFactory factory = ServiceFactory.getInstance();
        // 登录时向玩家发送完整稀有度数据
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // 发送完整稀有度数据
            factory.getSyncManager().syncRarityToClients(factory.getRarityRegistry().getItemRarityMap());
            
            // 发送NBT匹配规则
            factory.getNbtSyncManager().syncNbtRulesToPlayer(serverPlayer);
        }
    }
}