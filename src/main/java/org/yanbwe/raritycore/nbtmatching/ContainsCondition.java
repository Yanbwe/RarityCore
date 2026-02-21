package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.List;

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
        // 检查是否使用通配符
        if (NbtPathResolver.containsWildcard(path)) {
            return matchesWildcard(nbt);
        }
        
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
    
    /**
     * 处理通配符路径的包含匹配
     * @param nbt NBT标签
     * @return 是否匹配成功
     */
    private boolean matchesWildcard(CompoundTag nbt) {
        List<Tag> results = NbtPathResolver.resolveWildcardPath(nbt, path);
        if (results.isEmpty()) {
            return false;
        }
        
        // 对于通配符，采用"任意匹配"策略：只要有一个元素包含子字符串即返回true
        for (Tag result : results) {
            if (result.getAsString().contains(substring)) {
                return true;
            }
        }
        
        return false;
    }
    
    public String getSubstring() {
        return substring;
    }
}