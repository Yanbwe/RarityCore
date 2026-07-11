package org.yanbwe.raritycore.util;

import net.minecraft.ChatFormatting;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 稀有度颜色工具类
 * 统一管理不同稀有度对应的颜色值
 * 支持外部注入自定义颜色（从 RarityClientConfig），1-7 级有内置默认值
 */
public class RarityColorUtil {

    /** 默认 RGB 颜色（灰色），用于无效输入的回退 */
    public static final int DEFAULT_RGB_COLOR = 0xCCCCCC;

    /** 外部注入的自定义颜色（来自 RarityStyle.json） */
    private static final Map<Integer, Integer> CUSTOM_COLORS = new ConcurrentHashMap<>();

    /**
     * 注入自定义颜色（由 RarityStyleConfigManager 调用）
     */
    public static void setCustomColor(int rarity, int rgb) {
        CUSTOM_COLORS.put(rarity, rgb);
    }

    /**
     * 批量注入自定义颜色
     */
    public static void setCustomColors(Map<Integer, Integer> colors) {
        CUSTOM_COLORS.clear();
        CUSTOM_COLORS.putAll(colors);
    }

    /**
     * 根据稀有度等级获取对应的颜色格式
     * @param rarity 稀有度等级
     * @return ChatFormatting颜色,永不为null
     */
    @Nonnull
    public static ChatFormatting getRarityChatColor(int rarity) {
        Integer custom = CUSTOM_COLORS.get(rarity);
        if (custom != null) {
            return rgbToChatFormatting(custom);
        }
        return switch (rarity) {
            case 1 -> ChatFormatting.WHITE;
            case 2 -> ChatFormatting.GREEN;
            case 3 -> ChatFormatting.DARK_AQUA;
            case 4 -> ChatFormatting.LIGHT_PURPLE;
            case 5 -> ChatFormatting.GOLD;
            case 6 -> ChatFormatting.RED;
            case 7 -> ChatFormatting.DARK_RED;
            default -> ChatFormatting.WHITE;
        };
    }

    /**
     * 根据稀有度等级获取对应的 RGB 颜色值（不含 alpha 通道）
     * @param rarity 稀有度等级
     * @return RGB 颜色值 (0xRRGGBB)
     */
    public static int getRarityRgbColor(int rarity) {
        Integer custom = CUSTOM_COLORS.get(rarity);
        if (custom != null) {
            return custom;
        }
        return switch (rarity) {
            case 1 -> 0xCCCCCC;
            case 2 -> 0x55FF55;
            case 3 -> 0x55FFFF;
            case 4 -> 0xFF55FF;
            case 5 -> 0xFFCC00;
            case 6 -> 0xFF6666;
            case 7 -> 0xFF3333;
            default -> DEFAULT_RGB_COLOR;
        };
    }

    private static ChatFormatting rgbToChatFormatting(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        ChatFormatting best = ChatFormatting.WHITE;
        int bestDist = Integer.MAX_VALUE;
        for (ChatFormatting fmt : ChatFormatting.values()) {
            if (fmt.getColor() == null) continue;
            int cr = (fmt.getColor() >> 16) & 0xFF;
            int cg = (fmt.getColor() >> 8) & 0xFF;
            int cb = fmt.getColor() & 0xFF;
            int dist = (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb);
            if (dist < bestDist) {
                bestDist = dist;
                best = fmt;
            }
        }
        return best;
    }

    public static int parseRgbColor(String hex) {
        if (hex == null || hex.isEmpty()) {
            return DEFAULT_RGB_COLOR;
        }
        try {
            String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
            if (cleaned.length() != 6) return DEFAULT_RGB_COLOR;
            return Integer.parseInt(cleaned, 16);
        } catch (NumberFormatException e) {
            return DEFAULT_RGB_COLOR;
        }
    }

    public static String formatRgbColor(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    @Deprecated
    public static int getRarityArgbColor(int rarity) {
        return 0xFF000000 | getRarityRgbColor(rarity);
    }
}
