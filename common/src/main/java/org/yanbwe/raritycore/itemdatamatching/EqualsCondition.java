package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
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

        if (actual == null) {
            return false;
        }

        String actualString = String.valueOf(actual);
        String expectedString = String.valueOf(expected);

        // 数值期望值：与数值型实际值按数值比较（容差 0.001）
        // 说明：DataComponentPathResolver 对旧式 NBT 数值会经 getTagValue 返回 Double，
        // 而同步包还原出的是 Long，两者数值相等但 toString() 可能不同（1 vs 1.0），必须按数值比较
        if (expected instanceof Number) {
            Double actualNum = toNumber(actual);
            if (actualNum == null) {
                return false;
            }
            return Math.abs(actualNum - ((Number) expected).doubleValue()) < 0.001;
        }

        // 布尔期望值：DataComponent API 的布尔组件返回 Boolean，旧式 NBT 标志位返回 Double(1.0/0.0)，
        // 因此必须同时兼容"布尔 ↔ 数值 0/1"与"布尔 ↔ 字符串 true/false"
        if (expected instanceof Boolean) {
            boolean expectedBool = ((Boolean) expected).booleanValue();
            Boolean actualBool = toBoolean(actual);
            if (actualBool != null) {
                return actualBool.booleanValue() == expectedBool;
            }
            return actualString.equalsIgnoreCase(expectedString);
        }

        if (actualString.equals(expectedString)) {
            return true;
        }

        // 字符串形式的期望值：兼容"配置写数字/布尔但实际值是数值"的情形
        // （如 {"value":"1"} 对应数值 1、{"value":"true"} 对应 1/0），
        // 该情形源于旧版本同步包把数值序列化为字符串，此分支使客户端无需重连即可自愈
        if (expected instanceof String) {
            Double actualNum = toNumber(actual);
            if (actualNum != null) {
                try {
                    double expectedNum = Double.parseDouble(expectedString.trim());
                    if (Math.abs(actualNum - expectedNum) < 0.001) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // 期望值不是数字，继续按布尔处理
                }
                if ("true".equalsIgnoreCase(expectedString.trim())) {
                    return actualNum.doubleValue() != 0.0;
                }
                if ("false".equalsIgnoreCase(expectedString.trim())) {
                    return actualNum.doubleValue() == 0.0;
                }
            }
        }

        return false;
    }

    /**
     * 将实际值转为数值，非数值类型返回 null
     * 兼容 Double/Integer/Long/Float/Short/Byte 以及数字字符串（"1b" 等紧凑后缀也接受）
     */
    @Nullable
    private Double toNumber(Object value) {
        if (value instanceof Number number) {
            return Double.valueOf(number.doubleValue());
        }
        if (value instanceof Boolean bool) {
            return Double.valueOf(bool.booleanValue() ? 1.0 : 0.0);
        }
        if (value instanceof String text) {
            try {
                return Double.valueOf(stripNumericSuffix(text.trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 将实际值转为布尔，无法判定时返回 null
     * 兼容 Boolean、数值（非 0 为 true）与字符串 "true"/"false"、"1b"/"0b"
     */
    @Nullable
    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            if ("true".equalsIgnoreCase(normalized)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(normalized)) {
                return Boolean.FALSE;
            }
        }
        Double number = toNumber(value);
        return number == null ? null : Boolean.valueOf(number.doubleValue() != 0.0);
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

    public Object getExpectedValue() {
        return expectedValue;
    }
}
