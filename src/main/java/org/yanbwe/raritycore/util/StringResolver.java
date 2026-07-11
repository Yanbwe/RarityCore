package org.yanbwe.raritycore.util;

import net.minecraft.network.chat.Component;

/**
 * 字符串解析工具
 * 处理翻译键 $(key) 与占位符 @{level}/@{star} 的解析
 */
public final class StringResolver {

    private StringResolver() {}

    /**
     * 判断整串是否为翻译键 $(key)
     */
    public static boolean isTranslationKey(String s) {
        if (s == null || s.length() < 4) {
            return false;
        }
        return s.startsWith("$(") && s.endsWith(")");
    }

    /**
     * 提取翻译键内容（去掉 $() 包裹）
     */
    public static String extractKey(String s) {
        return s.substring(2, s.length() - 1);
    }

    /**
     * 将翻译键渲染为组件（语言文件缺失时返回键本身）
     */
    public static Component translate(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * 将翻译键渲染为字符串
     */
    public static String translateAsString(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /**
     * 检测翻译键是否在语言文件中缺失（未翻译时 Component 返回原键文本）
     */
    public static boolean isKeyMissing(String key) {
        return translateAsString(key).equals(key);
    }

    /**
     * 将字面量中的 $(key) 片段替换为对应翻译文本，返回字符串
     */
    public static String resolveEmbeddedKeys(String literal) {
        if (literal == null || literal.isEmpty()) {
            return literal;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < literal.length()) {
            if (literal.charAt(i) == '$' && i + 1 < literal.length() && literal.charAt(i + 1) == '(') {
                int end = literal.indexOf(')', i);
                if (end > i + 1) {
                    String key = literal.substring(i + 2, end);
                    sb.append(translateAsString(key));
                    i = end + 1;
                    continue;
                }
            }
            sb.append(literal.charAt(i));
            i++;
        }
        return sb.toString();
    }

    /**
     * 整串为翻译键时渲染为组件，否则按字面量处理嵌入键
     */
    public static Component resolveWholeOrLiteral(String s) {
        if (isTranslationKey(s)) {
            return translate(extractKey(s));
        }
        return Component.literal(resolveEmbeddedKeys(s));
    }
}
