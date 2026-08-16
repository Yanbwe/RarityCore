package org.yanbwe.raritycore.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * RarityStyle.json 工具提示内容字符串解析器（V14）。
 *
 * <p>解析规则：
 * <ol>
 *   <li>整串匹配 {@code $(key)} 形式 → 视为翻译键，返回 {@link Component#translatable(String)} 的字符串形式。</li>
 *   <li>否则为字面量模板，替换以下占位符：
 *     <ul>
 *       <li>{@code @{level}} → 稀有度等级数值字符串</li>
 *       <li>{@code @{star}} → 星星字符串</li>
 *     </ul>
 *   </li>
 * </ol>
 */
public final class StringResolver {

    private StringResolver() {}

    /**
     * 解析工具提示内容模板字符串（纯字符串结果）。
     *
     * @param template  内容模板（如 "[@{level}] @{star}"）
     * @param level     稀有度等级
     * @param starText  星星字符串
     * @return 解析后的字符串，永不为 null
     */
    public static String resolve(String template, int level, String starText) {
        return resolve(template, String.valueOf(level), starText);
    }

    /**
     * 解析工具提示内容模板字符串（纯字符串结果）。
     *
     * @param template  内容模板（如 "[@{level}] @{star}"）
     * @param levelName 稀有度等级显示名称（如 "普通"、"Common" 或 "1"）
     * @param starText  星星字符串
     * @return 解析后的字符串，永不为 null
     */
    public static String resolve(String template, String levelName, String starText) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        // 规则 1：整串匹配 $(key) → 翻译键字符串
        if (template.startsWith("$(") && template.endsWith(")") && template.indexOf("$(", 1) < 0) {
            String key = template.substring(2, template.length() - 1);
            if (!key.isEmpty()) {
                return Component.translatable(key).getString();
            }
        }
        // 规则 2：字面量替换占位符
        String result = template;
        result = result.replace("@{level}", levelName == null ? "" : levelName);
        result = result.replace("@{star}", starText == null ? "" : starText);
        return result;
    }

    /**
     * 解析工具提示内容模板为 {@link MutableComponent}。
     *
     * <p>规则：
     * <ul>
     *   <li>整串 {@code $(key)}：返回 {@link Component#translatable(String)}；{@code colored=true} 时整体应用 {@code levelColor}。</li>
     *   <li>非整串翻译键：按 {@code @{level}} / {@code @{star}} 分段构建；
     *       {@code colored=true} 时整体应用 {@code levelColor}；
     *       {@code colored=false} 时 {@code @{level}} 段用 {@code levelColor}、{@code @{star}} 段用 {@code starColor}、静态文本保持默认颜色。</li>
     * </ul>
     *
     * @param template   内容模板
     * @param level      稀有度等级
     * @param starText   星星字符串
     * @param colored    是否整体着色
     * @param levelColor 等级颜色（可为 null）
     * @param starColor  星星颜色（可为 null）
     * @return 解析后的可变组件，永不为 null
     */
    public static MutableComponent resolveComponent(String template, int level, String starText, boolean colored,
                                                    TextColor levelColor, TextColor starColor) {
        return resolveComponent(template, String.valueOf(level), starText, colored, levelColor, starColor);
    }

    /**
     * 解析工具提示内容模板为 {@link MutableComponent}。
     *
     * @param template   内容模板
     * @param levelName  稀有度等级显示名称（如 "普通"、"Common" 或 "1"）
     * @param starText   星星字符串
     * @param colored    是否整体着色
     * @param levelColor 等级颜色（可为 null）
     * @param starColor  星星颜色（可为 null）
     * @return 解析后的可变组件，永不为 null
     */
    public static MutableComponent resolveComponent(String template, String levelName, String starText, boolean colored,
                                                    TextColor levelColor, TextColor starColor) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }
        String levelTextSafe = levelName == null ? "" : levelName;
        String starTextSafe = starText == null ? "" : starText;

        // 规则 1：整串 $(key) → 翻译键
        if (template.startsWith("$(") && template.endsWith(")") && template.indexOf("$(", 1) < 0) {
            String key = template.substring(2, template.length() - 1);
            if (!key.isEmpty()) {
                MutableComponent comp = Component.translatable(key);
                if (colored && levelColor != null) {
                    comp = comp.withStyle(Style.EMPTY.withColor(levelColor));
                }
                return comp;
            }
        }

        // 规则 2：字面量分段替换
        MutableComponent result = Component.empty();
        int pos = 0;
        while (pos < template.length()) {
            int levelIdx = template.indexOf("@{level}", pos);
            int starIdx = template.indexOf("@{star}", pos);
            int nextIdx = -1;
            boolean isLevel = false;
            if (levelIdx == -1 && starIdx == -1) {
                // 剩余全部为静态文本
                appendStatic(result, template.substring(pos), colored, levelColor);
                break;
            } else if (levelIdx == -1) {
                nextIdx = starIdx;
                isLevel = false;
            } else if (starIdx == -1) {
                nextIdx = levelIdx;
                isLevel = true;
            } else {
                if (levelIdx < starIdx) {
                    nextIdx = levelIdx;
                    isLevel = true;
                } else {
                    nextIdx = starIdx;
                    isLevel = false;
                }
            }

            // 占位符前的静态文本
            if (nextIdx > pos) {
                appendStatic(result, template.substring(pos, nextIdx), colored, levelColor);
            }

            // 占位符本身
            if (isLevel) {
                MutableComponent seg = Component.literal(levelTextSafe);
                // @{level} 段在 colored 与 !colored 下都使用 levelColor
                if (levelColor != null) {
                    seg = seg.withStyle(Style.EMPTY.withColor(levelColor));
                }
                result.append(seg);
                pos = nextIdx + "@{level}".length();
            } else {
                MutableComponent seg = Component.literal(starTextSafe);
                if (colored && levelColor != null) {
                    seg = seg.withStyle(Style.EMPTY.withColor(levelColor));
                } else if (!colored && starColor != null) {
                    seg = seg.withStyle(Style.EMPTY.withColor(starColor));
                }
                result.append(seg);
                pos = nextIdx + "@{star}".length();
            }
        }
        return result;
    }

    private static void appendStatic(MutableComponent target, String text, boolean colored, TextColor levelColor) {
        if (text.isEmpty()) {
            return;
        }
        MutableComponent seg = Component.literal(text);
        if (colored && levelColor != null) {
            seg = seg.withStyle(Style.EMPTY.withColor(levelColor));
        }
        target.append(seg);
    }
}
