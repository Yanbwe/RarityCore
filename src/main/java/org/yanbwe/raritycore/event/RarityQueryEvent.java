package org.yanbwe.raritycore.event;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

import javax.annotation.Nullable;

/**
 * 稀有度查询事件
 * 在查询物品的稀有度时触发，允许通过事件总线
 * 修改稀有度查询结果。
 * <p>
 * {@link #getRarity()} 返回的稀有度可以被修改，
 * 调用 {@link #setRarity(int)} 来覆盖查询结果。
 */
public class RarityQueryEvent extends Event {
    @Nullable
    private final ItemStack itemStack;
    @Nullable
    private final Item item;
    private int rarity;
    private final String source;

    public RarityQueryEvent(@Nullable ItemStack itemStack, @Nullable Item item,
                            int rarity, String source) {
        this.itemStack = itemStack;
        this.item = item;
        this.rarity = rarity;
        this.source = source;
    }

    /** 获取查询的物品堆，可能为 null（如果查询来源仅为 Item） */
    @Nullable
    public ItemStack getItemStack() {
        return itemStack;
    }

    /** 获取查询的物品，可能为 null（如果查询来源仅为 ItemStack） */
    @Nullable
    public Item getItem() {
        return item;
    }

    /** 获取当前稀有度查询结果 */
    public int getRarity() {
        return rarity;
    }

    /** 修改稀有度查询结果（可变） */
    public void setRarity(int rarity) {
        this.rarity = rarity;
    }

    /** 获取查询来源标识，用于区分不同的查询触发点 */
    public String getSource() {
        return source;
    }
}
