package org.yanbwe.raritycore.client;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.util.CacheRefreshCoordinator;

@EventBusSubscriber(modid = RarityCore.MODID)
public class CacheInvalidationListener {

    @SubscribeEvent
    public static void onRarityChange(RarityChangeEvent event) {
        try {
            Item item = event.getItem();
            if (item != null) {
                CacheRefreshCoordinator.coordinateRefresh();
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling rarity change event", e);
        }
    }

    @SubscribeEvent
    public static void onResourceReload(AddServerReloadListenersEvent event) {
        try {
            CacheRefreshCoordinator.coordinateRefresh();
            RarityCore.LOGGER.info("All caches invalidated due to resource reload");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling resource reload event", e);
        }
    }

    public static void onClientConfigChange() {
        try {
            CacheRefreshCoordinator.coordinateRefresh();
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling client config change", e);
        }
    }

    public static void onNetworkSync() {
        try {
            CacheRefreshCoordinator.coordinateRefresh();
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling network sync", e);
        }
    }
}