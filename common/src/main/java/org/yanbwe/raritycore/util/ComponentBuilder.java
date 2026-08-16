package org.yanbwe.raritycore.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import javax.annotation.Nonnull;

/**
 * 组件构建器工具类
 * Minecraft组件创建和组装工具
 */
public class ComponentBuilder {
    
    
    /**
     * 获取预构建的星星字符串
     * @param rarity 星星数量/稀有度等级
     * @return 星星字符串,永不为null
     */
    @Nonnull
    public static String getStars(int rarity) {
        return getStars(rarity, org.yanbwe.raritycore.config.RarityStyleConfigManager.getStarConfig(rarity));
    }

    /**
     * 按 {@link org.yanbwe.raritycore.config.RarityStyleConfigManager.StarStyle} 构建星星字符串。
     * <p>
     * <ul>
     *   <li>{@code starStyle} 为 null，或其 mode 为 null/空时返回空字符串；</li>
     *   <li>{@code custom} 非 null 且非空时直接返回 custom（不要求 mode 为 custom）；</li>
     *   <li>否则按 repeatChar 重复 {@code max(1, rarity)} 次（默认字符为 {@code ★}）。</li>
     * </ul>
     *
     * @param rarity    稀有度等级
     * @param starStyle 星星样式配置
     * @return 星星字符串,永不为null
     */
    @Nonnull
    public static String getStars(int rarity, org.yanbwe.raritycore.config.RarityStyleConfigManager.StarStyle starStyle) {
        if (starStyle == null) {
            return "";
        }

        String mode = starStyle.mode();
        if (mode == null || mode.isEmpty()) {
            return "";
        }

        String custom = starStyle.custom();
        if (custom != null && !custom.isEmpty()) {
            return custom;
        }

        String repeatChar = starStyle.repeatChar();
        if (repeatChar == null || repeatChar.isEmpty()) {
            repeatChar = "★";
        }

        int effectiveRarity = Math.max(1, rarity);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < effectiveRarity; i++) {
            result.append(repeatChar);
        }
        return result.toString();
    }

    /**
     * 构建统一稀有度工具提示组件（V14）。
     * <p>
     * 读取 {@link org.yanbwe.raritycore.config.RarityStyleConfigManager#getTooltip(int)}：
     * show 为 false 时返回空组件；否则使用 {@link StringResolver#resolveComponent} 构建。
     *
     * @param rarity 稀有度等级
     * @return 构建好的组件,永不为null
     */
    @Nonnull
    public static MutableComponent buildRarityTooltipComponent(int rarity) {
        org.yanbwe.raritycore.config.RarityStyleConfigManager.TooltipStyle tooltip =
                org.yanbwe.raritycore.config.RarityStyleConfigManager.getTooltip(rarity);
        if (tooltip == null || !tooltip.show()) {
            return Component.empty();
        }

        String stars = getStars(rarity);
        TextColor color = org.yanbwe.raritycore.config.RarityStyleConfigManager.getTextColor(rarity);
        return StringResolver.resolveComponent(tooltip.content(), rarity, stars, tooltip.colored(), color, color);
    }

    /**
     * 构建稀有度组件(高性能版本)
     * @param rarity 稀有度等级
     * @param color 颜色格式
     * @return 构建好的组件,永不为null
     * @deprecated Use {@link #buildRarityComponent(int, TextColor)} instead.
     */
    @Deprecated
    @Nonnull
    public static MutableComponent buildRarityComponent(int rarity, ChatFormatting color) {
        if (rarity <= 0) return Component.empty();
        String stars = getStars(rarity);
        if (org.yanbwe.raritycore.config.RarityStyleConfigManager.isTooltipColorEnabled()) {
            return Component.literal(" " + stars).withStyle(color);
        } else {
            return Component.literal(" " + stars);
        }
    }

    /**
     * 构建稀有度组件(TextColor 版本)
     * @param rarity 稀有度等级
     * @param color TextColor 颜色
     * @return 构建好的组件,永不为null
     */
    @Nonnull
    public static MutableComponent buildRarityComponent(int rarity, TextColor color) {
        if (rarity <= 0) return Component.empty();
        String stars = getStars(rarity);
        if (org.yanbwe.raritycore.config.RarityStyleConfigManager.isTooltipColorEnabled()) {
            return Component.literal(" " + stars).withStyle(Style.EMPTY.withColor(color));
        } else {
            return Component.literal(" " + stars);
        }
    }
    
    /**
     * 高效地将组件插入到工具提示中
     * @param tooltip 工具提示列表
     * @param component 要插入的组件
     */
    public static void insertIntoTooltip(java.util.List<Component> tooltip, MutableComponent component) {
        if (tooltip.isEmpty()) {
            tooltip.add(component);
        } else {
            tooltip.add(1, component); // 插入到第二行
        }
    }
}