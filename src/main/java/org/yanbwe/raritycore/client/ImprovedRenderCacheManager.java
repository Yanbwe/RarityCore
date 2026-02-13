package org.yanbwe.raritycore.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 渲染缓存管理器
 * 基于注册表大小的动态缓存容量策略
 */
@Mod.EventBusSubscriber(modid = RarityCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ImprovedRenderCacheManager {
    
    // 动态缓存倍数配置（相对于注册表大小）
    private static final double RARITY_CACHE_MULTIPLIER = 1.5;   // 物品缓存倍数
    private static final double ITEMSTACK_CACHE_MULTIPLIER = 0.3; // 物品堆缓存倍数
    private static final double MEMORY_SAFETY_FACTOR = 0.6;      // 内存安全因子
    
    // 缓存容量配置
    private static final long INITIAL_RARITY_CAPACITY = 3000;    // 物品缓存初始容量
    private static final long INITIAL_ITEMSTACK_CAPACITY = 1000; // 物品堆缓存初始容量
    private static final long MIN_DYNAMIC_CAPACITY = 1000;       // 动态调整最小容量
    private static final long MAX_DYNAMIC_CAPACITY = 50000;      // 动态调整最大容量
    
    // 自适应容量调整参数
    private static final double CAPACITY_GROWTH_FACTOR = 1.2;    // 容量增长因子
    private static final double CAPACITY_SHRINK_FACTOR = 0.8;    // 容量收缩因子
    private static volatile long currentRarityCapacity = INITIAL_RARITY_CAPACITY;
    private static volatile long currentItemStackCapacity = INITIAL_ITEMSTACK_CAPACITY;
    
    // 分级清理阈值
    private static final double WARNING_THRESHOLD = 0.80;  // 80%警告
    private static final double PREPARE_THRESHOLD = 0.90;  // 90%准备清理
    private static final double FORCE_THRESHOLD = 0.95;    // 95%强制清理
    
    // 缓存过期时间（分钟）
    private static final int CACHE_EXPIRE_MINUTES = 60;
    
    // 物品稀有度缓存 - 使用Guava Cache实现LRU和自动过期
    private static Cache<Item, Integer> rarityCache = createRarityCache(INITIAL_RARITY_CAPACITY);
    
    // 物品堆缓存 - 使用弱引用键防止内存泄漏
    private static Cache<Integer, Integer> itemStackCache = createItemStackCache(INITIAL_ITEMSTACK_CAPACITY);
    
    /**
     * 创建物品稀有度缓存
     */
    private static Cache<Item, Integer> createRarityCache(long capacity) {
        return CacheBuilder.newBuilder()
                .maximumSize(capacity)
                .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .expireAfterAccess(20, TimeUnit.MINUTES)
                .removalListener(new CacheRemovalListener())
                .recordStats()
                .build();
    }
    
    /**
     * 创建物品堆缓存
     */
    private static Cache<Integer, Integer> createItemStackCache(long capacity) {
        return CacheBuilder.newBuilder()
                .maximumSize(capacity)
                .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .weakKeys()
                .removalListener(new CacheRemovalListener())
                .recordStats()
                .build();
    }
    
    // 缓存统计
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    private static final AtomicLong cacheClears = new AtomicLong(0);
    
    // 上次清理时间戳
    private static volatile long lastCleanupTime = System.currentTimeMillis();
    
    // 内存估算相关
    private static volatile double averageEntrySizeBytes = 100.0; // 初始估算：每个条目100字节
    private static final int CALIBRATION_INTERVAL = 1000; // 校准间隔（毫秒）
    private static volatile long lastCalibrationTime = System.currentTimeMillis();
    private static final AtomicLong totalCalibrationSamples = new AtomicLong(0);
    private static final AtomicLong totalMemoryUsed = new AtomicLong(0);
    
    // 智能预加载相关
    private static final AtomicLong lastPreloadTime = new AtomicLong(System.currentTimeMillis());
    private static final long PRELOAD_INTERVAL = TimeUnit.HOURS.toMillis(1); // 1小时预加载间隔
    
    // 线程安全锁
    private static final Object cleanupLock = new Object();
    private static final Object preloadLock = new Object();
    private static final Object executorLock = new Object();
    private static volatile ExecutorService cacheExecutor = null;
    private static final AtomicBoolean isShutdown = new AtomicBoolean(false);
    
    // 线程池配置
    private static final int CORE_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 2;
    private static final long KEEP_ALIVE_TIME = 60L;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;
    
    // 缓存系统开关状态
    private static volatile boolean cacheSystemEnabled = true;
    
    /**
     * 缓存移除监听器
     */
    private static class CacheRemovalListener implements RemovalListener<Object, Object> {
        @Override
        public void onRemoval(RemovalNotification<Object, Object> notification) {
            // 移除DEBUG日志以减少日志污染
            // RarityCore.LOGGER.debug("Cache entry removed: {} - reason: {}", 
            //     notification.getKey(), notification.getCause());
        }
    }
    
    /**
     * 获取缓存系统是否启用
     */
    public static boolean isCacheSystemEnabled() {
        return cacheSystemEnabled;
    }
    
    /**
     * 设置缓存系统启用状态
     */
    public static void setCacheSystemEnabled(boolean enabled) {
        boolean wasEnabled = cacheSystemEnabled;
        cacheSystemEnabled = enabled;
        
        RarityCore.LOGGER.info("Cache system {}", enabled ? "enabled" : "disabled");
        
        // 如果从禁用变为启用，预加载缓存
        if (enabled && !wasEnabled) {
            preloadCache();
        }
        // 如果从启用变为禁用，清空缓存和重置计数器
        else if (!enabled && wasEnabled) {
            clearAllCache();
            resetCounters(); // 重置计数器
        }
    }
    
    /**
     * 重置缓存计数器
     */
    private static void resetCounters() {
        cacheHits.set(0);
        cacheMisses.set(0);
        cacheClears.set(0);
        RarityCore.LOGGER.debug("Cache counters reset");
    }
    
    /**
     * 缓存健康检查
     * @return true如果缓存系统健康，false如果存在问题
     */
    public static boolean isCacheHealthy() {
        try {
            // 检查缓存是否能够正常响应
            rarityCache.cleanUp();
            itemStackCache.cleanUp();
            
            // 检查统计信息是否正常
            com.google.common.cache.CacheStats rarityStats = rarityCache.stats();
            com.google.common.cache.CacheStats itemStackStats = itemStackCache.stats();
            
            // 检查是否有异常高的异常率
            long totalRarityOps = rarityStats.hitCount() + rarityStats.missCount();
            long totalItemStackOps = itemStackStats.hitCount() + itemStackStats.missCount();
            
            if (totalRarityOps > 0 && (double) rarityStats.missCount() / totalRarityOps > 0.95) {
                RarityCore.LOGGER.warn("Rarity cache health warning: very high miss rate ({:.1}%)", 
                    (double) rarityStats.missCount() / totalRarityOps * 100);
            }
            
            if (totalItemStackOps > 0 && (double) itemStackStats.missCount() / totalItemStackOps > 0.95) {
                RarityCore.LOGGER.warn("ItemStack cache health warning: very high miss rate ({:.1}%)", 
                    (double) itemStackStats.missCount() / totalItemStackOps * 100);
            }
            
            // 检查内存使用情况
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            double memoryUsage = (double) usedMemory / runtime.maxMemory();
            
            if (memoryUsage > 0.9) { // 90%内存使用率警告
                RarityCore.LOGGER.warn("High memory usage detected: {:.1}% - cache performance may be affected", 
                    memoryUsage * 100);
            }
            
            return true;
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cache health check failed - potential cache corruption", e);
            return false;
        }
    }
    
    /**
     * 执行缓存完整性检查
     */
    public static CacheHealthReport performHealthCheck() {
        try {
            boolean isHealthy = isCacheHealthy();
            
            // 收集详细统计信息
            CacheStats currentStats = getCacheStats();
            MemoryEstimationStats memoryStats = getMemoryEstimationStats();
            
            long totalEntries = currentStats.getRarityCacheSize() + currentStats.getItemStackCacheSize();
            double avgHitRate = (currentStats.getRarityCacheSize() > 0 || currentStats.getItemStackCacheSize() > 0) ? 
                currentStats.getHitRate() : 0.0;
            
            return new CacheHealthReport(
                isHealthy,
                totalEntries,
                avgHitRate,
                currentStats.getClears(),
                memoryStats,
                System.currentTimeMillis() - lastCleanupTime
            );
            
        } catch (Exception e) {
            return new CacheHealthReport(
                false,
                0,
                0.0,
                cacheClears.get(),
                new MemoryEstimationStats(0, 0, 0),
                0
            );
        }
    }
    
    /**
     * 缓存健康报告类
     */
    public static class CacheHealthReport {
        private final boolean healthy;
        private final long totalEntries;
        private final double averageHitRate;
        private final long cleanupCount;
        private final MemoryEstimationStats memoryStats;
        private final long timeSinceLastCleanup;
        
        public CacheHealthReport(boolean healthy, long totalEntries, double averageHitRate,
                               long cleanupCount, MemoryEstimationStats memoryStats, long timeSinceLastCleanup) {
            this.healthy = healthy;
            this.totalEntries = totalEntries;
            this.averageHitRate = averageHitRate;
            this.cleanupCount = cleanupCount;
            this.memoryStats = memoryStats;
            this.timeSinceLastCleanup = timeSinceLastCleanup;
        }
        
        // Getters
        public boolean isHealthy() { return healthy; }
        public long getTotalEntries() { return totalEntries; }
        public double getAverageHitRate() { return averageHitRate; }
        public long getCleanupCount() { return cleanupCount; }
        public MemoryEstimationStats getMemoryStats() { return memoryStats; }
        public long getTimeSinceLastCleanup() { return timeSinceLastCleanup; }
        
        @Override
        public String toString() {
            return String.format("CacheHealthReport{healthy=%s, entries=%d, hitRate=%.1f%%, cleanups=%d, sinceCleanup=%ds}",
                healthy, totalEntries, averageHitRate, cleanupCount, timeSinceLastCleanup / 1000);
        }
    }
    
    /**
     * 获取物品的缓存稀有度
     * @param item 物品
     * @return 稀有度值，如果未缓存则返回null
     */
    public static Integer getCachedRarity(Item item) {
        // 如果缓存系统被禁用，直接返回null，不进行计数
        if (!cacheSystemEnabled || !org.yanbwe.raritycore.config.ConfigManager.isEnableCacheSystem()) {
            return null;
        }
        
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
     * 获取缓存的物品堆稀有度
     * @param itemStack 物品堆
     * @return 缓存的稀有度，如果未缓存则返回null
     */
    public static Integer getCachedItemStackRarity(ItemStack itemStack) {
        // 如果缓存系统被禁用，直接返回null
        if (!cacheSystemEnabled || !org.yanbwe.raritycore.config.ConfigManager.isEnableCacheSystem()) {
            return null;
        }
        
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        
        try {
            int hash = getItemStackHash(itemStack);
            return itemStackCache.getIfPresent(hash);
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Error getting cached itemstack rarity", e);
            return null;
        }
    }
    
    /**
     * 缓存物品堆稀有度
     * @param itemStack 物品堆
     * @param rarity 稀有度
     */
    public static void cacheItemStackRarity(ItemStack itemStack, Integer rarity) {
        // 如果缓存系统被禁用，不执行缓存操作
        if (!cacheSystemEnabled || !org.yanbwe.raritycore.config.ConfigManager.isEnableCacheSystem()) {
            return;
        }
        
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
     * 智能清理缓存 - 基于实际使用情况的渐进式清理（线程安全版本）
     */
    public static void smartCleanup() {
        // 使用同步块确保线程安全
        synchronized (cleanupLock) {
            try {
                // 清理过期条目
                rarityCache.cleanUp();
                itemStackCache.cleanUp();
                
                long currentRaritySize = rarityCache.size();
                long currentItemStackSize = itemStackCache.size();
                
                // 动态计算理论最大容量
                long registrySize = RarityRegistry.ITEM_RARITY_MAP.size();
                long targetRaritySize = calculateTargetCapacity(registrySize, RARITY_CACHE_MULTIPLIER, 
                    INITIAL_RARITY_CAPACITY, MAX_DYNAMIC_CAPACITY);
                long targetItemStackSize = calculateTargetCapacity(registrySize, ITEMSTACK_CACHE_MULTIPLIER,
                    INITIAL_ITEMSTACK_CAPACITY, MAX_DYNAMIC_CAPACITY / 2);
                
                // 应用内存安全限制
                targetRaritySize = applyMemorySafetyLimit(targetRaritySize);
                targetItemStackSize = applyMemorySafetyLimit(targetItemStackSize);
                
                // 自适应容量调整
                adjustCacheCapacities(targetRaritySize, targetItemStackSize);
                
                // 分级清理策略
                boolean cleaned = false;
                
                if (currentRaritySize > targetRaritySize * FORCE_THRESHOLD) {
                    // 强制清理：达到95%阈值
                    RarityCore.LOGGER.warn("Rarity cache size ({}) exceeded force threshold ({}), performing aggressive cleanup", 
                        currentRaritySize, (long)(targetRaritySize * FORCE_THRESHOLD));
                    performProgressiveCleanup(rarityCache, currentRaritySize, targetRaritySize);
                    cleaned = true;
                } else if (currentRaritySize > targetRaritySize * PREPARE_THRESHOLD) {
                    // 准备清理：达到90%阈值
                    RarityCore.LOGGER.info("Rarity cache size ({}) approaching prepare threshold ({}), performing gentle cleanup", 
                        currentRaritySize, (long)(targetRaritySize * PREPARE_THRESHOLD));
                    performLightCleanup(rarityCache, currentRaritySize, targetRaritySize);
                    cleaned = true;
                } else if (currentRaritySize > targetRaritySize * WARNING_THRESHOLD) {
                    // 警告级别：达到80%阈值
                    // RarityCore.LOGGER.debug("Rarity cache size ({}) at warning level ({}), monitoring closely", 
                    //     currentRaritySize, (long)(targetRaritySize * WARNING_THRESHOLD));
                }
                
                if (currentItemStackSize > targetItemStackSize * FORCE_THRESHOLD) {
                    // 物品堆缓存强制清理
                    RarityCore.LOGGER.warn("ItemStack cache size ({}) exceeded force threshold ({}), cleaning up",
                        currentItemStackSize, (long)(targetItemStackSize * FORCE_THRESHOLD));
                    itemStackCache.cleanUp();
                    cleaned = true;
                }
                
                if (cleaned) {
                    cacheClears.incrementAndGet();
                    lastCleanupTime = System.currentTimeMillis();
                    
                    // 记录清理后的状态
                    RarityCore.LOGGER.info("Cache cleanup completed - Rarity: {}->{}, ItemStack: {}->{}",
                        currentRaritySize, rarityCache.size(), 
                        currentItemStackSize, itemStackCache.size());
                }
                
                // 记录内存使用情况
                logMemoryUsage();
                
            } catch (Exception e) {
                RarityCore.LOGGER.error("Error during smart cache cleanup", e);
                // 故障恢复：尝试紧急清理
                emergencyCleanup();
            }
        }
    }
    
    /**
     * 执行轻量级清理 - 移除少量最不常用的缓存条目（异步版本）
     */
    private static <K, V> void performLightCleanup(Cache<K, V> cache, long currentSize, long maxSize) {
        try {
            long targetSize = (long)(maxSize * 0.85); // 目标清理到85%容量
            long entriesToRemove = currentSize - targetSize;
            
            if (entriesToRemove <= 0) {
                return;
            }

            RarityCore.LOGGER.debug("Performing light cleanup: removing {} entries ({:.1}% of cache)", 
                entriesToRemove, (double) entriesToRemove / currentSize * 100);

            // 触发Guava的自动清理机制
            cache.cleanUp();
            
            // 异步触发额外清理以确保效果（使用CompletableFuture）
            CompletableFuture.runAsync(() -> {
                try {
                    // 使用短暂延迟而非Thread.sleep
                    Thread.yield();
                    cache.cleanUp();
                } catch (Exception e) {
                    // 吞掉异常，不影响主线程
                    // RarityCore.LOGGER.debug("Background cleanup task encountered exception", e);
                }
            }, getCacheExecutor());
            
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Error during light cache cleanup", e);
        }
    }
    
    /**
     * 执行渐进式清理 - 移除最少使用的缓存条目（异步版本）
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
            
            // 根据调试日志管理规范，注释掉高频触发的调试信息
            /*
            RarityCore.LOGGER.debug("Performing progressive cleanup: removing {} entries ({:.1}% of cache)", 
                entriesToRemove, removalRatio * 100);
            */
            
            // 由于Guava Cache不直接暴露内部条目，通过触发清理来间接实现
            // Guava会根据LRU策略自动移除最少使用的条目
            for (int i = 0; i < Math.min(3, entriesToRemove / 100 + 1); i++) {
                cache.cleanUp();
                // 异步触发额外清理以确保效果
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.yield(); // 使用yield让出CPU时间片
                        cache.cleanUp();
                    } catch (Exception e) {
                        // RarityCore.LOGGER.debug("Background progressive cleanup task encountered exception", e);
                    }
                }, getCacheExecutor());
            }
            
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Error during progressive cache cleanup", e);
        }
    }
    
    /**
     * 重建缓存系统
     */
    private static void rebuildCache() {
        try {
            RarityCore.LOGGER.info("Rebuilding cache system");
            
            // 清空现有缓存
            rarityCache.invalidateAll();
            itemStackCache.invalidateAll();
            
            // 重置统计计数器
            cacheHits.set(0);
            cacheMisses.set(0);
            cacheClears.incrementAndGet();
            
            // 重新预加载关键数据
            preloadEssentialCache();
            
            // 执行一次清理确保状态干净
            rarityCache.cleanUp();
            itemStackCache.cleanUp();
            
            RarityCore.LOGGER.info("Cache rebuild completed - Size: Rarity={}, ItemStack={}", 
                rarityCache.size(), itemStackCache.size());
                
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error during cache rebuild", e);
        }
    }
    
    /**
     * 紧急清理机制 - 当常规清理失败时的备用方案
     */
    private static void emergencyCleanup() {
        try {
            RarityCore.LOGGER.warn("Initiating emergency cache cleanup");
            
            // 立即清理过期条目
            rarityCache.cleanUp();
            itemStackCache.cleanUp();
            
            long raritySize = rarityCache.size();
            long itemStackSize = itemStackCache.size();
            
            // 如果仍然过大，执行激进清理
            if (raritySize > INITIAL_RARITY_CAPACITY * 2) {
                RarityCore.LOGGER.warn("Emergency: Rarity cache still oversized ({}), invalidating 30% of entries", raritySize);
                invalidatePercentage(rarityCache, 0.3);
            }
            
            if (itemStackSize > INITIAL_ITEMSTACK_CAPACITY * 2) {
                RarityCore.LOGGER.warn("Emergency: ItemStack cache still oversized ({}), invalidating 30% of entries", itemStackSize);
                invalidatePercentage(itemStackCache, 0.3);
            }
            
            cacheClears.incrementAndGet();
            RarityCore.LOGGER.info("Emergency cleanup completed - Sizes: Rarity={}, ItemStack={}", 
                rarityCache.size(), itemStackCache.size());
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Critical error during emergency cleanup - cache may be corrupted", e);
        }
    }
    
    /**
     * 按百分比使缓存条目失效
     */
    private static <K, V> void invalidatePercentage(Cache<K, V> cache, double percentage) {
        try {
            long totalSize = cache.size();
            long toInvalidate = (long)(totalSize * percentage);
            
            if (toInvalidate <= 0) return;
            
            // 获取所有键并随机选择要失效的条目
            java.util.Random random = new java.util.Random();
            java.util.List<K> keys = new java.util.ArrayList<>(cache.asMap().keySet());
            java.util.Collections.shuffle(keys, random);
            
            int invalidated = 0;
            for (K key : keys) {
                if (invalidated >= toInvalidate) break;
                cache.invalidate(key);
                invalidated++;
            }
            
            RarityCore.LOGGER.debug("Invalidated {} cache entries ({:.1}%)", invalidated, percentage * 100);
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error invalidating cache percentage", e);
        }
    }
    
    /**
     * 计算目标缓存容量
     */
    private static long calculateTargetCapacity(long registrySize, double multiplier, 
                                              long minCapacity, long maxCapacity) {
        long calculated = Math.max(minCapacity, (long)(registrySize * multiplier));
        return Math.min(maxCapacity, calculated);
    }
    
    /**
     * 自适应调整缓存容量
     */
    private static void adjustCacheCapacities(long targetRaritySize, long targetItemStackSize) {
        boolean needsResize = false;
        
        // 调整物品缓存容量
        if (Math.abs(targetRaritySize - currentRarityCapacity) > currentRarityCapacity * 0.2) {
            long newCapacity = targetRaritySize;
            if (newCapacity > currentRarityCapacity) {
                newCapacity = Math.min(newCapacity, (long)(currentRarityCapacity * CAPACITY_GROWTH_FACTOR));
            } else {
                newCapacity = Math.max(newCapacity, (long)(currentRarityCapacity * CAPACITY_SHRINK_FACTOR));
            }
            newCapacity = Math.max(MIN_DYNAMIC_CAPACITY, Math.min(MAX_DYNAMIC_CAPACITY, newCapacity));
            
            if (newCapacity != currentRarityCapacity) {
                resizeRarityCache(newCapacity);
                needsResize = true;
            }
        }
        
        // 调整物品堆缓存容量
        if (Math.abs(targetItemStackSize - currentItemStackCapacity) > currentItemStackCapacity * 0.2) {
            long newCapacity = targetItemStackSize;
            if (newCapacity > currentItemStackCapacity) {
                newCapacity = Math.min(newCapacity, (long)(currentItemStackCapacity * CAPACITY_GROWTH_FACTOR));
            } else {
                newCapacity = Math.max(newCapacity, (long)(currentItemStackCapacity * CAPACITY_SHRINK_FACTOR));
            }
            newCapacity = Math.max(MIN_DYNAMIC_CAPACITY / 2, Math.min(MAX_DYNAMIC_CAPACITY / 2, newCapacity));
            
            if (newCapacity != currentItemStackCapacity) {
                resizeItemStackCache(newCapacity);
                needsResize = true;
            }
        }
        
        if (needsResize) {
            RarityCore.LOGGER.info("Cache capacities adjusted - Rarity: {}->{}, ItemStack: {}->{}",
                currentRarityCapacity, rarityCache.size(), 
                currentItemStackCapacity, itemStackCache.size());
        }
    }
    
    /**
     * 调整物品缓存大小
     */
    private static void resizeRarityCache(long newCapacity) {
        try {
            Cache<Item, Integer> newCache = createRarityCache(newCapacity);
            
            // 迁移现有数据
            int migratedCount = 0;
            for (Map.Entry<Item, Integer> entry : rarityCache.asMap().entrySet()) {
                if (migratedCount >= newCapacity) break;
                newCache.put(entry.getKey(), entry.getValue());
                migratedCount++;
            }
            
            // 替换缓存实例为新容量
            rarityCache = newCache;
            currentRarityCapacity = newCapacity;
            
            RarityCore.LOGGER.debug("Resized rarity cache to {} entries, migrated {} entries", 
                newCapacity, migratedCount);
                
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to resize rarity cache to {}", newCapacity, e);
        }
    }
    
    /**
     * 调整物品堆缓存大小
     */
    private static void resizeItemStackCache(long newCapacity) {
        try {
            Cache<Integer, Integer> newCache = createItemStackCache(newCapacity);
            
            // 迁移现有数据
            int migratedCount = 0;
            for (Map.Entry<Integer, Integer> entry : itemStackCache.asMap().entrySet()) {
                if (migratedCount >= newCapacity) break;
                newCache.put(entry.getKey(), entry.getValue());
                migratedCount++;
            }
            
            // 替换缓存实例为新容量
            itemStackCache = newCache;
            currentItemStackCapacity = newCapacity;
            
            RarityCore.LOGGER.debug("Resized itemStack cache to {} entries, migrated {} entries", 
                newCapacity, migratedCount);
                
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to resize itemStack cache to {}", newCapacity, e);
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
            
            // 执行内存校准（定期）
            calibrateMemoryEstimation();
            
            // 计算可用于缓存的安全内存量
            long safeMemoryForCache = (long)(maxMemory * MEMORY_SAFETY_FACTOR - usedMemory);
            
            // 使用动态估算的每个条目大小
            long maxSafeEntries = Math.max(MIN_DYNAMIC_CAPACITY / 10, 
                (long)(safeMemoryForCache / averageEntrySizeBytes));
            
            // 返回请求大小和安全大小中的较小值
            long finalSize = Math.min(requestedSize, maxSafeEntries);
            
            if (finalSize < requestedSize) {
                RarityCore.LOGGER.debug("Cache size limited by memory: requested={}, actual={}, avgEntrySize={:.1f} bytes", 
                    requestedSize, finalSize, averageEntrySizeBytes);
            }
            
            return finalSize;
            
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Error applying memory safety limit, using requested size", e);
            return requestedSize;
        }
    }
    
    /**
     * 内存估算校准 - 被动监控而非主动触发GC
     */
    private static void calibrateMemoryEstimation() {
        long currentTime = System.currentTimeMillis();
        
        // 每隔一段时间进行校准
        if (currentTime - lastCalibrationTime < CALIBRATION_INTERVAL) {
            return;
        }
        
        try {
            Runtime runtime = Runtime.getRuntime();
            long usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory();
            
            // 改为被动监控，不再强制GC
            // 只有当内存使用较高时才考虑轻微清理
            long maxMemory = runtime.maxMemory();
            double memoryUsage = (double) usedMemoryBefore / maxMemory;
            
            if (memoryUsage > 0.85) { // 内存使用超过85%时才考虑清理
                // 建议JVM进行垃圾回收，但不强制等待
                System.gc();
            }
            
            long usedMemoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryDelta = usedMemoryAfter - usedMemoryBefore;
            
            // 计算缓存实际内存占用
            long currentCacheEntries = rarityCache.size() + itemStackCache.size();
            
            if (currentCacheEntries > 100 && Math.abs(memoryDelta) > 1024) { // 至少100个条目且内存变化超过1KB
                double newAverageSize = (double) Math.abs(memoryDelta) / currentCacheEntries;
                
                // 使用移动平均法平滑估算值
                averageEntrySizeBytes = (averageEntrySizeBytes * 0.8) + (newAverageSize * 0.2);
                
                // 限制估算范围（防止极端值）
                averageEntrySizeBytes = Math.max(50.0, Math.min(500.0, averageEntrySizeBytes));
                
                totalCalibrationSamples.incrementAndGet();
                totalMemoryUsed.addAndGet(Math.abs(memoryDelta));
                
                RarityCore.LOGGER.debug("Memory calibration: {} entries, {:.1f} bytes/entry (avg: {:.1f}), memUsage: {:.1}%",
                    currentCacheEntries, newAverageSize, averageEntrySizeBytes, memoryUsage * 100);
            }
            
            lastCalibrationTime = currentTime;
            
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Memory calibration skipped due to error", e);
        }
    }
    
    /**
     * 获取内存估算统计信息
     */
    public static MemoryEstimationStats getMemoryEstimationStats() {
        return new MemoryEstimationStats(
            averageEntrySizeBytes,
            totalCalibrationSamples.get(),
            totalMemoryUsed.get()
        );
    }
    
    /**
     * 内存估算统计信息类
     */
    public static class MemoryEstimationStats {
        private final double averageEntrySize;
        private final long calibrationSamples;
        private final long totalMemoryUsed;
        
        public MemoryEstimationStats(double averageEntrySize, long calibrationSamples, long totalMemoryUsed) {
            this.averageEntrySize = averageEntrySize;
            this.calibrationSamples = calibrationSamples;
            this.totalMemoryUsed = totalMemoryUsed;
        }
        
        public double getAverageEntrySize() { return averageEntrySize; }
        public long getCalibrationSamples() { return calibrationSamples; }
        public long getTotalMemoryUsed() { return totalMemoryUsed; }
        
        @Override
        public String toString() {
            return String.format("MemoryEstimationStats{avgEntrySize=%.1f bytes, samples=%d, totalMemory=%d KB}",
                averageEntrySize, calibrationSamples, totalMemoryUsed / 1024);
        }
    }
    
    /**
     * 清空所有缓存
     */
    public static void clearAllCache() {
        // 即使缓存被禁用也允许手动清理（用于重置状态）
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
     * 配置重载时使所有缓存失效
     */
    public static void invalidateAllCachesOnConfigReload() {
        try {
            RarityCore.LOGGER.info("Invalidating all caches due to configuration reload");
            rarityCache.invalidateAll();
            itemStackCache.invalidateAll();
            cacheClears.incrementAndGet();
            
            // 重新预加载常用数据
            preloadCache();
            
            RarityCore.LOGGER.info("Cache invalidation completed - Preloaded {} items", rarityCache.size());
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error during cache invalidation on config reload", e);
        }
    }
    
    /**
     * 客户端配置变更时的缓存处理
     */
    public static void handleClientConfigChange() {
        try {
            RarityCore.LOGGER.debug("Handling client configuration change");
            
            // 同步配置中的缓存开关状态
            boolean configCacheEnabled = org.yanbwe.raritycore.config.ConfigManager.isEnableCacheSystem();
            if (cacheSystemEnabled != configCacheEnabled) {
                setCacheSystemEnabled(configCacheEnabled);
            }
            
            // 清理物品堆缓存（可能受渲染设置影响）
            itemStackCache.invalidateAll();
            
            // 重新预加载关键数据
            preloadEssentialCache();
            
            RarityCore.LOGGER.debug("Client config change handled - Cache enabled: {}, ItemStack cache cleared and essential data preloaded", cacheSystemEnabled);
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling client config change", e);
        }
    }
    
    /**
     * 网络同步数据到达时的缓存处理
     */
    public static void handleNetworkSync() {
        try {
            RarityCore.LOGGER.debug("Handling network synchronization");
            
            // 使所有缓存失效以确保数据一致性
            rarityCache.invalidateAll();
            itemStackCache.invalidateAll();
            
            // 预加载最新稀有度数据
            preloadCache();
            
            RarityCore.LOGGER.debug("Network sync handled - All caches refreshed");
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling network sync", e);
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
     * 预加载关键数据 - 只加载最常用的物品
     */
    public static void preloadEssentialCache() {
        try {
            RarityCore.LOGGER.debug("Preloading essential cache data...");
            int preloadedCount = 0;
            
            // 定义关键物品类别
            String[] essentialPrefixes = {
                "minecraft:diamond", "minecraft:iron", "minecraft:gold",
                "minecraft:netherite", "minecraft:emerald", "minecraft:enchanted"
            };
            
            // 只预加载关键物品
            for (java.util.Map.Entry<net.minecraft.resources.ResourceLocation, Integer> entry : 
                 RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
                String itemId = entry.getKey().toString();
                
                // 检查是否为关键物品
                boolean isEssential = false;
                for (String prefix : essentialPrefixes) {
                    if (itemId.contains(prefix)) {
                        isEssential = true;
                        break;
                    }
                }
                
                if (isEssential) {
                    net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(entry.getKey());
                    if (item != null) {
                        cacheRarity(item, entry.getValue());
                        preloadedCount++;
                    }
                }
            }
            
            RarityCore.LOGGER.debug("Essential cache preloaded with {} items", preloadedCount);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error during essential cache preloading", e);
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
     * 故障恢复机制 - 当缓存出现问题时的自动恢复
     */
    public static RecoveryResult attemptRecovery() {
        try {
            RarityCore.LOGGER.warn("Attempting cache system recovery");
            
            // 步骤1：健康检查
            CacheHealthReport healthReport = performHealthCheck();
            RarityCore.LOGGER.info("Pre-recovery health status: {}", healthReport);
            
            // 步骤2：紧急清理
            emergencyCleanup();
            
            // 步骤3：重建缓存
            rebuildCache();
            
            // 步骤4：验证恢复结果
            CacheHealthReport postRecoveryReport = performHealthCheck();
            boolean recoverySuccessful = postRecoveryReport.isHealthy() && 
                postRecoveryReport.getTotalEntries() > 0;
            
            RarityCore.LOGGER.info("Post-recovery health status: {}", postRecoveryReport);
            
            if (recoverySuccessful) {
                RarityCore.LOGGER.info("Cache recovery successful");
            } else {
                RarityCore.LOGGER.error("Cache recovery failed - system may be unstable");
            }
            
            return new RecoveryResult(recoverySuccessful, healthReport, postRecoveryReport);
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Critical failure during cache recovery", e);
            return new RecoveryResult(false, null, null);
        }
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
            
            // RarityCore.LOGGER.debug("Memory usage: {}/{} MB ({:.1}%), Cache sizes: rarity={}, itemStack={}",
            //     usedMemory / (1024 * 1024), maxMemory / (1024 * 1024), 
            //     memoryUsagePercent, rarityCache.size(), itemStackCache.size());
                
        } catch (Exception e) {
            // RarityCore.LOGGER.debug("Failed to log memory usage", e);
        }
    }
    
    /**
     * 智能预加载缓存 - 基于使用频率和模组热度的自适应预加载
     */
    public static void smartPreloadCache() {
        long currentTime = System.currentTimeMillis();
        
        // 检查预加载间隔
        if (currentTime - lastPreloadTime.get() < PRELOAD_INTERVAL) {
            return;
        }
        
        synchronized (preloadLock) {
            // 双重检查
            if (currentTime - lastPreloadTime.get() < PRELOAD_INTERVAL) {
                return;
            }
            
            try {
                RarityCore.LOGGER.info("Starting smart cache preloading...");
                
                // 获取注册表统计信息
                int registrySize = RarityRegistry.ITEM_RARITY_MAP.size();
                if (registrySize == 0) {
                    return;
                }
                
                // 分析模组热度（基于物品数量）
                Map<String, Integer> modPopularity = analyzeModPopularity();
                
                // 获取高频使用物品
                Set<Item> frequentItems = getFrequentlyUsedItems();
                
                int preloadedCount = 0;
                int skippedCount = 0;
                
                // 第一层：预加载高频使用物品
                for (Item item : frequentItems) {
                    if (rarityCache.getIfPresent(item) == null) {
                        Integer rarity = RarityRegistry.getRarity(item);
                        if (rarity != null) {
                            cacheRarity(item, rarity);
                            preloadedCount++;
                        }
                    }
                }
                
                // 第二层：按模组热度预加载
                for (Map.Entry<String, Integer> modEntry : modPopularity.entrySet()) {
                    String modId = modEntry.getKey();
                    int itemCount = modEntry.getValue();
                    
                    // 对热门模组进行更深的预加载
                    double preloadRatio = calculatePreloadRatio(modId, itemCount, registrySize);
                    
                    preloadedCount += preloadModItems(modId, preloadRatio);
                }
                
                // 第三层：基础预加载（确保核心物品被缓存）
                preloadedCount += preloadEssentialItems();
                
                lastPreloadTime.set(currentTime);
                
                RarityCore.LOGGER.info("Smart preload completed: {} items loaded, {} skipped", 
                    preloadedCount, skippedCount);
                
            } catch (Exception e) {
                RarityCore.LOGGER.error("Error during smart cache preloading", e);
            }
        }
    }
    
    /**
     * 分析模组热度
     */
    private static Map<String, Integer> analyzeModPopularity() {
        Map<String, Integer> modCounts = new HashMap<>();
        
        for (ResourceLocation itemId : RarityRegistry.ITEM_RARITY_MAP.keySet()) {
            String modId = itemId.getNamespace();
            modCounts.put(modId, modCounts.getOrDefault(modId, 0) + 1);
        }
        
        return modCounts;
    }
    
    /**
     * 计算预加载比例
     */
    private static double calculatePreloadRatio(String modId, int itemCount, int totalItems) {
        // 基础比例基于模组规模
        double baseRatio = Math.min(1.0, (double) itemCount / Math.max(1, totalItems / 10));
        
        // 热门模组加权（Minecraft原版、常见模组）
        String[] popularMods = {"minecraft", "forge", "jei", "rei", "curios"};
        boolean isPopularMod = Arrays.stream(popularMods)
            .anyMatch(mod -> modId.equals(mod) || modId.startsWith(mod + ":"));
        
        if (isPopularMod) {
            baseRatio *= 1.5; // 热门模组增加50%预加载
        }
        
        // 限制最大预加载比例
        return Math.min(0.8, baseRatio);
    }
    
    /**
     * 预加载指定模组的物品
     */
    private static int preloadModItems(String modId, double ratio) {
        int preloaded = 0;
        List<Map.Entry<ResourceLocation, Integer>> modItems = new ArrayList<>();
        
        // 收集模组物品
        for (Map.Entry<ResourceLocation, Integer> entry : RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
            if (entry.getKey().getNamespace().equals(modId)) {
                modItems.add(entry);
            }
        }
        
        // 按比例预加载
        int targetCount = (int) (modItems.size() * ratio);
        Collections.shuffle(modItems); // 随机化以避免偏见
        
        for (int i = 0; i < Math.min(targetCount, modItems.size()); i++) {
            ResourceLocation itemId = modItems.get(i).getKey();
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null && rarityCache.getIfPresent(item) == null) {
                cacheRarity(item, modItems.get(i).getValue());
                preloaded++;
            }
        }
        
        return preloaded;
    }
    
    /**
     * 预加载必需的核心物品
     */
    private static int preloadEssentialItems() {
        int preloaded = 0;
        String[] essentialPrefixes = {
            "minecraft:diamond", "minecraft:iron", "minecraft:gold",
            "minecraft:netherite", "minecraft:emerald", "minecraft:enchanted"
        };
        
        for (Map.Entry<ResourceLocation, Integer> entry : RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
            String itemId = entry.getKey().toString();
            boolean isEssential = Arrays.stream(essentialPrefixes)
                .anyMatch(itemId::contains);
            
            if (isEssential) {
                Item item = ForgeRegistries.ITEMS.getValue(entry.getKey());
                if (item != null && rarityCache.getIfPresent(item) == null) {
                    cacheRarity(item, entry.getValue());
                    preloaded++;
                }
            }
        }
        
        return preloaded;
    }
    
    /**
     * 获取高频使用物品（模拟实现）
     */
    private static Set<Item> getFrequentlyUsedItems() {
        // 这里可以根据实际使用情况进行扩展
        // 目前返回空集合，后续可以基于实际统计数据实现
        return new HashSet<>();
    }
    
    /**
     * 获取缓存执行器（支持懒加载和自动重启）
     */
    private static ExecutorService getCacheExecutor() {
        if (cacheExecutor == null || cacheExecutor.isShutdown() || cacheExecutor.isTerminated()) {
            synchronized (executorLock) {
                // 双重检查
                if (cacheExecutor == null || cacheExecutor.isShutdown() || cacheExecutor.isTerminated()) {
                    if (isShutdown.get()) {
                        // 如果已被标记为关闭，尝试重置状态
                        isShutdown.set(false);
                    }
                    
                    // 创建新的线程池
                    cacheExecutor = new ThreadPoolExecutor(
                        CORE_POOL_SIZE,
                        MAX_POOL_SIZE,
                        KEEP_ALIVE_TIME,
                        TIME_UNIT,
                        new LinkedBlockingQueue<>(),
                        r -> {
                            Thread t = new Thread(r, "RarityCore-Cache-Worker");
                            t.setDaemon(true);
                            t.setPriority(Thread.NORM_PRIORITY - 1); // 稍低优先级
                            return t;
                        },
                        new ThreadPoolExecutor.DiscardOldestPolicy() // 拒绝策略
                    );

                }
            }
        }
        return cacheExecutor;
    }
    
    /**
     * 关闭缓存执行器
     */
    public static void shutdownExecutor() {
        if (isShutdown.compareAndSet(false, true)) {
            synchronized (executorLock) {
                if (cacheExecutor != null) {
                    try {
                        // 先尝试优雅关闭
                        cacheExecutor.shutdown();
                        if (!cacheExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                            // 强制关闭
                            List<Runnable> remainingTasks = cacheExecutor.shutdownNow();
                            if (!remainingTasks.isEmpty()) {
                                RarityCore.LOGGER.warn("{} cache tasks were cancelled during shutdown", remainingTasks.size());
                            }
                        }
                    } catch (InterruptedException e) {
                        cacheExecutor.shutdownNow();
                        Thread.currentThread().interrupt();
                        RarityCore.LOGGER.warn("Cache executor shutdown interrupted");
                    } finally {
                        cacheExecutor = null;
                    }
                }
            }
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
    
    /**
     * 恢复结果类
     */
    public static class RecoveryResult {
        private final boolean successful;
        private final CacheHealthReport preRecovery;
        private final CacheHealthReport postRecovery;
        
        public RecoveryResult(boolean successful, CacheHealthReport preRecovery, CacheHealthReport postRecovery) {
            this.successful = successful;
            this.preRecovery = preRecovery;
            this.postRecovery = postRecovery;
        }
        
        public boolean isSuccessful() { return successful; }
        public CacheHealthReport getPreRecovery() { return preRecovery; }
        public CacheHealthReport getPostRecovery() { return postRecovery; }
        
        @Override
        public String toString() {
            return String.format("RecoveryResult{successful=%s, pre=%s, post=%s}",
                successful, 
                preRecovery != null ? preRecovery.toString() : "null",
                postRecovery != null ? postRecovery.toString() : "null");
        }
    }
}