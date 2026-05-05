package org.yanbwe.raritycore.event;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * 稀有度查询事件
 * <p>
 * 在 {@code RarityRegistry.getRarityInternal()} 确定稀有度结果后、
 * 返回结果前触发。监听器可以通过 {@link #setCanceled(boolean)} 取消默认查询结果，
 * 并通过 {@link #setOverriddenRarity(int)} 设置覆盖的稀有度等级。
 * </p>
 * <p>
 * 此事件继承 {@link Event} 并实现 {@link ICancellableEvent}，支持取消操作。
 * 使用模式：
 * <pre>{@code
 * RarityQueryEvent event = new RarityQueryEvent(stack, originalRarity, "source");
 * NeoForge.EVENT_BUS.post(event);
 * if (event.isCanceled()) {
 *     return event.getOverriddenRarity();
 * }
 * }</pre>
 * </p>
 */
public class RarityQueryEvent extends Event implements ICancellableEvent {

    private final ItemStack itemStack;
    private final int originalRarity;
    private int overriddenRarity;
    private final String source;

    /**
     * @param itemStack      被查询稀有度的物品栈
     * @param originalRarity 查询到的原始稀有度等级
     * @param source         查询来源标识（如 "component"、"itemdata"、"tag" 等）
     */
    public RarityQueryEvent(ItemStack itemStack, int originalRarity, String source) {
        this.itemStack = itemStack;
        this.originalRarity = originalRarity;
        this.overriddenRarity = originalRarity;
        this.source = source;
    }

    /**
     * @return 被查询稀有度的物品栈
     */
    public ItemStack getItemStack() {
        return itemStack;
    }

    /**
     * 获取原始查询到的稀有度等级（未经覆盖）。
     *
     * @return 原始稀有度等级
     */
    public int getOriginalRarity() {
        return originalRarity;
    }

    /**
     * 获取覆盖后的稀有度等级。
     *
     * @return 覆盖后的稀有度等级，默认为原始值
     */
    public int getOverriddenRarity() {
        return overriddenRarity;
    }

    /**
     * 设置覆盖的稀有度等级（通常在取消事件后调用）。
     *
     * @param overriddenRarity 覆盖后的稀有度等级
     */
    public void setOverriddenRarity(int overriddenRarity) {
        this.overriddenRarity = overriddenRarity;
    }

    /**
     * 获取查询来源标识。
     *
     * @return 查询来源字符串（如 "component"、"apotheosis"、"tag" 等）
     */
    public String getSource() {
        return source;
    }
}
