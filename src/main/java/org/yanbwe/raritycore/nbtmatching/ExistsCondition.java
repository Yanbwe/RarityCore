package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

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
        Tag tag = NbtPathResolver.resolve(nbt, path);
        return tag != null;
    }
}