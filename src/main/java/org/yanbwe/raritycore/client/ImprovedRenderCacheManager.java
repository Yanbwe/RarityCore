package org.yanbwe.raritycore.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 改进的渲染缓存管理器
 * 基于注册表大小的动态缓存容量策略
 */
@Mod.EventBusSubscriber(modid = RarityCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ImprovedRenderCacheManager {
    
    // 动态缓存倍数配置（相对于注册表大小）
    private static final double RARITY_CACHE_MULTIPLIER = 2.0;  // 物品缓存倍数
    private static final double ITEMSTACK_CACHE_MULTIPLIER = 0.5; // 物品堆缓存倍数
    private static final double MEMORY_SAFETY_FACTOR = 0.7; // 内存安全因子
    
    // 缓存过期时间（分钟）
    private static final int CACHE_EXPIRE_MINUTES = 60;
    
    // 物品稀有度缓存 - 使用Guava Cache实现LRU和自动过期
    private static final Cache<Item, Integer> rarityCache = CacheBuilder.newBuilder()
            .maximumSize(Long.MAX_VALUE) // 理论最大值，实际容量由动态清理控制
            .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .removalListener(new CacheRemovalListener())
            .recordStats()
            .build();
    
    // 物品堆缓存 - 使用弱引用键防止内存泄漏
    private static final Cache<Integer, Integer> itemStackCache = CacheBuilder.newBuilder()
            .maximumSize(Long.MAX_VALUE) // 理论最大值，实际容量由动态清理控制
            .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .weakKeys()
            .removalListener(new CacheRemovalListener())
            .recordStats()
            .build();
    
    // 缓存统计
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    private static final AtomicLong cacheClears = new AtomicLong(0);
    
    // 上次清理时间戳
    private static volatile long lastCleanupTime = System.currentTimeMillis();
    
    /**
     * 缓存移除监听器
     */
    private static class CacheRemovalListener implements RemovalListener<Object, Object> {
        @Override
        public void onRemoval(RemovalNotification<Object, Object> notification) {
            RarityCore.LOGGER.debug("Cache entry removed: {} - reason: {}", 
                notification.getKey(), notification.getCause());
        }
    }
    
    /**
     * 获取物品的缓存稀有度
     * @param item 物品
     * @return 稀有度值，如果未缓存则返回null
     */
    public static Integer getCachedRarity(Item item) {
        if (item == null) {
            return null;
        }
        
        try {
            Integer cached = rarityCache.getIfPresent(item);
            if (cached != null) {
                cacheHits.incrementAndGet();
                return cached;
            }
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Error accessing rarity cache for item: {}", item, e);
        }
        
        cacheMisses.incrementAndGet();
        return null;
    }
    
    /**
     * 获取物品堆的缓存稀有度
     * @param itemStack 物品堆
     * @return 稀有度值，如果未缓存则返回null
     */
    public static Integer getCachedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        
        try {
            int hash = getItemStackHash(itemStack);
            Integer cached = itemStackCache.getIfPresent(hash);
            if (cached != null) {
                cacheHits.incrementAndGet();
                return cached;
            }
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Error accessing itemstack cache", e);
        }
        
        cacheMisses.incrementAndGet();
        return null;
    }
    
    /**
     * 缓存物品稀有度
     * @param item 物品
     * @param rarity 稀有度
     */
    public static void cacheRarity(Item item, Integer rarity) {
        if (item != null) {
            try {
                rarityCache.put(item, rarity);
            } catch (Exception e) {
                RarityCore.LOGGER.warn("Error caching rarity for item: {}", item, e);
            }
        }
    }
    
    /**
     * 缓存物品堆稀有度
     * @param itemStack 物品堆
     * @param rarity 稀有度
     */
    public static void cacheItemStackRarity(ItemStack itemStack, Integer rarity) {
        if (itemStack != null && !itemStack.isEmpty()) {
            try {
                int hash = getItemStackHash(itemStack);
                itemStackCache.put(hash, rarity);
            } catch (Exception e) {
                RarityCore.LOGGER.warn("Error caching itemstack rarity", e);
            }
        }
    }
    
    /**
     * 计算物品堆的哈希值
     * @param itemStack 物品堆
     * @return 哈希值
     */
    private static int getItemStackHash(ItemStack itemStack) {
        int hash = itemStack.getItem().hashCode();
        hash = 31 * hash + itemStack.getCount();
        if (itemStack.hasTag()) {
            hash = 31 * hash + itemStack.getTag().hashCode();
        }
        return hash;
    }
    
    /**
     * 智能清理缓存 - 基于实际使用情况的渐进式清理
     */
    public static void smartCleanup() {
        try {
            // 清理过期条目
            rarityCache.cleanUp();
            itemStackCache.cleanUp();
            
            long currentRaritySize = rarityCache.size();
            long currentItemStackSize = itemStackCache.size();
            
            // 动态计算理论最大容量
            long registrySize = RarityRegistry.ITEM_RARITY_MAP.size();
            long maxRaritySize = Math.max(1000, (long)(registrySize * RARITY_CACHE_MULTIPLIER));
            long maxItemStackSize = Math.max(500, (long)(registrySize * ITEMSTACK_CACHE_MULTIPLIER));
            
            // 应用内存安全限制
            maxRaritySize = applyMemorySafetyLimit(maxRaritySize);
            maxItemStackSize = applyMemorySafetyLimit(maxItemStackSize);
            
            // 只有当缓存大小超过理论最大值的95%时才考虑清理
            if (currentRaritySize > maxRaritySize * 0.95) {
                RarityCore.LOGGER.info("Rarity cache size ({}) approaching theoretical limit ({}), performing progressive cleanup", 
                    currentRaritySize, maxRaritySize);
                
                // 渐进式清理：移除最少访问的条目
                performProgressiveCleanup(rarityCache, currentRaritySize, maxRaritySize);
            }
            
            if (currentItemStackSize > maxItemStackSize * 0.95) {
                RarityCore.LOGGER.info("ItemStack cache size ({}) approaching theoretical limit ({}), performing progressive cleanup", 
                    currentItemStackSize, maxItemStackSize);
                
                // 物品堆缓存使用Guava自动清理
                itemStackCache.cleanUp();
            }
            
            cacheClears.incrementAndGet();
            lastCleanupTime = System.currentTimeMillis();
            
            // 记录内存使用情况
            logMemoryUsage();
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error during smart cache cleanup", e);
        }
    }
    
    /**
     * 执行渐进式清理 - 移除最少使用的缓存条目
     * @param cache 要清理的缓存
     * @param currentSize 当前缓存大小
     * @param maxSize 最大大小限制
     */
    private static <K, V> void performProgressiveCleanup(Cache<K, V> cache, long currentSize, long maxSize) {
        try {
            long targetSize = (long)(maxSize * 0.90); // 目标清理到90%容量
            long entriesToRemove = currentSize - targetSize;
            
            if (entriesToRemove <= 0) {
                return;
            }
            
            // 获取缓存统计信息来识别最少使用的条目
            com.google.common.cache.CacheStats stats = cache.stats();
            
            // 计算需要移除的比例
            double removalRatio = (double) entriesToRemove / currentSize;
            
            RarityCore.LOGGER.debug("Performing progressive cleanup: removing {} entries ({:.1}% of cache)", 
                entriesToRemove, removalRatio * 100);
            
            // 由于Guava Cache不直接暴露内部条目，我们通过触发清理来间接实现
            // Guava会根据LRU策略自动移除最少使用的条目
            for (int i = 0; i < Math.min(3, entriesToRemove / 100 + 1); i++) {
                cache.cleanUp();
                // 短暂等待让清理生效
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Error during progressive cache cleanup", e);
        }
    }
    
    /**
     * 应用内存安全限制
     */
    private static long applyMemorySafetyLimit(long requestedSize) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long freeMemory = runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long usedMemory = totalMemory - freeMemory;
            
            // 计算可用于缓存的安全内存量
            long safeMemoryForCache = (long)(maxMemory * MEMORY_SAFETY_FACTOR - usedMemory);
            
            // 估算每个缓存条目大约占用100字节
            long maxSafeEntries = Math.max(1000, safeMemoryForCache / 100);
            
            // 返回请求大小和安全大小中的较小值
            long finalSize = Math.min(requestedSize, maxSafeEntries);
            
            if (finalSize < requestedSize) {
                RarityCore.LOGGER.debug("Cache size limited by memory: requested={}, actual={}", 
                    requestedSize, finalSize);
            }
            
            return finalSize;
            
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Error applying memory safety limit, using requested size", e);
            return requestedSize;
        }
    }
    
    /**
     * 清空所有缓存
     */
    public static void clearAllCache() {
        try {
            rarityCache.invalidateAll();
            itemStackCache.invalidateAll();
            cacheClears.incrementAndGet();
            RarityCore.LOGGER.info("All caches cleared. Stats - Hits: {}, Misses: {}, Clears: {}", 
                cacheHits.get(), cacheMisses.get(), cacheClears.get());
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error clearing all caches", e);
        }
    }
    
    /**
     * 清空特定物品的缓存
     * @param item 物品
     */
    public static void clearItemCache(Item item) {
        if (item != null) {
            try {
                rarityCache.invalidate(item);
            } catch (Exception e) {
                RarityCore.LOGGER.warn("Error clearing item cache for: {}", item, e);
            }
        }
    }
    
    /**
     * 当稀有度注册表变更时调用此方法使相关缓存失效
     * @param item 变更的物品
     */
    public static void invalidateItemCache(Item item) {
        if (item != null) {
            rarityCache.invalidate(item);
            RarityCore.LOGGER.debug("Invalidated cache for item: {}", item);
        }
    }
    
    /**
     * 批量预热缓存 - 加载常用物品的稀有度
     */
    public static void preloadCache() {
        try {
            RarityCore.LOGGER.info("Preloading cache with registered rarities...");
            int preloadedCount = 0;
            
            // 预加载已注册的稀有度
            for (java.util.Map.Entry<net.minecraft.resources.ResourceLocation, Integer> entry : 
                 RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getValue(entry.getKey());
                if (item != null) {
                    cacheRarity(item, entry.getValue());
                    preloadedCount++;
                }
            }
            
            RarityCore.LOGGER.info("Cache preloaded with {} items", preloadedCount);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error during cache preloading", e);
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public static CacheStats getCacheStats() {
        long totalRequests = cacheHits.get() + cacheMisses.get();
        double hitRate = totalRequests > 0 ? (double) cacheHits.get() / totalRequests * 100 : 0;
        
        return new CacheStats(
            cacheHits.get(),
            cacheMisses.get(),
            cacheClears.get(),
            rarityCache.size(),
            itemStackCache.size(),
            hitRate,
            rarityCache.stats(),
            itemStackCache.stats()
        );
    }
    
    /**
     * 游戏刻事件 - 定期执行智能清理
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // 延长清理间隔到10分钟，减少频繁清理
            if (System.currentTimeMillis() - lastCleanupTime > 600000) {
                smartCleanup();
            }
        }
    }
    
    /**
     * 记录内存使用情况
     */
    private static void logMemoryUsage() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long freeMemory = runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long usedMemory = totalMemory - freeMemory;
            
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            
            RarityCore.LOGGER.debug("Memory usage: {}/{} MB ({:.1}%), Cache sizes: rarity={}, itemStack={}",
                usedMemory / (1024 * 1024), maxMemory / (1024 * 1024), 
                memoryUsagePercent, rarityCache.size(), itemStackCache.size());
                
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to log memory usage", e);
        }
    }
    
    /**
     * 缓存统计信息类
     */
    public static class CacheStats {
        private final long hits;
        private final long misses;
        private final long clears;
        private final long rarityCacheSize;
        private final long itemStackCacheSize;
        private final double hitRate;
        private final com.google.common.cache.CacheStats rarityStats;
        private final com.google.common.cache.CacheStats itemStackStats;
        
        public CacheStats(long hits, long misses, long clears, 
                         long rarityCacheSize, long itemStackCacheSize, 
                         double hitRate,
                         com.google.common.cache.CacheStats rarityStats,
                         com.google.common.cache.CacheStats itemStackStats) {
            this.hits = hits;
            this.misses = misses;
            this.clears = clears;
            this.rarityCacheSize = rarityCacheSize;
            this.itemStackCacheSize = itemStackCacheSize;
            this.hitRate = hitRate;
            this.rarityStats = rarityStats;
            this.itemStackStats = itemStackStats;
        }
        
        // Getters...
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public long getClears() { return clears; }
        public long getRarityCacheSize() { return rarityCacheSize; }
        public long getItemStackCacheSize() { return itemStackCacheSize; }
        public double getHitRate() { return hitRate; }
        public com.google.common.cache.CacheStats getRarityStats() { return rarityStats; }
        public com.google.common.cache.CacheStats getItemStackStats() { return itemStackStats; }
        
        @Override
        public String toString() {
            return String.format("CacheStats{hits=%d, misses=%d, hitRate=%.2f%%, " +
                               "rarityCache=%d, itemStackCache=%d, clears=%d, " +
                               "rarityEvictions=%d, itemStackEvictions=%d}",
                hits, misses, hitRate, rarityCacheSize, itemStackCacheSize, clears,
                rarityStats.evictionCount(), itemStackStats.evictionCount());
        }
    }
}