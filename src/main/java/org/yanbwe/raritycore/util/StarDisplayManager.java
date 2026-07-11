package org.yanbwe.raritycore.util;

import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 星星显示管理器
 * 负责星星显示策略和配置（数据源来自 RarityStyleConfigManager）
 */
public class StarDisplayManager {

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

    public static StarDisplayManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private void initializeStrategies() {
        strategyCache.put("repeat_default", new RepeatStarStrategy("⭐"));
        RarityCore.LOGGER.debug("星星显示策略初始化完成");
    }

    /**
     * 根据配置更新当前策略
     */
    public void updateStrategyFromConfig() {
        RarityStyleConfigManager.StarSegmentConfig star = RarityStyleConfigManager.getStarConfig(RarityConstants.MIN_RARITY);
        if (star == null || star.custom == null || star.custom.isEmpty()) {
            // 自定义模式但未配置字符串时回退到重复模式
            currentStrategy = createRepeatStrategy(star);
            return;
        }
        StarMode starMode = parseStarMode(star.mode);
        switch (starMode) {
            case REPEAT:
                currentStrategy = createRepeatStrategy(star);
                break;
            case CUSTOM:
                currentStrategy = createCustomStrategy(star);
                break;
            default:
                currentStrategy = createRepeatStrategy(star);
                break;
        }
        RarityCore.LOGGER.info("星星显示策略已更新为: {}", currentStrategy != null ? currentStrategy.getName() : "disabled");
    }

    private StarMode parseStarMode(String modeString) {
        if (modeString == null || modeString.isEmpty()) {
            return StarMode.REPEAT;
        }
        try {
            return StarMode.valueOf(modeString.toUpperCase());
        } catch (IllegalArgumentException e) {
            RarityCore.LOGGER.warn("无效的星星显示模式: {}, 使用默认模式", modeString);
            return StarMode.REPEAT;
        }
    }

    private StarDisplayStrategy createRepeatStrategy(RarityStyleConfigManager.StarSegmentConfig star) {
        String character = star != null && star.repeatChar != null && !star.repeatChar.isEmpty()
            ? star.repeatChar : RarityConstants.DEFAULT_REPEAT_CHARACTER;
        return new RepeatStarStrategy(character);
    }

    private StarDisplayStrategy createCustomStrategy(RarityStyleConfigManager.StarSegmentConfig star) {
        Map<Integer, String> customStrings = new ConcurrentHashMap<>();
        if (star != null && star.custom != null && !star.custom.isEmpty()) {
            // custom 为单串时按稀有度逐字符拆分（兼容旧 custom strings 数组语义）
            int len = star.custom.length();
            for (int i = 1; i <= len; i++) {
                customStrings.put(i, star.custom.substring(0, i));
            }
        }
        return new CustomStarStrategy(customStrings);
    }

    /**
     * 获取星星显示字符串
     * @param rarity 稀有度等级
     * @return 显示的字符串,如果不应显示则返回空字符串
     */
    public String getStarDisplayString(int rarity) {
        if (currentStrategy == null) {
            return "";
        }
        try {
            return currentStrategy.getDisplayString(rarity);
        } catch (Exception e) {
            RarityCore.LOGGER.error("获取星星显示字符串时发生错误: 稀有度{}", rarity, e);
            return "";
        }
    }

    public boolean shouldDisplayStars(int rarity) {
        if (currentStrategy == null) {
            return false;
        }
        String displayString = getStarDisplayString(rarity);
        return displayString != null && !displayString.isEmpty();
    }

    public String getCurrentStrategyName() {
        return currentStrategy != null ? currentStrategy.getName() : "Disabled";
    }

    public void reloadConfiguration() {
        updateStrategyFromConfig();
        RarityCore.LOGGER.info("星星显示配置已重新加载");
    }

    public void cleanup() {
        strategyCache.clear();
        currentStrategy = null;
        RarityCore.LOGGER.debug("星星显示管理器已清理");
    }
}
