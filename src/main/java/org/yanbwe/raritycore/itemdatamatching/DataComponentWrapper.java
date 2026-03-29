package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public class DataComponentWrapper {
    private final ItemStack itemStack;
    private final int count;
    private final String componentsString;

    public DataComponentWrapper(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.count = itemStack.getCount();
        this.componentsString = itemStack.getComponentsPatch() != null ?
            itemStack.getComponentsPatch().toString() : "";
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public int getCount() {
        return count;
    }

    public String getComponentsString() {
        return componentsString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataComponentWrapper that = (DataComponentWrapper) o;
        return count == that.count &&
            Objects.equals(itemStack.getItem(), that.itemStack.getItem()) &&
            Objects.equals(componentsString, that.componentsString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemStack.getItem(), count, componentsString);
    }
}