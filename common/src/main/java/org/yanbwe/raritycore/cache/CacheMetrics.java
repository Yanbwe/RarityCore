package org.yanbwe.raritycore.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存性能统计
 */
public class CacheMetrics {

    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    private static final AtomicLong totalRequests = new AtomicLong(0);

    public static void recordHit() {
        totalRequests.incrementAndGet();
        cacheHits.incrementAndGet();
    }

    public static void recordMiss() {
        totalRequests.incrementAndGet();
        cacheMisses.incrementAndGet();
    }

    public static double getOverallHitRate() {
        long total = totalRequests.get();
        return total > 0 ? (double) cacheHits.get() / total * 100 : 0.0;
    }

    public static void reset() {
        cacheHits.set(0);
        cacheMisses.set(0);
        totalRequests.set(0);
    }

    public static String getDetailedStats() {
        return String.format(
            "缓存统计 - 总请求: %d, 命中: %d (%.1f%%), 未命中: %d (%.1f%%)",
            totalRequests.get(),
            cacheHits.get(), getOverallHitRate(),
            cacheMisses.get(), 100 - getOverallHitRate()
        );
    }
}
