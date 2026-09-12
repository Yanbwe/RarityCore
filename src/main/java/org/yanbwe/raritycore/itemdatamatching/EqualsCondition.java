package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/**
 * 等值匹配条件
 * 检查物品数据的值是否等于指定值
 */
public class EqualsCondition extends ItemDataCondition {
    private final Object expectedValue;
    
    public EqualsCondition(String path, Object value) {
        super(path, MatchType.EQUALS);
        this.expectedValue = value;
    }
    
    public EqualsCondition(String path, Object value, String description) {
        super(path, MatchType.EQUALS, description);
        this.expectedValue = value;
    }
    
    @Override
    public boolean matches(CompoundTag nbt) {
        // 检查是否使用通配符
        if (ItemDataPathResolver.containsWildcard(path)) {
            return matchesWildcard(nbt);
        }
        
        Tag actualTag = ItemDataPathResolver.resolve(nbt, path);
        if (actualTag == null) {
            return false;
        }
        
        return compareTags(actualTag, expectedValue);
    }
    
    /**
     * 处理通配符路径的匹配逻辑
     * @param nbt NBT标签
     * @return 是否匹配成功
     */
    private boolean matchesWildcard(CompoundTag nbt) {
        List<Tag> results = ItemDataPathResolver.resolveWildcardPath(nbt, path);
        if (results.isEmpty()) {
            return false;
        }
        
        // 对于通配符,采用"任意匹配"策略:只要有一个元素匹配成功即返回true
        for (Tag result : results) {
            if (compareTags(result, expectedValue)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean compareTags(Tag actual, Object expected) {
        if (expected == null) {
            return actual == null || actual.getId() == 0; // END tag
        }
        
        // 获取实际NBT值
        String actualString = actual.getAsString();
        
        // 正确处理期望值的字符串表示
        String expectedString;
        if (expected instanceof String) {
            expectedString = (String) expected;
        } else {
            expectedString = expected.toString();
        }
        
        // 类型安全：所有分支都兼容 Number/Boolean/String 任意实例，避免 ClassCastException
        if (expected instanceof Number) {
            // 仅在"实际标签本身是数值类型"时做数值比较，避免 StringTag("32") 被 equals(32) 误匹配
            // 注意 ByteTag 的 getAsString() 带后缀（"1b"/"0b"），需按字节字面量比较
            if (!isNumberComparableTag(actual)) {
                return false;
            }
            return equalsNumeric(actualString, ((Number) expected).doubleValue());
        }
        
        // 对于布尔值：NBT 没有布尔类型，原版与模组普遍用 ByteTag(1b/0b) 存储标志位，同时兼容 IntTag(1/0)
        if (expected instanceof Boolean) {
            if (!isNumberComparableTag(actual)) {
                return false;
            }
            return readBoolean(actualString) == ((Boolean) expected).booleanValue();
        }
        
        // 默认使用字符串比较
        if (actualString.equals(expectedString)) {
            return true;
        }
        
        // 字符串形式的期望值：兼容"配置写数字/布尔但 NBT 是数值标签"的情形
        // （如 {"value":"1"} 对应 ByteTag(1b)、{"value":"32"} 对应 IntTag(32)、{"value":"true"} 对应 ByteTag(1b)）
        // 该情形源于旧版本同步包把数值一律序列化为字符串，此分支使客户端无需重连即可自愈；
        // 仍要求实际标签是数值类型，因此不会误匹配 StringTag("32")
        if (expected instanceof String && isNumberComparableTag(actual)) {
            if (numericEquals(actualString, expectedString)) {
                return true;
            }
            if ("true".equalsIgnoreCase(expectedString) && readBoolean(actualString)) {
                return true;
            }
            if ("false".equalsIgnoreCase(expectedString) && !readBoolean(actualString)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判断标签是否可参与数值/布尔比较
     * 限定为真正的数值型标签，避免数字型期望值误匹配字符串标签内容
     */
    private boolean isNumberComparableTag(Tag tag) {
        return isNumericTag(tag);
    }
    
    /**
     * 判断标签是否为数值类型（BYTE/SHORT/INT/LONG/FLOAT/DOUBLE）
     */
    private boolean isNumericTag(Tag tag) {
        int id = tag.getId();
        return id == Tag.TAG_BYTE || id == Tag.TAG_SHORT || id == Tag.TAG_INT
            || id == Tag.TAG_LONG || id == Tag.TAG_FLOAT || id == Tag.TAG_DOUBLE;
    }
    
    /**
     * 将 NBT 的字符串表示与期望数值比较
     * ByteTag 的 getAsString() 形如 "1b"/"0b"，需剥离后缀后再解析
     */
    private boolean equalsNumeric(String actualString, double expectedNum) {
        try {
            return Math.abs(Double.parseDouble(stripNumericSuffix(actualString)) - expectedNum) < 0.001;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 按数值比较两个字符串（实际 NBT 的 getAsString 结果与期望值字符串）
     * 实际值会先剥离 ByteTag/LongTag 等紧凑后缀，使旧格式 {"value":"1"} 仍能匹配 ByteTag(1b)
     */
    private boolean numericEquals(String actualString, String expectedString) {
        try {
            double actualNum = Double.parseDouble(stripNumericSuffix(actualString.trim()));
            double expectedNum = Double.parseDouble(expectedString.trim());
            return Math.abs(actualNum - expectedNum) < 0.001;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 剥离 NBT 数值字符串的紧凑后缀（b/s/L/f/d，如 "1b"、"100L"、"1.5f"）
     */
    private String stripNumericSuffix(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char last = value.charAt(value.length() - 1);
        if (last == 'b' || last == 'B' || last == 's' || last == 'S'
                || last == 'l' || last == 'L' || last == 'f' || last == 'F' || last == 'd' || last == 'D') {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
    
    /**
     * 将 NBT 字符串形式的值读取为布尔
     * 支持 "1"/"0"（数值标签）、"1b"/"0b"（ByteTag）以及 "true"/"false"
     * @return 无法识别为布尔时返回 false
     */
    private boolean readBoolean(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.endsWith("b") || normalized.endsWith("B")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        try {
            return Double.parseDouble(normalized) != 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    public Object getExpectedValue() {
        return expectedValue;
    }
}