package org.yanbwe.raritycore.client;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.event.RarityChangeEvent;

/**
 * 缓存失效监听器
 * 监听稀有度变更事件并及时使缓存失效
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
                // 使用改进的缓存管理器使缓存失效
                ImprovedRenderCacheManager.invalidateItemCache(item);
                RarityCore.LOGGER.debug("Cache invalidated for item {} due to rarity change", item);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling rarity change event", e);
        }
    }
}