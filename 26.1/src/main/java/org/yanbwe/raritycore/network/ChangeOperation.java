package org.yanbwe.raritycore.network;

import net.minecraft.resources.Identifier;

public class ChangeOperation {
    public enum OperationType {
        ADD,
        UPDATE,
        DELETE
    }

    private final OperationType type;
    private final Identifier itemId;
    private final Integer rarity;

    public ChangeOperation(OperationType type, Identifier itemId, Integer rarity) {
        this.type = type;
        this.itemId = itemId;
        this.rarity = rarity;
    }

    public OperationType getType() {
        return type;
    }

    public Identifier getItemId() {
        return itemId;
    }

    public Integer getRarity() {
        return rarity;
    }
}