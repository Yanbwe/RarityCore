package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.nbt.*;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NBT路径解析器
 * 负责解析和访问NBT标签中的嵌套结构
 */
public class NbtPathResolver {
    
    // 路径分隔符模式
    private static final Pattern PATH_PATTERN = Pattern.compile(
        "([^.\\[\\]]+)" +           // 普通键名
        "|(?:\\[(\\d+)\\])" +       // 数组索引 [0], [1] 等
        "|(?:\\.([^\\.\\[\\]]+))"   // 点号分隔的键名
    );
    
    /**
     * 根据路径解析NBT标签
     * 支持的路径格式：
     * - "Enchantments"                    // 简单键访问
     * - "Enchantments[0]"                 // 数组索引访问
     * - "Enchantments[0].id"              // 嵌套访问
     * - "display.Name"                    // 点号分隔的嵌套访问
     * 
     * @param nbt 要解析的NBT标签
     * @param path NBT路径
     * @return 解析到的标签，如果路径无效则返回null
     */
    @Nullable
    public static Tag resolve(CompoundTag nbt, String path) {
        if (nbt == null || path == null || path.isEmpty()) {
            return null;
        }
        
        try {
            return resolvePathRecursive(nbt, path);
        } catch (Exception e) {
            RarityCore.LOGGER.debug("解析NBT路径 '{}' 时发生错误: {}", path, e.getMessage());
            return null;
        }
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
        String[] parts = splitPath(remainingPath);
        if (parts.length == 0) {
            return current;
        }
        
        String firstPart = parts[0];
        String restPath = parts.length > 1 ? String.join(".", 
            java.util.Arrays.copyOfRange(parts, 1, parts.length)) : "";
        
        // 处理当前标签
        Tag next = getNextTag(current, firstPart);
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
            if (pathSegment.matches("\\d+")) {
                // 数字索引，但在复合标签中应该是键名
                return compound.get(pathSegment);
            } else {
                return compound.get(pathSegment);
            }
        } else if (current instanceof ListTag list) {
            // 处理列表标签
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
        // 简单的分割实现
        return path.split("\\.|(?=\\[)|(?<=\\])");
    }
    
    /**
     * 获取标签的值（转换为适当的Java类型）
     */
    @Nullable
    public static Object getTagValue(Tag tag) {
        if (tag == null) {
            return null;
        }
        
        // 使用最安全的方式：通过ID判断类型并转换
        switch (tag.getId()) {
            case 1: // BYTE
            case 2: // SHORT  
            case 3: // INT
            case 4: // LONG
            case 5: // FLOAT
            case 6: // DOUBLE
                try {
                    return Double.parseDouble(tag.getAsString());
                } catch (NumberFormatException e) {
                    return tag.getAsString();
                }
            case 7: // BYTE_ARRAY
                if (tag instanceof net.minecraft.nbt.ByteArrayTag byteArrayTag) {
                    return byteArrayTag.getAsByteArray();
                }
                return tag.getAsString();
            case 8: // STRING
                return tag.getAsString();
            case 9: // LIST
                if (tag instanceof ListTag listTag) {
                    return getListAsJavaList(listTag);
                }
                return tag.getAsString();
            case 10: // COMPOUND
                if (tag instanceof CompoundTag compoundTag) {
                    return getCompoundAsJavaMap(compoundTag);
                }
                return tag.getAsString();
            case 11: // INT_ARRAY
                if (tag instanceof net.minecraft.nbt.IntArrayTag intArrayTag) {
                    return intArrayTag.getAsIntArray();
                }
                return tag.getAsString();
            case 12: // LONG_ARRAY
                if (tag instanceof net.minecraft.nbt.LongArrayTag longArrayTag) {
                    return longArrayTag.getAsLongArray();
                }
                return tag.getAsString();
            default:
                return tag.getAsString();
        }
    }
    
    /**
     * 将NBT列表转换为Java列表
     */
    private static java.util.List<Object> getListAsJavaList(ListTag listTag) {
        java.util.List<Object> result = new java.util.ArrayList<>();
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
        for (String key : compoundTag.getAllKeys()) {
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
     * 获取路径的最后一个部分（用于调试）
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
}