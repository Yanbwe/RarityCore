package org.yanbwe.raritycore.service;

import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.DualCacheManager;
import org.yanbwe.raritycore.calc.AutoRarityCalculator;
import org.yanbwe.raritycore.calc.AutoRarityConfigManager;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.compat.CompatibilityChecker;
import org.yanbwe.raritycore.compat.CompatibilityManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.data.RarityDataLoader;
import org.yanbwe.raritycore.itemdatamatching.ItemDataConfigLoader;
import org.yanbwe.raritycore.network.DelayedSyncManager;
import org.yanbwe.raritycore.network.ItemDataSyncManager;
import org.yanbwe.raritycore.network.SyncBatchManager;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务工厂
 * 管理所有服务的访问,提供依赖注入功能
 */
public class ServiceFactory {
    
    // 使用静态内部类实现线程安全的单例模式
    private static class SingletonHolder {
        private static final ServiceFactory INSTANCE = new ServiceFactory();
    }
    
    private ServiceFactory() {
        initializeServiceRegistry();
    }
    
    /**
     * 获取服务工厂实例
     */
    public static ServiceFactory getInstance() {
        return SingletonHolder.INSTANCE;
    }
    
    // 服务注册信息
    private static class ServiceInfo<T> {
        private final Class<T> serviceClass;
        private final java.util.function.Supplier<T> supplier;
        private final int priority; // 初始化优先级,数字越小优先级越高
        
        public ServiceInfo(Class<T> serviceClass, java.util.function.Supplier<T> supplier, int priority) {
            this.serviceClass = serviceClass;
            this.supplier = supplier;
            this.priority = priority;
        }
        
        public Class<T> getServiceClass() {
            return serviceClass;
        }
        
        public java.util.function.Supplier<T> getSupplier() {
            return supplier;
        }
        
        public int getPriority() {
            return priority;
        }
    }
    
    // 服务注册列表
    private final List<ServiceInfo<?>> serviceRegistry = new ArrayList<>();
    
    // 服务实例缓存
    private final Map<Class<?>, Object> serviceCache = new ConcurrentHashMap<>();
    
    /**
     * 初始化服务注册
     */
    private void initializeServiceRegistry() {
        // 注册服务,按照依赖关系设置优先级
        registerService(ConfigManager.class, ConfigManager::new, 10);
        registerService(ConfigReloadService.class, ConfigReloadService::new, 12);
        registerService(ServerConfigManager.class, ServerConfigManager::new, 15);
        registerService(DualCacheManager.class, DualCacheManager::new, 20);
        registerService(CompatibilityManager.class, CompatibilityManager::new, 25);
        registerService(CompatibilityChecker.class, CompatibilityChecker::new, 30);
        registerService(SyncManager.class, SyncManager::new, 35);
        registerService(ItemDataSyncManager.class, ItemDataSyncManager::new, 40);
        registerService(DelayedSyncManager.class, DelayedSyncManager::new, 45);
        registerService(SyncBatchManager.class, SyncBatchManager::new, 50);
        registerService(AutoRarityConfigManager.class, AutoRarityConfigManager::new, 55);
        registerService(AutoRarityCalculator.class, AutoRarityCalculator::new, 60);
        registerService(RarityRegistry.class, RarityRegistry::new, 65);
        registerService(RarityCoreCommands.class, RarityCoreCommands::new, 70);
        registerService(SchedulerService.class, () -> new SchedulerService(this), 75);
    }
    
    /**
     * 注册服务
     * @param serviceClass 服务类
     * @param supplier 服务实例创建器
     * @param priority 初始化优先级
     * @param <T> 服务类型
     */
    private <T> void registerService(Class<T> serviceClass, java.util.function.Supplier<T> supplier, int priority) {
        serviceRegistry.add(new ServiceInfo<>(serviceClass, supplier, priority));
    }
    
    /**
     * 初始化所有服务
     * 按照优先级顺序初始化各个服务组件
     */
    public void initializeAllServices() {
        // 按照优先级排序
        serviceRegistry.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
        
        // 初始化配置
        ConfigManager.initializeConfigs();
        ClientConfigManager.initialize();
        ServerConfigManager.initializeServerConfigs();
        
        // 初始化缓存系统
        DualCacheManager.initialize();
        
        // 初始化兼容性适配器
        CompatibilityManager.initializeCompatibilityAdapters();
        
        // 执行兼容性检查
        CompatibilityChecker.performCompatibilityCheck();

        // 执行一次完整的配置重载（仅在工作流开始时调用一次，确保所有本地配置和数据包配置一致加载）
        org.yanbwe.raritycore.service.ConfigReloadService.reloadOnStartup();
        
        RarityCore.LOGGER.info("All services initialized successfully");
    }
    
    /**
     * 获取服务实例
     * @param serviceClass 服务类
     * @param <T> 服务类型
     * @return 服务实例
     */
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass) {
        if (serviceClass == RarityDataLoader.class) {
            return (T) RarityDataLoader.INSTANCE;
        }
        
        return (T) serviceCache.computeIfAbsent(serviceClass, clazz -> {
            for (ServiceInfo<?> info : serviceRegistry) {
                if (info.getServiceClass() == clazz) {
                    return info.getSupplier().get();
                }
            }
            throw new IllegalArgumentException("Service not registered: " + clazz.getName());
        });
    }
    
    /**
     * 创建物品数据配置加载器
     * @return 物品数据配置加载器实例
     */
    public ItemDataConfigLoader createItemDataConfigLoader() {
        return new ItemDataConfigLoader();
    }
    
    // 保持向后兼容的方法
    
    /**
     * 获取配置管理器
     * @return 配置管理器实例
     */
    public ConfigManager getConfigManager() {
        return getService(ConfigManager.class);
    }
    
    /**
     * 获取服务器配置管理器
     * @return 服务器配置管理器实例
     */
    public ServerConfigManager getServerConfigManager() {
        return getService(ServerConfigManager.class);
    }
    
    /**
     * 获取双缓存管理器
     * @return 双缓存管理器实例
     */
    public DualCacheManager getDualCacheManager() {
        return getService(DualCacheManager.class);
    }
    
    /**
     * 获取稀有度数据加载器
     * @return 稀有度数据加载器实例
     */
    public RarityDataLoader getRarityDataLoader() {
        return RarityDataLoader.INSTANCE;
    }
    
    /**
     * 获取同步管理器
     * @return 同步管理器实例
     */
    public SyncManager getSyncManager() {
        return getService(SyncManager.class);
    }
    
    /**
     * 获取物品数据同步管理器
     * @return 物品数据同步管理器实例
     */
    public ItemDataSyncManager getItemDataSyncManager() {
        return getService(ItemDataSyncManager.class);
    }
    
    /**
     * 获取延迟同步管理器
     * @return 延迟同步管理器实例
     */
    public DelayedSyncManager getDelayedSyncManager() {
        return getService(DelayedSyncManager.class);
    }
    
    /**
     * 获取同步批处理管理器
     * @return 同步批处理管理器实例
     */
    public SyncBatchManager getSyncBatchManager() {
        return getService(SyncBatchManager.class);
    }
    
    /**
     * 获取自动稀有度计算器
     * @return 自动稀有度计算器实例
     */
    public AutoRarityCalculator getAutoRarityCalculator() {
        return getService(AutoRarityCalculator.class);
    }
    
    /**
     * 获取自动稀有度配置管理器
     * @return 自动稀有度配置管理器实例
     */
    public AutoRarityConfigManager getAutoRarityConfigManager() {
        return getService(AutoRarityConfigManager.class);
    }
    
    /**
     * 获取稀有度核心命令
     * @return 稀有度核心命令实例
     */
    public RarityCoreCommands getRarityCoreCommands() {
        return getService(RarityCoreCommands.class);
    }
    
    /**
     * 获取兼容性管理器
     * @return 兼容性管理器实例
     */
    public CompatibilityManager getCompatibilityManager() {
        return getService(CompatibilityManager.class);
    }
    
    /**
     * 获取兼容性检查器
     * @return 兼容性检查器实例
     */
    public CompatibilityChecker getCompatibilityChecker() {
        return getService(CompatibilityChecker.class);
    }
    
    /**
     * 获取稀有度注册表
     * @return 稀有度注册表实例
     */
    public RarityRegistry getRarityRegistry() {
        return getService(RarityRegistry.class);
    }
    
    public ConfigReloadService getConfigReloadService() {
        return getService(ConfigReloadService.class);
    }
    
    /**
     * 获取调度器服务
     * @return 调度器服务实例
     */
    public SchedulerService getSchedulerService() {
        return getService(SchedulerService.class);
    }
}
