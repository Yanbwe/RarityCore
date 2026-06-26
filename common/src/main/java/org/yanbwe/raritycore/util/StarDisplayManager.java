package org.yanbwe.raritycore.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yanbwe.raritycore.config.StarDisplayConfigManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 星星显示管理器
 * 负责管理星星显示策略和配置
 */
public class StarDisplayManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(StarDisplayManager.class);
    
    // 使用静态内部类实现线程安全的单例模式
    private static class SingletonHolder {
        private static final StarDisplayManager INSTANCE = new StarDisplayManager();
    }
    
    private StarDisplayStrategy currentStrategy;
    private final Map<String, StarDisplayStrategy> strategyCache;
    
    private StarDisplayManager() {
        this.strategyCache = new ConcurrentHashMap<>();
        initializeStrategies();
        updateStrategyFromConfig();
    }
    
    /**
     * 获取单例实例
     */
    public static StarDisplayManager getInstance() {
        return SingletonHolder.INSTANCE;
    }
    
    /**
     * 初始化所有策略
     */
    private void initializeStrategies() {
        // 预创建常用的策略实例
        strategyCache.put("repeat_default", new RepeatStarStrategy("⭐"));
        LOGGER.debug("星星显示策略初始化完成");
    }
    
    /**
     * 根据配置更新当前策略
     */
    public void updateStrategyFromConfig() {
        if (!StarDisplayConfigManager.isEnableStarDisplay()) {
            currentStrategy = null;
            return;
        }
        
        String mode = StarDisplayConfigManager.getStarMode();
        StarMode starMode = parseStarMode(mode);
        
        switch (starMode) {
            case REPEAT:
                currentStrategy = createRepeatStrategy();
                break;
            case CUSTOM:
                currentStrategy = createCustomStrategy();
                break;
            default:
                currentStrategy = createRepeatStrategy(); // 默认使用重复模式
                break;
        }
        
        LOGGER.info("星星显示策略已更新为: {}", currentStrategy != null ? currentStrategy.getName() : "disabled");
    }
    
    /**
     * 解析星星显示模式
     */
    private StarMode parseStarMode(String modeString) {
        if (modeString == null || modeString.isEmpty()) {
            return StarMode.REPEAT; // 默认模式
        }
        
        try {
            return StarMode.valueOf(modeString.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("无效的星星显示模式: {}, 使用默认模式", modeString);
            return StarMode.REPEAT;
        }
    }
    
    /**
     * 创建重复模式策略
     */
    private StarDisplayStrategy createRepeatStrategy() {
        String character = StarDisplayConfigManager.getRepeatCharacter();
        if (character == null || character.isEmpty()) {
            character = RarityConstants.DEFAULT_REPEAT_CHARACTER;
        }
        return new RepeatStarStrategy(character);
    }
    
    /**
     * 创建自定义模式策略
     */
    private StarDisplayStrategy createCustomStrategy() {
        Map<Integer, String> customStrings = StarDisplayConfigManager.getCustomStarStrings();
        return new CustomStarStrategy(customStrings);
    }
    
    /**
     * 获取星星显示字符串
     * @param rarity 稀有度等级
     * @return 显示的字符串,如果不应显示则返回空字符串
     */
    public String getStarDisplayString(int rarity) {
        if (!StarDisplayConfigManager.isEnableStarDisplay() || currentStrategy == null) {
            return "";
        }
        
        try {
            return currentStrategy.getDisplayString(rarity);
        } catch (Exception e) {
            LOGGER.error("获取星星显示字符串时发生错误: 稀有度{}", rarity, e);
            return ""; // 安全回退
        }
    }
    
    /**
     * 检查指定稀有度是否应该显示星星
     * @param rarity 稀有度等级
     * @return 是否应该显示
     */
    public boolean shouldDisplayStars(int rarity) {
        if (!StarDisplayConfigManager.isEnableStarDisplay() || currentStrategy == null) {
            return false;
        }
        
        String displayString = getStarDisplayString(rarity);
        return displayString != null && !displayString.isEmpty();
    }
    
    /**
     * 获取当前策略名称
     */
    public String getCurrentStrategyName() {
        return currentStrategy != null ? currentStrategy.getName() : "Disabled";
    }
    
    /**
     * 重新加载配置并更新策略
     */
    public void reloadConfiguration() {
        updateStrategyFromConfig();
        LOGGER.info("星星显示配置已重新加载");
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        strategyCache.clear();
        currentStrategy = null;
        LOGGER.debug("星星显示管理器已清理");
    }
}
