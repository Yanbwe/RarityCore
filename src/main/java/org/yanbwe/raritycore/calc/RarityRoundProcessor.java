package org.yanbwe.raritycore.calc;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.mixin.SmithingTransformRecipeAccessor;
import org.yanbwe.raritycore.registry.RarityRegistry;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 稀有度轮次处理器 — 执行逐轮次的稀有度传播计算
 * 从 AutoRarityCalculator 提取的核心计算逻辑,访问其共享状态
 */
public class RarityRoundProcessor {
    /** 处理单个配料物品:查找所有使用该配料的配方并计算产物稀有度 */
    static void processMaterial(Item material) {
        ResourceLocation materialId = BuiltInRegistries.ITEM.getKey(material);
        if (materialId == null) {
            return;
        }

        // 从缓存中查找包含该配料的所有配方
        List<Recipe<?>> recipes = AutoRarityCalculator.ingredientToRecipesMap.get(materialId);
        if (recipes == null || recipes.isEmpty()) {
            return;
        }

        for (Recipe<?> recipe : recipes) {
            try {
                // 获取所有配料的稀有度
                List<Integer> ingredientRarities = getIngredientRaritiesForNewAlgorithm(recipe);

                // 计算产物稀有度
                ItemStack result = recipe.getResultItem(RegistryAccess.EMPTY);
                if (result.isEmpty()) {
                    continue;
                }

                Item outputItem = result.getItem();
                ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(outputItem);

                if (outputId == null) {
                    continue;
                }

                // 跳过 A 类物品(已有手动配置)
                if (AutoRarityCalculator.isTypeA(outputId)) {
                    continue;
                }

                // 计算新的稀有度
                int outputRarity = calculateOutputRarityNew(recipe, ingredientRarities);

                // 保留最高稀有度,并记录首次出现的轮次
                updateRarityWithMax(outputItem, outputRarity);

            } catch (Exception e) {
                // 跳过此配方
            }
        }
    }

    /** 获取配料的稀有度列表(新算法,查注册表→缓存→原版) */
    static List<Integer> getIngredientRaritiesForNewAlgorithm(Recipe<?> recipe) {
        List<Integer> rarities = new ArrayList<>();

        // 特殊处理锻造台配方
        if (recipe instanceof SmithingTransformRecipe) {
            return handleSmithingRecipe(recipe);
        }

        // 检查是否是其他类型的锻造台配方
        String recipeClassName = recipe.getClass().getName();
        if (recipeClassName.contains("Smithing")) {
            // 非标准锻造台配方,跳过不处理
        }

        // 默认处理
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient.isEmpty()) {
                rarities.add(1);
                continue;
            }

            // 遍历配料的所有物品,取最低稀有度
            Integer minRarity = null;
            for (ItemStack stack : ingredient.getItems()) {
                Integer rarity = resolveItemRarity(stack.getItem(), stack);
                if (rarity != null) {
                    if (minRarity == null || rarity < minRarity) {
                        minRarity = rarity;
                    }
                }
            }

            if (minRarity == null) {
                minRarity = 1;
            }

            rarities.add(minRarity);
        }

        return rarities;
    }

    /** 处理锻造台配方(通过 Mixin 访问器获取三个配料) */
    private static List<Integer> handleSmithingRecipe(Recipe<?> recipe) {
        List<Integer> rarities = new ArrayList<>();

        try {
            SmithingTransformRecipeAccessor accessor =
                (SmithingTransformRecipeAccessor) recipe;

            Ingredient template = accessor.getTemplate();
            Ingredient base = accessor.getBase();
            Ingredient addition = accessor.getAddition();

            int templateRarity = processSmithingIngredient(template);
            int baseRarity = processSmithingIngredient(base);
            int additionRarity = processSmithingIngredient(addition);

            rarities.add(templateRarity);
            rarities.add(baseRarity);
            rarities.add(additionRarity);

            return rarities;

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 处理锻造台单个配料,返回稀有度或 1(普通物品) */
    private static int processSmithingIngredient(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return 1;
        }

        Integer minRarity = null;
        for (ItemStack stack : ingredient.getItems()) {
            Integer rarity = resolveItemRarity(stack.getItem(), stack);
            if (rarity != null) {
                if (minRarity == null || rarity < minRarity) {
                    minRarity = rarity;
                }
            }
        }

        return minRarity != null ? minRarity : 1;
    }

    /**
     * 解析物品稀有度：注册表 → maxRarityCache → currentRoundResults → 原版 getRarity()
     * @param item 目标物品
     * @param testStack 用于原版稀有度查询的 ItemStack（通常来自 Ingredient.getItems()）
     * @return 稀有度值 3-5（原版映射），null 表示无法解析
     */
    @Nullable
    private static Integer resolveItemRarity(Item item, ItemStack testStack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        Integer rarity = null;

        // 1. 查注册表
        if (itemId != null) {
            rarity = RarityRegistry.ITEM_RARITY_MAP.get(itemId);
        }

        // 2. 查缓存：先全局后当前轮
        if (rarity == null) {
            rarity = AutoRarityCalculator.maxRarityCache.get(item);
            if (rarity == null) {
                rarity = AutoRarityCalculator.currentRoundResults.get(item);
            }
        }

        // 3. 查原版稀有度
        if (rarity == null && ServerConfigManager.isCheckVanillaRarity()) {
            try {
                net.minecraft.world.item.Rarity vanillaRarity = testStack.getRarity();
                if (vanillaRarity == net.minecraft.world.item.Rarity.UNCOMMON) {
                    rarity = 3;
                } else if (vanillaRarity == net.minecraft.world.item.Rarity.RARE) {
                    rarity = 4;
                } else if (vanillaRarity == net.minecraft.world.item.Rarity.EPIC) {
                    rarity = 5;
                }
            } catch (Exception ignored) {
                // 原版 API 不可用时返回 null
            }
        }

        return rarity;
    }

    /** 根据配方类型和配料稀有度计算产物稀有度 */
    static int calculateOutputRarityNew(Recipe<?> recipe, List<Integer> ingredientRarities) {
        if (ingredientRarities.isEmpty()) {
            return 1;
        }

        List<Integer> allIngredientRarities = new ArrayList<>();
        for (Integer rarity : ingredientRarities) {
            if (rarity != null) {
                allIngredientRarities.add(rarity);
            }
        }

        if (allIngredientRarities.isEmpty()) {
            return 1;
        }

        // 检查是否全部相同
        boolean allSame = allIngredientRarities.stream().distinct().count() == 1;

        if (!allSame) {
            // 情况 1: 不完全相同 → 继承最高
            return Collections.max(allIngredientRarities);
        }

        // 全部相同,检查有效稀有度(排除 1)
        List<Integer> validRarities = new ArrayList<>();
        for (Integer rarity : allIngredientRarities) {
            if (rarity > 1) {
                validRarities.add(rarity);
            }
        }

        if (validRarities.isEmpty()) {
            return 1;
        }

        int baseRarity = validRarities.get(0);

        if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
            // 工作台配方:检查数量比例
            int ingredientCount = recipe.getIngredients().size();
            int outputCount = recipe.getResultItem(RegistryAccess.EMPTY).getCount();

            if (outputCount > 0 && (double) ingredientCount / outputCount >= 2.0) {
                return baseRarity + 1;
            } else {
                return baseRarity;
            }
        } else if (recipe instanceof AbstractCookingRecipe) {
            // 烧制配方 → 继承
            return baseRarity;
        } else {
            // 其他配方(锻造台等)→ +1
            return baseRarity + 1;
        }
    }

    /** 更新稀有度(保留最高值,记录首次出现轮次用于下一轮传播) */
    static void updateRarityWithMax(Item outputItem, int newRarity) {
        Integer existingRarity = AutoRarityCalculator.maxRarityCache.get(outputItem);

        if (existingRarity == null || newRarity > existingRarity) {
            AutoRarityCalculator.maxRarityCache.put(outputItem, newRarity);

            boolean isFirstTime = !AutoRarityCalculator.itemFirstRoundMap.containsKey(outputItem);
            if (isFirstTime) {
                AutoRarityCalculator.itemFirstRoundMap.put(outputItem, AutoRarityCalculator.currentRound);
            }

            int firstRound = AutoRarityCalculator.itemFirstRoundMap.get(outputItem);
            Map<Item, Integer> roundMap = AutoRarityCalculator.allRoundResults
                .computeIfAbsent(firstRound, k -> new HashMap<>());
            roundMap.put(outputItem, newRarity);

            // 只有首次计算的物品才加入当前轮结果(用于下一轮传播)
            if (isFirstTime) {
                AutoRarityCalculator.currentRoundResults.put(outputItem, newRarity);
            }
        }
    }
}
