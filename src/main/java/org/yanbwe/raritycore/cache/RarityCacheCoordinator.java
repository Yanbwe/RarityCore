package org.yanbwe.raritycore.cache;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter;
import org.yanbwe.raritycore.compat.ironsspells.IronSpellsAdapter;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.itemdatamatching.ItemDataRarityMatcher;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 稀有度缓存协调器
 * 统一协调ID缓存和组件缓存的两级缓存系统
 */
public class RarityCacheCoordinator {

    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    /** 配置重载的防重复刷新保护 */
    private static volatile boolean isReloading = false;
    private static volatile long lastReloadTime = 0;
    private static final long MIN_RELOAD_INTERVAL_MS = 1000;

    /** 神化模组激活状态缓存 — 避免快速路径中每次调用都进入 try-catch */
    private static volatile boolean cachedApotheosisActive = false;
    private static volatile boolean apotheosisActiveChecked = false;

    /** Iron's Spells 模组激活状态缓存 — 避免快速路径中每次调用都进入 try-catch */
    private static volatile boolean cachedIronSpellsActive = false;
    private static volatile boolean ironSpellsActiveChecked = false;

    /**
     * 初始化缓存系统
     */
    public static void initialize() {
        if (!initialized.compareAndSet(false, true)) {
            return; // Another thread already completed or is completing initialization
        }

        IdCacheManager.initialize();
        ComponentCacheManager.initialize();

        RarityCore.LOGGER.info("稀有度缓存协调器初始化完成");
    }

    /**
     * 获取物品的缓存稀有度（性能优化版，含神化模组兼容）
     * 查询逻辑：
     * 1. 无组件匹配规则 + 神化模组未激活 → 直接ID缓存（快速路径，无序列化）
     * 2. 无组件匹配规则 + 神化模组激活 → 必须先检查组件缓存和hasNonTrivialData
     *    （神化物品的稀有度基于组件数据，不能用类型级ID缓存）
     * 3. 有组件匹配规则 → 完整逻辑：组件缓存 → hasNonTrivialData → ID缓存
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

        // P2 FIX: 无组件匹配规则且神化/Iron's Spells 均未激活 → 快速ID缓存路径
        // 神化/Iron's Spells 激活时必须走完整逻辑，因为它们的稀有度基于组件数据
        if (!hasComponentMatchRules(itemId) && !isApotheosisActive() && !isIronSpellsActive()) {
            return IdCacheManager.getCachedRarity(itemId);
        }

        // 以下为需要组件感知缓存的逻辑
        // 1. 优先检查组件缓存(基于ItemStack NBT哈希)
        Integer componentRarity = ComponentCacheManager.getCachedRarity(itemStack);
        if (componentRarity != null) {
            return componentRarity;
        }

        // 2. 如果物品有特殊NBT数据(如神化组件、附魔等),跳过ID缓存
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
     * 缓存物品稀有度（自动检测组件匹配规则，性能优化版 + 神化兼容）
     * 缓存策略:
     * - 有组件匹配规则的物品 → 组件缓存(基于NBT哈希,区分不同物品堆)
     * - 神化模组激活且有非平凡NBT的物品 → 组件缓存(防止ID缓存污染神化物品)
     * - 其他物品 → ID缓存(基于物品类型ID)
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
        if (hasComponentMatch) {
            // 有组件匹配规则 → 必须区分不同NBT的物品堆
            ComponentCacheManager.cacheRarity(itemStack, rarity);
        } else if ((isApotheosisActive() || isIronSpellsActive()) && ComponentCacheManager.hasNonTrivialData(itemStack)) {
            // 神化/Iron's Spells 激活 + 物品有特殊NBT → 组件缓存(防止ID缓存污染)
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
        long currentTime = System.currentTimeMillis();

        // 防止短时间内重复调用（与 IdCacheManager 一致的防重复刷新机制）
        if (isReloading || (currentTime - lastReloadTime) < MIN_RELOAD_INTERVAL_MS) {
            return;
        }

        synchronized (RarityCacheCoordinator.class) {
            if (isReloading || (currentTime - lastReloadTime) < MIN_RELOAD_INTERVAL_MS) {
                return;
            }
            isReloading = true;
            lastReloadTime = currentTime;
        }

        try {
            IdCacheManager.handleConfigReload();
            ComponentCacheManager.handleConfigReload();
            // 重置神化和 Iron's Spells 激活状态缓存，强制下次调用时重新评估
            apotheosisActiveChecked = false;
            ironSpellsActiveChecked = false;
            RarityCore.LOGGER.info("稀有度缓存系统重载完成");
        } finally {
            isReloading = false;
        }
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
     * 检查神化模组兼容是否激活（影响缓存策略选择）— 懒加载缓存版本
     * 首次调用时执行 try-catch 检查并缓存结果；后续调用直接返回缓存值。
     * 配置重载后通过 handleConfigReload() 重置缓存，触发重新评估。
     * @return 神化兼容是否处于激活状态
     */
    private static boolean isApotheosisActive() {
        if (apotheosisActiveChecked) {
            return cachedApotheosisActive;
        }
        // 首次调用：执行实际检查并缓存结果
        try {
            cachedApotheosisActive = ServerConfigManager.isCheckApotheosisRarity()
                && ApotheosisAdapter.isLoaded();
        } catch (Exception e) {
            // 安全回退：如果无法检查神化状态，保守返回false使用快速路径
            cachedApotheosisActive = false;
        }
        apotheosisActiveChecked = true;
        return cachedApotheosisActive;
    }

    /**
     * 检查 Iron's Spells 兼容是否激活（影响缓存策略选择）— 懒加载缓存版本。
     * 与 {@link #isApotheosisActive()} 机制一致：Iron's Spells 物品的稀有度
     * 基于 spell_container 组件数据，不能用类型级 ID 缓存，必须走组件缓存路径。
     * @return Iron's Spells 兼容是否处于激活状态
     */
    private static boolean isIronSpellsActive() {
        if (ironSpellsActiveChecked) {
            return cachedIronSpellsActive;
        }
        try {
            cachedIronSpellsActive = IronSpellsAdapter.isLoaded();
        } catch (Exception e) {
            cachedIronSpellsActive = false;
        }
        ironSpellsActiveChecked = true;
        return cachedIronSpellsActive;
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
