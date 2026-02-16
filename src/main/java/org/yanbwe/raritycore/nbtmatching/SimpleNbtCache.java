package org.yanbwe.raritycore.nbtmatching;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 简化版NBT匹配缓存
 * 提供高效的物品稀有度缓存机制，支持动态容量调整
 */
public class SimpleNbtCache {
    
    private static Cache<ItemStack, Integer> itemCache;
    private static final int BASE_CACHE_SIZE = 100;  // 基础缓存大小
    private static final int MAX_CACHE_SIZE = 2000;   // 最大缓存大小
    
    static {
        initializeCache();
    }
    
    /**
     * 初始化缓存，根据配置数量动态调整大小
     */
    private static void initializeCache() {
        int dynamicSize = calculateDynamicCacheSize();
        itemCache = CacheBuilder.newBuilder()
            .maximumSize(dynamicSize)
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();
        
        RarityCore.LOGGER.info("NBT匹配缓存初始化完成，容量: {}", dynamicSize);
    }
    
    /**
     * 根据配置规则数量计算动态缓存大小
     * @return 计算得出的缓存大小
     */
    private static int calculateDynamicCacheSize() {
        int ruleCount = NbtRarityMatcher.getRuleCount();
        // 基础大小 + 规则数量 × 10，但不超过最大限制
        int calculatedSize = BASE_CACHE_SIZE + (ruleCount * 10);
        return Math.min(calculatedSize, MAX_CACHE_SIZE);
    }
    
    /**
     * 重新初始化缓存（在配置重载后调用）
     */
    public static void reinitializeCache() {
        itemCache.invalidateAll();
        initializeCache();
        RarityCore.LOGGER.info("NBT匹配缓存已重新初始化");
    }
    
    /**
     * 获取缓存的稀有度值
     * @param stack 物品堆
     * @return 缓存的稀有度，如果未缓存或未匹配到规则则返回null
     */
    public static Integer getCachedRarity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        
        try {
            Integer result = itemCache.get(stack, () -> calculateRarity(stack));
            // 将特殊值-1转换回null
            return result != -1 ? result : null;
        } catch (ExecutionException e) {
            RarityCore.LOGGER.debug("缓存获取失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 直接计算稀有度（不使用缓存）
     * @param stack 物品堆
     * @return 计算得到的稀有度，如果无法计算则返回-1（特殊值）
     */
    private static Integer calculateRarity(ItemStack stack) {
        Integer result = NbtRarityMatcher.calculateWithoutCache(stack);
        // Guava缓存不允许返回null，返回-1表示未找到匹配
        return result != null ? result : -1;
    }
    
    /**
     * 手动添加缓存条目
     * @param stack 物品堆
     * @param rarity 稀有度值
     */
    public static void put(ItemStack stack, Integer rarity) {
        if (stack != null && !stack.isEmpty() && rarity != null) {
            itemCache.put(stack, rarity);
        }
    }
    
    /**
     * 清除指定物品的缓存
     * @param stack 物品堆
     */
    public static void invalidate(ItemStack stack) {
        itemCache.invalidate(stack);
    }
    
    /**
     * 清空所有缓存
     */
    public static void invalidateAll() {
        itemCache.invalidateAll();
        RarityCore.LOGGER.debug("NBT匹配缓存已清空");
    }
    
    /**
     * 获取缓存统计信息
     * @return 缓存大小
     */
    public static long getCacheSize() {
        return itemCache.size();
    }
}