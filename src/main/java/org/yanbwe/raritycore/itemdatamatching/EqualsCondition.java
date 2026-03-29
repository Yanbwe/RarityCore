package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

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
    public boolean matches(CompoundTag nbt) {
        if (ItemDataPathResolver.containsWildcard(path)) {
            return matchesWildcard(nbt);
        }

        Tag actualTag = ItemDataPathResolver.resolve(nbt, path);
        if (actualTag == null) {
            return false;
        }

        return compareTags(actualTag, expectedValue);
    }

    private boolean matchesWildcard(CompoundTag nbt) {
        List<Tag> results = ItemDataPathResolver.resolveWildcardPath(nbt, path);
        if (results.isEmpty()) {
            return false;
        }

        for (Tag result : results) {
            if (compareTags(result, expectedValue)) {
                return true;
            }
        }

        return false;
    }

    private boolean compareTags(Tag actual, Object expected) {
        if (expected == null) {
            return actual == null || actual.getId() == 0;
        }

        String actualString = actual.toString();

        String expectedString;
        if (expected instanceof String) {
            expectedString = (String) expected;
        } else {
            expectedString = expected.toString();
        }

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
            return actualString.equals(expected.toString());
        }

        return actualString.equals(expectedString);
    }

    public Object getExpectedValue() {
        return expectedValue;
    }
}
