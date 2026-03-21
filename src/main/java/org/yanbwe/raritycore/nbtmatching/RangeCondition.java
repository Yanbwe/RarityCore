package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/**
 * 范围匹配条件
 * 检查数值类型的NBT标签是否在指定范围内
 */
public class RangeCondition extends NbtCondition {
    private final Number minValue;
    private final Number maxValue;
    
    public RangeCondition(String path, Number min, Number max) {
        super(path, MatchType.RANGE);
        this.minValue = min;
        this.maxValue = max;
    }
    
    public RangeCondition(String path, Number min, Number max, String description) {
        super(path, MatchType.RANGE, description);
        this.minValue = min;
        this.maxValue = max;
    }
    
    @Override
    public boolean matches(CompoundTag nbt) {
        // 检查是否使用通配符
        if (NbtPathResolver.containsWildcard(path)) {
            return matchesWildcard(nbt);
        }
        
        Tag tag = NbtPathResolver.resolve(nbt, path);
        if (tag == null) {
            return false;
        }
        
        return isInRange(tag, minValue, maxValue);
    }
    
    /**
     * 处理通配符路径的范围匹配
     * @param nbt NBT标签
     * @return 是否匹配成功
     */
    private boolean matchesWildcard(CompoundTag nbt) {
        List<Tag> results = NbtPathResolver.resolveWildcardPath(nbt, path);
        if (results.isEmpty()) {
            return false;
        }
        
        // 对于通配符,采用"任意匹配"策略:只要有一个元素在范围内即返回true
        for (Tag result : results) {
            if (isInRange(result, minValue, maxValue)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isInRange(Tag tag, Number min, Number max) {
        try {
            double value = Double.parseDouble(tag.getAsString());
            double minVal = min.doubleValue();
            double maxVal = max.doubleValue();
            return value >= minVal && value <= maxVal;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    public Number getMinValue() {
        return minValue;
    }
    
    public Number getMaxValue() {
        return maxValue;
    }
}