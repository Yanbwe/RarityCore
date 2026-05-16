package org.yanbwe.raritycore.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import org.yanbwe.raritycore.config.RarityClientConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 稀有度颜色工具类
 * 统一管理不同稀有度对应的颜色值
 */
public class RarityColorUtil {
    
    /**
     * 根据稀有度等级获取对应的颜色格式
     * @param rarity 稀有度等级 (1-7)
     * @return ChatFormatting颜色,永不为null
     * @deprecated Use {@link #getRarityTextColor(int)} or {@link #getRarityRgbColor(int)} instead.
     */
    @Deprecated
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
     * 根据稀有度等级获取对应的ARGB颜色值
     * @param rarity 稀有度等级 (1-7)
     * @return ARGB颜色值
     * @deprecated Use {@link #getRarityRgbColor(int)} instead.
     */
    @Deprecated
    public static int getRarityArgbColor(int rarity) {
        return switch (rarity) {
            case 1 -> // 普通 - 灰色(更明显)
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

    /** Default fallback RGB color (white). */
    private static final int DEFAULT_RGB = 0xFFFFFF;

    /**
     * 根据稀有度等级获取对应的 RGB 颜色值 (0xRRGGBB packed int, 无 alpha 通道)。
     * RGB 值对应原 ChatFormatting 颜色：
     * <ul>
     *   <li>1 (普通) — 白色 {@code #FFFFFF}</li>
     *   <li>2 (稀有) — 绿色 {@code #55FF55}</li>
     *   <li>3 (罕见) — 深青色 {@code #00AAAA}</li>
     *   <li>4 (史诗) — 浅紫色 {@code #FF55FF}</li>
     *   <li>5 (传说) — 金色 {@code #FFAA00}</li>
     *   <li>6 (神话) — 红色 {@code #FF5555}</li>
     *   <li>7 (唯一) — 深红色 {@code #AA0000}</li>
     * </ul>
     *
     * @param rarity 稀有度等级 (1-7)
     * @return RGB 颜色值 (0xRRGGBB), 默认返回 0xFFFFFF (白色)
     */
    public static int getRarityRgbColor(int rarity) {
        return switch (rarity) {
            case 1 -> 0xFFFFFF;  // WHITE
            case 2 -> 0x55FF55;  // GREEN
            case 3 -> 0x00AAAA;  // DARK_AQUA
            case 4 -> 0xFF55FF;  // LIGHT_PURPLE
            case 5 -> 0xFFAA00;  // GOLD
            case 6 -> 0xFF5555;  // RED
            case 7 -> 0xAA0000;  // DARK_RED
            default -> DEFAULT_RGB;
        };
    }

    /**
     * 根据稀有度等级获取 {@link TextColor}，优先使用 {@link RarityClientConfig} 中的自定义颜色。
     * <p>
     * 查询逻辑：
     * <ol>
     *   <li>先通过 {@link RarityClientConfig#getTextColor(int)} 查询自定义颜色</li>
     *   <li>若未配置自定义颜色，则回退到内置 RGB 映射（{@link #getRarityRgbColor(int)}）</li>
     * </ol>
     *
     * @param rarity 稀有度等级 (1-7)
     * @return TextColor，永不为 null
     */
    @Nonnull
    public static TextColor getRarityTextColor(int rarity) {
        // 优先使用 RarityClientConfig 自定义颜色
        TextColor custom = RarityClientConfig.getTextColor(rarity);
        if (custom != null) {
            return custom;
        }
        // 回退到内置 RGB 映射
        return TextColor.fromRgb(getRarityRgbColor(rarity));
    }

    /**
     * 解析十六进制颜色字符串为 RGB int 值 (0xRRGGBB)。
     * 支持 {@code "#RRGGBB"} 和 {@code "RRGGBB"} 两种格式。
     *
     * @param hex 十六进制颜色字符串，如 {@code "#FF5555"} 或 {@code "FF5555"}；可为 null
     * @return RGB int (0xRRGGBB)；解析失败或输入为 null/空时返回 {@code 0xFFFFFF} (白色)
     */
    public static int parseHexColor(@Nullable String hex) {
        if (hex == null || hex.isEmpty()) {
            return DEFAULT_RGB;
        }
        String stripped = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Integer.parseInt(stripped, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return DEFAULT_RGB;
        }
    }
}