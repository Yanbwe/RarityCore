package org.yanbwe.raritycore.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.CacheMetrics.CacheType;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.concurrent.TimeUnit;

/**
 * 双缓存管理系统
 * ID缓存：基于物品ID的永久性缓存，预加载所有注册物品
 * NBT缓存：基于完整NBT数据的LRU缓存，动态构建
 */
public class DualCacheManager {
    
    // ID缓存 - 永久性，预加载所有物品
    private static Cache<ResourceLocation, Integer> idCache;
    
    // NBT缓存 - LRU动态管理
    private static Cache<String, Integer> nbtCache;
    
    // 缓存配置
    private static CacheConfig config;
    
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
        // ID缓存：无大小限制，永不过期
        idCache = CacheBuilder.newBuilder()
            .build();
        
        // NBT缓存：LRU策略，大小限制
        nbtCache = CacheBuilder.newBuilder()
            .maximumSize(config.getMaxNbtCacheSize())
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .removalListener(notification -> {
                RarityCore.LOGGER.debug("NBT cache entry removed: {}", notification.getKey());
            })
            .build();
    }
    
    /**
     * 预加载ID缓存
     */
    private static void preloadIdCache() {
        ForgeRegistries.ITEMS.getValues().forEach(item -> {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                Integer baseRarity = RarityRegistry.getRarity(item);
                idCache.put(itemId, baseRarity);
            }
        });
        
        RarityCore.LOGGER.info("ID cache preloaded with {} items", idCache.size());
    }
    
    /**
     * 获取缓存的稀有度
     */
    public static Integer getCachedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        
        // 优先检查NBT缓存
        if (config.isNbtCacheEnabled() && itemStack.hasTag()) {
            String nbtKey = generateNbtKey(itemStack);
            Integer nbtResult = nbtCache.getIfPresent(nbtKey);
            if (nbtResult != null) {
                CacheMetrics.recordHit(CacheType.NBT);
                return nbtResult;
            }
        }
        
        // 回退到ID缓存
        ResourceLocation idKey = generateIdKey(itemStack);
        Integer idResult = idCache.getIfPresent(idKey);
        if (idResult != null) {
            CacheMetrics.recordHit(CacheType.ID);
            // 关键修改：如果有NBT数据但ID缓存命中，仍然返回null以触发填充
            if (itemStack.hasTag() && config.isNbtCacheEnabled()) {
                return null;
            }
            return idResult;
        }
        
        CacheMetrics.recordMiss();
        return null;
    }
    
    /**
     * 缓存稀有度
     */
    public static void cacheRarity(ItemStack itemStack, Integer rarity) {
        if (itemStack == null || itemStack.isEmpty() || rarity == null) {
            return;
        }
        
        // 总是填充ID缓存
        ResourceLocation idKey = generateIdKey(itemStack);
        idCache.put(idKey, rarity);
        
        // 条件填充NBT缓存
        if (config.isNbtCacheEnabled() && itemStack.hasTag()) {
            String nbtKey = generateNbtKey(itemStack);
            nbtCache.put(nbtKey, rarity);
        }
    }
    
    /**
     * 处理配置重载
     */
    public static void handleConfigReload() {
        // 清空所有缓存
        idCache.invalidateAll();
        nbtCache.invalidateAll();
        
        // 重建ID缓存
        preloadIdCache();
        
        // NBT缓存保持空状态，按需填充
        RarityCore.LOGGER.info("Dual cache system reloaded - ID cache: {}, NBT cache: {}", 
            idCache.size(), nbtCache.size());
    }
    
    /**
     * 生成ID缓存键
     */
    private static ResourceLocation generateIdKey(ItemStack itemStack) {
        return ForgeRegistries.ITEMS.getKey(itemStack.getItem());
    }
    
    /**
     * 生成NBT缓存键
     */
    private static String generateNbtKey(ItemStack itemStack) {
        ResourceLocation itemId = generateIdKey(itemStack);
        StringBuilder key = new StringBuilder(itemId.toString());
        
        if (itemStack.hasTag()) {
            key.append("|nbt:").append(itemStack.getTag().toString());
        }
        
        return key.toString();
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