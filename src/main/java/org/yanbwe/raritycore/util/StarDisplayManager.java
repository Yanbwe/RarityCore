package org.yanbwe.raritycore.util;

import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager.TooltipStarConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 星星显示管理器
 * 负责管理星星显示策略和配置
 */
public class StarDisplayManager {
    
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
        RarityCore.LOGGER.debug("星星显示策略初始化完成");
    }
    
    /**
     * 根据 RarityStyleConfigManager 逐级配置更新当前策略（使用等级1作为全局默认）
     */
    public void updateStrategyFromConfig() {
        RarityStyleConfigManager mgr = RarityStyleConfigManager.getInstance();
        if (!mgr.isTooltipEnabled()) {
            currentStrategy = null;
            return;
        }
        
        TooltipStarConfig star = mgr.resolveTooltip(RarityConstants.MIN_RARITY).star;
        String mode = star.mode;
        StarMode starMode = parseStarMode(mode);
        
        switch (starMode) {
            case REPEAT:
                currentStrategy = createRepeatStrategy(RarityConstants.MIN_RARITY);
                break;
            case CUSTOM:
                currentStrategy = createCustomStrategy(RarityConstants.MIN_RARITY);
                break;
            default:
                currentStrategy = createRepeatStrategy(RarityConstants.MIN_RARITY);
                break;
        }
        
        RarityCore.LOGGER.info("星星显示策略已更新为: {}", currentStrategy != null ? currentStrategy.getName() : "disabled");
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
            RarityCore.LOGGER.warn("无效的星星显示模式: {}, 使用默认模式", modeString);
            return StarMode.REPEAT;
        }
    }
    
    /**
     * 创建重复模式策略（从 RarityStyleConfigManager 逐级配置读取字符）
     */
    private StarDisplayStrategy createRepeatStrategy(int level) {
        TooltipStarConfig star = RarityStyleConfigManager.getInstance().resolveTooltip(level).star;
        String character = star.repeatChar;
        if (character == null || character.isEmpty()) {
            character = RarityConstants.DEFAULT_REPEAT_CHARACTER;
        }
        return new RepeatStarStrategy(character);
    }
    
    /**
     * 创建自定义模式策略（从 RarityStyleConfigManager 逐级配置读取自定义字符串）
     */
    private StarDisplayStrategy createCustomStrategy(int level) {
        TooltipStarConfig star = RarityStyleConfigManager.getInstance().resolveTooltip(level).star;
        String custom = star.custom;
        if (custom != null && !custom.isEmpty()) {
            Map<Integer, String> customStrings = new java.util.HashMap<>();
            customStrings.put(level, custom);
            return new CustomStarStrategy(customStrings);
        }
        // 回退到重复模式
        return createRepeatStrategy(level);
    }
    
    /**
     * 获取星星显示字符串（逐级从 RarityStyleConfigManager 读取配置）
     * @param rarity 稀有度等级
     * @return 显示的字符串,如果不应显示则返回空字符串
     */
    public String getStarDisplayString(int rarity) {
        RarityStyleConfigManager mgr = RarityStyleConfigManager.getInstance();
        if (!mgr.isTooltipEnabled()) {
            return "";
        }
        
        try {
            // 逐级获取配置并动态构建策略
            TooltipStarConfig star = mgr.resolveTooltip(rarity).star;
            StarDisplayStrategy strategy = buildStrategyForLevel(rarity, star);
            return strategy.getDisplayString(rarity);
        } catch (Exception e) {
            RarityCore.LOGGER.error("获取星星显示字符串时发生错误: 稀有度{}", rarity, e);
            return "";
        }
    }
    
    /**
     * 检查指定稀有度是否应该显示星星
     * @param rarity 稀有度等级
     * @return 是否应该显示
     */
    public boolean shouldDisplayStars(int rarity) {
        RarityStyleConfigManager mgr = RarityStyleConfigManager.getInstance();
        if (!mgr.isTooltipEnabled()) {
            return false;
        }
        String displayString = getStarDisplayString(rarity);
        return displayString != null && !displayString.isEmpty();
    }
    
    /**
     * 根据逐级配置动态构建策略
     */
    private StarDisplayStrategy buildStrategyForLevel(int level, TooltipStarConfig star) {
        String cacheKey = level + "_" + star.mode + "_" + star.repeatChar + "_" + star.custom;
        return strategyCache.computeIfAbsent(cacheKey, k -> {
            String mode = star.mode;
            if ("custom".equals(mode) && star.custom != null && !star.custom.isEmpty()) {
                Map<Integer, String> customStrings = new java.util.HashMap<>();
                customStrings.put(level, star.custom);
                return new CustomStarStrategy(customStrings);
            } else {
                String character = star.repeatChar;
                if (character == null || character.isEmpty()) {
                    character = RarityConstants.DEFAULT_REPEAT_CHARACTER;
                }
                return new RepeatStarStrategy(character);
            }
        });
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
        RarityCore.LOGGER.info("星星显示配置已重新加载");
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        strategyCache.clear();
        currentStrategy = null;
        RarityCore.LOGGER.debug("星星显示管理器已清理");
    }
}