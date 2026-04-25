package org.yanbwe.raritycore.cache;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
     * 获取物品的缓存稀有度
     * 查询逻辑：
     * 1. 先查组件缓存(基于ItemStack NBT哈希,能区分不同NBT数据的物品堆)
     *    如果命中直接返回,避免ID缓存的跨物品堆污染问题
     * 2. 如果组件缓存未命中且物品有特殊NBT数据(如神化模组组件),
     *    返回null强制重新计算,防止ID缓存的类型级数据被错误使用
     * 3. 如果物品没有特殊NBT数据,回退到ID缓存(基于物品类型ID)
     * @param itemStack 物品堆
     * @return 缓存的稀有度，如果不存在返回null
     */
    @Nullable
    public static Integer getCachedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return null;
        }

        // 1. 优先检查组件缓存(基于ItemStack NBT哈希)
        //    组件缓存能区分相同物品类型但不同NBT数据的物品堆
        //    这防止了神化模组稀有度(基于ItemStack数据组件)泄漏到同类型的非神化物品
        Integer componentRarity = ComponentCacheManager.getCachedRarity(itemStack);
        if (componentRarity != null) {
            return componentRarity;
        }

        // 2. 如果物品有特殊NBT数据(如神化组件、附魔等),跳过ID缓存
        //    ID缓存只存储按物品类型区分的稀有度,会错误地应用于所有同类型物品堆
        //    例如:有神化数据的剑和无神化数据的剑不应共享同一个缓存条目
        if (ComponentCacheManager.hasNonTrivialData(itemStack)) {
            return null; // 强制调用方重新计算稀有度
        }

        // 3. 回退到ID缓存(基于物品类型ID,适用于稀有度仅取决于物品类型的场景)
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
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
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

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
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
     * 缓存物品稀有度（自动检测组件匹配规则）
     * 缓存策略:
     * - 有组件匹配规则或有特殊NBT数据的物品 → 组件缓存(基于NBT哈希,区分不同物品堆)
     * - 无特殊数据的普通物品 → ID缓存(基于物品类型ID)
     * @param itemStack 物品堆
     * @param rarity 稀有度等级
     */
    public static void cacheRarity(ItemStack itemStack, Integer rarity) {
        if (itemStack == null || itemStack.isEmpty() || rarity == null) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return;
        }

        boolean hasComponentMatch = hasComponentMatchRules(itemId);
        // 对有特殊NBT数据的物品也使用组件缓存(如神化模组物品的稀有度基于数据组件)
        // 这防止了ID缓存污染:同类型但不同NBT数据的物品堆不应共享同一个ID缓存条目
        if (!hasComponentMatch && ComponentCacheManager.hasNonTrivialData(itemStack)) {
            hasComponentMatch = true;
        }
        cacheRarity(itemStack, rarity, hasComponentMatch);
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
    public static void updateIdCache(ResourceLocation itemId, Integer rarity) {
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
    public static void invalidate(ResourceLocation itemId) {
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
    public static boolean hasComponentMatchRules(ResourceLocation itemId) {
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
