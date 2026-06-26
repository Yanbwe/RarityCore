package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * DataComponentWrapper - 包装 ItemStack 的数据组件信息
 * 完全基于 DataComponent API.
 */
public class DataComponentWrapper {
    private final ItemStack itemStack;
    private final int count;
    private final DataComponentMap components;

    public DataComponentWrapper(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.count = itemStack.getCount();
        this.components = itemStack.getComponents();
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public int getCount() {
        return count;
    }

    public DataComponentMap getComponents() {
        return components;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataComponentWrapper that = (DataComponentWrapper) o;
        return count == that.count &&
            Objects.equals(itemStack.getItem(), that.itemStack.getItem()) &&
            Objects.equals(components.toString(), that.components.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemStack.getItem(), count, components.toString());
    }
}