package org.yanbwe.raritycore.client;

import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.util.CacheRefreshCoordinator;

/**
 * 缓存失效监听器
 * 监听多种事件并及时使缓存失效
 */
@Mod.EventBusSubscriber(modid = RarityCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
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
     * 监听客户端配置变更
     * 注意：这需要在ConfigManager中调用相应的方法
     */
    public static void onClientConfigChange() {
        try {
            // 协调并执行缓存刷新
            CacheRefreshCoordinator.coordinateRefresh();
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

    /**
     * 监听客户端断开连接事件，重置同步版本号
     * 确保连接到新服务器时必定重新同步稀有度数据
     */
    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        org.yanbwe.raritycore.network.RaritySyncPacket.resetClientVersion();
        RarityCore.LOGGER.debug("Client rarity sync version reset on disconnect");
    }
}