package org.yanbwe.raritycore.client;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.util.CacheRefreshCoordinator;

/**
 * 缓存失效监听器
 * 监听多种事件并及时使缓存失效
 */
@EventBusSubscriber(modid = RarityCore.MODID)
public class CacheInvalidationListener {
    
    /**
     * 监听稀有度变更事件
     */
    @SubscribeEvent
    public static void onRarityChange(RarityChangeEvent event) {
        try {
            Item item = event.getItem();
            if (item != null) {
                // 协调并执行缓存刷新
                CacheRefreshCoordinator.coordinateRefresh();
                // RarityCore.LOGGER.debug("Cache invalidated for item {} due to rarity change", item);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling rarity change event", e);
        }
    }
    
    /**
     * 监听资源配置重载事件
     */
    @SubscribeEvent
    public static void onResourceReload(AddReloadListenerEvent event) {
        try {
            // 协调并执行缓存刷新
            CacheRefreshCoordinator.coordinateRefresh();
            RarityCore.LOGGER.info("All caches invalidated due to resource reload");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling resource reload event", e);
        }
    }
    
    /**
     * 监听客户端配置变更
     * 注意:这需要在ConfigManager中调用相应的方法
     */
    public static void onClientConfigChange() {
        try {
            // 协调并执行缓存刷新
            CacheRefreshCoordinator.coordinateRefresh();
            // 同时清除 RarityTooltipHandler 中的 tooltip 组件缓存
            RarityTooltipHandler.invalidateCaches();
            // RarityCore.LOGGER.debug("Handled client config change for cache");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling client config change", e);
        }
    }
    
    /**
     * 监听网络同步事件
     */
    public static void onNetworkSync() {
        try {
            // 协调并执行缓存刷新
            CacheRefreshCoordinator.coordinateRefresh();
            // RarityCore.LOGGER.debug("Handled network sync for cache");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling network sync", e);
        }
    }
}