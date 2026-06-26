package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;

import java.util.List;

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
    public boolean matches(DataComponentMap components, ItemStack itemStack) {
        if (DataComponentPathResolver.containsWildcard(path)) {
            return matchesWildcard(components, itemStack);
        }

        Object actualValue = DataComponentPathResolver.resolve(components, itemStack, path);
        if (actualValue == null) {
            return false;
        }

        return compareValues(actualValue, expectedValue);
    }

    private boolean matchesWildcard(DataComponentMap components, ItemStack itemStack) {
        List<Object> results = DataComponentPathResolver.resolveWildcard(components, itemStack, path);
        if (results.isEmpty()) {
            return false;
        }

        for (Object result : results) {
            if (compareValues(result, expectedValue)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean compareValues(Object actual, Object expected) {
        if (expected == null) {
            return actual == null;
        }

        String actualString = actual.toString();
        String expectedString = expected.toString();

        if (expected instanceof Number) {
            try {
                double actualNum = Double.parseDouble(actualString);
                double expectedNum = ((Number) expected).doubleValue();
                return Math.abs(actualNum - expectedNum) < 0.001;
            } catch (NumberFormatException e) {
                return actualString.equals(expectedString);
            }
        }

        if (expected instanceof Boolean) {
            return actualString.equals(expectedString);
        }

        return actualString.equals(expectedString);
    }

    public Object getExpectedValue() {
        return expectedValue;
    }
}
