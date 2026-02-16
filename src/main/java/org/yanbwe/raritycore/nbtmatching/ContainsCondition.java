package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * 包含匹配条件
 * 检查字符串类型的NBT标签是否包含指定子字符串
 */
public class ContainsCondition extends NbtCondition {
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
    public boolean matches(CompoundTag nbt) {
        Tag tag = NbtPathResolver.resolve(nbt, path);
        if (tag == null) {
            return false;
        }
        
        if (tag instanceof StringTag stringTag) {
            return stringTag.getAsString().contains(substring);
        }
        
        // 对于非字符串标签，转换为字符串后检查
        return tag.getAsString().contains(substring);
    }
    
    public String getSubstring() {
        return substring;
    }
}