package org.yanbwe.raritycore.cache;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 渲染缓存管理器 - 适配器模式
 * 委托所有操作给DualCacheManager以保持API兼容性
 *
 * <p>新增 per-frame ThreadLocal 缓存：在同一帧内（tooltip handler → border renderer →
 * name color mixer）多次查询同一 ItemStack 时，直接返回已查询的结果，
 * 避免每帧(60fps)重复走完整优先级链。</p>
 */
public class RenderCacheManager {

    /**
     * Per-frame ThreadLocal 稀有度缓存。
     * 在一帧内，tooltip handler、border renderer、name mixer 可能分别查询同一物品的稀有度。
     * 通过缓存避免热路径上重复走完整的9级优先级链。
     *
     * <p>生命期：单帧。由于渲染始终在 Render Thread 单线程执行，
     * 最坏情况是上一帧的 ItemStack 引用被 GC 自动清理，不会泄漏。</p>
     *
     * <p>容量：Minecraft 最多 36 格快捷栏 + ~40 格容器 = 76 个物品，
     * 留 2x 余量设置 200 条目，超出时 LRU 驱逐即可。</p>
     */
    private static final ThreadLocal<java.util.LinkedHashMap<Integer, Integer>> FRAME_CACHE =
        ThreadLocal.withInitial(() -> new java.util.LinkedHashMap<>(64, 0.75f, true) {
            private static final int MAX_ENTRIES = 200;
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<Integer, Integer> eldest) {
                return size() > MAX_ENTRIES;
            }
        });

    /**
     * 清空当前线程的 per-frame 缓存。
     * 应在每帧开始时调用（例如在 GuiMixin 中）。
     */
    public static void clearFrameCache() {
        java.util.Map<Integer, Integer> cache = FRAME_CACHE.get();
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * 获取物品的缓存稀有度
     */
    public static Integer getCachedRarity(Item item) {
        if (item == null) return null;
        return DualCacheManager.getCachedRarity(new ItemStack(item));
    }

    /**
     * 获取物品堆的缓存稀有度（含 per-frame 快捷路径）
     *
     * <p>查询顺序：per-frame 缓存 → DualCache（ID + Component）</p>
     */
    public static Integer getCachedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        // Per-frame 快捷路径：使用 System.identityHashCode 作为快速 key
        // （ItemStack 可变，hashCode 可能多次不同，但 identityHashCode 在对象存活期内不变）
        int identityKey = System.identityHashCode(itemStack);
        java.util.Map<Integer, Integer> frameCache = FRAME_CACHE.get();
        Integer frameResult = frameCache.get(identityKey);
        if (frameResult != null) {
            return frameResult;
        }

        Integer result = DualCacheManager.getCachedRarity(itemStack);
        if (result != null) {
            frameCache.put(identityKey, result);
        }
        return result;
    }

    /**
     * 缓存物品稀有度（同时写入 per-frame 缓存和 DualCache）
     */
    public static void cacheRarity(Item item, Integer rarity) {
        if (item == null || rarity == null) return;
        DualCacheManager.cacheRarity(new ItemStack(item), rarity);
    }

    /**
     * 缓存物品堆稀有度（同时写入 per-frame 缓存和 DualCache）
     */
    public static void cacheItemStackRarity(ItemStack itemStack, Integer rarity) {
        if (itemStack == null || itemStack.isEmpty() || rarity == null) return;
        DualCacheManager.cacheRarity(itemStack, rarity);
        // 同时写入 per-frame 缓存
        FRAME_CACHE.get().put(System.identityHashCode(itemStack), rarity);
    }

    /**
     * 清空所有缓存
     */
    public static void clearAllCache() {
        DualCacheManager.handleConfigReload();
        // 清空当前线程的 per-frame 缓存
        FRAME_CACHE.get().clear();
    }

    /**
     * 获取缓存统计信息
     */
    public static CacheStats getCacheStats() {
        DualCacheManager.CacheStatistics dualStats = DualCacheManager.getStatistics();
        return new CacheStats(
            0,
            0,
            0,
            (int) dualStats.getCacheSize(),
            (int) dualStats.getCacheSize(),
            dualStats.getOverallHitRate()
        );
    }

    /**
     * 缓存统计信息类(保持向后兼容)
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
