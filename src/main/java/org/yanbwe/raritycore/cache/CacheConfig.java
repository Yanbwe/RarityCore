package org.yanbwe.raritycore.cache;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.JsonPerformanceOptimizer;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 缓存系统配置
 */
public class CacheConfig {
    /** 缓存配置文件路径 */
    private static final Path CACHE_CONFIG_FILE = Paths.get(RarityConstants.CONFIG_DIR_PARENT)
        .resolve(RarityConstants.CONFIG_DIR_NAME)
        .resolve("cache.json");

    private int maxCacheSize = 10000;

    private boolean dynamicCapacityEnabled = true;

    private double dynamicCapacityMultiplier = 20.0;

    private int dynamicCapacityMin = 1000;

    private int dynamicCapacityMax = 999999;

    private double cleanupThreshold = 0.9;

    public CacheConfig() {
        loadFromConfig();
    }

    /**
     * 从配置文件加载缓存参数。如果配置文件不存在或读取失败，使用硬编码默认值。
     */
    private void loadFromConfig() {
        if (!Files.exists(CACHE_CONFIG_FILE)) {
            return; // 配置文件不存在，使用硬编码默认值
        }

        try (BufferedReader reader = Files.newBufferedReader(CACHE_CONFIG_FILE)) {
            Gson gson = JsonPerformanceOptimizer.getOptimizedGson();
            JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);

            if (jsonObject != null) {
                if (jsonObject.has("maxCacheSize")) {
                    this.maxCacheSize = Math.max(1, jsonObject.get("maxCacheSize").getAsInt());
                }

                if (jsonObject.has("dynamicCapacityMultiplier")) {
                    this.dynamicCapacityMultiplier = Math.max(0.1,
                        jsonObject.get("dynamicCapacityMultiplier").getAsDouble());
                }

                RarityCore.LOGGER.info("Cache config loaded: maxCacheSize={}, dynamicCapacityMultiplier={}",
                    maxCacheSize, dynamicCapacityMultiplier);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to load cache config from {}, using hardcoded defaults",
                CACHE_CONFIG_FILE, e);
        }
    }

    /**
     * 获取缓存的实际最大容量
     * @return 计算后的最大容量
     */
    public int getActualMaxCacheSize() {
        if (!dynamicCapacityEnabled) {
            return maxCacheSize;
        }

        int itemCount = getCurrentItemCount();

        int dynamicSize = (int) (itemCount * dynamicCapacityMultiplier);

        dynamicSize = Math.max(dynamicCapacityMin, dynamicSize);
        dynamicSize = Math.min(dynamicCapacityMax, dynamicSize);

        return dynamicSize;
    }

    /**
     * 获取当前注册的物品总数
     * @return 物品注册表中的物品数量
     */
    private int getCurrentItemCount() {
        try {
            return (int) BuiltInRegistries.ITEM.stream().count();
        } catch (Exception e) {
            return 1000;
        }
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    public boolean isDynamicCapacityEnabled() {
        return dynamicCapacityEnabled;
    }

    public void setDynamicCapacityEnabled(boolean dynamicCapacityEnabled) {
        this.dynamicCapacityEnabled = dynamicCapacityEnabled;
    }

    public double getDynamicCapacityMultiplier() {
        return dynamicCapacityMultiplier;
    }

    public void setDynamicCapacityMultiplier(double dynamicCapacityMultiplier) {
        this.dynamicCapacityMultiplier = Math.max(0.1, dynamicCapacityMultiplier);
    }

    public int getDynamicCapacityMin() {
        return dynamicCapacityMin;
    }

    public void setDynamicCapacityMin(int dynamicCapacityMin) {
        this.dynamicCapacityMin = Math.max(1, dynamicCapacityMin);
    }

    public int getDynamicCapacityMax() {
        return dynamicCapacityMax;
    }

    public void setDynamicCapacityMax(int dynamicCapacityMax) {
        this.dynamicCapacityMax = Math.max(1, dynamicCapacityMax);
    }

    public double getCleanupThreshold() {
        return cleanupThreshold;
    }

    public void setCleanupThreshold(double cleanupThreshold) {
        this.cleanupThreshold = Math.max(0.0, Math.min(1.0, cleanupThreshold));
    }
}
