package org.yanbwe.raritycore.client;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;

/**
 * 渲染缓存管理器（代理类）
 * 代理到改进的缓存管理器以保持向后兼容性
 */
public class RenderCacheManager {
    
    // 代理到改进的缓存管理器
    
    /**
     * 获取物品的缓存稀有度
     * @param item 物品
     * @return 稀有度值，如果未缓存则返回null
     */
    public static Integer getCachedRarity(Item item) {
        return ImprovedRenderCacheManager.getCachedRarity(item);
    }
    
    /**
     * 获取物品堆的缓存稀有度
     * @param itemStack 物品堆
     * @return 稀有度值，如果未缓存则返回null
     */
    public static Integer getCachedRarity(ItemStack itemStack) {
        return ImprovedRenderCacheManager.getCachedRarity(itemStack);
    }
    
    /**
     * 缓存物品稀有度
     * @param item 物品
     * @param rarity 稀有度
     */
    public static void cacheRarity(Item item, Integer rarity) {
        ImprovedRenderCacheManager.cacheRarity(item, rarity);
    }
    
    /**
     * 缓存物品堆稀有度
     * @param itemStack 物品堆
     * @param rarity 稀有度
     */
    public static void cacheItemStackRarity(ItemStack itemStack, Integer rarity) {
        ImprovedRenderCacheManager.cacheItemStackRarity(itemStack, rarity);
    }
    
    /**
     * 清空所有缓存
     */
    public static void clearAllCache() {
        ImprovedRenderCacheManager.clearAllCache();
    }
    
    /**
     * 清空特定物品的缓存
     * @param item 物品
     */
    public static void clearItemCache(Item item) {
        ImprovedRenderCacheManager.clearItemCache(item);
    }
    
    /**
     * 使特定物品的缓存失效
     * @param item 物品
     */
    public static void invalidateItemCache(Item item) {
        ImprovedRenderCacheManager.invalidateItemCache(item);
    }
    
    /**
     * 执行智能清理
     */
    public static void smartCleanup() {
        ImprovedRenderCacheManager.smartCleanup();
    }
    
    /**
     * 预加载缓存
     */
    public static void preloadCache() {
        ImprovedRenderCacheManager.preloadCache();
    }
    
    /**
     * 获取缓存统计信息
     */
    public static CacheStats getCacheStats() {
        ImprovedRenderCacheManager.CacheStats improvedStats = ImprovedRenderCacheManager.getCacheStats();
        return new CacheStats(
            improvedStats.getHits(),
            improvedStats.getMisses(),
            improvedStats.getClears(),
            (int)improvedStats.getRarityCacheSize(),
            (int)improvedStats.getItemStackCacheSize(),
            improvedStats.getHitRate()
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
            return String.format("CacheStats{hits=%d, misses=%d, hitRate=%.2f%%, " +
                               "rarityCache=%d, itemStackCache=%d, clears=%d}",
                hits, misses, hitRate, rarityCacheSize, itemStackCacheSize, clears);
        }
    }
}