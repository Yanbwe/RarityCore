package org.yanbwe.raritycore.util;

import org.yanbwe.raritycore.RarityCore;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义模式星星显示策略
 * 每个稀有度等级使用独立配置的字符串
 * 内部缓存 lookup 结果，避免每帧热路径重复 HashMap.get()
 */
public class CustomStarStrategy implements StarDisplayStrategy {

    private final Map<Integer, String> customStrings;
    private final ConcurrentHashMap<Integer, String> resolvedCache = new ConcurrentHashMap<>();

    public CustomStarStrategy(Map<Integer, String> customStrings) {
        this.customStrings = customStrings != null ? customStrings : Collections.emptyMap();
        RarityCore.LOGGER.debug("初始化自定义模式策略,配置了 {} 个稀有度字符串", this.customStrings.size());
    }

    @Override
    public String getDisplayString(int rarity) {
        // 边界处理:负数和0都按1处理
        int effectiveRarity = Math.max(1, rarity);

        // 使用缓存，避免每帧对同一 rarity 重复查询 HashMap
        return resolvedCache.computeIfAbsent(effectiveRarity, r -> {
            String configured = customStrings.get(r);
            return configured != null ? configured : "";
        });
    }

    @Override
    public String getName() {
        return "Custom";
    }

    /**
     * 获取所有配置的稀有度字符串映射
     * @return 稀有度到字符串的映射
     */
    public Map<Integer, String> getCustomStrings() {
        return Collections.unmodifiableMap(customStrings);
    }

    /**
     * 检查指定稀有度是否有配置
     * @param rarity 稀有度等级
     * @return 是否有配置
     */
    public boolean hasConfiguration(int rarity) {
        return customStrings.containsKey(Math.max(1, rarity));
    }
}