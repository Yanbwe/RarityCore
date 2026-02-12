package org.yanbwe.raritycore.client;

import net.minecraft.world.item.Item;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.event.RarityChangeEvent;

/**
 * 缓存失效监听器
 * 监听多种事件并及时使缓存失效
 */
@Mod.EventBusSubscriber(modid = RarityCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CacheInvalidationListener {
    
    /**
     * 监听稀有度变更事件
     */
    @SubscribeEvent
    public static void onRarityChange(RarityChangeEvent event) {
        try {
            Item item = event.getItem();
            if (item != null) {
                // 使用缓存管理器使缓存失效
                ImprovedRenderCacheManager.invalidateItemCache(item);
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
            // 资源重载时使所有缓存失效
            ImprovedRenderCacheManager.invalidateAllCachesOnConfigReload();
            RarityCore.LOGGER.info("All caches invalidated due to resource reload");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling resource reload event", e);
        }
    }
    
    /**
     * 监听客户端配置变更
     * 注意：这需要在ConfigManager中调用相应的方法
     */
    public static void onClientConfigChange() {
        try {
            // 客户端配置变更时处理相关缓存
            ImprovedRenderCacheManager.handleClientConfigChange();
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
            // 网络同步时刷新缓存
            ImprovedRenderCacheManager.handleNetworkSync();
            // RarityCore.LOGGER.debug("Handled network sync for cache");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling network sync", e);
        }
    }
}