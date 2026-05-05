package org.yanbwe.raritycore.calc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.mixin.SmithingTransformRecipeAccessor;

import java.util.*;

/**
 * 配方索引器 — 按需构建和维护配料→配方映射
 * 为 AutoRarityCalculator 提供惰性配方查询,避免启动时全量预建索引导致的高内存峰值
 */
public class RecipeIndexer {

    /** 缓存的配方管理器引用,供惰性查询使用 */
    private static RecipeManager recipeManager;

    /**
     * 初始化配方索引器(替代原先的 buildIngredientRecipeMap 全量预建)
     * 仅存储配方管理器引用,实际索引在首次查询时按需构建
     */
    static void init(RecipeManager rm) {
        recipeManager = rm;
    }

    /**
     * 惰性查找包含指定物品作为配料的所有配方
     * 通过 Ingredient.test() 匹配而非展开 getItems(),避免大量 ItemStack 数组分配
     *
     * @param itemId 目标物品的资源标识
     * @return 使用该物品的配方列表(无结果时返回空列表,非 null)
     */
    static List<Recipe<?>> findRecipesForItem(ResourceLocation itemId) {
        if (recipeManager == null) {
            return Collections.emptyList();
        }

        Item targetItem = BuiltInRegistries.ITEM.get(itemId);
        if (targetItem == null) {
            return Collections.emptyList();
        }

        List<Recipe<?>> result = new ArrayList<>();
        ItemStack testStack = new ItemStack(targetItem);

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!isSupportedRecipeType(recipe)) {
                continue;
            }

            // 特殊处理锻造台配方(使用 Mixin 访问器获取 template/base/addition)
            if (recipe instanceof SmithingTransformRecipe smithingRecipe) {
                if (smithingRecipeUsesItem(smithingRecipe, testStack)) {
                    result.add(recipe);
                }
                continue; // 已通过 Mixin 检查,跳过默认配料遍历
            }

            // 默认处理:遍历配方的所有配料,使用 test() 而非 getItems()
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient != null && !ingredient.isEmpty() && ingredient.test(testStack)) {
                    result.add(recipe);
                    break; // 找到匹配即跳出,无需检查其余配料
                }
            }
        }

        return result;
    }

    /**
     * 确保指定物品的配方已缓存到映射中(供 AutoRarityCalculator.tick 在调用
     * RarityRoundProcessor 前使用,使后者无需修改即可通过 .get() 获取结果)
     */
    static void ensureCached(ResourceLocation itemId,
                             Map<ResourceLocation, List<Recipe<?>>> targetMap) {
        targetMap.computeIfAbsent(itemId, RecipeIndexer::findRecipesForItem);
    }

    /**
     * 检查锻造台配方(SmithingTransformRecipe)的三个配料槽是否包含目标物品
     */
    private static boolean smithingRecipeUsesItem(SmithingTransformRecipe recipe, ItemStack testStack) {
        try {
            SmithingTransformRecipeAccessor accessor =
                (SmithingTransformRecipeAccessor) recipe;

            return ingredientMatches(accessor.getTemplate(), testStack)
                || ingredientMatches(accessor.getBase(), testStack)
                || ingredientMatches(accessor.getAddition(), testStack);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to check smithing recipe ingredients: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查单个 Ingredient 是否匹配目标物品
     */
    private static boolean ingredientMatches(Ingredient ingredient, ItemStack testStack) {
        return ingredient != null && !ingredient.isEmpty() && ingredient.test(testStack);
    }

    /**
     * 判断是否是支持的配方类型(与原有逻辑一致)
     */
    static boolean isSupportedRecipeType(Recipe<?> recipe) {
        // 支持工作台配方
        if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
            return true;
        }

        // 支持熔炉类配方(包括熔炉、smoker、blast furnace、campfire)
        if (recipe instanceof AbstractCookingRecipe) {
            return true;
        }

        // 支持锻造台升级配方(排除盔甲纹饰)
        if (recipe instanceof SmithingTransformRecipe) {
            return true;
        }

        // 检查是否是非标准的锻造台配方
        String recipeClassName = recipe.getClass().getName();
        if (recipeClassName.contains("Smithing")) {
            // 非标准锻造台配方,跳过
        }

        return false;
    }
}
