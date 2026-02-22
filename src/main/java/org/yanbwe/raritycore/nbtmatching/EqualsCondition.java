package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.nbt.*;
import org.yanbwe.raritycore.RarityCore;

import java.util.List;

/**
 * 等值匹配条件
 * 检查NBT标签的值是否等于指定值
 */
public class EqualsCondition extends NbtCondition {
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
        if (NbtPathResolver.containsWildcard(path)) {
            return matchesWildcard(nbt);
        }
        
        Tag actualTag = NbtPathResolver.resolve(nbt, path);
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
        List<Tag> results = NbtPathResolver.resolveWildcardPath(nbt, path);
        if (results.isEmpty()) {
            return false;
        }
        
        // 对于通配符，采用"任意匹配"策略：只要有一个元素匹配成功即返回true
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
        
        // 对于数值类型，尝试数值比较
        if (expected instanceof Number) {
            try {
                double actualNum = Double.parseDouble(actualString);
                double expectedNum = ((Number) expected).doubleValue();
                return Math.abs(actualNum - expectedNum) < 0.001;
            } catch (NumberFormatException e) {
                // 如果不能转换为数字，则使用字符串比较
                return actualString.equals(expectedString);
            }
        }
        
        // 对于布尔值
        if (expected instanceof Boolean) {
            return actualString.equals(expected.toString());
        }
        
        // 默认使用字符串比较
        return actualString.equals(expectedString);
    }
    
    private boolean compareListTags(ListTag actualList, Object expected) {
        if (!(expected instanceof java.util.List)) {
            return false;
        }
        
        java.util.List<?> expectedList = (java.util.List<?>) expected;
        if (actualList.size() != expectedList.size()) {
            return false;
        }
        
        for (int i = 0; i < actualList.size(); i++) {
            Tag actualElement = actualList.get(i);
            Object expectedElement = expectedList.get(i);
            if (!compareTags(actualElement, expectedElement)) {
                return false;
            }
        }
        
        return true;
    }
    
    private boolean compareCompoundTags(CompoundTag actualCompound, Object expected) {
        if (!(expected instanceof java.util.Map)) {
            return false;
        }
        
        java.util.Map<?, ?> expectedMap = (java.util.Map<?, ?>) expected;
        if (actualCompound.size() != expectedMap.size()) {
            return false;
        }
        
        for (String key : actualCompound.getAllKeys()) {
            if (!expectedMap.containsKey(key)) {
                return false;
            }
            
            Tag actualValue = actualCompound.get(key.toString());
            Object expectedValue = expectedMap.get(key);
            if (!compareTags(actualValue, expectedValue)) {
                return false;
            }
        }
        
        return true;
    }
    
    public Object getExpectedValue() {
        return expectedValue;
    }
}