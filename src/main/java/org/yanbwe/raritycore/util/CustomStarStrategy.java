package org.yanbwe.raritycore.util;

import org.yanbwe.raritycore.RarityCore;

import java.util.Collections;
import java.util.Map;

/**
 * 自定义模式星星显示策略
 * 每个稀有度等级使用独立配置的字符串
 */
public class CustomStarStrategy implements StarDisplayStrategy {
    
    private final Map<Integer, String> customStrings;
    
    public CustomStarStrategy(Map<Integer, String> customStrings) {
        this.customStrings = customStrings != null ? customStrings : Collections.emptyMap();
        RarityCore.LOGGER.debug("初始化自定义模式策略,配置了 {} 个稀有度字符串", this.customStrings.size());
    }
    
    @Override
    public String getDisplayString(int rarity) {
        // 边界处理:负数和0都按1处理
        int effectiveRarity = Math.max(1, rarity);
        
        // 查找配置的字符串
        String result = customStrings.get(effectiveRarity);
        
        // 如果未配置则不显示(返回空字符串)
        if (result == null) {
            return "";
        }
        
        return result;
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