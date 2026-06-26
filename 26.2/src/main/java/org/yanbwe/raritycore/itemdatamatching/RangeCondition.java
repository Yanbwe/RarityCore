package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;

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
    public boolean matches(DataComponentMap components, ItemStack itemStack) {
        if (DataComponentPathResolver.containsWildcard(path)) {
            return matchesWildcard(components, itemStack);
        }

        Object value = DataComponentPathResolver.resolve(components, itemStack, path);
        if (value == null) {
            return false;
        }

        return isInRange(value, minValue, maxValue);
    }

    private boolean matchesWildcard(DataComponentMap components, ItemStack itemStack) {
        List<Object> results = DataComponentPathResolver.resolveWildcard(components, itemStack, path);
        if (results.isEmpty()) {
            return false;
        }

        for (Object result : results) {
            if (isInRange(result, minValue, maxValue)) {
                return true;
            }
        }

        return false;
    }

    private boolean isInRange(Object value, Number min, Number max) {
        try {
            double numericValue;
            if (value instanceof Number num) {
                numericValue = num.doubleValue();
            } else {
                numericValue = Double.parseDouble(value.toString());
            }
            double minVal = min.doubleValue();
            double maxVal = max.doubleValue();
            return numericValue >= minVal && numericValue <= maxVal;
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
