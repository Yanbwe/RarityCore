package org.yanbwe.raritycore.cache;

import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 缓存系统配置
 */
public class CacheConfig {
    private int maxCacheSize = 10000;

    private boolean dynamicCapacityEnabled = true;

    private double dynamicCapacityMultiplier = 20.0;

    private int dynamicCapacityMin = 1000;

    private int dynamicCapacityMax = 999999;

    private double cleanupThreshold = 0.9;

    public CacheConfig() {
        loadFromConfig();
    }

    private void loadFromConfig() {
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
