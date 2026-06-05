package org.yanbwe.raritycore.util;

import org.yanbwe.raritycore.RarityCore;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 重复模式星星显示策略
 * 根据稀有度等级重复显示指定字符
 * 内部使用 ConcurrentHashMap 缓存不同 rarity 对应的字符串，避免每帧重复构建
 */
public class RepeatStarStrategy implements StarDisplayStrategy {

    private final String character;

    /** 按稀有度等级缓存星星字符串，避免热路径上重复 StringBuilder 循环 */
    private final ConcurrentHashMap<Integer, String> displayCache = new ConcurrentHashMap<>();

    public RepeatStarStrategy(String character) {
        this.character = character != null && !character.isEmpty() ? character : "⭐";
        RarityCore.LOGGER.debug("初始化重复模式策略,使用字符: {}", this.character);
    }

    @Override
    public String getDisplayString(int rarity) {
        // 边界处理:负数和0都按1处理
        int effectiveRarity = Math.max(1, rarity);

        // 从缓存获取，缓存未命中时才构建
        return displayCache.computeIfAbsent(effectiveRarity, r -> {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < r; i++) {
                result.append(character);
            }
            return result.toString();
        });
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