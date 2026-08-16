package org.yanbwe.raritycore.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * 稀有度颜色工具类
 * 统一管理不同稀有度对应的颜色值
 */
public class RarityColorUtil {
    
    /** 外部注入的自定义颜色（来自 RarityStyleConfigManager） */
    private static final java.util.Map<Integer, Integer> CUSTOM_COLORS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 注入自定义颜色 */
    public static void setCustomColor(int rarity, int rgb) {
        CUSTOM_COLORS.put(rarity, rgb);
    }

    /** 批量注入自定义颜色 */
    public static void setCustomColors(java.util.Map<Integer, Integer> colors) {
        CUSTOM_COLORS.clear();
        if (colors != null) {
            CUSTOM_COLORS.putAll(colors);
        }
    }

    /**
     * 根据稀有度等级获取对应的颜色格式
     * @param rarity 稀有度等级 (1-7)
     * @return ChatFormatting颜色,永不为null
     * @deprecated Use {@link #getRarityTextColor(int)} or {@link #getRarityRgbColor(int)} instead.
     */
    @Deprecated
    @Nonnull
    public static ChatFormatting getRarityChatColor(int rarity) {
        Integer custom = CUSTOM_COLORS.get(rarity);
        if (custom != null) {
            return rgbToChatFormatting(custom);
        }
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

    private static ChatFormatting rgbToChatFormatting(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        ChatFormatting best = ChatFormatting.WHITE;
        int bestDist = Integer.MAX_VALUE;
        // 使用标准16色格式代码的固定RGB值（跨MC版本稳定，26.2+ 的 ChatFormatting 不再提供 getColor()）
        for (ChatFormatting fmt : ChatFormatting.values()) {
            Integer color = CHAT_FORMATTING_RGB.get(fmt);
            if (color == null) continue;
            int cr = (color >> 16) & 0xFF;
            int cg = (color >> 8) & 0xFF;
            int cb = color & 0xFF;
            int dist = (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb);
            if (dist < bestDist) {
                bestDist = dist;
                best = fmt;
            }
        }
        return best;
    }

    /** 标准 16 色格式代码对应的 RGB 颜色值（Minecraft 官方色板，跨版本稳定） */
    private static final Map<ChatFormatting, Integer> CHAT_FORMATTING_RGB = Map.ofEntries(
        Map.entry(ChatFormatting.BLACK, 0x000000),
        Map.entry(ChatFormatting.DARK_BLUE, 0x0000AA),
        Map.entry(ChatFormatting.DARK_GREEN, 0x00AA00),
        Map.entry(ChatFormatting.DARK_AQUA, 0x00AAAA),
        Map.entry(ChatFormatting.DARK_RED, 0xAA0000),
        Map.entry(ChatFormatting.DARK_PURPLE, 0xAA00AA),
        Map.entry(ChatFormatting.GOLD, 0xFFAA00),
        Map.entry(ChatFormatting.GRAY, 0xAAAAAA),
        Map.entry(ChatFormatting.DARK_GRAY, 0x555555),
        Map.entry(ChatFormatting.BLUE, 0x5555FF),
        Map.entry(ChatFormatting.GREEN, 0x55FF55),
        Map.entry(ChatFormatting.AQUA, 0x55FFFF),
        Map.entry(ChatFormatting.RED, 0xFF5555),
        Map.entry(ChatFormatting.LIGHT_PURPLE, 0xFF55FF),
        Map.entry(ChatFormatting.YELLOW, 0xFFFF55),
        Map.entry(ChatFormatting.WHITE, 0xFFFFFF)
    );
    
    /**
     * 根据稀有度等级获取对应的ARGB颜色值
     * @param rarity 稀有度等级 (1-7)
     * @return ARGB颜色值
     * @deprecated Use {@link #getRarityRgbColor(int)} instead.
     */
    @Deprecated
    public static int getRarityArgbColor(int rarity) {
        return switch (rarity) {
            case 1 -> // 普通 - 灰色
                    0xFFCCCCCC;
            case 2 -> // 稀有 - 绿色
                    0xFF55FF55;
            case 3 -> // 罕见 - 青蓝色
                    0xFF00AAAA;
            case 4 -> // 史诗 - 浅紫色
                    0xFFC870FF;
            case 5 -> // 传说 - 金色
                    0xFFFFAA00;
            case 6 -> // 神话 - 红色
                    0xFFFF5555;
            case 7 -> // 唯一 - 红色
                    0xFFFF3333;
            default -> 0xFFFF3333; // 默认红色（回退到 7 级色）
        };
    }

    /** 解析十六进制颜色失败或输入为空时使用的回退色（白色）。 */
    private static final int DEFAULT_RGB = 0xFFFFFF;

    /**
     * 根据稀有度等级获取对应的 RGB 颜色值 (0xRRGGBB packed int, 无 alpha 通道)。
     * 内置回退 RGB 颜色值：
     * <ul>
     *   <li>1 (普通) — 灰色 {@code #CCCCCC}</li>
     *   <li>2 (稀有) — 绿色 {@code #55FF55}</li>
     *   <li>3 (罕见) — 青蓝色 {@code #00AAAA}</li>
     *   <li>4 (史诗) — 浅紫色 {@code #C870FF}</li>
     *   <li>5 (传说) — 金色 {@code #FFAA00}</li>
     *   <li>6 (神话) — 红色 {@code #FF5555}</li>
     *   <li>7 (唯一) — 红色 {@code #FF3333}</li>
     * </ul>
     *
     * @param rarity 稀有度等级
     * @return RGB 颜色值 (0xRRGGBB), 未匹配时回退到 7 级色 {@code 0xFF3333}
     */
    public static int getRarityRgbColor(int rarity) {
        Integer custom = CUSTOM_COLORS.get(rarity);
        if (custom != null) {
            return custom;
        }
        return switch (rarity) {
            case 1 -> 0xCCCCCC;  // 普通 - 灰色
            case 2 -> 0x55FF55;  // 稀有 - 绿色
            case 3 -> 0x00AAAA;  // 罕见 - 青蓝色
            case 4 -> 0xC870FF;  // 史诗 - 浅紫色
            case 5 -> 0xFFAA00;  // 传说 - 金色
            case 6 -> 0xFF5555;  // 神话 - 红色
            case 7 -> 0xFF3333;  // 唯一 - 红色
            default -> getRarityRgbColor(RarityConstants.MAX_RARITY); // 回退到 7 级色
        };
    }

    /**
     * 根据稀有度等级获取 {@link TextColor}，优先使用 {@link RarityStyleConfigManager} 中的样式颜色。
     * <p>
     * 查询逻辑：
     * <ol>
     *   <li>先通过 {@link RarityStyleConfigManager#getTextColor(int)} 查询 V14 样式颜色</li>
     *   <li>若未配置自定义颜色，则回退到内置 RGB 映射（{@link #getRarityRgbColor(int)}）</li>
     * </ol>
     *
     * @param rarity 稀有度等级 (1-7)
     * @return TextColor，永不为 null
     */
    @Nonnull
    public static TextColor getRarityTextColor(int rarity) {
        // 优先使用 RarityStyleConfigManager 样式颜色
        TextColor styleColor = RarityStyleConfigManager.getTextColor(rarity);
        if (styleColor != null) {
            return styleColor;
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