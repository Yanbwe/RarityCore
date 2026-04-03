package org.yanbwe.raritycore.cache;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.registry.RarityRegistry;

/**
 * 渲染缓存管理器 - 适配器模式
 * 委托所有操作给DualCacheManager以保持API兼容性
 */
public class RenderCacheManager {

    /**
     * 获取物品的缓存稀有度
     */
    public static Integer getCachedRarity(Item item) {
        if (item == null) return null;
        return getCachedRarity(new ItemStack(item));
    }

    /**
     * 获取物品堆的缓存稀有度
     * 如果缓存未命中，会调用RarityRegistry.getRarity()计算稀有度并缓存
     */
    public static Integer getCachedRarity(ItemStack itemStack) {
        Integer cachedRarity = DualCacheManager.getCachedRarity(itemStack);
        if (cachedRarity != null) {
            return cachedRarity;
        }

        Integer rarity = RarityRegistry.getRarity(itemStack);
        if (rarity != null) {
            cacheItemStackRarity(itemStack, rarity);
        }
        return rarity;
    }

    /**
     * 缓存物品稀有度
     */
    public static void cacheRarity(Item item, Integer rarity) {
        if (item == null || rarity == null) return;
        DualCacheManager.cacheRarity(new ItemStack(item), rarity);
    }

    /**
     * 缓存物品堆稀有度
     */
    public static void cacheItemStackRarity(ItemStack itemStack, Integer rarity) {
        DualCacheManager.cacheRarity(itemStack, rarity);
    }

    /**
     * 清空所有缓存
     */
    public static void clearAllCache() {
        DualCacheManager.handleConfigReload();
    }

    /**
     * 获取缓存统计信息
     */
    public static CacheStats getCacheStats() {
        DualCacheManager.CacheStatistics dualStats = DualCacheManager.getStatistics();
        return new CacheStats(
            0, // hits - 通过DualCacheManager内部统计
            0, // misses - 通过DualCacheManager内部统计
            0, // clears - 暂时为0
            (int) dualStats.getIdCacheSize(),
            (int) dualStats.getNbtCacheSize(),
            dualStats.getOverallHitRate()
        );
    }
    
    /**
     * 缓存统计信息类（保持向后兼容）
     */
    public static class CacheStats {
        private final long hits;
        private final long misses;
        private final long clears;
        private final int rarityCacheSize;
        private final int itemStackCacheSize;
        private final double hitRate;
        
        public CacheStats(long hits, long misses, long clears, 
                         int rarityCacheSize, int itemStackCacheSize, double hitRate) {
            this.hits = hits;
            this.misses = misses;
            this.clears = clears;
            this.rarityCacheSize = rarityCacheSize;
            this.itemStackCacheSize = itemStackCacheSize;
            this.hitRate = hitRate;
        }
        
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public long getClears() { return clears; }
        public int getRarityCacheSize() { return rarityCacheSize; }
        public int getItemStackCacheSize() { return itemStackCacheSize; }
        public double getHitRate() { return hitRate; }
        
        @Override
        public String toString() {
            return String.format("CacheStats{hitRate=%.2f%%, rarityCache=%d, itemStackCache=%d}", 
                hitRate, rarityCacheSize, itemStackCacheSize);
        }
    }
}
