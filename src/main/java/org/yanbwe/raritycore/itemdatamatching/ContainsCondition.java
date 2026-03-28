package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

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
    public boolean matches(CompoundTag nbt) {
        if (ItemDataPathResolver.containsWildcard(path)) {
            return matchesWildcard(nbt);
        }

        Tag tag = ItemDataPathResolver.resolve(nbt, path);
        if (tag == null) {
            return false;
        }

        if (tag instanceof StringTag stringTag) {
            return stringTag.asString().orElse("").contains(substring);
        }

        return tag.toString().contains(substring);
    }

    private boolean matchesWildcard(CompoundTag nbt) {
        List<Tag> results = ItemDataPathResolver.resolveWildcardPath(nbt, path);
        if (results.isEmpty()) {
            return false;
        }

        for (Tag result : results) {
            if (result instanceof StringTag stringTag) {
                if (stringTag.asString().orElse("").contains(substring)) {
                    return true;
                }
            } else if (result.toString().contains(substring)) {
                return true;
            }
        }

        return false;
    }

    public String getSubstring() {
        return substring;
    }
}
