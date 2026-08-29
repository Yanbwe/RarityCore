package org.yanbwe.raritycore.compat.ftblibrary;

import net.minecraft.world.item.ItemStack;

/**
 * FTB Library ItemIcon 鸭子类型接口
 * ItemIcon 本身已有公开方法 getStack()，运行时通过接口强转直接调用，无需注入
 *
 * 注意：此接口必须放在 mixin 包之外——mixin 包内的类只能被 mixin 配置引用，
 * 若被注入到目标类的方法直接引用，运行时会抛出 IllegalClassLoadError
 */
public interface ItemIconAccessor {

    ItemStack getStack();
}
