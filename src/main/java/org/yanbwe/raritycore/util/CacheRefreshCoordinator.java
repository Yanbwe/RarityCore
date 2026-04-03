package org.yanbwe.raritycore.util;

import org.slf4j.Logger;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RarityCacheCoordinator;

/**
 * 缓存刷新协调器
 * 统一管理所有触发缓存系统刷新的请求,确保刷新逻辑集中化和一致性.
 */
public class CacheRefreshCoordinator {
    private static final Logger LOGGER = RarityCore.LOGGER;

    /**
     * 协调所有相关的缓存刷新操作.
     * 由各个子系统在完成自身工作后调用,而不是直接操作 DualCacheManager.
     */
    public static void coordinateRefresh() {
        LOGGER.debug("Coordinating cache refresh...");
        RarityCacheCoordinator.handleConfigReload();
    }
}
