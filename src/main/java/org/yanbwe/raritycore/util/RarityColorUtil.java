package org.yanbwe.raritycore.util;

import net.minecraft.ChatFormatting;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nonnull;

/**
 * 稀有度颜色工具类
 * 统一管理不同稀有度对应的颜色值
 */
public class RarityColorUtil {
    
    /** 默认 RGB 颜色（灰色），用于无效输入的回退 */
    public static final int DEFAULT_RGB_COLOR = 0xCCCCCC;

    /**
     * 根据稀有度等级获取对应的颜色格式
     * @param rarity 稀有度等级 (1-7)
     * @return ChatFormatting颜色,永不为null
     */
    @Nonnull
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
     * 根据稀有度等级获取对应的 RGB 颜色值（不含 alpha 通道）
     * @param rarity 稀有度等级 (1-7)
     * @return RGB 颜色值 (0xRRGGBB)
     */
    public static int getRarityRgbColor(int rarity) {
        return switch (rarity) {
            case 1 -> 0xCCCCCC; // 普通 - 亮灰色
            case 2 -> 0x55FF55; // 稀有 - 亮绿色
            case 3 -> 0x55FFFF; // 罕见 - 亮青色
            case 4 -> 0xFF55FF; // 史诗 - 亮紫色
            case 5 -> 0xFFCC00; // 传说 - 亮金色
            case 6 -> 0xFF6666; // 神话 - 亮红色
            case 7 -> 0xFF3333; // 唯一 - 亮深红
            default -> DEFAULT_RGB_COLOR;
        };
    }

    /**
     * 解析十六进制颜色字符串为 RGB int
     * @param hex 十六进制颜色字符串，如 "#RRGGBB" 或 "RRGGBB"
     * @return RGB int 值 (0xRRGGBB)，解析失败返回默认灰色
     */
    public static int parseRgbColor(String hex) {
        if (hex == null || hex.isEmpty()) {
            RarityCore.LOGGER.warn("parseRgbColor received null/empty input, falling back to default gray");
            return DEFAULT_RGB_COLOR;
        }
        try {
            String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
            if (cleaned.length() != 6) {
                RarityCore.LOGGER.warn("parseRgbColor received invalid hex length: '{}', falling back to default gray", hex);
                return DEFAULT_RGB_COLOR;
            }
            return Integer.parseInt(cleaned, 16);
        } catch (NumberFormatException e) {
            RarityCore.LOGGER.warn("parseRgbColor failed to parse '{}', falling back to default gray: {}", hex, e.getMessage());
            return DEFAULT_RGB_COLOR;
        }
    }

    /**
     * 将 RGB int 值格式化为 #RRGGBB 字符串
     * @param rgb RGB int 值
     * @return 如 "#A0A0A0"
     */
    public static String formatRgbColor(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /**
     * @deprecated 使用 {@link #getRarityRgbColor(int)} 替代，返回纯 RGB 不含 alpha
     */
    @Deprecated
    public static int getRarityArgbColor(int rarity) {
        return 0xFF000000 | getRarityRgbColor(rarity);
    }
}