package org.yanbwe.raritycore.event;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

import java.util.List;

/**
 * 稀有度工具提示事件
 * 在构建物品的工具提示（tooltip）时触发，
 * 允许监听器修改工具提示内容和稀有度相关显示。
 * <p>
 * {@link #getTooltipList()} 返回的是可变列表的引用，
 * 监听器可以直接添加、移除或修改列表中的 Component。
 */
public class RarityTooltipEvent extends Event {
    private final ItemStack itemStack;
    private final int rarity;
    private final List<Component> tooltipList;
    private final boolean isSpecialRarity;

    public RarityTooltipEvent(ItemStack itemStack, int rarity,
                              List<Component> tooltipList, boolean isSpecialRarity) {
        this.itemStack = itemStack;
        this.rarity = rarity;
        this.tooltipList = tooltipList;
        this.isSpecialRarity = isSpecialRarity;
    }

    /** 获取当前显示工具提示的物品堆 */
    public ItemStack getItemStack() {
        return itemStack;
    }

    /** 获取物品当前的稀有度等级 */
    public int getRarity() {
        return rarity;
    }

    /**
     * 获取工具提示列表（可变）
     * 返回的是原始列表引用，监听器可直接修改此列表
     * 来添加、移除或替换工具提示行
     */
    public List<Component> getTooltipList() {
        return tooltipList;
    }

    /** 该物品是否具有特殊稀有度（非标准等级） */
    public boolean isSpecialRarity() {
        return isSpecialRarity;
    }
}
