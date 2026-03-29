package org.yanbwe.raritycore.calc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.service.ConfigReloadService;
import org.yanbwe.raritycore.mixin.ShapedRecipeAccessor;
import org.yanbwe.raritycore.mixin.ShapelessRecipeAccessor;
import org.yanbwe.raritycore.mixin.SingleItemRecipeAccessor;
import org.yanbwe.raritycore.mixin.SmithingTransformRecipeAccessor;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.*;

@SuppressWarnings("mixin")
public class AutoRarityCalculator {
    private static final int ITEMS_PER_TICK = 20;

    private static boolean isCalculating = false;
    private static int currentRound = 0;
    private static List<Item> pendingItemList = new ArrayList<>();
    private static Map<Integer, Map<Item, Integer>> allRoundResults = new HashMap<>();
    private static Map<Item, Integer> currentRoundResults = new HashMap<>();
    private static Map<Item, Integer> itemFirstRoundMap = new HashMap<>();
    private static Map<Item, Integer> maxRarityCache = new HashMap<>();
    private static Map<Identifier, List<Recipe<?>>> ingredientToRecipesMap = new HashMap<>();

    private static int totalItemsInRound = 0;
    private static int processedItemsInRound = 0;
    private static long lastProgressUpdateTime = 0;

    public static void startAutoCalculation() {
        if (isCalculating) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        isCalculating = true;
        currentRound = 1;

        pendingItemList = new ArrayList<>();
        allRoundResults = new HashMap<>();
        currentRoundResults = new HashMap<>();
        itemFirstRoundMap = new HashMap<>();
        maxRarityCache = new HashMap<>();
        ingredientToRecipesMap = new HashMap<>();

        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_starting")
                .withStyle(ChatFormatting.YELLOW));

        buildIngredientRecipeMap(server.getRecipeManager());
        initFirstRound();
    }

    private static void initFirstRound() {
        for (Identifier itemId : RarityRegistry.ITEM_RARITY_MAP.keySet()) {
            Item item = BuiltInRegistries.ITEM.get(itemId)
                    .map(holder -> holder.value())
                    .orElse(null);
            if (item != null) {
                pendingItemList.add(item);
            }
        }

        RarityCore.LOGGER.info("Round 1 initialized with {} items from manual config", pendingItemList.size());
        startProcessingRound();
    }

    private static void buildIngredientRecipeMap(RecipeManager recipeManager) {
        ingredientToRecipesMap.clear();

        int smithingCount = 0;

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!isSupportedRecipeType(recipe)) {
                continue;
            }

            if (recipe instanceof SmithingTransformRecipe smithingRecipe) {
                try {
                    SmithingTransformRecipeAccessor accessor =
                            (SmithingTransformRecipeAccessor) smithingRecipe;

                    Optional<Ingredient> optTemplate = accessor.getTemplate();
                    Ingredient base = accessor.getBase();
                    Optional<Ingredient> optAddition = accessor.getAddition();

                    optTemplate.ifPresent(template -> addIngredientToMap(template, recipe));
                    addIngredientToMap(base, recipe);
                    optAddition.ifPresent(addition -> addIngredientToMap(addition, recipe));

                    smithingCount++;

                } catch (Exception e) {
                    RarityCore.LOGGER.error("Failed to get ingredients from smithing recipe {}: {}", holder.id(), e.getMessage());
                }
            }

            for (Optional<Ingredient> optIngredient : getRecipeIngredients(recipe)) {
                if (optIngredient.isPresent()) {
                    Ingredient ingredient = optIngredient.get();
                    if (!ingredient.isEmpty()) {
                        for (Item item : ingredient.items().map(Holder::value).toList()) {
                            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
                            if (itemId != null) {
                                ingredientToRecipesMap.computeIfAbsent(itemId, k -> new ArrayList<>()).add(recipe);
                            }
                        }
                    }
                }
            }
        }

        RarityCore.LOGGER.info("Built ingredient recipe map: {} entries, smithingCount={}", ingredientToRecipesMap.size(), smithingCount);
    }

    private static List<Optional<Ingredient>> getRecipeIngredients(Recipe<?> recipe) {
        try {
            if (recipe instanceof ShapedRecipe shapedRecipe) {
                return shapedRecipe.getIngredients();
            } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                return ((ShapelessRecipeAccessor) shapelessRecipe).getIngredients().stream().map(Optional::of).toList();
            } else if (recipe instanceof SingleItemRecipe singleItemRecipe) {
                return List.of(Optional.of(singleItemRecipe.input()));
            } else if (recipe instanceof SmithingTransformRecipe smithingRecipe) {
                SmithingTransformRecipeAccessor accessor =
                        (SmithingTransformRecipeAccessor) smithingRecipe;
                List<Optional<Ingredient>> result = new ArrayList<>();
                accessor.getTemplate().ifPresent(ing -> result.add(Optional.of(ing)));
                result.add(Optional.of(accessor.getBase()));
                accessor.getAddition().ifPresent(ing -> result.add(Optional.of(ing)));
                return result;
            }
            return List.of();
        } catch (Exception e) {
            RarityCore.LOGGER.error("getRecipeIngredients failed for {}: {}", recipe.getClass().getName(), e.getMessage());
            return List.of();
        }
    }

    private static boolean isSupportedRecipeType(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
            return true;
        }
        if (recipe instanceof AbstractCookingRecipe) {
            return true;
        }
        if (recipe instanceof SmithingTransformRecipe) {
            return true;
        }
        return false;
    }

    private static void addIngredientToMap(Ingredient ingredient, Recipe<?> recipe) {
        if (ingredient == null || ingredient.isEmpty()) {
            return;
        }
        for (Item item : ingredient.items().map(Holder::value).toList()) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null) {
                ingredientToRecipesMap.computeIfAbsent(itemId, k -> new ArrayList<>()).add(recipe);
            }
        }
    }

    private static void startProcessingRound() {
        if (pendingItemList.isEmpty()) {
            finishCalculation();
            return;
        }

        currentRoundResults = new HashMap<>();
        totalItemsInRound = pendingItemList.size();
        processedItemsInRound = 0;
        lastProgressUpdateTime = System.currentTimeMillis();

        RarityCore.LOGGER.info("Starting round {}: processing {} items", currentRound, pendingItemList.size());

        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_round_start",
                currentRound, pendingItemList.size())
                .withStyle(ChatFormatting.YELLOW));
    }

    public static void tick() {
        if (!isCalculating) {
            return;
        }

        if (pendingItemList.isEmpty()) {
            nextRound();
            return;
        }

        int processedInThisTick = 0;
        while (processedInThisTick < ITEMS_PER_TICK && !pendingItemList.isEmpty()) {
            Item material = pendingItemList.remove(0);
            processMaterial(material);
            processedInThisTick++;
            processedItemsInRound++;
        }

        long currentTime = System.currentTimeMillis();
        if (totalItemsInRound > 0 && (currentTime - lastProgressUpdateTime) >= 1000) {
            int progressPercent = (int) ((processedItemsInRound * 100.0) / totalItemsInRound);
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_progress",
                    currentRound, processedItemsInRound, totalItemsInRound, progressPercent)
                    .withStyle(ChatFormatting.YELLOW));
            lastProgressUpdateTime = currentTime;
        }

        if (pendingItemList.isEmpty()) {
            nextRound();
        }
    }

    private static void processMaterial(Item material) {
        Identifier materialId = BuiltInRegistries.ITEM.getKey(material);
        if (materialId == null) {
            return;
        }

        List<Recipe<?>> recipes = ingredientToRecipesMap.get(materialId);
        if (recipes == null || recipes.isEmpty()) {
            return;
        }

        for (Recipe<?> recipe : recipes) {
            try {
                List<Integer> ingredientRarities = getIngredientRaritiesForNewAlgorithm(recipe);

                ItemStack result = getRecipeResult(recipe);
                if (result.isEmpty()) {
                    continue;
                }

                Item outputItem = result.getItem();
                Identifier outputId = BuiltInRegistries.ITEM.getKey(outputItem);

                if (outputId == null) {
                    continue;
                }

                if (isTypeA(outputId)) {
                    continue;
                }

                int outputRarity = calculateOutputRarityNew(recipe, ingredientRarities);

                updateRarityWithMax(outputItem, outputRarity);

            } catch (Exception e) {
            }
        }
    }

    private static boolean isTypeA(Identifier itemId) {
        return RarityRegistry.ITEM_RARITY_MAP.containsKey(itemId);
    }

    private static List<Integer> getIngredientRaritiesForNewAlgorithm(Recipe<?> recipe) {
        List<Integer> rarities = new ArrayList<>();

        if (recipe instanceof SmithingTransformRecipe) {
            return handleSmithingRecipe(recipe);
        }

        String recipeClassName = recipe.getClass().getName();
        if (recipeClassName.contains("Smithing")) {
        }

        for (Optional<Ingredient> optIngredient : getRecipeIngredients(recipe)) {
            if (!optIngredient.isPresent()) {
                rarities.add(1);
                continue;
            }
            Ingredient ingredient = optIngredient.get();
            if (ingredient == null || ingredient.isEmpty()) {
                rarities.add(1);
                continue;
            }

            Integer minRarity = null;
            for (Item item : ingredient.items().map(Holder::value).toList()) {
                ItemStack stack = new ItemStack(item);

                Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
                Integer rarity = null;
                if (itemId != null) {
                    rarity = RarityRegistry.ITEM_RARITY_MAP.get(itemId);
                }

                if (rarity == null) {
                    rarity = maxRarityCache.get(item);
                    if (rarity == null) {
                        rarity = currentRoundResults.get(item);
                    }
                }

                if (rarity == null && ServerConfigManager.isCheckVanillaRarity()) {
                    try {
                        net.minecraft.world.item.Rarity vanillaRarity = stack.getRarity();
                        if (vanillaRarity == net.minecraft.world.item.Rarity.UNCOMMON) {
                            rarity = 3;
                        } else if (vanillaRarity == net.minecraft.world.item.Rarity.RARE) {
                            rarity = 4;
                        } else if (vanillaRarity == net.minecraft.world.item.Rarity.EPIC) {
                            rarity = 5;
                        }
                    } catch (Throwable e) {
                    }
                }

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

    private static List<Integer> handleSmithingRecipe(Recipe<?> recipe) {
        List<Integer> rarities = new ArrayList<>();

        try {
            SmithingTransformRecipeAccessor accessor =
                    (SmithingTransformRecipeAccessor) recipe;

            Optional<Ingredient> optTemplate = accessor.getTemplate();
            Ingredient base = accessor.getBase();
            Optional<Ingredient> optAddition = accessor.getAddition();

            int templateRarity = optTemplate.map(ingredient -> processSmithingIngredient(ingredient)).orElse(1);
            int baseRarity = processSmithingIngredient(base);
            int additionRarity = optAddition.map(ingredient -> processSmithingIngredient(ingredient)).orElse(1);

            rarities.add(templateRarity);
            rarities.add(baseRarity);
            rarities.add(additionRarity);

            return rarities;

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static int processSmithingIngredient(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return 1;
        }

        Integer minRarity = null;
        for (Item item : ingredient.items().map(Holder::value).toList()) {
            ItemStack stack = new ItemStack(item);

            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            Integer rarity = null;
            if (itemId != null) {
                rarity = RarityRegistry.ITEM_RARITY_MAP.get(itemId);
            }

            if (rarity == null) {
                rarity = maxRarityCache.get(item);
                if (rarity == null) {
                    rarity = currentRoundResults.get(item);
                }
            }

            if (rarity != null) {
                if (minRarity == null || rarity < minRarity) {
                    minRarity = rarity;
                }
            }
        }

        return minRarity != null ? minRarity : 1;
    }

    private static int calculateOutputRarityNew(Recipe<?> recipe, List<Integer> ingredientRarities) {
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

        boolean allSame = allIngredientRarities.stream().distinct().count() == 1;

        if (!allSame) {
            return Collections.max(allIngredientRarities);
        }

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
            int ingredientCount = getRecipeIngredients(recipe).size();
            int outputCount = getRecipeResult(recipe).getCount();

            if (outputCount > 0 && (double) ingredientCount / outputCount >= 2.0) {
                return baseRarity + 1;
            } else {
                return baseRarity;
            }
        } else if (recipe instanceof AbstractCookingRecipe) {
            return baseRarity;
        } else {
            return baseRarity + 1;
        }
    }

    private static ItemStack getRecipeResult(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            return ((ShapedRecipeAccessor) shapedRecipe).getResult();
        } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            return ((ShapelessRecipeAccessor) shapelessRecipe).getResult();
        } else if (recipe instanceof SingleItemRecipe singleItemRecipe) {
            return ((SingleItemRecipeAccessor) singleItemRecipe).getResult();
        } else if (recipe instanceof SmithingTransformRecipe smithingRecipe) {
            return getSmithingResult(smithingRecipe);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack getSmithingResult(SmithingTransformRecipe smithingRecipe) {
        try {
            TransmuteResult transmuteResult = ((SmithingTransformRecipeAccessor) smithingRecipe).getResult();
            Holder<Item> itemHolder = transmuteResult.item();
            int count = transmuteResult.count();
            Item item = itemHolder.value();
            return new ItemStack(item, count);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to get smithing result: {}", e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    private static void updateRarityWithMax(Item outputItem, int newRarity) {
        Integer existingRarity = maxRarityCache.get(outputItem);

        if (existingRarity == null || newRarity > existingRarity) {
            maxRarityCache.put(outputItem, newRarity);

            boolean isFirstTime = !itemFirstRoundMap.containsKey(outputItem);
            if (isFirstTime) {
                itemFirstRoundMap.put(outputItem, currentRound);
            }

            int firstRound = itemFirstRoundMap.get(outputItem);
            Map<Item, Integer> roundMap = allRoundResults.computeIfAbsent(firstRound, k -> new HashMap<>());
            roundMap.put(outputItem, newRarity);

            if (isFirstTime) {
                currentRoundResults.put(outputItem, newRarity);
            }
        }
    }

    private static void nextRound() {
        if (!currentRoundResults.isEmpty()) {
            allRoundResults.put(currentRound, new HashMap<>(currentRoundResults));
        }

        if (currentRoundResults.isEmpty()) {
            finishCalculation();
            return;
        }

        currentRound++;
        pendingItemList.clear();
        pendingItemList.addAll(currentRoundResults.keySet());

        RarityCore.LOGGER.info("Round {} complete, {} new items calculated. Starting round {}",
                currentRound - 1, pendingItemList.size(), currentRound);

        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_round_complete",
                currentRound - 1)
                .withStyle(ChatFormatting.GREEN));

        startProcessingRound();
    }

    private static void finishCalculation() {
        isCalculating = false;

        Map<Item, Integer> finalResults = mergeAllRoundResults();

        RarityCore.LOGGER.info("Auto rarity calculation completed: {} items calculated across {} rounds",
                finalResults.size(), currentRound);

        if (!finalResults.isEmpty()) {
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_complete", finalResults.size())
                    .withStyle(ChatFormatting.YELLOW));

            RarityCore.LOGGER.info("Writing auto configs...");
            writeAutoConfigs(finalResults);

            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_will_auto_reload")
                    .withStyle(ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD));

            scheduleAutoReload();

        } else {
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_no_items_calculated")
                    .withStyle(ChatFormatting.YELLOW));
            RarityCore.LOGGER.warn("No items were calculated during auto rarity calculation");
        }
    }

    private static Map<Item, Integer> mergeAllRoundResults() {
        Map<Item, Integer> merged = new HashMap<>();

        for (Map.Entry<Integer, Map<Item, Integer>> roundEntry : allRoundResults.entrySet()) {
            Map<Item, Integer> roundMap = roundEntry.getValue();

            for (Map.Entry<Item, Integer> entry : roundMap.entrySet()) {
                Item item = entry.getKey();
                int rarity = entry.getValue();

                Integer existing = merged.get(item);
                if (existing == null || rarity > existing) {
                    merged.put(item, rarity);
                }
            }
        }

        RarityCore.LOGGER.info("Merged results from {} rounds: {} unique items",
                allRoundResults.size(), merged.size());

        return merged;
    }

    private static void writeAutoConfigs(Map<Item, Integer> finalResults) {
        if (finalResults.isEmpty()) {
            RarityCore.LOGGER.warn("No data to write, skipping auto config write");
            return;
        }

        AutoRarityConfigManager.cleanupAutoNbtFiles();
        AutoRarityConfigManager.writeAutoRarityJson(finalResults);

        RarityCore.LOGGER.info("Config write completed: {} items written to auto_rarity.json",
                finalResults.size());
    }

    private static void scheduleAutoReload() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            try {
                ConfigReloadService.reloadAllConfigs(null, false);
                sendToAllPlayers(Component.translatable("rarity.core.auto_reload_complete_message")
                        .withStyle(ChatFormatting.GREEN));
                RarityCore.LOGGER.info("Auto reload completed after calculation");
            } catch (Exception e) {
                RarityCore.LOGGER.error("Auto reload failed", e);
                sendToAllPlayers(Component.translatable("rarity.core.config_reload_failed", e.getMessage())
                        .withStyle(ChatFormatting.RED));
            }
        });
    }

    private static void sendToAllPlayers(Component message) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    public static boolean isCalculating() {
        return isCalculating;
    }

    public static int getCurrentRound() {
        return currentRound;
    }

    public static int getPendingItemCount() {
        return pendingItemList.size();
    }

    public static void forceRecalculate() {
        if (isCalculating) {
            return;
        }

        pendingItemList = new ArrayList<>();
        allRoundResults = new HashMap<>();
        currentRoundResults = new HashMap<>();
        itemFirstRoundMap = new HashMap<>();
        maxRarityCache = new HashMap<>();
        ingredientToRecipesMap = new HashMap<>();

        startAutoCalculation();
    }
}
