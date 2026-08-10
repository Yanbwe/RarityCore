package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.cache.DualCacheManager;

/**
 * 物品数据匹配缓存
 * 作为DualCacheManager的前端，提供统一的缓存接口
 */
public class SimpleItemDataCache {
    
    /**
     * 获取缓存的稀有度值
     * @param stack 物品堆
     * @return 缓存的稀有度,如果未缓存或未匹配到规则则返回 null
     */
    public static Integer getCachedRarity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        
        // 直接使用DualCacheManager的缓存
        return DualCacheManager.getCachedRarity(stack);
    }
    
    /**
     * 手动添加缓存条目
     * @param stack 物品堆
     * @param rarity 稀有度值
     */
    public static void put(ItemStack stack, Integer rarity) {
        if (stack != null && !stack.isEmpty()) {
            // 使用DualCacheManager的缓存
            DualCacheManager.cacheRarity(stack, rarity);
        }
    }
    
    /**
     * 清除指定物品的缓存
     * @param stack 物品堆
     */
    public static void invalidate(ItemStack stack) {
        // 暂时不实现，因为DualCacheManager没有提供单个物品的缓存清除方法
    }
    
    /**
     * 清空所有缓存
     */
    public static void invalidateAll() {
        // 调用DualCacheManager的重载方法来清空缓存
        DualCacheManager.handleConfigReload();
    }
    
    /**
     * 重新初始化缓存
     */
    public static void reinitializeCache() {
        // 调用DualCacheManager的重载方法来重新初始化缓存
        DualCacheManager.handleConfigReload();
    }
    
    /**
     * 获取缓存统计信息
     * @return 缓存大小
     */
    public static long getCacheSize() {
        // 返回DualCacheManager中物品数据缓存的大小
        return DualCacheManager.getStatistics().getCacheSize();
    }
}