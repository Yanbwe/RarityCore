package org.yanbwe.raritycore.cache;

/**
 * 缓存系统配置
 */
public class CacheConfig {
    // NBT缓存开关
    private boolean nbtCacheEnabled = true;
    
    // NBT缓存最大条目数
    private int maxNbtCacheSize = 10000;
    
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
    
    public double getCleanupThreshold() {
        return cleanupThreshold;
    }
    
    public void setCleanupThreshold(double cleanupThreshold) {
        this.cleanupThreshold = Math.max(0.0, Math.min(1.0, cleanupThreshold));
    }
}