package org.yanbwe.raritycore.event;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

/**
 * 稀有度工具提示事件
 * tooltip 构建时触发，允许其他模组向稀有度 tooltip 中添加自定义信息
 */
public class RarityTooltipEvent extends Event {
    private final ItemStack itemStack;
    private final int rarity;
    private final List<Component> tooltipList;

    public RarityTooltipEvent(ItemStack itemStack, int rarity, List<Component> tooltipList) {
        this.itemStack = itemStack;
        this.rarity = rarity;
        this.tooltipList = tooltipList;
    }

    /** 当前物品栈 */
    public ItemStack getItemStack() {
        return itemStack;
    }

    /** 该物品的稀有度等级 */
    public int getRarity() {
        return rarity;
    }

    /** 可追加的 tooltip 文本列表 */
    public List<Component> getTooltipList() {
        return tooltipList;
    }
}
