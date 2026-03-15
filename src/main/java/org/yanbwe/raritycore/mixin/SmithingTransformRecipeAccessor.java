package org.yanbwe.raritycore.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * SmithingTransformRecipe 访问器 Mixin
 * 用于获取锻造台配方的配料
 */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {
    
    /**
     * 获取模板配料
     */
    @Accessor("template")
    Ingredient getTemplate();
    
    /**
     * 获取基础配料
     */
    @Accessor("base")
    Ingredient getBase();
    
    /**
     * 获取升级配料
     */
    @Accessor("addition")
    Ingredient getAddition();
}
