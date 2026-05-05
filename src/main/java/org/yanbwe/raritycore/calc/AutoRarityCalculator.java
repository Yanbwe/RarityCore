package org.yanbwe.raritycore.calc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.*;

/**
 * 自动稀有度计算器(门面)
 * 通过合成表反向推导产物的稀有度
 * 委托给 RecipeIndexer、RarityRoundProcessor、AutoRarityWriter、ReloadScheduler
 */
public class AutoRarityCalculator {

    private static final int ITEMS_PER_TICK = 20; // 每 tick 处理的物品个数

    // 计算状态
    private static boolean isCalculating = false;

    // 轮次计数器
    static int currentRound = 0;

    // C 列表:待处理物品队列
    private static List<Item> pendingItemList = new ArrayList<>();

    // 所有轮次的结果 Map(E1, E2, E3...)
    static Map<Integer, Map<Item, Integer>> allRoundResults = new HashMap<>();

    // 当前轮次的结果 Map(En)
    static Map<Item, Integer> currentRoundResults = new HashMap<>();

    // 物品首次出现的轮次(用于定位更新哪个 E)
    static Map<Item, Integer> itemFirstRoundMap = new HashMap<>();

    // 全局最高稀有度缓存(用于快速比较)
    static Map<Item, Integer> maxRarityCache = new HashMap<>();

    // 预构建的配料→配方映射(性能优化)
    static Map<ResourceLocation, List<Recipe<?>>> ingredientToRecipesMap = new HashMap<>();

    // 进度跟踪
    private static int totalItemsInRound = 0;
    private static int processedItemsInRound = 0;
    private static long lastProgressUpdateTime = 0;

    /**
     * 计算任务单元(保留用于兼容性)
     */
    public static class CalculationTask {
        public final Recipe<?> recipe;
        public final Item materialItem;

        public CalculationTask(Recipe<?> recipe, Item materialItem) {
            this.recipe = recipe;
            this.materialItem = materialItem;
        }
    }

    // ==================== 公共 API ====================

    /**
     * 是否正在计算
     */
    public static boolean isCalculating() {
        return isCalculating;
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
        pendingItemList = new ArrayList<>();
        allRoundResults = new HashMap<>();
        currentRoundResults = new HashMap<>();
        itemFirstRoundMap = new HashMap<>();
        maxRarityCache = new HashMap<>();
        ingredientToRecipesMap = new HashMap<>();

        // 发送世界消息
        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_starting")
            .withStyle(ChatFormatting.YELLOW));

        // 委托给 RecipeIndexer 初始化惰性索引(不再全量预建,按需查找)
        RecipeIndexer.init(server.getRecipeManager());

        // 第一轮:将所有 A 类物品加入 C 列表
        initFirstRound();
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
            Item material = pendingItemList.remove(0);
            // 惰性索引:确保该物品的配方在 RarityRoundProcessor 查询前已缓存
            ResourceLocation materialId = BuiltInRegistries.ITEM.getKey(material);
            if (materialId != null) {
                RecipeIndexer.ensureCached(materialId, ingredientToRecipesMap);
            }
            // 委托给 RarityRoundProcessor 处理单个配料
            RarityRoundProcessor.processMaterial(material);
            processedInThisTick++;
            processedItemsInRound++;
        }

        // 每秒发送一次进度消息
        long currentTime = System.currentTimeMillis();
        if (totalItemsInRound > 0 && (currentTime - lastProgressUpdateTime) >= 1000) {
            int progressPercent = (int)((processedItemsInRound * 100.0) / totalItemsInRound);
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_progress",
                    currentRound, processedItemsInRound, totalItemsInRound, progressPercent)
                .withStyle(ChatFormatting.YELLOW));
            lastProgressUpdateTime = currentTime;
        }

        // 检查本轮是否完成
        if (pendingItemList.isEmpty()) {
            nextRound();
        }
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
        pendingItemList = new ArrayList<>();
        allRoundResults = new HashMap<>();
        currentRoundResults = new HashMap<>();
        itemFirstRoundMap = new HashMap<>();
        maxRarityCache = new HashMap<>();
        ingredientToRecipesMap = new HashMap<>();

        // 开始计算
        startAutoCalculation();
    }

    // ==================== 包内可见方法(供子组件调用) ====================

    /**
     * 判断物品是否为 A 类物品(已有配置的稀有度)
     */
    static boolean isTypeA(ResourceLocation itemId) {
        return RarityRegistry.ITEM_RARITY_MAP.containsKey(itemId);
    }

    /**
     * 发送世界消息
     */
    @SuppressWarnings("null")
    static void sendToAllPlayers(Component message) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    // ==================== 内部轮次管理(私有) ====================

    /**
     * 第一轮初始化:将所有 A 类物品加入 C 列表
     */
    private static void initFirstRound() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        for (ResourceLocation itemId : RarityRegistry.ITEM_RARITY_MAP.keySet()) {
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item != null) {
                pendingItemList.add(item);
            }
        }

        RarityCore.LOGGER.info("Round 1 initialized with {} items from manual config", pendingItemList.size());

        startProcessingRound();
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

        sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_round_start",
                currentRound, pendingItemList.size())
            .withStyle(ChatFormatting.YELLOW));
    }

    /**
     * 进入下一轮
     */
    private static void nextRound() {
        if (!currentRoundResults.isEmpty()) {
            allRoundResults.put(currentRound, currentRoundResults);
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
                .withStyle(ChatFormatting.YELLOW));

            // 委托给 AutoRarityWriter 写入配置
            RarityCore.LOGGER.info("Writing auto configs...");
            AutoRarityWriter.writeAutoConfigs(finalResults);

            // 发送聊天栏消息:计算完毕,将在 10 秒后自动重载
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_will_auto_reload")
                .withStyle(ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD));

            // 委托给 ReloadScheduler 安排延迟重载
            ReloadScheduler.scheduleAutoReload();

        } else {
            sendToAllPlayers(Component.translatable("rarity.core.auto_calculation_no_items_calculated")
                .withStyle(ChatFormatting.YELLOW));
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
     * 判断物品是否已计算过(C 类物品)
     * (保留以备将来使用,目前未被内部调用)
     */
    @SuppressWarnings("unused")
    private static boolean isTypeC(Item item) {
        return maxRarityCache.containsKey(item);
    }
}
