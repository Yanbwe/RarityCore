package org.yanbwe.raritycore.event;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * 稀有度查询事件
 * 查询稀有度时触发，允许修改返回值
 */
public class RarityQueryEvent extends Event {
    private final ItemStack itemStack;
    private int rarity;
    private String source;

    public RarityQueryEvent(ItemStack itemStack, int rarity, String source) {
        this.itemStack = itemStack;
        this.rarity = rarity;
        this.source = source;
    }

    /** 被查询的物品栈 */
    public ItemStack getItemStack() {
        return itemStack;
    }

    /** 当前稀有度结果（可修改） */
    public int getRarity() {
        return rarity;
    }

    public void setRarity(int rarity) {
        this.rarity = rarity;
    }

    /** 查询来源（如 "registry"、"nbt"、"tag" 等） */
    public String getSource() {
        return source;
    }
}
