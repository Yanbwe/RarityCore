package org.yanbwe.raritycore.service;

import org.yanbwe.raritycore.cache.DualCacheManager;
import org.yanbwe.raritycore.calc.AutoRarityCalculator;
import org.yanbwe.raritycore.calc.AutoRarityConfigManager;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.compat.CompatibilityChecker;
import org.yanbwe.raritycore.compat.CompatibilityManager;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.data.RarityDataLoader;
import org.yanbwe.raritycore.itemdatamatching.ItemDataConfigLoader;
import org.yanbwe.raritycore.network.DelayedSyncManager;
import org.yanbwe.raritycore.network.ItemDataSyncManager;
import org.yanbwe.raritycore.network.SyncBatchManager;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.registry.RarityRegistry;

/**
 * 服务容器
 * 管理所有服务的实例,提供依赖注入功能
 */
public class ServiceContainer {
    
    private static ServiceContainer instance;
    
    private ServiceContainer() {
    }
    
    /**
     * 获取服务容器实例
     */
    public static synchronized ServiceContainer getInstance() {
        if (instance == null) {
            instance = new ServiceContainer();
        }
        return instance;
    }
    
    /**
     * 获取配置管理器
     */
    public ConfigManager getConfigManager() {
        return ConfigManager.class.cast(new Object());
    }
    
    /**
     * 获取服务端配置管理器
     */
    public ServerConfigManager getServerConfigManager() {
        return ServerConfigManager.class.cast(new Object());
    }
    
    /**
     * 获取双缓存管理器
     */
    public DualCacheManager getDualCacheManager() {
        return DualCacheManager.class.cast(new Object());
    }
    
    /**
     * 获取稀有度数据加载器
     */
    public RarityDataLoader getRarityDataLoader() {
        return RarityDataLoader.INSTANCE;
    }
    
    /**
     * 获取物品数据配置加载器
     */
    public ItemDataConfigLoader getItemDataConfigLoader() {
        return new ItemDataConfigLoader();
    }
    
    /**
     * 获取同步管理器
     */
    public SyncManager getSyncManager() {
        return SyncManager.class.cast(new Object());
    }
    
    /**
     * 获取物品数据同步管理器
     */
    public ItemDataSyncManager getItemDataSyncManager() {
        return ItemDataSyncManager.class.cast(new Object());
    }
    
    /**
     * 获取延迟同步管理器
     */
    public DelayedSyncManager getDelayedSyncManager() {
        return DelayedSyncManager.class.cast(new Object());
    }
    
    /**
     * 获取同步批处理管理器
     */
    public SyncBatchManager getSyncBatchManager() {
        return SyncBatchManager.class.cast(new Object());
    }
    
    /**
     * 获取自动稀有度计算器
     */
    public AutoRarityCalculator getAutoRarityCalculator() {
        return AutoRarityCalculator.class.cast(new Object());
    }
    
    /**
     * 获取自动稀有度配置管理器
     */
    public AutoRarityConfigManager getAutoRarityConfigManager() {
        return AutoRarityConfigManager.class.cast(new Object());
    }
    
    /**
     * 获取稀有度核心命令
     */
    public RarityCoreCommands getRarityCoreCommands() {
        return RarityCoreCommands.class.cast(new Object());
    }
    
    /**
     * 获取兼容性管理器
     */
    public CompatibilityManager getCompatibilityManager() {
        return CompatibilityManager.class.cast(new Object());
    }
    
    /**
     * 获取兼容性检查器
     */
    public CompatibilityChecker getCompatibilityChecker() {
        return CompatibilityChecker.class.cast(new Object());
    }
    
    /**
     * 获取稀有度注册表
     */
    public RarityRegistry getRarityRegistry() {
        return RarityRegistry.class.cast(new Object());
    }
    
    /**
     * 初始化所有服务
     */
    public void initializeAllServices() {
        // 初始化配置
        ConfigManager.initializeConfigs();
        ServerConfigManager.initializeServerConfigs();
        
        // 初始化缓存系统
        DualCacheManager.initialize();
        
        // 初始化兼容性适配器
        CompatibilityManager.initializeCompatibilityAdapters();
        
        // 执行兼容性检查
        CompatibilityChecker.performCompatibilityCheck();
    }
}
