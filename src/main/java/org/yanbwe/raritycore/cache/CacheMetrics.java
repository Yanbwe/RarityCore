package org.yanbwe.raritycore.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存性能统计
 */
public class CacheMetrics {
    
    // 统计计数器
    private static final AtomicLong idCacheHits = new AtomicLong(0);
    private static final AtomicLong nbtCacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    private static final AtomicLong totalRequests = new AtomicLong(0);
    
    /**
     * 记录缓存命中
     */
    public static void recordHit(CacheType type) {
        totalRequests.incrementAndGet();
        switch (type) {
            case ID:
                idCacheHits.incrementAndGet();
                break;
            case NBT:
                nbtCacheHits.incrementAndGet();
                break;
        }
    }
    
    /**
     * 记录缓存未命中
     */
    public static void recordMiss() {
        totalRequests.incrementAndGet();
        cacheMisses.incrementAndGet();
    }
    
    /**
     * 获取ID缓存命中率
     */
    public static double getIdHitRate() {
        long total = totalRequests.get();
        return total > 0 ? (double) idCacheHits.get() / total * 100 : 0.0;
    }
    
    /**
     * 获取NBT缓存命中率
     */
    public static double getNbtHitRate() {
        long total = totalRequests.get();
        return total > 0 ? (double) nbtCacheHits.get() / total * 100 : 0.0;
    }
    
    /**
     * 获取总体命中率
     */
    public static double getOverallHitRate() {
        long hits = idCacheHits.get() + nbtCacheHits.get();
        long total = totalRequests.get();
        return total > 0 ? (double) hits / total * 100 : 0.0;
    }
    
    /**
     * 重置所有统计
     */
    public static void reset() {
        idCacheHits.set(0);
        nbtCacheHits.set(0);
        cacheMisses.set(0);
        totalRequests.set(0);
    }
    
    /**
     * 获取详细统计信息
     */
    public static String getDetailedStats() {
        return String.format(
            "Cache Metrics - Total: %d, ID Hits: %d (%.1f%%), NBT Hits: %d (%.1f%%), Misses: %d (%.1f%%)",
            totalRequests.get(),
            idCacheHits.get(), getIdHitRate(),
            nbtCacheHits.get(), getNbtHitRate(),
            cacheMisses.get(), 100 - getOverallHitRate()
        );
    }
    
    /**
     * 缓存类型枚举
     */
    public enum CacheType {
        ID, NBT
    }
}