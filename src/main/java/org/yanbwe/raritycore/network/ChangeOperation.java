package org.yanbwe.raritycore.network;

import net.minecraft.resources.ResourceLocation;

/**
 * 表示稀有度变更的操作类型
 */
public class ChangeOperation {
    public enum OperationType {
        ADD,    // 添加新物品稀有度
        UPDATE, // 更新已有物品稀有度
        DELETE  // 删除物品稀有度
    }

    private final OperationType type;
    private final ResourceLocation itemId;
    private final Integer rarity; // 对于DELETE操作，此值为null

    public ChangeOperation(OperationType type, ResourceLocation itemId, Integer rarity) {
        this.type = type;
        this.itemId = itemId;
        this.rarity = rarity;
    }

    public OperationType getType() {
        return type;
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    public Integer getRarity() {
        return rarity;
    }
}