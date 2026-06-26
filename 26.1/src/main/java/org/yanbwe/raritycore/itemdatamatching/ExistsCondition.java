package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 存在性匹配条件
 * 检查指定路径的物品数据是否存在
 */
public class ExistsCondition extends ItemDataCondition {
    
    public ExistsCondition(String path) {
        super(path, MatchType.EXISTS);
    }
    
    public ExistsCondition(String path, String description) {
        super(path, MatchType.EXISTS, description);
    }
    
    @Override
    public boolean matches(DataComponentMap components, ItemStack itemStack) {
        if (DataComponentPathResolver.containsWildcard(path)) {
            return matchesWildcard(components, itemStack);
        }
        
        Object value = DataComponentPathResolver.resolve(components, itemStack, path);
        return value != null;
    }
    
    private boolean matchesWildcard(DataComponentMap components, ItemStack itemStack) {
        List<Object> results = DataComponentPathResolver.resolveWildcard(components, itemStack, path);
        return !results.isEmpty();
    }
}
