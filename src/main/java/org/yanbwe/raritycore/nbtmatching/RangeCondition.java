package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

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
        Tag tag = NbtPathResolver.resolve(nbt, path);
        if (tag == null) {
            return false;
        }
        
        return isInRange(tag, minValue, maxValue);
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