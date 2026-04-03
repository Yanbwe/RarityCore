package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.yanbwe.raritycore.RarityCore;

import java.util.List;

/**
 * 存在性匹配条件
 * 检查指定路径的NBT标签是否存在
 */
public class ExistsCondition extends NbtCondition {
    
    public ExistsCondition(String path) {
        super(path, MatchType.EXISTS);
    }
    
    public ExistsCondition(String path, String description) {
        super(path, MatchType.EXISTS, description);
    }
    
    @Override
    public boolean matches(CompoundTag nbt) {
        RarityCore.LOGGER.debug("[ExistsCondition] matches: 检查路径 '{}', NBT内容={}", path, nbt);
        
        // 检查是否使用通配符
        if (NbtPathResolver.containsWildcard(path)) {
            RarityCore.LOGGER.debug("[ExistsCondition] matches: 检测到通配符路径");
            return matchesWildcard(nbt);
        }
        
        Tag tag = NbtPathResolver.resolve(nbt, path);
        RarityCore.LOGGER.debug("[ExistsCondition] matches: 路径 '{}' 解析结果={}, 匹配={}", path, tag, tag != null);
        return tag != null;
    }
    
    /**
     * 处理通配符路径的存在性匹配
     * @param nbt NBT标签
     * @return 是否匹配成功
     */
    private boolean matchesWildcard(CompoundTag nbt) {
        List<Tag> results = NbtPathResolver.resolveWildcardPath(nbt, path);
        // 对于存在性检查，只要返回结果列表不为空即表示存在
        RarityCore.LOGGER.debug("[ExistsCondition] matchesWildcard: 通配符路径 '{}' 匹配结果数量={}", path, results.size());
        return !results.isEmpty();
    }
}