package org.yanbwe.raritycore.calc;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.nbtmatching.NbtCondition;
import org.yanbwe.raritycore.nbtmatching.NbtMatchRule;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 自动稀有度计算器
 * 通过合成表反向推导产物的稀有度
 */
public class AutoRarityCalculator {
    
    private static final int ITEMS_PER_TICK = 20; // 每 tick 处理的物品个数
    
    // 计算状态
    private static boolean isCalculating = false;
    
    /**
     * 判断物品是否为 A 类物品（已有配置的稀有度）
     * @param itemId 物品 ID
     * @return 如果是 A 类返回 true
     */
    private static boolean isTypeA(ResourceLocation itemId) {
        return RarityRegistry.ITEM_RARITY_MAP.containsKey(itemId);
    }
    
    /**
     * 判断物品是否已计算过（C 类物品）
     * @param item 物品对象
     * @return 如果已计算返回 true
     */
    private static boolean isTypeC(Item item) {
        return tempComputedRarities.containsKey(item);
    }
    
    /**
     * 判断物品是否为 B 类物品（无配置，可以计算）
     * @param itemId 物品 ID
     * @param item 物品对象
     * @return 如果是 B 类返回 true
     */
    private static boolean isTypeB(ResourceLocation itemId, Item item) {
        // 不是 A 类且不是 C 类，就是 B 类
        return !isTypeA(itemId) && !isTypeC(item);
    }
    private static int currentRound = 0;
    private static int itemsProcessedThisTick = 0;
    
    // 缓存
    private static Map<Item, Integer> tempComputedRarities = new HashMap<>();
    private static Set<ResourceLocation> visitedRecipes = new HashSet<>();
    private static List<NbtRuleWithItem> pendingNbtRules = new ArrayList<>();
    
    // 待处理队列
    private static Queue<CalculationTask> pendingTasks = new ConcurrentLinkedQueue<>();
    
    // 统计
    private static int newlyAddedCount = 0;
    private static int roundTotalTasks = 0; // 本轮总任务数
    private static int roundProcessedTasks = 0; // 本轮已处理任务数
    private static long lastProgressUpdateTime = 0; // 上次进度更新时间（毫秒）
    
    // 记录本轮新计算的物品（用于多轮迭代）
    private static Set<Item> currentRoundNewItems = new HashSet<>();
    
    /**
     * NBT 规则打包类
     */
    public static class NbtRuleWithItem {
        public final ResourceLocation itemId;
        public final CompoundTag nbtTag;
        public final List<NbtCondition> conditions;
        public final int rarity; // 添加稀有度字段
        
        public NbtRuleWithItem(ResourceLocation itemId, CompoundTag nbtTag, List<NbtCondition> conditions, int rarity) {
            this.itemId = itemId;
            this.nbtTag = nbtTag;
            this.conditions = conditions;
            this.rarity = rarity;
        }
    }
    
    /**
     * 计算任务单元
     */
    public static class CalculationTask {
        public final Recipe<?> recipe;
        public final List<Integer> ingredientRarities;
        
        public CalculationTask(Recipe<?> recipe, List<Integer> ingredientRarities) {
            this.recipe = recipe;
            this.ingredientRarities = ingredientRarities;
        }
    }
    
    /**
     * 开始自动计算（在世界启动后调用）
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
        newlyAddedCount = 0;
        currentRoundNewItems.clear();
        tempComputedRarities.clear();
        visitedRecipes.clear();
        pendingNbtRules.clear();
        pendingTasks.clear();
        
        // 发送世界消息
        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_starting")
            .withStyle(net.minecraft.ChatFormatting.YELLOW));
        
        // 开始第一轮扫描
        scanAndQueueFirstRound();
    }
    
    /**
     * 第一轮扫描：遍历所有已知稀有度的物品
     */
    private static void scanAndQueueFirstRound() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        
        RecipeManager recipeManager = server.getRecipeManager();
        
        // 获取所有已知稀有度的物品（排除 NBT 匹配和神化）
        Set<Item> knownRarityItems = new HashSet<>();
        
        // 从注册表中获取（包括原版、数据包、配置）
        for (ResourceLocation itemId : RarityRegistry.ITEM_RARITY_MAP.keySet()) {
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null) {
                knownRarityItems.add(item);
            }
        }
        
        RarityCore.LOGGER.info("Starting auto rarity calculation: Round 1, known items: {}", knownRarityItems.size());
        
        // 遍历所有配方
        int taskCount = 0;
        int smithingCount = 0;
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            // 只处理工作台、熔炉、锻造台配方
            if (!isSupportedRecipeType(recipe)) {
                continue;
            }
            
            // 记录锻造台配方数量
            if (recipe instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe) {
                smithingCount++;
            }
            
            // 跳过已访问的配方
            ResourceLocation recipeId = recipe.getId();
            if (visitedRecipes.contains(recipeId)) {
                continue;
            }
            
            // 检查配料是否都已知
            List<Integer> rarities = getIngredientRarities(recipe, knownRarityItems);
            // 关键修复：getIngredientRarities 现在总是返回列表（可能为空），不再返回 null
            // 所以只需要检查列表是否为空即可
            if (!rarities.isEmpty()) {
                pendingTasks.offer(new CalculationTask(recipe, rarities));
                visitedRecipes.add(recipeId);
                taskCount++;
            }
        }
        
        RarityCore.LOGGER.info("Round 1: Found {} smithing recipes, queued {} total tasks", smithingCount, taskCount);
        
        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_round_scan_complete", currentRound, taskCount)
            .withStyle(net.minecraft.ChatFormatting.YELLOW));
        roundTotalTasks = taskCount;
        roundProcessedTasks = 0;
        lastProgressUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 判断是否是支持的配方类型
     */
    private static boolean isSupportedRecipeType(Recipe<?> recipe) {
        // 支持工作台配方
        if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
            return true;
        }
            
        // 支持熔炉类配方（包括熔炉、smoker、blast furnace）
        if (recipe instanceof AbstractCookingRecipe) {
            return true;
        }
            
        // 支持锻造台升级配方（排除盔甲纹饰）
        // SmithingTransformRecipe 用于物品升级（如下界合金升级）
        // SmithingTrimRecipe 用于盔甲纹饰（仅改变外观，不处理）
        if (recipe instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe) {
            return true;
        }
            
        return false;
    }
    
    /**
     * 获取配料的稀有度列表
     * @param recipe 配方
     * @param knownItems 已知稀有度的物品集合（用于检查配料是否有稀有度）
     * @return 如果所有配料都有稀有度则返回列表，否则返回 null
     */
    private static List<Integer> getIngredientRarities(Recipe<?> recipe, Set<Item> knownItems) {
        List<Integer> rarities = new ArrayList<>();
        
        // 特殊处理锻造台配方
        if (recipe instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe smithingRecipe) {
            try {
                // 尝试使用不同的字段名（可能是映射名）
                Ingredient template = null, base = null, addition = null;
                
                // 可能的字段名列表 - 根据实际日志，字段名应该是 f_265xxx_ 格式
                String[] possibleTemplateNames = {"template", "f_44139_", "field_17786_a", "f_265949_"};
                String[] possibleBaseNames = {"base", "f_44140_", "field_17787_b", "f_265888_"};
                String[] possibleAdditionNames = {"addition", "f_44141_", "field_17788_c", "f_265907_"};
                
                for (String name : possibleTemplateNames) {
                    try {
                        java.lang.reflect.Field field = recipe.getClass().getDeclaredField(name);
                        field.setAccessible(true);
                        template = (Ingredient) field.get(recipe);
                        break;
                    } catch (NoSuchFieldException ignored) {}
                }
                
                for (String name : possibleBaseNames) {
                    try {
                        java.lang.reflect.Field field = recipe.getClass().getDeclaredField(name);
                        field.setAccessible(true);
                        base = (Ingredient) field.get(recipe);
                        break;
                    } catch (NoSuchFieldException ignored) {}
                }
                
                for (String name : possibleAdditionNames) {
                    try {
                        java.lang.reflect.Field field = recipe.getClass().getDeclaredField(name);
                        field.setAccessible(true);
                        addition = (Ingredient) field.get(recipe);
                        break;
                    } catch (NoSuchFieldException ignored) {}
                }
                
                if (template != null || base != null || addition != null) {
                    // 处理 template
                    if (template != null && !template.isEmpty()) {
                        processIngredient(template, "template", rarities, knownItems);
                    } else {
                        rarities.add(1);
                    }
                    
                    // 处理 base
                    if (base != null && !base.isEmpty()) {
                        processIngredient(base, "base", rarities, knownItems);
                    } else {
                        rarities.add(1);
                    }
                    
                    // 处理 addition
                    if (addition != null && !addition.isEmpty()) {
                        processIngredient(addition, "addition", rarities, knownItems);
                    } else {
                        rarities.add(1);
                    }
                    
                    return rarities;
                } else {
                    RarityCore.LOGGER.error("Could not find any ingredient fields for smithing recipe {}", recipe.getId());
                }
                
            } catch (Exception e) {
                RarityCore.LOGGER.error("Failed to get ingredients for smithing recipe {} via reflection: {}", 
                    recipe.getId(), e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 默认处理：使用 getIngredients()
        Ingredient[] ingredients = recipe.getIngredients().toArray(new Ingredient[0]);
        for (int i = 0; i < ingredients.length; i++) {
            Ingredient ingredient = ingredients[i];
            
            if (ingredient == null || ingredient.isEmpty()) {
                rarities.add(1);
                continue;
            }
            
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length == 0) {
                rarities.add(1);
                continue;
            }
            
            boolean found = false;
            for (ItemStack stack : stacks) {
                Item item = stack.getItem();
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                
                Integer rarity = tempComputedRarities.get(item);
                if (rarity == null) {
                    rarity = RarityRegistry.ITEM_RARITY_MAP.get(itemId);
                }
                
                if (rarity != null) {
                    rarities.add(rarity);
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                rarities.add(1);
            }
        }
        
        return rarities;
    }
    
    /**
     * 处理单个配料，添加其稀有度到列表中
     */
    private static void processIngredient(Ingredient ingredient, String name, List<Integer> rarities, Set<Item> knownItems) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length == 0) {
            rarities.add(1);
            return;
        }
        
        boolean found = false;
        for (ItemStack stack : stacks) {
            Item item = stack.getItem();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            
            Integer rarity = tempComputedRarities.get(item);
            if (rarity == null) {
                rarity = RarityRegistry.ITEM_RARITY_MAP.get(itemId);
            }
            
            if (rarity != null) {
                rarities.add(rarity);
                found = true;
                break;
            }
        }
        
        if (!found) {
            rarities.add(1);
        }
    }
    
    /**
     * Tick 更新（每 tick 调用）
     */
    public static void tick() {
        if (!isCalculating || pendingTasks.isEmpty()) {
            return;
        }
        
        // 处理最多 2 个物品
        int processedInThisTick = 0;
        while (processedInThisTick < ITEMS_PER_TICK && !pendingTasks.isEmpty()) {
            CalculationTask task = pendingTasks.poll();
            processTask(task);
            processedInThisTick++;
            roundProcessedTasks++;
        }
        
        // 每秒发送一次进度消息
        long currentTime = System.currentTimeMillis();
        if (roundTotalTasks > 0 && (currentTime - lastProgressUpdateTime) >= 1000) {
            int progressPercent = (int) ((roundProcessedTasks * 100.0) / roundTotalTasks);
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_progress", currentRound, roundProcessedTasks, roundTotalTasks, progressPercent)
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
            lastProgressUpdateTime = currentTime;
        }
        
        if (pendingTasks.isEmpty()) {
            // 本轮完成
            checkAndStartNextRound();
        }
    }
    
    /**
     * 处理单个计算任务
     */
    private static void processTask(CalculationTask task) {
        try {
            Recipe<?> recipe = task.recipe;
            ItemStack result = recipe.getResultItem(null);
            
            if (result.isEmpty()) {
                return;
            }
            
            Item outputItem = result.getItem();
            ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(outputItem);
            
            if (outputId == null) {
                RarityCore.LOGGER.debug("Skipping item with null ID: {}", outputItem);
                return;
            }
            
            // 关键修复：只计算 B 类物品
            // A 类物品（已有配置）→ 跳过，不触碰
            // C 类物品（已计算过）→ 跳过，避免重复
            // B 类物品（无配置）→ 开始计算
            if (isTypeA(outputId)) {
                // A 类物品：已有配置，绝对不触碰
                return;
            }
            
            if (isTypeC(outputItem)) {
                // C 类物品：本轮已计算过，跳过
                return;
            }
            
            // B 类物品：开始计算
            
            // 计算产物稀有度
            int outputRarity = calculateOutputRarity(task.ingredientRarities);
            
            // 关键修复：如果物品已经被计算过（有多个配方），只保留最高稀有度
            Integer existingRarity = tempComputedRarities.get(outputItem);
            if (existingRarity != null) {
                // 物品已经有稀有度，只在新计算的稀有度更高时才覆盖
                if (outputRarity > existingRarity) {
                    tempComputedRarities.put(outputItem, outputRarity);
                }
            } else {
                // 第一次计算此物品，直接保存
                tempComputedRarities.put(outputItem, outputRarity);
                newlyAddedCount++;
                currentRoundNewItems.add(outputItem); // 记录为本轮新物品
            }
            
            // 如果产物有 NBT，生成 NBT 规则
            if (result.hasTag()) {
                generateNbtRule(outputId, result.getTag(), outputRarity);
            }
            
        } catch (Exception e) {
            // 跳过此物品
        }
    }
    
    /**
     * 计算产物稀有度
     * 公式：
     * - 配料稀有度全部相同 → 产物 = 配料 + 1
     * - 配料稀有度不同 → 产物 = 最高配料稀有度
     */
    private static int calculateOutputRarity(List<Integer> ingredientRarities) {
        if (ingredientRarities == null || ingredientRarities.isEmpty()) {
            return 1;
        }
        
        // 缺失稀有度视为 1
        List<Integer> rarities = new ArrayList<>();
        for (Integer r : ingredientRarities) {
            rarities.add(r == null ? 1 : r);
        }
        
        // 判断是否全部相同
        boolean allSame = rarities.stream().distinct().count() == 1;
        
        if (allSame) {
            return rarities.get(0) + 1; // 加一级
        } else {
            return Collections.max(rarities); // 继承最高
        }
    }
    
    /**
     * 生成 NBT 规则
     */
    private static void generateNbtRule(ResourceLocation itemId, CompoundTag nbtTag, int rarity) {
        try {
            List<NbtCondition> conditions = NbtRuleGenerator.generateConditions(nbtTag);
            if (!conditions.isEmpty()) {
                pendingNbtRules.add(new NbtRuleWithItem(itemId, nbtTag, conditions, rarity));
            }
        } catch (Exception e) {
            // 忽略 NBT 规则生成失败
        }
    }
    
    /**
     * 检查并开始下一轮
     */
    private static void checkAndStartNextRound() {
        // 检查本轮是否有新物品
        if (currentRoundNewItems.isEmpty()) {
            // 没有新物品，计算完成
            finishCalculation();
            return;
        }
        
        // 有新获得稀有度的物品，开始下一轮
        currentRound++;
        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_new_items_found", currentRound)
            .withStyle(net.minecraft.ChatFormatting.YELLOW));
        
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        
        RecipeManager recipeManager = server.getRecipeManager();
        // 使用本轮新计算的物品集合
        Set<Item> newlyAddedItems = new HashSet<>(currentRoundNewItems);
        
        // 清空本轮新物品记录，为下一轮准备
        currentRoundNewItems.clear();
        
        // 重新扫描所有配方，找到所有产物仍然是 B 类的配方
        int taskCount = 0;
        int checkedRecipes = 0;
        int skippedHasConfig = 0;
        int skippedNoNewIngredient = 0;
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            if (!isSupportedRecipeType(recipe)) {
                continue;
            }
            
            checkedRecipes++;
            
            // 检查产物的稀有度是否已经确定（包括临时缓存和注册表）
            ItemStack result = recipe.getResultItem(null);
            if (result.isEmpty()) {
                continue;
            }
            
            Item outputItem = result.getItem();
            ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(outputItem);
            
            // 如果产物已经有稀有度，跳过此配方
            if (tempComputedRarities.containsKey(outputItem) || 
                RarityRegistry.ITEM_RARITY_MAP.containsKey(outputId)) {
                skippedHasConfig++;
                continue;
            }
            
            // 关键修复：不再检查是否包含新配料，只要产物是 B 类就计算
            // 因为配料的稀有度可能在之前的轮次中已经获得
            
            // 获取配料的稀有度列表
            List<Integer> rarities = getIngredientRarities(recipe, newlyAddedItems);
            // 关键修复：getIngredientRarities 现在总是返回列表（可能为空），不再返回 null
            if (!rarities.isEmpty()) {
                pendingTasks.offer(new CalculationTask(recipe, rarities));
                taskCount++;
            } else {
                // 配料中有无法确定稀有度的，暂时跳过
                skippedNoNewIngredient++;
                // 锻造台配方配料为空是常见的（如 KubeJS 修改），不输出警告
                if (!(recipe instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe)) {
                    RarityCore.LOGGER.warn("Skipped recipe {} for product {}: ingredients returned empty list", 
                        recipe.getId(), outputId);
                }
            }
        }
        
        RarityCore.LOGGER.info("Round {} scan: checked {} recipes, skipped {} (has config), skipped {} (missing ingredient rarity), queued {} tasks", 
            currentRound, checkedRecipes, skippedHasConfig, skippedNoNewIngredient, taskCount);
        
        if (taskCount > 0) {
            roundTotalTasks = taskCount;
            roundProcessedTasks = 0;
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_round_scan_complete", currentRound, taskCount)
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        } else {
            // 没有新任务，计算完成
            finishCalculation();
        }
    }
    
    /**
     * 完成计算
     */
    private static void finishCalculation() {
        isCalculating = false;
        
        // 统计信息
        int totalCalculated = newlyAddedCount;
        int nbtRulesGenerated = pendingNbtRules.size();
        
        RarityCore.LOGGER.info("Auto rarity calculation completed: {} items calculated, {} NBT rules generated", 
            totalCalculated, nbtRulesGenerated);
        
        if (totalCalculated > 0 || nbtRulesGenerated > 0) {
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_complete", totalCalculated)
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
            
            // 写入配置
            RarityCore.LOGGER.info("Writing auto configs...");
            writeAutoConfigs();
            
            // 提示玩家需要手动执行 reload 指令
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_manual_reload_required")
                .withStyle(net.minecraft.ChatFormatting.GREEN));
        } else {
            // 没有计算出任何物品
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_no_items_calculated")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
            RarityCore.LOGGER.warn("No items were calculated during auto rarity calculation");
            
            // 清空临时数据，不写入空文件
            tempComputedRarities.clear();
            pendingNbtRules.clear();
        }
    }
    
    /**
     * 写入自动计算的配置
     */
    private static void writeAutoConfigs() {
        try {
            RarityCore.LOGGER.info("Starting to write auto configs: {} items, {} NBT rules", 
                tempComputedRarities.size(), pendingNbtRules.size());
            
            // 如果没有要写入的数据，直接返回
            if (tempComputedRarities.isEmpty() && pendingNbtRules.isEmpty()) {
                RarityCore.LOGGER.warn("No data to write, skipping auto config write");
                return;
            }
            
            // 清理旧的 auto_*.json（只在有新数据时才清理）
            AutoRarityConfigManager.cleanupAutoNbtFiles();
            
            // 写入 auto_rarity.json
            if (!tempComputedRarities.isEmpty()) {
                AutoRarityConfigManager.writeAutoRarityJson(tempComputedRarities);
            } else {
                RarityCore.LOGGER.debug("No rarity data to write to auto_rarity.json");
            }
            
            // 写入 auto_*.json (NBT 规则)
            int nbtFilesWritten = 0;
            for (NbtRuleWithItem rule : pendingNbtRules) {
                AutoRarityConfigManager.writeNbtRuleFile(rule.itemId, rule.conditions, rule.rarity);
                nbtFilesWritten++;
            }
            
            RarityCore.LOGGER.info("Config write completed: {} items written to auto_rarity.json, {} NBT rule files created", 
                tempComputedRarities.size(), nbtFilesWritten);
            
            // 写入完成后清空临时缓存
            tempComputedRarities.clear();
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to write auto rarity configs", e);
        }
    }
    
    /**
     * 发送世界消息
     */
    private static void sendToAllPlayers(net.minecraft.network.chat.Component message) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(message, false);
        }
    }
    

    
    /**
     * 是否正在计算
     */
    public static boolean isCalculating() {
        return isCalculating;
    }
    
    /**
     * 强制重新计算（删除旧文件并重新开始）
     */
    public static void forceRecalculate() {
        if (isCalculating) {
            return;
        }
        
        // 删除旧文件并获取被删除的物品 ID
        java.util.List<ResourceLocation> removedIds = AutoRarityConfigManager.deleteAutoRarityFile();
        AutoRarityConfigManager.cleanupAutoNbtFiles();
        
        // 清空临时缓存
        tempComputedRarities.clear();
        visitedRecipes.clear();
        pendingNbtRules.clear();
        pendingTasks.clear();
        newlyAddedCount = 0;
        
        // 开始计算
        startAutoCalculation();
    }
}
