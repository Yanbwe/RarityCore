package org.yanbwe.raritycore.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import javax.annotation.Nonnull;

/**
 * 组件构建器工具类
 * Minecraft组件创建和组装工具
 */
public class ComponentBuilder {
    
    
    /**
     * 获取预构建的星星字符串
     * @param count 星星数量
     * @return 星星字符串,永不为null
     */
    @Nonnull
    public static String getStars(int count) {
        // 使用新的星星显示管理器
        try {
            org.yanbwe.raritycore.util.StarDisplayManager manager = 
                org.yanbwe.raritycore.util.StarDisplayManager.getInstance();
            return manager.getStarDisplayString(count);
        } catch (Exception e) {
            // 回退到旧的实现方式
            if (count <= 0) {
                return "";
            }
            
            // 简单直接构建星星字符串
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                sb.append("⭐");
            }
            return sb.toString();
        }
    }
    
    /**
     * 构建稀有度组件(高性能版本)
     * @param rarity 稀有度等级
     * @param color 颜色格式
     * @return 构建好的组件,永不为null
     */
    @Nonnull
    public static MutableComponent buildRarityComponent(int rarity, ChatFormatting color) {
        if (rarity <= 0) return Component.empty();
        String stars = getStars(rarity);
        return Component.literal(" ").append(stars).withStyle(color);
    }
    
    /**
     * 构建稀有度组件(支持变色控制)
     * @param rarity 稀有度等级
     * @param color 颜色格式
     * @param enableColor 是否启用变色
     * @return 构建好的组件,永不为null
     */
    @Nonnull
    public static MutableComponent buildRarityComponent(int rarity, ChatFormatting color, boolean enableColor) {
        if (rarity <= 0) return Component.empty();
        String stars = getStars(rarity);
        if (enableColor) {
            return Component.literal(" ").append(stars).withStyle(color);
        } else {
            return Component.literal(" ").append(stars);
        }
    }

    /**
     * 构建稀有度组件(RGB 颜色版本)
     */
    @Nonnull
    public static MutableComponent buildRarityComponent(int rarity, Style colorStyle, boolean enableColor) {
        if (rarity <= 0) return Component.empty();
        String stars = getStars(rarity);
        if (enableColor) {
            return Component.literal(" ").append(stars).withStyle(colorStyle);
        } else {
            return Component.literal(" ").append(stars);
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