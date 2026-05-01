package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 物品数据路径解析器
 * 负责解析和访问物品数据中的嵌套结构
 */
public class ItemDataPathResolver {
    
    // 路径分隔符模式
    private static final Pattern PATH_PATTERN = Pattern.compile(
        "([^.\\[\\]]+)" +           // 普通键名
        "|(?:\\[(\\d+)\\])" +       // 数组索引 [0], [1] 等
        "|(?:\\.([^\\.\\[\\]]+))"   // 点号分隔的键名
    );
    
    /**
     * 根据路径解析物品数据
     * 支持的路径格式:
     * - "Enchantments"                    // 简单键访问
     * - "Enchantments[0]"                 // 数组索引访问
     * - "Enchantments[0].id"              // 嵌套访问
     * - "display.Name"                    // 点号分隔的嵌套访问
     * - "Enchantments[*].id"              // 通配符访问
     * - "Enchantments[*].lvl"             // 通配符访问
     * - "tag.Yanbwe"                      // 根级tag路径访问
     * - "Count"                           // 根级Count字段访问
     * - "id"                              // 根级id字段访问
     * - "components.stored_enchantments"   // 组件路径访问
     * 
     * @param nbt 要解析的NBT标签
     * @param path 物品数据路径
     * @return 解析到的标签,如果路径无效则返回null
     */
    /** "minecraft:custom_data" 组件的注册键 */
    private static final String CUSTOM_DATA_KEY = "minecraft:custom_data";

    /**
     * 旧式 NBT 键名 → 新式 DataComponent 注册键映射
     * 用于向后兼容旧配置文件中的路径
     */
    private static final java.util.Map<String, String> LEGACY_KEY_MAPPING = java.util.Map.ofEntries(
        java.util.Map.entry("Enchantments", "minecraft:enchantments"),
        java.util.Map.entry("StoredEnchantments", "minecraft:stored_enchantments"),
        java.util.Map.entry("display", "minecraft:custom_name"),
        java.util.Map.entry("Damage", "minecraft:damage"),
        java.util.Map.entry("RepairCost", "minecraft:repair_cost"),
        java.util.Map.entry("AttributeModifiers", "minecraft:attribute_modifiers"),
        java.util.Map.entry("CustomPotionEffects", "minecraft:potion_contents"),
        java.util.Map.entry("Potion", "minecraft:potion_contents"),
        java.util.Map.entry("HideFlags", "minecraft:hide_tooltip")
    );

    /**
     * 将包含旧式键名的路径段转换为新式键名 (仅转换每个路径段的第一部分).
     * 例如: "Enchantments[0].id" → "minecraft:enchantments[0].id"
     */
    private static String translateLegacyKeyInFirstSegment(String path) {
        // 提取第一个路径段 (直到 '.' 或 '[', 或整个路径)
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

    @Nullable
    public static Tag resolve(CompoundTag nbt, String path) {
        if (nbt == null || path == null || path.isEmpty()) {
            return null;
        }

        try {
            // 检查是否包含通配符
            if (path.contains("[*]")) {
                List<Tag> results = resolveWildcardPath(nbt, path);
                if (results.isEmpty()) {
                    // 向后兼容: 通配符路径也尝试 "components." 前缀
                    String translatedPath = translateLegacyPath(path);
                    if (translatedPath != null && !translatedPath.equals(path)) {
                        results = resolveWildcardPath(nbt, translatedPath);
                    }
                }
                return results.isEmpty() ? null : results.get(0);
            }

            // 尝试直接解析路径 (适用于新格式路径, 如 "components.minecraft:enchantments")
            Tag result = resolvePathRecursive(nbt, path);
            if (result != null) {
                return result;
            }

            // 向后兼容: 尝试转换后的路径
            String translatedPath = translateLegacyPath(path);
            if (translatedPath != null && !translatedPath.equals(path)) {
                result = resolvePathRecursive(nbt, translatedPath);
                if (result != null) {
                    return result;
                }
            }

            return null;
        } catch (Exception e) {
            RarityCore.LOGGER.debug("解析物品数据路径 '{}' 时发生错误: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 将旧式路径转换为新格式路径. 返回 null 表示无法转换 (不改变原路径).
     */
    @Nullable
    private static String translateLegacyPath(String path) {
        // "tag.XXX" → "components.minecraft:custom_data.XXX"
        if (path.startsWith("tag.")) {
            String remainingPath = path.substring(4);
            return "components." + CUSTOM_DATA_KEY + "." + remainingPath;
        }

        // "Count" → "count"
        if (path.equals("Count")) {
            return "count";
        }

        // 新格式路径不需要转换
        if (path.startsWith("components.") || path.equals("id") || path.equals("count")) {
            return path;
        }

        // 旧式路径: 转换键名 + 添加 "components." 前缀
        String translatedKey = translateLegacyKeyInFirstSegment(path);
        return "components." + translatedKey;
    }
    
    /**
     * 递归解析路径
     */
    private static Tag resolvePathRecursive(Tag current, String remainingPath) {
        if (remainingPath.isEmpty()) {
            return current;
        }
        
        if (current == null || current.getId() == 0) { // END tag
            return null;
        }
        
        // 解析路径的第一部分
        int dotIndex = remainingPath.indexOf('.');
        String firstPart = dotIndex == -1 ? remainingPath : remainingPath.substring(0, dotIndex);
        String restPath = dotIndex == -1 ? "" : remainingPath.substring(dotIndex + 1);
        
        // 处理当前标签
        Tag next = getNextTag(current, firstPart);
        
        // 如果是components前缀且没有找到，尝试直接从根级查找
        if (next == null && firstPart.equals("components")) {
            // 直接使用restPath作为键名从根级查找
            next = getNextTag(current, restPath);
            if (next != null) {
                return next;
            }
        }
        
        if (next == null) {
            return null;
        }
        
        // 递归处理剩余路径
        return resolvePathRecursive(next, restPath);
    }
    
    /**
     * 根据路径段获取下一个标签
     */
    private static Tag getNextTag(Tag current, String pathSegment) {
        if (current instanceof CompoundTag compound) {
            // 处理复合标签
            // 检查是否是数组索引格式 [0], [1] 等
            if (pathSegment.startsWith("[") && pathSegment.endsWith("]")) {
                String indexStr = pathSegment.substring(1, pathSegment.length() - 1);
                try {
                    int index = Integer.parseInt(indexStr);
                    // 在CompoundTag中无法直接使用数组索引
                    return null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            
            // 直接返回复合标签中对应的键值，支持包含冒号的键名
            return compound.get(pathSegment);
        } else if (current instanceof ListTag list) {
            // 处理列表标签
            // 检查是否是数组索引格式 [0], [1] 等
            if (pathSegment.startsWith("[") && pathSegment.endsWith("]")) {
                String indexStr = pathSegment.substring(1, pathSegment.length() - 1);
                try {
                    int index = Integer.parseInt(indexStr);
                    if (index >= 0 && index < list.size()) {
                        return list.get(index);
                    } else {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            
            try {
                int index = Integer.parseInt(pathSegment);
                if (index >= 0 && index < list.size()) {
                    return list.get(index);
                }
            } catch (NumberFormatException e) {
                // 不是有效数字索引
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * 分割路径为各个部分
     */
    private static String[] splitPath(String path) {
        // 改进的分割实现,支持通配符
        return path.split("\\.");
    }
    
    /**
     * 解析通配符路径
     * @param nbt 要解析的NBT标签
     * @param wildcardPath 包含通配符的路径
     * @return 匹配的所有标签结果
     */
    public static List<Tag> resolveWildcardPath(CompoundTag nbt, String wildcardPath) {
        List<Tag> results = new ArrayList<>();
        
        if (nbt == null || wildcardPath == null || wildcardPath.isEmpty()) {
            return results;
        }
        
        try {
            // 解析通配符路径
            String[] parts = splitWildcardPath(wildcardPath);
            if (parts.length == 0) {
                return results;
            }
            
            // 处理第一部分
            String firstPart = parts[0];
            String remainingPath = String.join(".", 
                Arrays.copyOfRange(parts, 1, parts.length));
            
            // 检查是否是通配符模式
            if (firstPart.endsWith("[*]")) {
                String arrayKey = firstPart.substring(0, firstPart.length() - 3);
                Tag arrayTag = nbt.get(arrayKey);
                
                if (arrayTag instanceof ListTag listTag) {
                    // 遍历数组中的每个元素
                    for (int i = 0; i < listTag.size(); i++) {
                        Tag element = listTag.get(i);
                        if (remainingPath.isEmpty()) {
                            // 如果没有剩余路径,直接添加元素
                            results.add(element);
                        } else {
                            // 递归解析剩余路径
                            Tag nestedResult = resolvePathRecursive(element, remainingPath);
                            if (nestedResult != null) {
                                results.add(nestedResult);
                            }
                        }
                    }
                }
            } else {
                // 非通配符部分,按原有逻辑处理
                Tag next = getNextTag(nbt, firstPart);
                if (next != null) {
                    if (remainingPath.isEmpty()) {
                        results.add(next);
                    } else {
                        Tag nestedResult = resolvePathRecursive(next, remainingPath);
                        if (nestedResult != null) {
                            results.add(nestedResult);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            RarityCore.LOGGER.debug("解析通配符路径 '{}' 时发生错误: {}", wildcardPath, e.getMessage());
        }
        
        return results;
    }
    
    /**
     * 分割通配符路径
     */
    private static String[] splitWildcardPath(String path) {
        // 支持通配符的路径分割
        List<String> parts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            
            if (c == '.') {
                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString());
                    currentPart.setLength(0);
                }
            } else if (c == '[') {
                // 检查是否是通配符
                if (i + 2 < path.length() && path.charAt(i + 1) == '*' && path.charAt(i + 2) == ']') {
                    if (currentPart.length() > 0) {
                        parts.add(currentPart.toString() + "[*]");
                        currentPart.setLength(0);
                    } else {
                        parts.add("[*]");
                    }
                    i += 2; // 跳过 *]
                } else {
                    // 普通数组索引
                    currentPart.append(c);
                    while (i + 1 < path.length() && path.charAt(i + 1) != ']') {
                        currentPart.append(path.charAt(++i));
                    }
                    if (i + 1 < path.length()) {
                        currentPart.append(path.charAt(++i)); // 添加 ]
                    }
                }
            } else {
                currentPart.append(c);
            }
        }
        
        if (currentPart.length() > 0) {
            parts.add(currentPart.toString());
        }
        
        return parts.toArray(new String[0]);
    }
    
    /**
     * 检查路径是否包含通配符
     */
    public static boolean containsWildcard(String path) {
        return path != null && path.contains("[*]");
    }
    
    /**
     * 获取标签的值(转换为适当的Java类型)
     */
    @Nullable
    public static Object getTagValue(Tag tag) {
        if (tag == null) {
            return null;
        }

        // 使用最安全的方式:通过ID判断类型并转换
        switch (tag.getId()) {
            case 1: // BYTE
            case 2: // SHORT
            case 3: // INT
            case 4: // LONG
            case 5: // FLOAT
            case 6: // DOUBLE
                try {
                    return Double.parseDouble(tag.asString().orElse(""));
                } catch (NumberFormatException e) {
                    return tag.asString().orElse("");
                }
            case 7: // BYTE_ARRAY
                if (tag instanceof net.minecraft.nbt.ByteArrayTag byteArrayTag) {
                    return byteArrayTag.getAsByteArray();
                }
                return tag.asString().orElse("");
            case 8: // STRING
                return tag.asString().orElse("");
            case 9: // LIST
                if (tag instanceof ListTag listTag) {
                    return getListAsJavaList(listTag);
                }
                return tag.asString().orElse("");
            case 10: // COMPOUND
                if (tag instanceof CompoundTag compoundTag) {
                    return getCompoundAsJavaMap(compoundTag);
                }
                return tag.asString().orElse("");
            case 11: // INT_ARRAY
                if (tag instanceof net.minecraft.nbt.IntArrayTag intArrayTag) {
                    return intArrayTag.getAsIntArray();
                }
                return tag.asString().orElse("");
            case 12: // LONG_ARRAY
                if (tag instanceof net.minecraft.nbt.LongArrayTag longArrayTag) {
                    return longArrayTag.getAsLongArray();
                }
                return tag.asString().orElse("");
            default:
                return tag.asString().orElse("");
        }
    }
    
    /**
     * 将NBT列表转换为Java列表
     */
    private static List<Object> getListAsJavaList(ListTag listTag) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < listTag.size(); i++) {
            Tag element = listTag.get(i);
            result.add(getTagValue(element));
        }
        return result;
    }
    
    /**
     * 将NBT复合标签转换为Java Map
     */
    private static java.util.Map<String, Object> getCompoundAsJavaMap(CompoundTag compoundTag) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        for (String key : compoundTag.keySet()) {
            Tag value = compoundTag.get(key);
            result.put(key, getTagValue(value));
        }
        return result;
    }
    
    /**
     * 检查路径是否有效
     */
    public static boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        // 基本格式检查
        return path.matches("^[a-zA-Z0-9_\\[\\]\\.]+$");
    }
    
    /**
     * 获取路径的最后一个部分(用于调试)
     */
    public static String getLastPathSegment(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        
        String[] parts = path.split("[\\.\\[\\]]");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty()) {
                return parts[i];
            }
        }
        return path;
    }
    
    /**
     * 判断是否是根级路径
     * @param path 路径字符串
     * @return 如果是根级路径返回true
     */
    private static boolean isRootLevelPath(String path) {
        return path.startsWith("tag.") || 
               path.startsWith("components.") ||
               path.equals("Count") || 
               path.equals("id") || 
               path.startsWith("Count.") || 
               path.startsWith("id.");
    }
    
    /**
     * 解析根级路径
     * @param tag 当前的tag标签
     * @param rootPath 根级路径
     * @return 解析结果
     */
    private static Tag resolveRootPath(CompoundTag tag, String rootPath) {
        // 构建完整的物品NBT结构
        CompoundTag itemNbt = new CompoundTag();
        itemNbt.put("tag", tag);
        
        // 如果路径以"tag."开头,去掉前缀
        if (rootPath.startsWith("tag.")) {
            String actualPath = rootPath.substring(4);
            return resolvePathRecursive(tag, actualPath);
        }
        
        // 如果路径以"components."开头,直接从tag中获取
        if (rootPath.startsWith("components.")) {
            String actualPath = rootPath.substring(11);
            return resolvePathRecursive(tag, actualPath);
        }
        
        // 处理其他根级字段
        if (rootPath.equals("Count") || rootPath.equals("id")) {
            // 这些需要从完整的物品NBT中获取,但当前只传入了tag部分
            // 在实际使用中,可能需要修改调用方传入完整NBT
            return null;
        }
        
        return null;
    }
}