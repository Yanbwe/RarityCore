package org.yanbwe.raritycore.cache;

import net.minecraftforge.registries.ForgeRegistries;

/**
 * 缓存系统配置
 */
public class CacheConfig {
    // NBT缓存开关
    private boolean nbtCacheEnabled = true;
    
    // NBT缓存最大条目数（固定模式）
    private int maxNbtCacheSize = 10000;
    
    // 是否启用动态容量
    private boolean dynamicCapacityEnabled = true;
    
    // 动态容量倍数（当前物品数量的倍数）
    private double dynamicCapacityMultiplier = 2.0;
    
    // 动态容量最小值
    private int dynamicCapacityMin = 1000;
    
    // 动态容量最大值
    private int dynamicCapacityMax = 999999;
    
    // 缓存清理阈值（0.0-1.0）
    private double cleanupThreshold = 0.9;
    
    public CacheConfig() {
        // 可以从配置文件加载默认值
        loadFromConfig();
    }
    
    private void loadFromConfig() {
        // TODO: 从实际配置文件加载
        // 暂时使用硬编码默认值
    }
    
    /**
     * 获取NBT缓存的实际最大容量（动态计算）
     * @return 计算后的最大容量
     */
    public int getActualMaxNbtCacheSize() {
        if (!dynamicCapacityEnabled) {
            return maxNbtCacheSize;
        }
        
        // 获取当前物品总数
        int itemCount = getCurrentItemCount();
        
        // 计算动态容量
        int dynamicSize = (int) (itemCount * dynamicCapacityMultiplier);
        
        // 应用最小值和最大值限制
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
            return ForgeRegistries.ITEMS.getValues().size();
        } catch (Exception e) {
            // 如果获取失败，返回默认值
            return 1000;
        }
    }
    
    // getter/setter方法
    public boolean isNbtCacheEnabled() {
        return nbtCacheEnabled;
    }
    
    public void setNbtCacheEnabled(boolean nbtCacheEnabled) {
        this.nbtCacheEnabled = nbtCacheEnabled;
    }
    
    public int getMaxNbtCacheSize() {
        return maxNbtCacheSize;
    }
    
    public void setMaxNbtCacheSize(int maxNbtCacheSize) {
        this.maxNbtCacheSize = maxNbtCacheSize;
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