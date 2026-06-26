package org.yanbwe.raritycore.cache;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
        return DualCacheManager.getCachedRarity(new ItemStack(item));
    }
    
    /**
     * 获取物品堆的缓存稀有度
     */
    public static Integer getCachedRarity(ItemStack itemStack) {
        return DualCacheManager.getCachedRarity(itemStack);
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
     * 获取完整的缓存统计信息（含两级缓存明细）
     */
    public static CacheStats getCacheStats() {
        RarityCacheCoordinator.CombinedCacheStatistics combined = 
            RarityCacheCoordinator.getStatistics();
        return new CacheStats(
            combined.getIdCacheSize(),
            combined.getComponentCacheSize(),
            combined.getTotalSize(),
            combined.getIdCacheHitRate(),
            combined.getComponentCacheHitRate(),
            combined.getOverallHitRate()
        );
    }
    
    /**
     * 缓存统计信息类（两级缓存架构）
     */
    public static class CacheStats {
        private final long idCacheSize;
        private final long componentCacheSize;
        private final long totalSize;
        private final double idCacheHitRate;
        private final double componentCacheHitRate;
        private final double overallHitRate;
        
        public CacheStats(long idCacheSize, long componentCacheSize, long totalSize,
                         double idCacheHitRate, double componentCacheHitRate, double overallHitRate) {
            this.idCacheSize = idCacheSize;
            this.componentCacheSize = componentCacheSize;
            this.totalSize = totalSize;
            this.idCacheHitRate = idCacheHitRate;
            this.componentCacheHitRate = componentCacheHitRate;
            this.overallHitRate = overallHitRate;
        }
        
        public long getIdCacheSize() { return idCacheSize; }
        public long getComponentCacheSize() { return componentCacheSize; }
        public long getTotalSize() { return totalSize; }
        public double getIdCacheHitRate() { return idCacheHitRate; }
        public double getComponentCacheHitRate() { return componentCacheHitRate; }
        public double getOverallHitRate() { return overallHitRate; }
        
        // 向后兼容的 getter（旧代码可能依赖这些）
        public long getHits() { return 0; }
        public long getMisses() { return 0; }
        public long getClears() { return 0; }
        public int getRarityCacheSize() { return (int) idCacheSize; }
        public int getItemStackCacheSize() { return (int) componentCacheSize; }
        public double getHitRate() { return overallHitRate; }
        
        @Override
        public String toString() {
            return String.format("CacheStats{ID: %d (%.1f%%), Component: %d (%.1f%%), Total: %d, Overall: %.1f%%}",
                idCacheSize, idCacheHitRate, componentCacheSize, componentCacheHitRate, totalSize, overallHitRate);
        }
    }
}
