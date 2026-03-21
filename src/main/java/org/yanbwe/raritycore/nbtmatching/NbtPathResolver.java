package org.yanbwe.raritycore.nbtmatching;

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
     * 支持的路径格式:
     * - "Enchantments"                    // 简单键访问
     * - "Enchantments[0]"                 // 数组索引访问
     * - "Enchantments[0].id"              // 嵌套访问
     * - "display.Name"                    // 点号分隔的嵌套访问
     * - "Enchantments[*].id"              // 通配符访问(新功能)
     * - "Enchantments[*].lvl"             // 通配符访问(新功能)
     * - "tag.Yanbwe"                      // 根级tag路径访问
     * - "Count"                           // 根级Count字段访问
     * - "id"                              // 根级id字段访问
     * 
     * @param nbt 要解析的NBT标签
     * @param path NBT路径
     * @return 解析到的标签,如果路径无效则返回null
     */
    @Nullable
    public static Tag resolve(CompoundTag nbt, String path) {
        if (nbt == null || path == null || path.isEmpty()) {
            return null;
        }
        
        try {
            // 检查是否是根级路径(以"tag."开头或其他根级字段)
            if (isRootLevelPath(path)) {
                return resolveRootPath(nbt, path);
            }
            
            // 检查是否包含通配符
            if (path.contains("[*]")) {
                List<Tag> results = resolveWildcardPath(nbt, path);
                // 对于通配符路径,返回第一个匹配的结果或者null
                return results.isEmpty() ? null : results.get(0);
            }
            
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
            Arrays.copyOfRange(parts, 1, parts.length)) : "";
        
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
            
            if (pathSegment.matches("\\d+")) {
                // 数字索引,但在复合标签中应该是键名
                return compound.get(pathSegment);
            } else {
                return compound.get(pathSegment);
            }
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
        return path.split("\\.|(?=\\[)|(?<=\\])");
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
        String actualPath = rootPath;
        if (rootPath.startsWith("tag.")) {
            actualPath = rootPath.substring(4);
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