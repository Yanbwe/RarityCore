package org.yanbwe.raritycore.util;

import org.yanbwe.raritycore.RarityCore;

/**
 * 重复模式星星显示策略
 * 根据稀有度等级重复显示指定字符
 */
public class RepeatStarStrategy implements StarDisplayStrategy {
    
    private final String character;
    
    public RepeatStarStrategy(String character) {
        this.character = character != null && !character.isEmpty() ? character : "⭐";
        RarityCore.LOGGER.debug("初始化重复模式策略，使用字符: {}", this.character);
    }
    
    @Override
    public String getDisplayString(int rarity) {
        // 边界处理：负数和0都按1处理
        int effectiveRarity = Math.max(1, rarity);
        
        // 重复字符
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < effectiveRarity; i++) {
            result.append(character);
        }
        
        return result.toString();
    }
    
    @Override
    public String getName() {
        return "Repeat";
    }
    
    /**
     * 获取基础字符
     * @return 基础字符
     */
    public String getCharacter() {
        return character;
    }
}