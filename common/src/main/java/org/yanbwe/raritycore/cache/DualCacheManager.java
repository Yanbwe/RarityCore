package org.yanbwe.raritycore.cache;

import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;

/**
 * 双层缓存管理器（适配器模式）
 * 委托所有操作给RarityCacheCoordinator以保持API兼容性
 * 内部使用ID缓存和组件缓存的两级缓存结构
 */
public class DualCacheManager {

    private static volatile CacheConfig config;

    public static CacheConfig getConfig() {
        return config;
    }

    public static void initialize() {
        config = new CacheConfig();
        RarityCacheCoordinator.initialize();
        RarityCore.LOGGER.info("双层缓存系统初始化完成 - 容量: {}",
            config.getActualMaxCacheSize());
    }

    /**
     * 获取物品的缓存稀有度
     * @param itemStack 物品堆
     * @return 缓存的稀有度，如果不存在返回null
     */
    public static Integer getCachedRarity(ItemStack itemStack) {
        return RarityCacheCoordinator.getCachedRarity(itemStack);
    }

    /**
     * 缓存物品稀有度
     * @param itemStack 物品堆
     * @param rarity 稀有度等级
     */
    public static void cacheRarity(ItemStack itemStack, Integer rarity) {
        RarityCacheCoordinator.cacheRarity(itemStack, rarity);
    }

    /**
     * 更新物品的ID缓存稀有度（用于编辑模式实时更新）
     * @param itemStack 物品堆
     * @param rarity 新的稀有度等级
     */
    public static void updateIdCache(ItemStack itemStack, Integer rarity) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }
        RarityCacheCoordinator.updateIdCache(itemStack.getItem(), rarity);
    }

    /**
     * 重载缓存
     */
    public static void handleConfigReload() {
        RarityCacheCoordinator.handleConfigReload();
    }

    /**
     * 获取缓存统计信息
     */
    public static CacheStatistics getStatistics() {
        RarityCacheCoordinator.CombinedCacheStatistics combinedStats = 
            RarityCacheCoordinator.getStatistics();
        return new CacheStatistics(
            combinedStats.getTotalSize(),
            combinedStats.getOverallHitRate()
        );
    }

    /**
     * 缓存统计信息类
     */
    public static class CacheStatistics {
        private final long cacheSize;
        private final double overallHitRate;

        public CacheStatistics(long cacheSize, double overallHitRate) {
            this.cacheSize = cacheSize;
            this.overallHitRate = overallHitRate;
        }

        public long getCacheSize() { return cacheSize; }
        public double getOverallHitRate() { return overallHitRate; }

        @Override
        public String toString() {
            return String.format("CacheStats{Size: %d, Overall: %.1f%%}",
                cacheSize, overallHitRate);
        }
    }
}
