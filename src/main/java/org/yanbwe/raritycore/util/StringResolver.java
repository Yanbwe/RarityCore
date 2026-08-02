package org.yanbwe.raritycore.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * RarityStyle.json 工具提示内容字符串解析器。
 *
 * <p>解析规则：
 * <ol>
 *   <li>整串匹配 {@code $(key)} 形式 → 视为翻译键，返回 {@link Component#translatable(String)}</li>
 *   <li>否则为字面量，替换以下占位符：
 *     <ul>
 *       <li>{@code @{level}} → 稀有度等级名称（翻译后）</li>
 *       <li>{@code @{star}} → 星星字符串</li>
 *     </ul>
 *   </li>
 * </ol>
 */
public final class StringResolver {

    private StringResolver() {}

    /**
     * 解析工具提示内容模板字符串。
     *
     * @param template    内容模板（如 "[@{level}] @{star}"）
     * @param levelName   当前等级的本地化名称组件
     * @param rarityLevel 稀有度等级（用于生成星星）
     * @return 解析后的 MutableComponent
     */
    public static MutableComponent resolve(String template, Component levelName, int rarityLevel) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }

        // 规则 1：整串匹配 $(key) → 翻译键
        if (template.startsWith("$(") && template.endsWith(")") && template.indexOf("$(", 1) < 0) {
            String key = template.substring(2, template.length() - 1);
            if (!key.isEmpty()) {
                return Component.translatable(key);
            }
        }

        // 规则 2：字面量替换
        String stars = ComponentBuilder.getStars(rarityLevel);
        String result = template;
        result = result.replace("@{star}", stars);

        // @{level} 需要转换为字符串后替换
        if (result.contains("@{level}")) {
            String levelStr = levelName.getString();
            result = result.replace("@{level}", levelStr);
        }

        return Component.literal(result);
    }

    /**
     * 解析 level.translationKey 或 level.fallback 字符串。
     * 先替换 {level} 占位符，再扫描字符串中内嵌的 $(key) 模式并替换为翻译文本。
     *
     * @param template 模板字符串
     * @param level    稀有度等级（用于 {level} 占位符替换）
     * @return 解析后的 MutableComponent
     */
    public static MutableComponent resolveTranslation(String template, int level) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }

        // 整串匹配 $(key) → 翻译键（无需拼接）
        if (template.startsWith("$(") && template.endsWith(")") && template.indexOf("$(", 1) < 0) {
            String key = template.substring(2, template.length() - 1);
            if (!key.isEmpty()) {
                return Component.translatable(key);
            }
        }

        // 先替换 {level} 占位符
        String result = template.replace("{level}", String.valueOf(level));

        // 扫描内嵌的 $(key) 模式并逐个替换为翻译文本
        return resolveEmbeddedTranslations(result);
    }

    /**
     * 扫描字符串中的 $(key) 模式，将每个翻译键替换为翻译结果。
     * 非 $(key) 段保持为字面量。
     */
    private static MutableComponent resolveEmbeddedTranslations(String text) {
        MutableComponent component = Component.empty();
        int pos = 0;
        while (pos < text.length()) {
            int start = text.indexOf("$(", pos);
            if (start == -1) {
                // 剩余部分全部为字面量
                component.append(Component.literal(text.substring(pos)));
                break;
            }
            // $( 前面的字面量
            if (start > pos) {
                component.append(Component.literal(text.substring(pos, start)));
            }
            // 查找匹配的 )
            int end = text.indexOf(")", start + 2);
            if (end == -1) {
                // 未闭合的 $( 视为字面量
                component.append(Component.literal(text.substring(start)));
                break;
            }
            String key = text.substring(start + 2, end);
            if (!key.isEmpty()) {
                component.append(Component.translatable(key));
            }
            pos = end + 1;
        }
        return component;
    }
}
