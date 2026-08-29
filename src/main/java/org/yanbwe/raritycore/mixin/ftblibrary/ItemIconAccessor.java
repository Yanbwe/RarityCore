package org.yanbwe.raritycore.mixin.ftblibrary;

import net.minecraft.world.item.ItemStack;

/**
 * FTB Library ItemIcon 鸭子类型接口
 * ItemIcon 本身已有公开方法 getStack()，运行时通过接口强转直接调用，无需注入
 */
public interface ItemIconAccessor {

    ItemStack getStack();
}
