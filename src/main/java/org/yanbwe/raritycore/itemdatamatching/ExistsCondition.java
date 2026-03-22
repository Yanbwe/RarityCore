package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

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
    public boolean matches(CompoundTag nbt) {
        // 检查是否使用通配符
        if (ItemDataPathResolver.containsWildcard(path)) {
            return matchesWildcard(nbt);
        }
        
        Tag tag = ItemDataPathResolver.resolve(nbt, path);
        return tag != null;
    }
    
    /**
     * 处理通配符路径的存在性匹配
     * @param nbt NBT标签
     * @return 是否匹配成功
     */
    private boolean matchesWildcard(CompoundTag nbt) {
        List<Tag> results = ItemDataPathResolver.resolveWildcardPath(nbt, path);
        // 对于存在性检查，只要返回结果列表不为空即表示存在
        return !results.isEmpty();
    }
}