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
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            // 只处理工作台、熔炉、锻造台配方
            if (!isSupportedRecipeType(recipe)) {
                continue;
            }
            
            // 跳过已访问的配方
            ResourceLocation recipeId = recipe.getId();
            if (visitedRecipes.contains(recipeId)) {
                continue;
            }
            
            // 检查配料是否都已知
            List<Integer> rarities = getIngredientRarities(recipe, knownRarityItems);
            if (rarities != null && !rarities.isEmpty()) {
                pendingTasks.offer(new CalculationTask(recipe, rarities));
                visitedRecipes.add(recipeId);
                taskCount++;
            }
        }
        
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
            
        // 支持锻造台配方，但排除盔甲纹饰
        if (recipe instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe) {
            // 双重检查：通过配方 ID 排除纹饰配方
            String recipeId = recipe.getId().toString();
            if (!recipeId.contains("trim")) {
                return true;
            }
        }
            
        return false;
    }
    
    /**
     * 获取配料的稀有度列表
     * @return 如果所有配料都有稀有度则返回列表，否则返回 null
     */
    private static List<Integer> getIngredientRarities(Recipe<?> recipe, Set<Item> knownItems) {
        List<Integer> rarities = new ArrayList<>();
        
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            
            // 获取此配料中的所有物品
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length == 0) {
                continue;
            }
            
            // 检查是否有至少一个物品已知稀有度
            boolean found = false;
            for (ItemStack stack : stacks) {
                Item item = stack.getItem();
                
                // 优先使用已计算的临时稀有度
                Integer rarity = tempComputedRarities.get(item);
                if (rarity == null) {
                    rarity = RarityRegistry.ITEM_RARITY_MAP.get(ForgeRegistries.ITEMS.getKey(item));
                }
                
                if (rarity != null) {
                    rarities.add(rarity);
                    found = true;
                    break; // 只需要一个已知稀有度
                }
            }
            
            if (!found) {
                // 配料中没有已知稀有度的物品，视为 1 级
                rarities.add(1);
            }
        }
        
        return rarities.isEmpty() ? null : rarities;
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
            
            // 检查产物是否已经计算过或已有配置
            if (tempComputedRarities.containsKey(outputItem) || 
                RarityRegistry.ITEM_RARITY_MAP.containsKey(outputId)) {
                return;
            }
            
            // 计算产物稀有度
            int outputRarity = calculateOutputRarity(task.ingredientRarities);
            
            // 保存到临时缓存
            tempComputedRarities.put(outputItem, outputRarity);
            newlyAddedCount++;
            
            // 如果产物有 NBT，生成 NBT 规则
            if (result.hasTag()) {
                generateNbtRule(outputId, result.getTag(), outputRarity);
            }
            
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to process recipe task", e);
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
            RarityCore.LOGGER.debug("Failed to generate NBT rule for item: {}", itemId, e);
        }
    }
    
    /**
     * 检查并开始下一轮
     */
    private static void checkAndStartNextRound() {
        if (newlyAddedCount == 0) {
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
        Set<Item> newlyAddedItems = new HashSet<>(tempComputedRarities.keySet());
        
        // 重新扫描所有配方
        int taskCount = 0;
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            if (!isSupportedRecipeType(recipe)) {
                continue;
            }
            
            // 跳过已访问的配方
            ResourceLocation recipeId = recipe.getId();
            if (visitedRecipes.contains(recipeId)) {
                continue;
            }
            
            List<Integer> rarities = getIngredientRarities(recipe, newlyAddedItems);
            if (rarities != null && !rarities.isEmpty()) {
                pendingTasks.offer(new CalculationTask(recipe, rarities));
                visitedRecipes.add(recipeId);
                taskCount++;
            }
        }
        
        if (taskCount > 0) {
            roundTotalTasks = taskCount;
            roundProcessedTasks = 0;
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_round_scan_complete", currentRound, taskCount)
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        } else {
            finishCalculation();
        }
    }
    
    /**
     * 完成计算
     */
    private static void finishCalculation() {
        isCalculating = false;
        
        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_complete", newlyAddedCount)
            .withStyle(net.minecraft.ChatFormatting.YELLOW));
        
        // 写入配置
        writeAutoConfigs();
        
        // 提示玩家需要手动执行 reload 指令
        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_manual_reload_required")
            .withStyle(net.minecraft.ChatFormatting.GREEN));
    }
    
    /**
     * 写入自动计算的配置
     */
    private static void writeAutoConfigs() {
        try {
            // 清理旧的 auto_*.json
            AutoRarityConfigManager.cleanupAutoNbtFiles();
            
            // 写入 auto_rarity.json
            AutoRarityConfigManager.writeAutoRarityJson(tempComputedRarities);
            
            // 写入 auto_*.json (NBT 规则)
            for (NbtRuleWithItem rule : pendingNbtRules) {
                AutoRarityConfigManager.writeNbtRuleFile(rule.itemId, rule.conditions, rule.rarity);
            }
            
            // 写入完成后清空临时缓存
            tempComputedRarities.clear();
            
            RarityCore.LOGGER.info("Auto rarity calculation completed: {} items calculated, {} NBT rules generated", 
                tempComputedRarities.size(), pendingNbtRules.size());
            
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
