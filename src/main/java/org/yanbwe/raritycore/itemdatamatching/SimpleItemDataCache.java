package org.yanbwe.raritycore.itemdatamatching;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;

import java.util.concurrent.TimeUnit;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

/**
 * 简化版物品数据匹配缓存
 * 提供高效的物品稀有度缓存机制,支持动态容量调整
 */
public class SimpleItemDataCache {
    
    /**
     * 缓存键类
     * 将除了id和count外的内容作为缓存键
     */
    private static class CacheKey {
        private final net.minecraft.resources.ResourceLocation itemId;
        private final net.minecraft.nbt.CompoundTag customData;
        
        public CacheKey(net.minecraft.world.item.ItemStack stack) {
            this.itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            this.customData = data.isEmpty() ? null : data.copyTag();
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return itemId.equals(cacheKey.itemId) && 
                   (customData == null ? cacheKey.customData == null : customData.equals(cacheKey.customData));
        }
        
        @Override
        public int hashCode() {
            int result = itemId.hashCode();
            result = 31 * result + (customData != null ? customData.hashCode() : 0);
            return result;
        }
    }
    
    private static Cache<CacheKey, Integer> itemCache;
    private static final int BASE_CACHE_SIZE = 100;  // 基础缓存大小
    private static final int MAX_CACHE_SIZE = 2000;   // 最大缓存大小
    
    static {
        initializeCache();
    }
    
    /**
     * 初始化缓存,根据配置数量动态调整大小
     */
    private static void initializeCache() {
        int dynamicSize = calculateDynamicCacheSize();
        itemCache = CacheBuilder.newBuilder()
            .maximumSize(dynamicSize)
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();
        
        RarityCore.LOGGER.info("Item data cache initialized with capacity: {}", dynamicSize);
    }
    
    /**
     * 根据配置规则数量计算动态缓存大小
     * @return 计算得出的缓存大小
     */
    private static int calculateDynamicCacheSize() {
        int ruleCount = ItemDataRarityMatcher.getRuleCount();
        // 基础大小 + 规则数量 × 10,但不超过最大限制
        int calculatedSize = BASE_CACHE_SIZE + (ruleCount * 10);
        return Math.min(calculatedSize, MAX_CACHE_SIZE);
    }
    
    /**
     * 重新初始化缓存(在配置重载后调用)
     */
    public static void reinitializeCache() {
        itemCache.invalidateAll();
        initializeCache();
        RarityCore.LOGGER.info("Item data cache reinitialized");
    }
    
    /**
     * 获取缓存的稀有度值(非阻塞版本)
     * @param stack 物品堆
     * @return 缓存的稀有度,如果未缓存或未匹配到规则则返回 null
     */
    public static Integer getCachedRarity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
            
        CacheKey key = new CacheKey(stack);
        // 先尝试从缓存获取,不阻塞
        Integer result = itemCache.getIfPresent(key);
        if (result != null) {
            // 将特殊值 -1 转换回 null
            return result != -1 ? result : null;
        }
            
        // 缓存未命中,直接计算但不填充缓存(避免阻塞渲染线程)
        // 让 RarityRegistry 的计算结果来填充缓存
        return null;
    }
    
    /**
     * 直接计算稀有度(不使用缓存)
     * @param stack 物品堆
     * @return 计算得到的稀有度,如果无法计算则返回-1(特殊值)
     */
    private static Integer calculateRarity(ItemStack stack) {
        Integer result = ItemDataRarityMatcher.calculateWithoutCache(stack);
        // Guava缓存不允许返回null,返回-1表示未找到匹配
        return result != null ? result : -1;
    }
    
    /**
     * 手动添加缓存条目(异步填充)
     * @param stack 物品堆
     * @param rarity 稀有度值
     */
    public static void put(ItemStack stack, Integer rarity) {
        if (stack != null && !stack.isEmpty()) {
            CacheKey key = new CacheKey(stack);
            if (rarity != null) {
                itemCache.put(key, rarity);
            } else {
                // 存储 -1 表示未找到匹配,避免重复计算
                itemCache.put(key, -1);
            }
        }
    }
    
    /**
     * 清除指定物品的缓存
     * @param stack 物品堆
     */
    public static void invalidate(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            CacheKey key = new CacheKey(stack);
            itemCache.invalidate(key);
        }
    }
    
    /**
     * 清空所有缓存
     */
    public static void invalidateAll() {
        itemCache.invalidateAll();
        // Item data cache cleared
    }
    
    /**
     * 获取缓存统计信息
     * @return 缓存大小
     */
    public static long getCacheSize() {
        return itemCache.size();
    }
}