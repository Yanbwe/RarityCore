package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;

public class RangeCondition extends ItemDataCondition {
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
        if (ItemDataPathResolver.containsWildcard(path)) {
            return matchesWildcard(nbt);
        }

        Tag tag = ItemDataPathResolver.resolve(nbt, path);
        if (tag == null) {
            return false;
        }

        return isInRange(tag, minValue, maxValue);
    }

    private boolean matchesWildcard(CompoundTag nbt) {
        List<Tag> results = ItemDataPathResolver.resolveWildcardPath(nbt, path);
        if (results.isEmpty()) {
            return false;
        }

        for (Tag result : results) {
            if (isInRange(result, minValue, maxValue)) {
                return true;
            }
        }

        return false;
    }

    private boolean isInRange(Tag tag, Number min, Number max) {
        try {
            double value;
            if (tag instanceof net.minecraft.nbt.IntTag intTag) {
                value = intTag.asInt().orElse(0);
            } else if (tag instanceof net.minecraft.nbt.LongTag longTag) {
                value = longTag.asLong().orElse(0L);
            } else if (tag instanceof net.minecraft.nbt.FloatTag floatTag) {
                value = floatTag.asFloat().orElse(0f);
            } else if (tag instanceof net.minecraft.nbt.DoubleTag doubleTag) {
                value = doubleTag.asDouble().orElse(0d);
            } else if (tag instanceof net.minecraft.nbt.ByteTag byteTag) {
                value = byteTag.asByte().orElse((byte)0);
            } else if (tag instanceof net.minecraft.nbt.ShortTag shortTag) {
                value = shortTag.asShort().orElse((short)0);
            } else {
                value = Double.parseDouble(tag.toString());
            }
            double minVal = min.doubleValue();
            double maxVal = max.doubleValue();
            return value >= minVal && value <= maxVal;
        } catch (Exception e) {
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
