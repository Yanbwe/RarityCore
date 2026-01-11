package org.yanbwe.raritycore.util;

import net.minecraft.ChatFormatting;

/**
 * 稀有度颜色工具类
 * 统一管理不同稀有度对应的颜色值
 */
public class RarityColorUtil {
    
    /**
     * 根据稀有度等级获取对应的颜色格式
     * @param rarity 稀有度等级 (1-7)
     * @return ChatFormatting颜色
     */
    public static ChatFormatting getRarityChatColor(int rarity) {
        switch (rarity) {
            case 1: // 普通 - 白色
                return ChatFormatting.WHITE;
            case 2: // 稀有 - 绿色
                return ChatFormatting.GREEN;
            case 3: // 罕见 - 深青色
                return ChatFormatting.DARK_AQUA;
            case 4: // 史诗 - 浅紫色
                return ChatFormatting.LIGHT_PURPLE;
            case 5: // 传说 - 金色
                return ChatFormatting.GOLD;
            case 6: // 神话 - 红色
                return ChatFormatting.RED;
            case 7: // 唯一 - 深红色
                return ChatFormatting.DARK_RED;
            default:
                return ChatFormatting.WHITE; // 默认白色
        }
    }
    
    /**
     * 根据稀有度等级获取对应的ARGB颜色值
     * @param rarity 稀有度等级 (1-7)
     * @return ARGB颜色值
     */
    public static int getRarityArgbColor(int rarity) {
        return switch (rarity) {
            case 1 -> // 普通 - 灰色（更明显）
                    0xFFA0A0A0;
            case 2 -> // 稀有 - 绿色
                    0xFF00AA00;
            case 3 -> // 罕见 - 青蓝色
                    0xFF00AAAA;
            case 4 -> // 史诗 - 浅紫色
                    0xFFC870FF;
            case 5 -> // 传说 - 金色
                    0xFFFFAA00;
            case 6 -> // 神话 - 红色
                    0xFFFF5555;
            case 7 -> // 唯一 - 深红色
                    0xFFAA0000;
            default -> 0xFFA0A0A0; // 默认灰色
        };
    }
}