package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DataComponent 路径解析器
 * 完全基于 DataComponent API，不依赖 NBT CompoundTag 作为主数据结构.
 * 支持与旧 ItemDataPathResolver 相同的路径格式，向后兼容旧配置文件.
 */
public class DataComponentPathResolver {

    /** "minecraft:custom_data" 组件注册键 */
    private static final String CUSTOM_DATA_KEY = "minecraft:custom_data";

    /** 旧式 NBT 键名 → 新式 DataComponent 注册键映射 */
    private static final Map<String, String> LEGACY_KEY_MAPPING = Map.ofEntries(
        Map.entry("Enchantments", "minecraft:enchantments"),
        Map.entry("StoredEnchantments", "minecraft:stored_enchantments"),
        Map.entry("display", "minecraft:custom_name"),
        Map.entry("Damage", "minecraft:damage"),
        Map.entry("RepairCost", "minecraft:repair_cost"),
        Map.entry("AttributeModifiers", "minecraft:attribute_modifiers"),
        Map.entry("CustomPotionEffects", "minecraft:potion_contents"),
        Map.entry("Potion", "minecraft:potion_contents"),
        Map.entry("HideFlags", "minecraft:hide_tooltip")
    );

    /**
     * 解析路径，返回对应值.
     *
     * @param components 物品的 DataComponentMap
     * @param itemStack  物品堆 (用于 id/count 等特殊路径)
     * @param path       路径字符串
     * @return 解析到的值, null 表示未找到
     */
    @Nullable
    public static Object resolve(DataComponentMap components, ItemStack itemStack, String path) {
        if (path == null || path.isEmpty() || components == null) {
            return null;
        }

        try {
            // 通配符路径
            if (path.contains("[*]")) {
                List<Object> results = resolveWildcard(components, itemStack, path);
                return results.isEmpty() ? null : results.get(0);
            }

            return resolvePath(components, itemStack, path);
        } catch (Exception e) {
            RarityCore.LOGGER.debug("DataComponentPathResolver: 解析路径 '{}' 失败: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 解析通配符路径，返回所有匹配值.
     */
    public static List<Object> resolveWildcard(DataComponentMap components, ItemStack itemStack, String wildcardPath) {
        if (wildcardPath == null || wildcardPath.isEmpty() || components == null) {
            return Collections.emptyList();
        }

        try {
            String translated = translateLegacyPath(wildcardPath);

            // 分割路径: 先按 "." 分，处理 [*]
            List<String> segments = splitPath(translated != null ? translated : wildcardPath);
            if (segments.isEmpty()) return Collections.emptyList();

            String first = segments.get(0);
            List<String> rest = segments.subList(1, segments.size());

            // 特殊路径: tag.xxx → custom_data 组件
            if (wildcardPath.startsWith("tag.") || first.equals(CUSTOM_DATA_KEY)) {
                return resolveCustomDataWildcard(components, first, rest, wildcardPath);
            }

            // 尝试作为 DataComponentType 解析
            DataComponentType<?> type = lookupComponentType(first);
            if (type != null) {
                Object value = components.get(type);
                if (value != null) {
                    return resolveValueWildcard(value, rest, wildcardPath);
                }
            }

            return Collections.emptyList();
        } catch (Exception e) {
            RarityCore.LOGGER.debug("DataComponentPathResolver: 通配符路径 '{}' 解析失败: {}", wildcardPath, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 检查路径是否包含通配符.
     */
    public static boolean containsWildcard(String path) {
        return path != null && path.contains("[*]");
    }

    // ─── 内部实现 ───────────────────────────────────────────

    @Nullable
    private static Object resolvePath(DataComponentMap components, ItemStack itemStack, String path) {
        // 特殊路径
        if (path.equals("id") || path.equals("count") || path.equals("Count")) {
            return resolveSpecialPath(itemStack, path);
        }

        // tag.xxx → custom_data 组件
        if (path.startsWith("tag.")) {
            String rest = path.substring(4);
            DataComponentType<?> customDataType =
                BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(CUSTOM_DATA_KEY));
            CustomData customData = null;
            if (customDataType != null) {
                Object raw = components.get(customDataType);
                if (raw instanceof CustomData cd) customData = cd;
            }
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                // 使用 NBT 路径解析处理 custom_data 内部的嵌套结构
                Tag nbtResult = ItemDataPathResolver.resolve(tag, rest);
                return nbtResult != null ? ItemDataPathResolver.getTagValue(nbtResult) : null;
            }
            return null;
        }

        // 翻译旧式路径
        String translated = translateLegacyPath(path);

        // 分割路径段
        List<String> segments = splitPath(translated);
        if (segments.isEmpty()) return null;

        String firstSegment = segments.get(0);

        // 尝试作为 DataComponentType 查找
        DataComponentType<?> type = lookupComponentType(firstSegment);
        if (type != null) {
            Object value = components.get(type);
            if (value == null) return null;

            // 遍历剩余路径段
            List<String> rest = segments.subList(1, segments.size());
            return traverseValue(value, rest);
        }

        return null;
    }

    @Nullable
    private static Object resolveSpecialPath(ItemStack itemStack, String path) {
        if (itemStack == null) return null;
        if (path.equals("id")) {
            return BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
        }
        if (path.equals("count") || path.equals("Count")) {
            return itemStack.getCount();
        }
        return null;
    }

    /**
     * 遍历类型化组件值的嵌套结构.
     */
    @Nullable
    private static Object traverseValue(Object value, List<String> segments) {
        if (segments.isEmpty()) {
            return value;
        }

        String segment = segments.get(0);
        List<String> rest = segments.subList(1, segments.size());

        // CustomData → 委托给 NBT 路径解析
        if (value instanceof CustomData customData) {
            CompoundTag tag = customData.copyTag();
            String subPath = String.join(".", segments);
            Tag nbtResult = ItemDataPathResolver.resolve(tag, subPath);
            return nbtResult != null ? ItemDataPathResolver.getTagValue(nbtResult) : null;
        }

        // ItemEnchantments → map 访问
        if (value instanceof ItemEnchantments enchantments) {
            // 按附魔注册键查找等级
            for (var entry : enchantments.entrySet()) {
                String enchantKey = entry.getKey().unwrapKey()
                    .map(key -> key.identifier().toString())
                    .orElse("");
                if (enchantKey.equals(segment)) {
                    return traverseValue(entry.getIntValue(), rest);
                }
            }
            return null;
        }

        // 数值/字符串 → 叶子节点，无更多嵌套
        if (rest.isEmpty()) {
            return value;
        }

        // 使用反射尝试字段访问
        try {
            var field = value.getClass().getDeclaredField(segment);
            field.setAccessible(true);
            Object fieldValue = field.get(value);
            return traverseValue(fieldValue, rest);
        } catch (Exception e) {
            // 无法访问，返回 null
            return null;
        }
    }

    // ─── 通配符处理 ─────────────────────────────────────────

    private static List<Object> resolveCustomDataWildcard(
        DataComponentMap components, String first, List<String> rest, String originalPath
    ) {
        DataComponentType<?> customDataType =
            BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(CUSTOM_DATA_KEY));
        CustomData customData = null;
        if (customDataType != null) {
            Object raw = components.get(customDataType);
            if (raw instanceof CustomData cd) customData = cd;
        }
        if (customData == null) return Collections.emptyList();

        CompoundTag tag = customData.copyTag();
        String subPath;
        if (first.equals(CUSTOM_DATA_KEY)) {
            subPath = String.join(".", rest);
        } else {
            subPath = originalPath.substring(4); // 去掉 "tag."
        }

        List<Tag> nbtResults = ItemDataPathResolver.resolveWildcardPath(tag, subPath);
        List<Object> results = new ArrayList<>();
        for (Tag t : nbtResults) {
            Object val = ItemDataPathResolver.getTagValue(t);
            if (val != null) results.add(val);
        }
        return results;
    }

    private static List<Object> resolveValueWildcard(Object value, List<String> rest, String originalPath) {
        if (value instanceof CustomData customData) {
            CompoundTag tag = customData.copyTag();
            String subPath = String.join(".", rest);
            List<Tag> nbtResults = ItemDataPathResolver.resolveWildcardPath(tag, subPath);
            List<Object> results = new ArrayList<>();
            for (Tag t : nbtResults) {
                Object val = ItemDataPathResolver.getTagValue(t);
                if (val != null) results.add(val);
            }
            return results;
        }

        if (value instanceof ItemEnchantments enchantments) {
            List<Object> results = new ArrayList<>();
            for (var entry : enchantments.entrySet()) {
                if (rest.isEmpty()) {
                    results.add(entry.getIntValue());
                } else {
                    String segment = rest.get(0);
                    List<String> deeper = rest.subList(1, rest.size());
                    String enchantKey = entry.getKey().unwrapKey()
                        .map(key -> key.identifier().toString())
                        .orElse("");
                    if (enchantKey.equals(segment) || segment.equals("level") || segment.equals("lvl")) {
                        Object v = traverseValue(entry.getIntValue(), deeper);
                        if (v != null) results.add(v);
                    }
                }
            }
            return results;
        }

        return Collections.emptyList();
    }

    // ─── 工具方法 ───────────────────────────────────────────

    /**
     * 按注册键查找 DataComponentType.
     */
    @Nullable
    private static DataComponentType<?> lookupComponentType(String key) {
        try {
            Identifier loc = Identifier.parse(key);
            return BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(loc);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将旧式路径转换为新格式.
     */
    @Nullable
    private static String translateLegacyPath(String path) {
        // tag.xxx → 已经在 resolvePath 中单独处理

        // Count → count
        if (path.equals("Count")) return "count";

        // 已是新格式
        if (path.equals("id") || path.equals("count")) return path;

        // 转换旧式键名
        String result = translateLegacyKeyInFirstSegment(path);

        return result;
    }

    private static String translateLegacyKeyInFirstSegment(String path) {
        int endIndex = path.length();
        int dotIndex = path.indexOf('.');
        int bracketIndex = path.indexOf('[');
        if (dotIndex >= 0) endIndex = Math.min(endIndex, dotIndex);
        if (bracketIndex >= 0) endIndex = Math.min(endIndex, bracketIndex);

        String firstSegment = path.substring(0, endIndex);
        String rest = path.substring(endIndex);

        String mapped = LEGACY_KEY_MAPPING.get(firstSegment);
        if (mapped != null) {
            return mapped + rest;
        }

        return path;
    }

    /**
     * 将路径按 "." 分割 (保留 [*] 等方括号语法).
     */
    private static List<String> splitPath(String path) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inBracket = false;

        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '[') inBracket = true;
            if (c == ']') inBracket = false;

            if (c == '.' && !inBracket) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts;
    }
}
