package org.yanbwe.raritycore.event;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.Event;

/**
 * 稀有度变更事件
 * 当物品的稀有度被修改时触发
 */
public class RarityChangeEvent extends Event {
    private final Item item;
    private final Integer oldRarity;
    private final Integer newRarity;
    private final ChangeType changeType;
    
    public enum ChangeType {
        REGISTER,    // 注册新稀有度
        UPDATE,      // 更新现有稀有度
        REMOVE       // 移除稀有度
    }
    
    public RarityChangeEvent(Item item, Integer oldRarity, Integer newRarity, ChangeType changeType) {
        this.item = item;
        this.oldRarity = oldRarity;
        this.newRarity = newRarity;
        this.changeType = changeType;
    }
    
    public Item getItem() {
        return item;
    }
    
    public Integer getOldRarity() {
        return oldRarity;
    }
    
    public Integer getNewRarity() {
        return newRarity;
    }
    
    public ChangeType getChangeType() {
        return changeType;
    }
}