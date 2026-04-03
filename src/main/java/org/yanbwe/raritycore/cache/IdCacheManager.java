package org.yanbwe.raritycore.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.yanbwe.raritycore.RarityCore;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ID缓存管理器
 * 基于物品ID的缓存系统，存储物品的基础稀有度
 * 适用于没有组件匹配配置的物品
 */
public class IdCacheManager {

    private static volatile Cache<ResourceLocation, Integer> idCache;

    private static volatile CacheConfig config;

    private static volatile boolean isReloading = false;
    private static volatile long lastReloadTime = 0;
    private static final long MIN_RELOAD_INTERVAL = 1000;

    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);

    public static CacheConfig getConfig() {
        return config;
    }

    public static void initialize() {
        config = new CacheConfig();
        createCache();
        RarityCore.LOGGER.info("ID缓存系统初始化完成 - 容量: {}", config.getActualMaxCacheSize());
    }

    private static void createCache() {
        int actualCacheSize = config.getActualMaxCacheSize();

        idCache = CacheBuilder.newBuilder()
            .maximumSize(actualCacheSize)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

        RarityCore.LOGGER.info("ID缓存创建完成，动态容量: {} 条目", actualCacheSize);
    }

    /**
     * 获取物品的缓存稀有度
     * @param itemId 物品资源位置
     * @return 缓存的稀有度，如果不存在返回null
     */
    public static Integer getCachedRarity(ResourceLocation itemId) {
        if (itemId == null) {
            return null;
        }

        Integer result = idCache.getIfPresent(itemId);
        if (result != null) {
            cacheHits.incrementAndGet();
            return result;
        }

        cacheMisses.incrementAndGet();
        return null;
    }

    /**
     * 获取物品的缓存稀有度
     * @param item 物品实例
     * @return 缓存的稀有度，如果不存在返回null
     */
    public static Integer getCachedRarity(Item item) {
        if (item == null) {
            return null;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return null;
        }
        return getCachedRarity(itemId);
    }

    /**
     * 缓存物品稀有度
     * @param itemId 物品资源位置
     * @param rarity 稀有度等级
     */
    public static void cacheRarity(ResourceLocation itemId, Integer rarity) {
        if (itemId == null || rarity == null) {
            return;
        }
        idCache.put(itemId, rarity);
    }

    /**
     * 缓存物品稀有度
     * @param item 物品实例
     * @param rarity 稀有度等级
     */
    public static void cacheRarity(Item item, Integer rarity) {
        if (item == null || rarity == null) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            cacheRarity(itemId, rarity);
        }
    }

    /**
     * 更新物品的缓存稀有度（用于编辑模式实时更新）
     * @param itemId 物品资源位置
     * @param rarity 新的稀有度等级
     */
    public static void updateRarity(ResourceLocation itemId, Integer rarity) {
        if (itemId == null) {
            return;
        }
        if (rarity != null) {
            idCache.put(itemId, rarity);
        } else {
            idCache.invalidate(itemId);
        }
    }

    /**
     * 更新物品的缓存稀有度（用于编辑模式实时更新）
     * @param item 物品实例
     * @param rarity 新的稀有度等级
     */
    public static void updateRarity(Item item, Integer rarity) {
        if (item == null) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            updateRarity(itemId, rarity);
        }
    }

    /**
     * 使指定物品的缓存失效
     * @param itemId 物品资源位置
     */
    public static void invalidate(ResourceLocation itemId) {
        if (itemId != null) {
            idCache.invalidate(itemId);
        }
    }

    /**
     * 重载缓存
     */
    public static void handleConfigReload() {
        long currentTime = System.currentTimeMillis();

        if (isReloading || (currentTime - lastReloadTime) < MIN_RELOAD_INTERVAL) {
            return;
        }

        synchronized (IdCacheManager.class) {
            if (isReloading || (currentTime - lastReloadTime) < MIN_RELOAD_INTERVAL) {
                return;
            }

            isReloading = true;
            lastReloadTime = currentTime;
        }

        try {
            idCache.invalidateAll();
            idCache = createCacheForReload();

            RarityCore.LOGGER.info("ID缓存系统重载完成 - 容量: {}", config.getActualMaxCacheSize());
        } finally {
            isReloading = false;
        }
    }

    private static Cache<ResourceLocation, Integer> createCacheForReload() {
        int actualCacheSize = config.getActualMaxCacheSize();

        return CacheBuilder.newBuilder()
            .maximumSize(actualCacheSize)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
    }

    /**
     * 获取缓存统计信息
     */
    public static IdCacheStatistics getStatistics() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0.0;

        return new IdCacheStatistics(
            idCache.size(),
            hits,
            misses,
            hitRate
        );
    }

    /**
     * 重置统计信息
     */
    public static void resetStatistics() {
        cacheHits.set(0);
        cacheMisses.set(0);
    }

    /**
     * ID缓存统计信息类
     */
    public static class IdCacheStatistics {
        private final long cacheSize;
        private final long hits;
        private final long misses;
        private final double hitRate;

        public IdCacheStatistics(long cacheSize, long hits, long misses, double hitRate) {
            this.cacheSize = cacheSize;
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
        }

        public long getCacheSize() { return cacheSize; }
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public double getHitRate() { return hitRate; }

        @Override
        public String toString() {
            return String.format("IdCacheStats{Size: %d, Hits: %d, Misses: %d, HitRate: %.1f%%}",
                cacheSize, hits, misses, hitRate);
        }
    }
}
