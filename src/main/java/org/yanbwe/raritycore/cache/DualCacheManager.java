package org.yanbwe.raritycore.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.yanbwe.raritycore.RarityCore;

import java.util.concurrent.TimeUnit;

public class DualCacheManager {

    private static volatile Cache<String, Integer> itemDataCache;

    private static volatile CacheConfig config;

    private static volatile boolean isReloading = false;
    private static volatile long lastReloadTime = 0;
    private static final long MIN_RELOAD_INTERVAL = 1000;

    public static CacheConfig getConfig() {
        return config;
    }

    public static void initialize() {
        config = new CacheConfig();
        createCaches();
        RarityCore.LOGGER.info("Unified cache system initialized - capacity: {}",
            config.getActualMaxCacheSize());
    }

    private static void createCaches() {
        itemDataCache = createItemDataCache();
    }

    private static Cache<String, Integer> createItemDataCache() {
        int actualCacheSize = config.getActualMaxCacheSize();

        Cache<String, Integer> cache = CacheBuilder.newBuilder()
            .maximumSize(actualCacheSize)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

        RarityCore.LOGGER.info("Unified cache created with dynamic capacity: {} entries", actualCacheSize);
        return cache;
    }

    public static Integer getCachedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        String unifiedKey = generateUnifiedKey(itemStack);
        if (unifiedKey == null) {
            return null;
        }

        Integer result = itemDataCache.getIfPresent(unifiedKey);
        if (result != null) {
            CacheMetrics.recordHit();
            return result;
        }

        CacheMetrics.recordMiss();
        return null;
    }

    public static void cacheRarity(ItemStack itemStack, Integer rarity) {
        if (itemStack == null || itemStack.isEmpty() || rarity == null) {
            return;
        }

        String unifiedKey = generateUnifiedKey(itemStack);
        if (unifiedKey != null) {
            itemDataCache.put(unifiedKey, rarity);
        }
    }

    public static void handleConfigReload() {
        long currentTime = System.currentTimeMillis();

        if (isReloading || (currentTime - lastReloadTime) < MIN_RELOAD_INTERVAL) {
            return;
        }

        synchronized (DualCacheManager.class) {
            if (isReloading || (currentTime - lastReloadTime) < MIN_RELOAD_INTERVAL) {
                return;
            }

            isReloading = true;
            lastReloadTime = currentTime;
        }

        try {
            itemDataCache.invalidateAll();
            itemDataCache = createItemDataCache();

            RarityCore.LOGGER.info("Unified cache system reloaded - capacity: {}",
                config.getActualMaxCacheSize());
        } finally {
            isReloading = false;
        }
    }

    private static String generateUnifiedKey(ItemStack itemStack) {
        var itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null) {
            return null;
        }

        if (org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter.hasApotheosisRarity(itemStack)) {
            return null;
        }

        StringBuilder key = new StringBuilder(itemId.toString());

        try {
            var tag = itemStack.save(net.minecraft.core.RegistryAccess.EMPTY);
            if (tag instanceof net.minecraft.nbt.CompoundTag compoundTag) {
                net.minecraft.nbt.CompoundTag filteredTag = new net.minecraft.nbt.CompoundTag();

                for (String keyName : compoundTag.getAllKeys()) {
                    if (!keyName.equals("id") && !keyName.equals("count")) {
                        filteredTag.put(keyName, compoundTag.get(keyName));
                    }
                }

                if (!filteredTag.isEmpty()) {
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                    byte[] hash = md.digest(filteredTag.toString().getBytes());
                    StringBuilder hexString = new StringBuilder();
                    for (byte b : hash) {
                        hexString.append(String.format("%02x", b));
                    }
                    key.append("|hash:").append(hexString.toString());
                }
            }
        } catch (IllegalStateException e) {
            RarityCore.LOGGER.debug("Error generating unified cache key (registry access issue, using item ID only): {}", e.getMessage());
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Error generating unified cache key: {}", e.getMessage());
        }

        return key.toString();
    }

    public static CacheStatistics getStatistics() {
        return new CacheStatistics(
            itemDataCache.size(),
            CacheMetrics.getOverallHitRate()
        );
    }

    public static class CacheStatistics {
        private final long cacheSize;
        private final double overallHitRate;

        public CacheStatistics(long cacheSize, double overallHitRate) {
            this.cacheSize = cacheSize;
            this.overallHitRate = overallHitRate;
        }

        public long getCacheSize() { return cacheSize; }
        public double getOverallHitRate() { return overallHitRate; }

        @Override
        public String toString() {
            return String.format("CacheStats{Size: %d, Overall: %.1f%%}",
                cacheSize, overallHitRate);
        }
    }
}