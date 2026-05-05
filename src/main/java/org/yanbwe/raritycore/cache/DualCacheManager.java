package org.yanbwe.raritycore.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher;
import org.yanbwe.raritycore.cache.CacheMetrics.CacheType;

import java.util.concurrent.TimeUnit;

/**
 * 双缓存管理系统
 * ID缓存:基于物品ID的永久性缓存,预加载所有注册物品
 * NBT缓存:基于完整NBT数据的LRU缓存,动态构建
 */
public class DualCacheManager {

    // ID缓存 - 永久性,预加载所有物品
    private static volatile Cache<ResourceLocation, Integer> idCache;
    
    // NBT缓存 - LRU动态管理
    private static volatile Cache<String, Integer> nbtCache;
    
    // 缓存配置
    private static volatile CacheConfig config;
    
    // 防抖相关变量
    private static volatile boolean isReloading = false;
    private static volatile long lastReloadTime = 0;
    private static final long MIN_RELOAD_INTERVAL = 1000; // 最小重载间隔1秒
    
    /**
     * 获取缓存配置实例
     */
    public static CacheConfig getConfig() {
        return config;
    }
    
    /**
     * 初始化双缓存系统
     */
    public static void initialize() {
        config = new CacheConfig();
        createCaches();
        preloadIdCache();
        RarityCore.LOGGER.info("Dual cache system initialized - ID cache: {}, NBT cache: {}", 
            idCache.size(), nbtCache.size());
    }
    
    /**
     * 创建缓存实例
     */
    private static void createCaches() {
        // ID缓存:无大小限制,永不过期
        idCache = CacheBuilder.newBuilder()
            .build();
        
        // NBT缓存:LRU策略,使用动态容量
        nbtCache = createNbtCache();
    }
    
    /**
     * 创建NBT缓存
     */
    private static Cache<String, Integer> createNbtCache() {
        // 获取动态容量
        int actualNbtCacheSize = config.getActualMaxNbtCacheSize();
        
        // 创建NBT缓存
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
            .maximumSize(actualNbtCacheSize)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
        
        RarityCore.LOGGER.info("NBT cache created with dynamic capacity: {} entries", actualNbtCacheSize);
        return cache;
    }
    
    /**
     * 预加载 ID 缓存
     * 注意:仅从配置映射中读取已配置的稀有度,不调用 RarityRegistry.getRarity()
     * 以避免触发某些物品的 getRarity() 方法导致 ClientLevel 数组越界
     */
    @SuppressWarnings("null")
    private static void preloadIdCache() {
        // 使用线程安全的原子计数器
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger errorCount = new java.util.concurrent.atomic.AtomicInteger(0);
            
        // 直接从配置映射中预加载,避免调用物品的 getRarity() 方法
        try {
            // 使用并行流加速预加载过程
            org.yanbwe.raritycore.registry.RarityRegistry.ITEM_RARITY_MAP.entrySet().parallelStream().forEach(entry -> {
                try {
                    idCache.put(entry.getKey(), entry.getValue());
                    successCount.incrementAndGet();
                } catch (Throwable e) {
                    errorCount.incrementAndGet();
                    RarityCore.LOGGER.debug("Failed to cache rarity for item: {}", entry.getKey(), e);
                }
            });
        } catch (Throwable e) {
            RarityCore.LOGGER.error("Error loading ITEM_RARITY_MAP during cache preload", e);
        }
            
        RarityCore.LOGGER.info("ID cache preloaded: {} items from config, {} items failed", 
            successCount.get(), errorCount.get());
    }
    
    /**
     * 获取缓存的稀有度
     */
    @SuppressWarnings("null")
    public static Integer getCachedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        // 一次获取 tag 引用，避免后续重复调用 getTag() 导致的重复序列化
        // 使用 CompoundTag.contains(key) 做 O(1) 键查找，而非 toString().contains() 做全量序列化
        net.minecraft.nbt.CompoundTag tag = itemStack.hasTag() ? itemStack.getTag() : null;

        // 神化物品(有 affix_data 键)不使用常规缓存，强制实时计算
        if (tag != null && tag.contains("affix_data")) {
            return null;
        }

        // Iron's Spellbooks 法术卷轴/法术书(有 irons_spellbooks:spell_container 键)也不使用常规缓存，强制实时计算
        if (tag != null && tag.contains("irons_spellbooks:spell_container")) {
            return null;
        }

        // 优先检查NBT缓存
        if (config.isNbtCacheEnabled() && tag != null) {
            String nbtKey = generateNbtKey(itemStack);
            Integer nbtResult = nbtCache.getIfPresent(nbtKey);
            if (nbtResult != null) {
                CacheMetrics.recordHit(CacheType.NBT);
                return nbtResult;
            }
        }

        // 检查物品是否有NBT匹配规则，如果有则跳过ID缓存
        ResourceLocation idKey = generateIdKey(itemStack);
        if (idKey != null && NbtRarityMatcher.hasRulesForItem(idKey)) {
            // 物品有NBT匹配规则，跳过ID缓存，强制重新计算
            CacheMetrics.recordMiss();
            return null;
        }

        // 回退到 ID 缓存
        Integer idResult = idCache.getIfPresent(idKey);
        if (idResult != null) {
            CacheMetrics.recordHit(CacheType.ID);
            // 关键修复:移除强制返回 null 的逻辑,直接返回 ID 缓存结果
            // 这样可以让没有 NBT 配置的物品直接使用 ID 缓存,避免重复计算
            return idResult;
        }

        CacheMetrics.recordMiss();
        return null;
    }
    
    /**
     * 缓存稀有度
     * 注意:对于含有神化NBT数据的物品,仅缓存到NBT缓存而不同时更新ID缓存,
     * 以避免神化稀有度污染同类型物品的默认稀有度
     */
    @SuppressWarnings("null")
    public static void cacheRarity(ItemStack itemStack, Integer rarity) {
        if (itemStack == null || itemStack.isEmpty() || rarity == null) {
            return;
        }
        
        // 一次获取 tag 引用，使用 O(1) 键查找代替全量 toString() 序列化
        net.minecraft.nbt.CompoundTag tag = itemStack.hasTag() ? itemStack.getTag() : null;
        boolean hasApotheosisData = tag != null && tag.contains("affix_data");
        boolean hasIronsData = tag != null && tag.contains("irons_spellbooks:spell_container");
        
        if (hasApotheosisData || hasIronsData) {
            // 神化/Iron's Spellbooks 物品:仅缓存到NBT缓存,避免污染ID缓存
            if (config.isNbtCacheEnabled()) {
                String nbtKey = generateNbtKey(itemStack);
                nbtCache.put(nbtKey, rarity);
            }
        } else {
            // 非神化非Iron's Spellbooks物品:正常写入ID缓存
            ResourceLocation idKey = generateIdKey(itemStack);
            idCache.put(idKey, rarity);
            
            // 条件填充NBT缓存
            if (config.isNbtCacheEnabled() && tag != null) {
                String nbtKey = generateNbtKey(itemStack);
                nbtCache.put(nbtKey, rarity);
            }
        }
    }
    
    /**
     * 处理配置重载(带防抖机制)
     */
    public static void handleConfigReload() {
        long currentTime = System.currentTimeMillis();

        // 防抖检查:如果正在重载或者距离上次重载时间太短,则跳过
        if (isReloading || (currentTime - lastReloadTime) < MIN_RELOAD_INTERVAL) {
            return;
        }

        synchronized (DualCacheManager.class) {
            // 双重检查锁定
            if (isReloading || (currentTime - lastReloadTime) < MIN_RELOAD_INTERVAL) {
                return;
            }

            isReloading = true;
            lastReloadTime = currentTime;
        }

        try {
            // 检查缓存是否已初始化
            if (idCache == null || nbtCache == null) {
                RarityCore.LOGGER.warn("DualCacheManager not initialized yet, skipping reload");
                return;
            }

            // 清空所有缓存
            idCache.invalidateAll();
            nbtCache.invalidateAll();

            // 重建ID缓存
            preloadIdCache();

            // 重新创建NBT缓存以应用新的容量设置
            nbtCache = createNbtCache();

            RarityCore.LOGGER.info("Dual cache system reloaded - ID cache: {}, NBT cache: {}",
                idCache.size(), nbtCache.size());
        } finally {
            isReloading = false;
        }
    }
    
    
    
    /**
     * 生成ID缓存键
     */
    private static ResourceLocation generateIdKey(ItemStack itemStack) {
        return ForgeRegistries.ITEMS.getKey(itemStack.getItem());
    }

    /**
     * 检查是否应该跳过NBT缓存键生成
     * 当物品没有NBT匹配规则时使用
     */
    private static boolean shouldSkipNbtKeyGeneration(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return true;
        }
        Item item = itemStack.getItem();
        if (item == null) {
            return true;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null) {
            return true;
        }
        return !org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher.hasRulesForItem(itemId);
    }

    /**
     * 生成NBT缓存键
     */
    private static String generateNbtKey(ItemStack itemStack) {
        ResourceLocation itemId = generateIdKey(itemStack);
        if (itemId == null) {
            return "unknown:item";
        }

        if (shouldSkipNbtKeyGeneration(itemStack)) {
            return itemId.toString();
        }

        StringBuilder key = new StringBuilder(itemId.toString());

        if (itemStack.hasTag() && itemStack.getTag() != null) {
            net.minecraft.nbt.CompoundTag tag = itemStack.getTag();
            if (tag != null) {
                // 使用 CompoundTag.hashCode() 替代 toString()+MD5
                // hashCode() 遍历 NBT 树使用原始类型操作，无需字符串序列化
                int nbtHash = tag.hashCode();
                key.append("|nbt:").append(nbtHash);
            }
        }

        return key.toString();
    }
    
    // ========================
    // SimpleNbtCache 委托接口
    // ========================
    
    /**
     * 直接查询NBT缓存（供SimpleNbtCache委托使用）
     * 仅查询共享的NBT缓存，不进行ID缓存回退或神化物品检查
     * @param nbtKey NBT缓存键（由SimpleNbtCache.generateNbtCacheKey生成）
     * @return 缓存的稀有度，未命中返回null
     */
    public static Integer getCachedNbtRarity(String nbtKey) {
        if (nbtKey == null || nbtKey.isEmpty() || nbtCache == null) {
            return null;
        }
        return nbtCache.getIfPresent(nbtKey);
    }
    
    /**
     * 直接写入NBT缓存（供SimpleNbtCache委托使用）
     * @param nbtKey NBT缓存键
     * @param rarity 稀有度值
     */
    public static void cacheNbtRarity(String nbtKey, Integer rarity) {
        if (nbtKey == null || nbtKey.isEmpty() || rarity == null || nbtCache == null) {
            return;
        }
        nbtCache.put(nbtKey, rarity);
    }
    
    /**
     * 清除指定NBT缓存条目（供SimpleNbtCache委托使用）
     * @param nbtKey NBT缓存键
     */
    public static void invalidateNbtCacheEntry(String nbtKey) {
        if (nbtKey != null && !nbtKey.isEmpty() && nbtCache != null) {
            nbtCache.invalidate(nbtKey);
        }
    }
    
    /**
     * 清空所有NBT缓存（供SimpleNbtCache.reinitializeCache委托使用）
     */
    public static void invalidateAllNbtCache() {
        if (nbtCache != null) {
            nbtCache.invalidateAll();
        }
    }
    
    /**
     * 获取NBT缓存当前大小
     * @return 缓存条目数
     */
    public static long getNbtCacheSize() {
        return nbtCache != null ? nbtCache.size() : 0;
    }
    
    /**
     * 获取缓存统计信息
     */
    public static CacheStatistics getStatistics() {
        return new CacheStatistics(
            idCache.size(),
            nbtCache.size(),
            CacheMetrics.getIdHitRate(),
            CacheMetrics.getNbtHitRate(),
            CacheMetrics.getOverallHitRate()
        );
    }
    
    /**
     * 缓存统计信息类
     */
    public static class CacheStatistics {
        private final long idCacheSize;
        private final long nbtCacheSize;
        private final double idHitRate;
        private final double nbtHitRate;
        private final double overallHitRate;
        
        public CacheStatistics(long idCacheSize, long nbtCacheSize, 
                             double idHitRate, double nbtHitRate, double overallHitRate) {
            this.idCacheSize = idCacheSize;
            this.nbtCacheSize = nbtCacheSize;
            this.idHitRate = idHitRate;
            this.nbtHitRate = nbtHitRate;
            this.overallHitRate = overallHitRate;
        }
        
        // getter方法
        public long getIdCacheSize() { return idCacheSize; }
        public long getNbtCacheSize() { return nbtCacheSize; }
        public double getIdHitRate() { return idHitRate; }
        public double getNbtHitRate() { return nbtHitRate; }
        public double getOverallHitRate() { return overallHitRate; }
        
        @Override
        public String toString() {
            return String.format("CacheStats{ID: %d/%.1f%%, NBT: %d/%.1f%%, Overall: %.1f%%}",
                idCacheSize, idHitRate, nbtCacheSize, nbtHitRate, overallHitRate);
        }
    }
}