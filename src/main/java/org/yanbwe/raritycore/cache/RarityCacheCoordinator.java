package org.yanbwe.raritycore.cache;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.itemdatamatching.ItemDataRarityMatcher;

import javax.annotation.Nullable;

/**
 * 稀有度缓存协调器
 * 统一协调ID缓存和组件缓存的两级缓存系统
 */
public class RarityCacheCoordinator {

    private static volatile boolean initialized = false;

    /**
     * 初始化缓存系统
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        IdCacheManager.initialize();
        ComponentCacheManager.initialize();

        initialized = true;
        RarityCore.LOGGER.info("稀有度缓存协调器初始化完成");
    }

    /**
     * 获取物品的缓存稀有度（性能优化版）
     * 查询逻辑：
     * 1. 无组件匹配规则 → 直接ID缓存（快速路径，无组件序列化）
     * 2. 有组件匹配规则 → 完整逻辑：组件缓存 → hasNonTrivialData → ID缓存
     * @param itemStack 物品堆
     * @return 缓存的稀有度，如果不存在返回null
     */
    @Nullable
    public static Integer getCachedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return null;
        }

        // 无组件匹配规则 → 快速ID缓存路径
        if (!hasComponentMatchRules(itemId)) {
            return IdCacheManager.getCachedRarity(itemId);
        }

        // 以下为需要组件感知缓存的逻辑
        // 1. 优先检查组件缓存(基于ItemStack组件哈希)
        Integer componentRarity = ComponentCacheManager.getCachedRarity(itemStack);
        if (componentRarity != null) {
            return componentRarity;
        }

        // 2. 如果物品有非平凡组件数据，跳过ID缓存
        if (ComponentCacheManager.hasNonTrivialData(itemStack)) {
            return null; // 强制调用方重新计算稀有度
        }

        // 3. 回退到ID缓存
        return IdCacheManager.getCachedRarity(itemId);
    }

    /**
     * 获取物品的缓存稀有度
     * @param item 物品实例
     * @return 缓存的稀有度，如果不存在返回null
     */
    @Nullable
    public static Integer getCachedRarity(Item item) {
        if (item == null) {
            return null;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return null;
        }
        return IdCacheManager.getCachedRarity(itemId);
    }

    /**
     * 缓存物品稀有度
     * 根据物品是否有组件匹配规则决定使用哪个缓存
     * @param itemStack 物品堆
     * @param rarity 稀有度等级
     * @param hasComponentMatch 是否有组件匹配（由调用方提供）
     */
    public static void cacheRarity(ItemStack itemStack, Integer rarity, boolean hasComponentMatch) {
        if (itemStack == null || itemStack.isEmpty() || rarity == null) {
            return;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return;
        }

        if (hasComponentMatch) {
            ComponentCacheManager.cacheRarity(itemStack, rarity);
        } else {
            IdCacheManager.cacheRarity(itemId, rarity);
        }
    }

    /**
     * 缓存物品稀有度（自动检测组件匹配规则，性能优化版）
     * 缓存策略:
     * - 有组件匹配规则的物品 → 组件缓存(基于组件哈希,区分不同物品堆)
     * - 其他物品 → ID缓存(基于物品类型ID)
     * @param itemStack 物品堆
     * @param rarity 稀有度等级
     */
    public static void cacheRarity(ItemStack itemStack, Integer rarity) {
        if (itemStack == null || itemStack.isEmpty() || rarity == null) {
            return;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return;
        }

        boolean hasComponentMatch = hasComponentMatchRules(itemId);
        if (hasComponentMatch) {
            // 有组件匹配规则 → 必须区分不同组件的物品堆
            ComponentCacheManager.cacheRarity(itemStack, rarity);
        } else {
            // 普通物品 → ID缓存（快速路径）
            IdCacheManager.cacheRarity(itemId, rarity);
        }
    }

    /**
     * 缓存物品稀有度（仅ID缓存）
     * @param item 物品实例
     * @param rarity 稀有度等级
     */
    public static void cacheRarity(Item item, Integer rarity) {
        IdCacheManager.cacheRarity(item, rarity);
    }

    /**
     * 更新物品的ID缓存稀有度（用于编辑模式实时更新）
     * @param itemId 物品资源位置
     * @param rarity 新的稀有度等级
     */
    public static void updateIdCache(Identifier itemId, Integer rarity) {
        IdCacheManager.updateRarity(itemId, rarity);
    }

    /**
     * 更新物品的ID缓存稀有度（用于编辑模式实时更新）
     * @param item 物品实例
     * @param rarity 新的稀有度等级
     */
    public static void updateIdCache(Item item, Integer rarity) {
        IdCacheManager.updateRarity(item, rarity);
    }

    /**
     * 使指定物品的所有缓存失效
     * @param itemId 物品资源位置
     */
    public static void invalidate(Identifier itemId) {
        IdCacheManager.invalidate(itemId);
    }

    /**
     * 使指定物品堆的组件缓存失效
     * @param itemStack 物品堆
     */
    public static void invalidate(ItemStack itemStack) {
        ComponentCacheManager.invalidate(itemStack);
    }

    /**
     * 重载所有缓存
     */
    public static void handleConfigReload() {
        IdCacheManager.handleConfigReload();
        ComponentCacheManager.handleConfigReload();
        RarityCore.LOGGER.info("稀有度缓存系统重载完成");
    }

    /**
     * 检查物品是否有组件匹配规则
     * @param itemId 物品资源位置
     * @return 如果有组件匹配规则返回true
     */
    public static boolean hasComponentMatchRules(Identifier itemId) {
        return ItemDataRarityMatcher.hasRulesForItem(itemId);
    }

    /**
     * 获取合并的缓存统计信息
     */
    public static CombinedCacheStatistics getStatistics() {
        IdCacheManager.IdCacheStatistics idStats = IdCacheManager.getStatistics();
        ComponentCacheManager.ComponentCacheStatistics componentStats = ComponentCacheManager.getStatistics();

        long totalSize = idStats.getCacheSize() + componentStats.getCacheSize();
        long totalHits = idStats.getHits() + componentStats.getHits();
        long totalMisses = idStats.getMisses() + componentStats.getMisses();
        long totalRequests = totalHits + totalMisses;
        double overallHitRate = totalRequests > 0 ? (double) totalHits / totalRequests * 100 : 0.0;

        return new CombinedCacheStatistics(
            idStats.getCacheSize(),
            componentStats.getCacheSize(),
            totalSize,
            idStats.getHitRate(),
            componentStats.getHitRate(),
            overallHitRate
        );
    }

    /**
     * 重置所有统计信息
     */
    public static void resetStatistics() {
        IdCacheManager.resetStatistics();
        ComponentCacheManager.resetStatistics();
    }

    /**
     * 合并缓存统计信息类
     */
    public static class CombinedCacheStatistics {
        private final long idCacheSize;
        private final long componentCacheSize;
        private final long totalSize;
        private final double idCacheHitRate;
        private final double componentCacheHitRate;
        private final double overallHitRate;

        public CombinedCacheStatistics(long idCacheSize, long componentCacheSize, long totalSize,
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

        @Override
        public String toString() {
            return String.format("CombinedCacheStats{ID: %d (%.1f%%), Component: %d (%.1f%%), Total: %d, Overall: %.1f%%}",
                idCacheSize, idCacheHitRate,
                componentCacheSize, componentCacheHitRate,
                totalSize, overallHitRate);
        }
    }
}
