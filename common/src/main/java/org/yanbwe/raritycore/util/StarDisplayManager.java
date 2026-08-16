package org.yanbwe.raritycore.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 星星显示管理器
 * 负责根据 RarityStyleConfigManager 提供的逐级 StarStyle 选择星星显示策略。
 */
public class StarDisplayManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(StarDisplayManager.class);
    
    // 使用静态内部类实现线程安全的单例模式
    private static class SingletonHolder {
        private static final StarDisplayManager INSTANCE = new StarDisplayManager();
    }
    
    private StarDisplayStrategy currentStrategy;
    private final ConcurrentHashMap<String, StarDisplayStrategy> strategyCache;
    
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
     * 初始化缓存中的常用策略
     */
    private void initializeStrategies() {
        strategyCache.put("repeat:" + RarityConstants.DEFAULT_REPEAT_CHARACTER,
                new RepeatStarStrategy(RarityConstants.DEFAULT_REPEAT_CHARACTER));
        LOGGER.debug("星星显示策略初始化完成");
    }
    
    /**
     * 根据配置更新当前策略。
     * <p>由于 V14 的星星配置按稀有度继承，实际单次显示会再通过
     * {@link #selectStrategy(int)} 按稀有度选择；此方法用于刷新默认/当前策略与禁用状态。</p>
     */
    public void updateStrategyFromConfig() {
        if (!RarityStyleConfigManager.isStarDisplayEnabled()) {
            currentStrategy = null;
            LOGGER.info("星星显示策略已更新为: {}", "disabled");
            return;
        }
        currentStrategy = selectStrategy(RarityConstants.MIN_RARITY);
        LOGGER.info("星星显示策略已更新为: {}", currentStrategy != null ? currentStrategy.getName() : "disabled");
    }
    
    /**
     * 根据指定稀有度的 {@link RarityStyleConfigManager.StarStyle} 选择策略。
     * <ul>
     *   <li>{@code custom()} 非 null 且非空 → {@link CustomStarStrategy}（单条目映射）</li>
     *   <li>否则 → {@link RepeatStarStrategy}</li>
     * </ul>
     */
    private StarDisplayStrategy selectStrategy(int rarity) {
        RarityStyleConfigManager.StarSegmentConfig starStyle = RarityStyleConfigManager.getStarConfig(rarity);
        if (starStyle == null) {
            return repeatStrategy(RarityConstants.DEFAULT_REPEAT_CHARACTER);
        }
        
        String custom = starStyle.custom;
        if (custom != null && !custom.isEmpty()) {
            String cacheKey = "custom:" + rarity + ":" + custom;
            return strategyCache.computeIfAbsent(cacheKey,
                    k -> new CustomStarStrategy(Collections.singletonMap(rarity, custom)));
        }

        String repeatChar = starStyle.repeatChar;
        if (repeatChar == null || repeatChar.isEmpty()) {
            repeatChar = RarityConstants.DEFAULT_REPEAT_CHARACTER;
        }
        return repeatStrategy(repeatChar);
    }
    
    private StarDisplayStrategy repeatStrategy(String character) {
        return strategyCache.computeIfAbsent("repeat:" + character,
                k -> new RepeatStarStrategy(character));
    }
    
    /**
     * 获取星星显示字符串
     * @param rarity 稀有度等级
     * @return 显示的字符串,如果不应显示则返回空字符串
     */
    public String getStarDisplayString(int rarity) {
        if (!RarityStyleConfigManager.isStarDisplayEnabled()) {
            return "";
        }

        StarDisplayStrategy strategy = selectStrategy(rarity);
        if (strategy == null) {
            return "";
        }
        
        try {
            return strategy.getDisplayString(rarity);
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
        if (!RarityStyleConfigManager.isStarDisplayEnabled()) {
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
