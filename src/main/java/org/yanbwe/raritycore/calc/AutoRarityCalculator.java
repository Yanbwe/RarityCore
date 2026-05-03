 package org.yanbwe.raritycore.calc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.*;

/**
 * 自动稀有度计算器
 * 通过合成表反向推导产物的稀有度
 */
public class AutoRarityCalculator {
    
    private static final int ITEMS_PER_TICK = 20; // 每 tick 处理的物品个数
    
    // 计算状态
    private static boolean isCalculating = false;
    
    /**
     * 判断物品是否为 A 类物品(已有配置的稀有度)
     * @param itemId 物品 ID
     * @return 如果是 A 类返回 true
     */
    private static boolean isTypeA(ResourceLocation itemId) {
        return RarityRegistry.ITEM_RARITY_MAP.containsKey(itemId);
    }
    
    /**
     * 判断物品是否已计算过(C 类物品)
     * @param item 物品对象
     * @return 如果已计算返回 true
     */
    private static boolean isTypeC(Item item) {
        return maxRarityCache.containsKey(item);
    }
    

    
    // 轮次计数器
    private static int currentRound = 0;
    
    // C 列表:待处理物品队列(ArrayDeque: O(1) poll, 替代 ArrayList 的 O(n) remove(0))
    private static ArrayDeque<Item> pendingItemList = new ArrayDeque<>();
    
    // 所有轮次的结果 Map(E1, E2, E3...)
    private static Map<Integer, Map<Item, Integer>> allRoundResults = new HashMap<>();
    
    // 当前轮次的结果 Map(En)
    private static Map<Item, Integer> currentRoundResults = new HashMap<>();
    
    // 物品首次出现的轮次(用于定位更新哪个 E)
    private static Map<Item, Integer> itemFirstRoundMap = new HashMap<>();
    
    // 全局最高稀有度缓存(用于快速比较)
    private static Map<Item, Integer> maxRarityCache = new HashMap<>();
    
    // 预构建的配料→配方映射(性能优化)
    private static Map<ResourceLocation, List<Recipe<?>>> ingredientToRecipesMap = new HashMap<>();
    

    
    // 进度跟踪
    private static int totalItemsInRound = 0; // 本轮总物品数
    private static int processedItemsInRound = 0; // 本轮已处理物品数
    private static long lastProgressUpdateTime = 0; // 上次进度更新时间(毫秒)
    
    // 自动重载调度器（存储引用以避免线程池泄漏，确保在任务完成后调用 shutdown 回收资源）
    private static java.util.concurrent.ScheduledExecutorService autoReloadExecutor;
    
    /**
     * 计算任务单元(保留用于兼容性)
     */
    public static class CalculationTask {
        public final Recipe<?> recipe;
        public final Item materialItem; // 当前处理的配料物品
        
        public CalculationTask(Recipe<?> recipe, Item materialItem) {
            this.recipe = recipe;
            this.materialItem = materialItem;
        }
    }
    
    /**
     * 开始自动计算(在世界启动后调用)
     */
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
        
        // 初始化数据结构
        pendingItemList = new ArrayDeque<>();
        allRoundResults = new HashMap<>();
        currentRoundResults = new HashMap<>();
        itemFirstRoundMap = new HashMap<>();
        maxRarityCache = new HashMap<>();
        ingredientToRecipesMap = new HashMap<>();
        
        // 发送世界消息
        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_starting")
            .withStyle(net.minecraft.ChatFormatting.YELLOW));
        
        // 预构建配料→配方映射
        buildIngredientRecipeMap(server.getRecipeManager());
        
        // 第一轮:将所有 A 类物品加入 C 列表
        initFirstRound();
    }
    
    /**
     * 第一轮初始化:将所有 A 类物品加入 C 列表
     */
    private static void initFirstRound() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        
        // 将所有已有配置的 A 类物品加入待处理列表
        for (ResourceLocation itemId : RarityRegistry.ITEM_RARITY_MAP.keySet()) {
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null) {
                pendingItemList.add(item);
            }
        }
        
        RarityCore.LOGGER.info("Round 1 initialized with {} items from manual config", pendingItemList.size());
        
        // 开始处理
        startProcessingRound();
    }
    
    /**
     * 预构建配料→配方映射(性能优化)
     */
    private static void buildIngredientRecipeMap(RecipeManager recipeManager) {
        ingredientToRecipesMap.clear();
        
        int smithingCount = 0;
        
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            if (!isSupportedRecipeType(recipe)) {
                continue;
            }
            
            // 特殊处理锻造台配方(使用 Mixin 获取配料)
            if (recipe instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe smithingRecipe) {
                try {
                    // 使用 Mixin 访问器获取配料
                    org.yanbwe.raritycore.mixin.SmithingTransformRecipeAccessor accessor = 
                        (org.yanbwe.raritycore.mixin.SmithingTransformRecipeAccessor) smithingRecipe;
                    
                    Ingredient template = accessor.getTemplate();
                    Ingredient base = accessor.getBase();
                    Ingredient addition = accessor.getAddition();
                    
                    // 将三个配料加入映射
                    addIngredientToMap(template, recipe);
                    addIngredientToMap(base, recipe);
                    addIngredientToMap(addition, recipe);
                    
                    smithingCount++;
                    
                } catch (Exception e) {
                    RarityCore.LOGGER.error("Failed to get ingredients from smithing recipe {}: {}", recipe.getId(), e.getMessage());
                }
            }
            
            // 默认处理
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient != null && !ingredient.isEmpty()) {
                    for (ItemStack stack : ingredient.getItems()) {
                        Item item = stack.getItem();
                        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                        if (itemId != null) {
                            ingredientToRecipesMap.computeIfAbsent(itemId, k -> new ArrayList<>()).add(recipe);
                        }
                    }
                }
            }
        }
        
        RarityCore.LOGGER.info("Built ingredient-to-recipe map: {} ingredients mapped to recipes ({} smithing recipes)", 
            ingredientToRecipesMap.size(), smithingCount);
    }
    
    /**
     * 将单个配料加入映射
     */
    private static void addIngredientToMap(Ingredient ingredient, Recipe<?> recipe) {
        if (ingredient == null || ingredient.isEmpty()) {
            return;
        }
        
        for (ItemStack stack : ingredient.getItems()) {
            Item item = stack.getItem();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null) {
                ingredientToRecipesMap.computeIfAbsent(itemId, k -> new ArrayList<>()).add(recipe);
            }
        }
    }
    
    /**
     * 开始处理当前轮次
     */
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
        
        // 发送聊天栏消息:开始处理回合,包含待处理物品数量
        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_round_start", 
                currentRound, pendingItemList.size())
            .withStyle(net.minecraft.ChatFormatting.YELLOW));
    }
    
    /**
     * Tick 更新(每 tick 调用)
     */
    public static void tick() {
        if (!isCalculating || pendingItemList.isEmpty()) {
            return;
        }
        
        // 每 tick 处理最多 20 个物品
        int processedInThisTick = 0;
        while (processedInThisTick < ITEMS_PER_TICK && !pendingItemList.isEmpty()) {
            // 从 C 列表取出一个物品进行处理
            Item material = pendingItemList.pollFirst();
            processMaterial(material);
            processedInThisTick++;
            processedItemsInRound++;
        }
        
        // 每秒发送一次进度消息
        long currentTime = System.currentTimeMillis();
        if (totalItemsInRound > 0 && (currentTime - lastProgressUpdateTime) >= 1000) {
            int progressPercent = (int)((processedItemsInRound * 100.0) / totalItemsInRound);
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_progress", 
                    currentRound, processedItemsInRound, totalItemsInRound, progressPercent)
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
            lastProgressUpdateTime = currentTime;
        }
        
        // 检查本轮是否完成
        if (pendingItemList.isEmpty()) {
            nextRound();
        }
    }
    
    /**
     * 处理单个配料物品
     */
    private static void processMaterial(Item material) {
        ResourceLocation materialId = ForgeRegistries.ITEMS.getKey(material);
        if (materialId == null) {
            return;
        }
        
        // 从缓存中查找包含该配料的所有配方
        List<Recipe<?>> recipes = ingredientToRecipesMap.get(materialId);
        if (recipes == null || recipes.isEmpty()) {
            return; // 没有使用该配料的配方,跳过
        }
        
        for (Recipe<?> recipe : recipes) {
            try {
                // 获取所有配料的稀有度
                List<Integer> ingredientRarities = getIngredientRaritiesForNewAlgorithm(recipe);
                
                // 计算产物稀有度
                ItemStack result = recipe.getResultItem(net.minecraft.core.RegistryAccess.EMPTY);
                if (result.isEmpty()) {
                    continue;
                }
                
                Item outputItem = result.getItem();
                ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(outputItem);
                
                if (outputId == null) {
                    continue;
                }
                
                // 跳过 A 类物品(已有手动配置)
                if (isTypeA(outputId)) {
                    continue;
                }
                
                // 计算新的稀有度
                int outputRarity = calculateOutputRarityNew(recipe, ingredientRarities);
                
                // 保留最高稀有度,并记录首次出现的轮次
                updateRarityWithMax(outputItem, outputRarity);
                
            } catch (Exception e) {
                RarityCore.LOGGER.debug("Skipping recipe {} for material {}: {} - {}",
                    recipe.getId(), materialId, e.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * 获取配料的稀有度列表(新算法版本)
     * @return 如果所有配料都有稀有度则返回列表,否则返回空列表
     */
    private static List<Integer> getIngredientRaritiesForNewAlgorithm(Recipe<?> recipe) {
        List<Integer> rarities = new ArrayList<>();
        
        // 特殊处理锻造台配方
        if (recipe instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe) {
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
                rarities.add(1); // 空配料视为 1
                continue;
            }
            
            // 遍历配料的所有物品,取最低稀有度
            Integer minRarity = null;
            for (ItemStack stack : ingredient.getItems()) {
                Item item = stack.getItem();
                
                // 先查注册表(数据包、FinalRarity.json、FinalRarityConfig文件夹)
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                Integer rarity = null;
                if (itemId != null) {
                    rarity = RarityRegistry.ITEM_RARITY_MAP.get(itemId);
                }
                
                // 如果注册表没有,再查之前轮次计算的稀有度(E1, E2...)
                if (rarity == null) {
                    // 优先查全局缓存(包括之前轮次计算的物品)
                    rarity = maxRarityCache.get(item);
                    if (rarity == null) {
                        // 再查本轮已计算的物品
                        rarity = currentRoundResults.get(item);
                    }
                }
                
                // 如果还没有,最后检查原版稀有度
                if (rarity == null && org.yanbwe.raritycore.config.ServerConfigManager.isCheckVanillaRarity()) {
                    try {
                        net.minecraft.world.item.Rarity vanillaRarity = stack.getRarity();
                        if (vanillaRarity == net.minecraft.world.item.Rarity.UNCOMMON) {
                            rarity = 3; // 罕见
                        } else if (vanillaRarity == net.minecraft.world.item.Rarity.RARE) {
                            rarity = 4; // 史诗
                        } else if (vanillaRarity == net.minecraft.world.item.Rarity.EPIC) {
                            rarity = 5; // 传说
                        }
                    } catch (Throwable e) {
                        // 忽略异常,继续返回 null
                    }
                }
                
                if (rarity != null) {
                    // 取最低稀有度
                    if (minRarity == null || rarity < minRarity) {
                        minRarity = rarity;
                    }
                }
            }
            
            // 如果找不到稀有度,视为 1(普通物品)
            if (minRarity == null) {
                minRarity = 1;
            }
            
            rarities.add(minRarity);
        }
        
        return rarities;
    }
    
    /**
     * 处理锻造台配方(使用 Mixin 获取配料)
     * @return 如果所有配料都有稀有度则返回列表,否则返回空列表
     */
    private static List<Integer> handleSmithingRecipe(Recipe<?> recipe) {
        List<Integer> rarities = new ArrayList<>();
        
        try {
            // 使用 Mixin 访问器获取配料
            org.yanbwe.raritycore.mixin.SmithingTransformRecipeAccessor accessor = 
                (org.yanbwe.raritycore.mixin.SmithingTransformRecipeAccessor) recipe;
            
            Ingredient template = accessor.getTemplate();
            Ingredient base = accessor.getBase();
            Ingredient addition = accessor.getAddition();
            
            // 处理三个配料(如果找不到稀有度则视为 1)
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
    
    /**
     * 处理锻造台单个配料
     * @return 如果找到稀有度返回该值,否则返回 1(普通物品)
     */
    private static int processSmithingIngredient(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return 1; // 空配料视为 1
        }
        
        // 遍历配料的所有物品,取最低稀有度
        Integer minRarity = null;
        for (ItemStack stack : ingredient.getItems()) {
            Item item = stack.getItem();
            
            // 先查注册表(数据包、FinalRarity.json、FinalRarityConfig文件夹)
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            Integer rarity = null;
            if (itemId != null) {
                rarity = RarityRegistry.ITEM_RARITY_MAP.get(itemId);
            }
            
            // 如果注册表没有,再查之前轮次计算的稀有度(E1, E2...)
            if (rarity == null) {
                // 优先查全局缓存(包括之前轮次计算的物品)
                rarity = maxRarityCache.get(item);
                if (rarity == null) {
                    // 再查本轮已计算的物品
                    rarity = currentRoundResults.get(item);
                }
            }
            
            // 如果还没有,最后检查原版稀有度
            if (rarity == null && org.yanbwe.raritycore.config.ServerConfigManager.isCheckVanillaRarity()) {
                try {
                    net.minecraft.world.item.Rarity vanillaRarity = stack.getRarity();
                    if (vanillaRarity == net.minecraft.world.item.Rarity.UNCOMMON) {
                        rarity = 3; // 罕见
                    } else if (vanillaRarity == net.minecraft.world.item.Rarity.RARE) {
                        rarity = 4; // 史诗
                    } else if (vanillaRarity == net.minecraft.world.item.Rarity.EPIC) {
                        rarity = 5; // 传说
                    }
                } catch (Throwable e) {
                    // 忽略异常
                }
            }
            
            if (rarity != null) {
                // 取最低稀有度
                if (minRarity == null || rarity < minRarity) {
                    minRarity = rarity;
                }
            }
        }
        
        // 没找到稀有度,返回 1(普通物品)
        return minRarity != null ? minRarity : 1;
    }
    
    /**
     * 新的稀有度计算公式
     */
    private static int calculateOutputRarityNew(Recipe<?> recipe, List<Integer> ingredientRarities) {
        if (ingredientRarities.isEmpty()) {
            return 1;
        }
        
        // 不直接过滤,而是先收集所有配料的稀有度(包括 1)
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
            if (rarity > 1) {  // 只统计大于 1 的稀有度
                validRarities.add(rarity);
            }
        }
        
        // 如果所有配料都是 1(空配料),返回 1
        if (validRarities.isEmpty()) {
            return 1;
        }
        
        // 全部相同,检查配方类型
        int baseRarity = validRarities.get(0);
        
        if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
            // 工作台配方:检查数量比例
            int ingredientCount = recipe.getIngredients().size();
            int outputCount = recipe.getResultItem(net.minecraft.core.RegistryAccess.EMPTY).getCount();
            
            // 使用浮点数除法避免精度丢失
            if (outputCount > 0 && (double) ingredientCount / outputCount >= 2.0) {
                return baseRarity + 1; // 配料数量/产物数量 >= 2 → +1
            } else {
                return baseRarity; // 否则继承
            }
        } else if (recipe instanceof AbstractCookingRecipe) {
            // 烧制配方 → 继承
            return baseRarity;
        } else {
            // 其他配方(锻造台等)→ +1
            return baseRarity + 1;
        }
    }
    
    /**
     * 更新稀有度(保留最高值,并记录首次轮次)
     */
    private static void updateRarityWithMax(Item outputItem, int newRarity) {
        // 检查是否已经有稀有度
        Integer existingRarity = maxRarityCache.get(outputItem);
        
        if (existingRarity == null || newRarity > existingRarity) {
            // 找到更高的稀有度,更新全局缓存
            maxRarityCache.put(outputItem, newRarity);
            
            // 记录物品首次出现的轮次
            boolean isFirstTime = !itemFirstRoundMap.containsKey(outputItem);
            if (isFirstTime) {
                itemFirstRoundMap.put(outputItem, currentRound);
            }
            
            // 更新或创建对应轮次的 Map(历史归档)
            int firstRound = itemFirstRoundMap.get(outputItem);
            Map<Item, Integer> roundMap = allRoundResults.computeIfAbsent(firstRound, k -> new HashMap<>());
            roundMap.put(outputItem, newRarity);
            
            // 关键修复:只有首次计算的物品才加入当前轮结果(用于下一轮传播)
            // 非首次计算仅更新历史记录,不加入当前轮结果,避免无限循环
            if (isFirstTime) {
                currentRoundResults.put(outputItem, newRarity);
            }
        }
    }
    
    /**
     * 进入下一轮
     */
    private static void nextRound() {
        // 保存本轮结果
        if (!currentRoundResults.isEmpty()) {
            allRoundResults.put(currentRound, currentRoundResults);
        }
        
        // 检查收敛
        if (currentRoundResults.isEmpty()) {
            finishCalculation();
            return;
        }
        
        // 准备下一轮
        currentRound++;
        pendingItemList.clear();
        pendingItemList.addAll(currentRoundResults.keySet());
        
        RarityCore.LOGGER.info("Round {} complete, {} new items calculated. Starting round {}", 
            currentRound - 1, pendingItemList.size(), currentRound);
        
        // 发送聊天栏消息:本回合完成
        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_round_complete", 
                currentRound - 1)
            .withStyle(net.minecraft.ChatFormatting.GREEN));
        
        startProcessingRound();
    }
    
    /**
     * 判断是否是支持的配方类型
     */
    private static boolean isSupportedRecipeType(Recipe<?> recipe) {
        // 支持工作台配方
        if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
            return true;
        }
            
        // 支持熔炉类配方(包括熔炉、smoker、blast furnace)
        if (recipe instanceof AbstractCookingRecipe) {
            return true;
        }
            
        // 支持锻造台升级配方(排除盔甲纹饰)
        // SmithingTransformRecipe 用于物品升级(如下界合金升级)
        // SmithingTrimRecipe 用于盔甲纹饰(仅改变外观,不处理)
        if (recipe instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe) {
            return true;
        }
            
        // 检查是否是非标准的锻造台配方
        String recipeClassName = recipe.getClass().getName();
        if (recipeClassName.contains("Smithing")) {
            // 非标准锻造台配方,跳过
        }
            
        return false;
    }
    
    /**
     * 完成计算
     */
    private static void finishCalculation() {
        isCalculating = false;
        
        // 合并所有轮次的结果,保留最高稀有度
        Map<Item, Integer> finalResults = mergeAllRoundResults();
        
        RarityCore.LOGGER.info("Auto rarity calculation completed: {} items calculated across {} rounds", 
            finalResults.size(), currentRound);
        
        if (!finalResults.isEmpty()) {
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_complete", finalResults.size())
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
            
            // 写入配置
            RarityCore.LOGGER.info("Writing auto configs...");
            writeAutoConfigs(finalResults);
            
            // 发送聊天栏消息:计算完毕,将在 10 秒后自动重载
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_will_auto_reload")
                .withStyle(ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD));
            
            // 10 秒后自动重载两次
            scheduleAutoReload();
            
        } else {
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_no_items_calculated")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
            RarityCore.LOGGER.warn("No items were calculated during auto rarity calculation");
        }
    }
    
    /**
     * 合并所有轮次的结果(保留最高稀有度)
     */
    private static Map<Item, Integer> mergeAllRoundResults() {
        Map<Item, Integer> merged = new HashMap<>();
        
        for (Map.Entry<Integer, Map<Item, Integer>> roundEntry : allRoundResults.entrySet()) {
            Map<Item, Integer> roundMap = roundEntry.getValue();
            
            for (Map.Entry<Item, Integer> entry : roundMap.entrySet()) {
                Item item = entry.getKey();
                int rarity = entry.getValue();
                
                // 保留最高稀有度
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
    
    /**
     * 写入自动计算的配置
     */
    private static void writeAutoConfigs(Map<Item, Integer> finalResults) {
        try {
            RarityCore.LOGGER.info("Starting to write auto config: {} items", finalResults.size());
            
            // 如果没有要写入的数据,直接返回
            if (finalResults.isEmpty()) {
                RarityCore.LOGGER.warn("No data to write, skipping auto config write");
                return;
            }
            
            // 清理旧的 auto_*.json(NBT 文件已废弃)
            AutoRarityConfigManager.cleanupAutoNbtFiles();
            
            // 写入 auto_rarity.json(仅 ID 匹配)
            AutoRarityConfigManager.writeAutoRarityJson(finalResults);
            
            RarityCore.LOGGER.info("Config write completed: {} items written to auto_rarity.json", 
                finalResults.size());
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to write auto rarity configs", e);
        }
    }
    
    /**
     * 发送世界消息
     */
    @SuppressWarnings("null")
    private static void sendToAllPlayers(net.minecraft.network.chat.Component message) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(message, false);
        }
    }
    
    /**
     * 安排自动重载(10 秒后执行两次重载)
     * 使用实例引用管理 ScheduledExecutorService 生命周期，避免每次调用创建新线程池而不回收
     */
    private static void scheduleAutoReload() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        
        // 关闭之前可能泄漏的 executor（如果存在且未关闭）
        if (autoReloadExecutor != null && !autoReloadExecutor.isShutdown()) {
            autoReloadExecutor.shutdown();
        }
        
        // 创建新的 ScheduledExecutorService 并存储引用以实现后续关闭
        autoReloadExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RarityCore-AutoReload");
            t.setDaemon(true);
            return t;
        });
        
        autoReloadExecutor.schedule(() -> {
            try {
                // 通过 server.execute 回到主线程执行重载
                server.execute(() -> {
                    try {
                        org.yanbwe.raritycore.service.ConfigReloadService.reloadFromCommand(null);
                        RarityCore.LOGGER.info("First auto reload completed");
                        
                        // 1 秒延迟后执行第二次重载
                        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                        
                        server.execute(() -> {
                            try {
                                org.yanbwe.raritycore.service.ConfigReloadService.reloadFromCommand(null);
                                RarityCore.LOGGER.info("Second auto reload completed");
                                
                                sendToAllPlayers(Component.translatable("rarity.core.auto_reload_complete_message")
                                    .withStyle(ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD));
                            } catch (Exception e) {
                                RarityCore.LOGGER.error("Error during second auto reload", e);
                            }
                        });
                    } catch (Exception e) {
                        RarityCore.LOGGER.error("Error during first auto reload", e);
                    }
                });
            } finally {
                // 延迟任务已触发，关闭 executor 释放线程资源（防止线程池泄漏）
                autoReloadExecutor.shutdown();
            }
        }, 10, java.util.concurrent.TimeUnit.SECONDS);
    }
    

    
    /**
     * 是否正在计算
     */
    public static boolean isCalculating() {
        return isCalculating;
    }
    
    /**
     * 强制重新计算(删除旧文件并重新开始)
     */
    public static void forceRecalculate() {
        if (isCalculating) {
            return;
        }
        
        // 删除旧文件
        AutoRarityConfigManager.deleteAutoRarityFile();
        AutoRarityConfigManager.cleanupAutoNbtFiles();
        
        // 清空所有缓存和数据结构
        pendingItemList = new ArrayDeque<>();
        allRoundResults = new HashMap<>();
        currentRoundResults = new HashMap<>();
        itemFirstRoundMap = new HashMap<>();
        maxRarityCache = new HashMap<>();
        ingredientToRecipesMap = new HashMap<>();
        
        // 开始计算
        startAutoCalculation();
    }
}
