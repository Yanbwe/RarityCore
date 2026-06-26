package org.yanbwe.raritycore.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 组件缓存管理器
 * 基于物品ID + 组件哈希的缓存系统
 * 仅当物品有组件匹配规则时才使用此缓存
 */
public class ComponentCacheManager {

    private static volatile Cache<String, Integer> componentCache;

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
        RarityCore.LOGGER.info("组件缓存系统初始化完成 - 容量: {}", config.getActualMaxCacheSize());
    }

    private static void createCache() {
        int actualCacheSize = config.getActualMaxCacheSize();

        componentCache = CacheBuilder.newBuilder()
            .maximumSize(actualCacheSize)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

        RarityCore.LOGGER.info("组件缓存创建完成，动态容量: {} 条目", actualCacheSize);
    }

    /**
     * 获取物品堆的缓存稀有度
     * @param itemStack 物品堆
     * @return 缓存的稀有度，如果不存在返回null
     */
    public static Integer getCachedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        String cacheKey = generateComponentCacheKey(itemStack);
        if (cacheKey == null) {
            return null;
        }

        Integer result = componentCache.getIfPresent(cacheKey);
        if (result != null) {
            cacheHits.incrementAndGet();
            return result;
        }

        cacheMisses.incrementAndGet();
        return null;
    }

    /**
     * 缓存物品堆稀有度
     * @param itemStack 物品堆
     * @param rarity 稀有度等级
     */
    public static void cacheRarity(ItemStack itemStack, Integer rarity) {
        if (itemStack == null || itemStack.isEmpty() || rarity == null) {
            return;
        }

        String cacheKey = generateComponentCacheKey(itemStack);
        if (cacheKey != null) {
            componentCache.put(cacheKey, rarity);
        }
    }

    /**
     * 使指定物品堆的缓存失效
     * @param itemStack 物品堆
     */
    public static void invalidate(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        String cacheKey = generateComponentCacheKey(itemStack);
        if (cacheKey != null) {
            componentCache.invalidate(cacheKey);
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

        synchronized (ComponentCacheManager.class) {
            if (isReloading || (currentTime - lastReloadTime) < MIN_RELOAD_INTERVAL) {
                return;
            }

            isReloading = true;
            lastReloadTime = currentTime;
        }

        try {
            componentCache.invalidateAll();
            componentCache = createCacheForReload();

            RarityCore.LOGGER.info("组件缓存系统重载完成 - 容量: {}", config.getActualMaxCacheSize());
        } finally {
            isReloading = false;
        }
    }

    private static Cache<String, Integer> createCacheForReload() {
        int actualCacheSize = config.getActualMaxCacheSize();

        return CacheBuilder.newBuilder()
            .maximumSize(actualCacheSize)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
    }

    /**
     * 生成组件缓存键（性能优化版）
     * 格式: itemId|hash:MD5哈希值
     * 基于组件补丁生成哈希，区分不同组件配置的物品堆
     * @param itemStack 物品堆
     * @return 缓存键，如果无法生成返回null
     */
    private static String generateComponentCacheKey(ItemStack itemStack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null) {
            return null;
        }

        StringBuilder key = new StringBuilder(itemId.toString());

        try {
            var componentsPatch = itemStack.getComponentsPatch();
            if (componentsPatch != null && !componentsPatch.isEmpty()) {
                String componentsString = componentsPatch.toString();
                if (componentsString != null && !componentsString.isEmpty()) {
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                    byte[] hash = md.digest(componentsString.getBytes());
                    StringBuilder hexString = new StringBuilder();
                    for (byte b : hash) {
                        hexString.append(String.format("%02x", b));
                    }
                    key.append("|hash:").append(hexString.toString());
                }
            }
        } catch (IllegalStateException e) {
            RarityCore.LOGGER.debug("生成组件缓存键时出错(注册表访问问题，仅使用物品ID): {}", e.getMessage());
        } catch (Exception e) {
            RarityCore.LOGGER.debug("生成组件缓存键时出错: {}", e.getMessage());
        }

        return key.toString();
    }

    /**
     * 检查物品堆是否有非平凡组件数据(超出空补丁)
     * 用于判断是否应该使用组件缓存而非ID缓存
     * @param itemStack 物品堆
     * @return 如果有非平凡组件数据返回true
     */
    public static boolean hasNonTrivialData(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        try {
            var componentsPatch = itemStack.getComponentsPatch();
            return componentsPatch != null && !componentsPatch.isEmpty();
        } catch (IllegalStateException e) {
            RarityCore.LOGGER.debug("检查组件数据时出错(注册表访问问题): {}", e.getMessage());
        } catch (Exception e) {
            RarityCore.LOGGER.debug("检查组件数据时出错: {}", e.getMessage());
        }

        return false;
    }

    /**
     * 获取缓存统计信息
     */
    public static ComponentCacheStatistics getStatistics() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0.0;

        return new ComponentCacheStatistics(
            componentCache.size(),
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
     * 组件缓存统计信息类
     */
    public static class ComponentCacheStatistics {
        private final long cacheSize;
        private final long hits;
        private final long misses;
        private final double hitRate;

        public ComponentCacheStatistics(long cacheSize, long hits, long misses, double hitRate) {
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
            return String.format("ComponentCacheStats{Size: %d, Hits: %d, Misses: %d, HitRate: %.1f%%}",
                cacheSize, hits, misses, hitRate);
        }
    }
}
