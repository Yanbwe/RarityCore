package org.yanbwe.raritycore.util;

import org.yanbwe.raritycore.RarityCore;

/**
 * 重复模式星星显示策略
 * 根据稀有度等级重复显示指定字符
 * 预计算并缓存常用 rarity 1-8 的结果字符串，避免热路径上的 StringBuilder 分配
 */
public class RepeatStarStrategy implements StarDisplayStrategy {
    
    private static final int MAX_CACHED_RARITY = 8;
    
    private final String character;
    private final String[] cachedResults;
    
    public RepeatStarStrategy(String character) {
        this.character = character != null && !character.isEmpty() ? character : "⭐";
        
        // 预计算 rarity 0-8 的字符串，0 返回空串
        cachedResults = new String[MAX_CACHED_RARITY + 1];
        cachedResults[0] = "";
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= MAX_CACHED_RARITY; i++) {
            sb.append(this.character);
            cachedResults[i] = sb.toString();
        }
        
        RarityCore.LOGGER.debug("初始化重复模式策略，使用字符: {}", this.character);
    }
    
    @Override
    public String getDisplayString(int rarity) {
        // 边界处理：负数和0都按1处理（与原实现一致）
        int effectiveRarity = Math.max(1, rarity);
        
        // 缓存命中：O(1) 返回预计算字符串
        if (effectiveRarity <= MAX_CACHED_RARITY) {
            return cachedResults[effectiveRarity];
        }
        
        // 超出缓存范围才动态构建（极少见的情况）
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