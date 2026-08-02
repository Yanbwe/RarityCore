package org.yanbwe.raritycore.event;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

import java.util.List;

/**
 * 稀有度工具提示事件
 * <p>
 * 在 {@code RarityTooltipHandler.onItemTooltip()} 构建完基础 tooltip 组件后、
 * 插入到物品 tooltip 之前触发。监听器可以通过 {@link #getTooltipComponents()}
 * 获取可变列表，追加或修改要插入的组件。
 * </p>
 */
public class RarityTooltipEvent extends Event {

    private final ItemStack itemStack;
    private final int rarity;
    private final List<Component> tooltipComponents;

    /**
     * @param itemStack         触发 tooltip 渲染的物品栈
     * @param rarity            稀有度等级（未标准化的原始值，可大于 7）
     * @param tooltipComponents 要插入 tooltip 的组件可变列表，监听器可向此列表追加组件
     */
    public RarityTooltipEvent(ItemStack itemStack, int rarity, List<Component> tooltipComponents) {
        this.itemStack = itemStack;
        this.rarity = rarity;
        this.tooltipComponents = tooltipComponents;
    }

    /**
     * @return 触发 tooltip 渲染的物品栈
     */
    public ItemStack getItemStack() {
        return itemStack;
    }

    /**
     * @return 稀有度等级（未标准化的原始值）
     */
    public int getRarity() {
        return rarity;
    }

    /**
     * 获取要插入 tooltip 的组件可变列表。
     * 监听器可以向此列表追加新组件，或修改/移除已有组件。
     *
     * @return 可变的组件列表
     */
    public List<Component> getTooltipComponents() {
        return tooltipComponents;
    }
}
