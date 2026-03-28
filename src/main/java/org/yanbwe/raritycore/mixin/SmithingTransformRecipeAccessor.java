package org.yanbwe.raritycore.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {

    @Accessor("template")
    Optional<Ingredient> getTemplate();

    @Accessor("base")
    Ingredient getBase();

    @Accessor("addition")
    Optional<Ingredient> getAddition();
}
