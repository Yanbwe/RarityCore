package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ContainsCondition extends ItemDataCondition {
    private final String substring;

    public ContainsCondition(String path, String substring) {
        super(path, MatchType.CONTAINS);
        this.substring = substring != null ? substring : "";
    }

    public ContainsCondition(String path, String substring, String description) {
        super(path, MatchType.CONTAINS, description);
        this.substring = substring != null ? substring : "";
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

        return value.toString().contains(substring);
    }

    private boolean matchesWildcard(DataComponentMap components, ItemStack itemStack) {
        List<Object> results = DataComponentPathResolver.resolveWildcard(components, itemStack, path);
        if (results.isEmpty()) {
            return false;
        }

        for (Object result : results) {
            if (result.toString().contains(substring)) {
                return true;
            }
        }

        return false;
    }

    public String getSubstring() {
        return substring;
    }
}
