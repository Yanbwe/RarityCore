package org.yanbwe.raritycore.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.network.ChangeOperation;
import org.yanbwe.raritycore.network.SyncBatchManager;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 配置加载工具类
 * 统一封装JSON配置文件的加载、解析和验证逻辑
 * 消除重复代码,提高代码可维护性
 */
public class ConfigLoaderUtils {
    private static final Gson GSON = JsonPerformanceOptimizer.getOptimizedGson();
    
    /**
     * 通用的JSON配置文件加载方法
     * @param configFile 配置文件路径
     * @param fileName 文件名(用于日志)
     * @param itemProcessor 物品处理回调函数 (itemId, rarity) -> void
     * @return 成功加载的物品数量
     */
    public static int loadJsonConfigFile(Path configFile, String fileName, BiConsumer<String, Integer> itemProcessor) {
        int itemCount = 0;
        
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
            
            if (jsonObject != null && JsonPerformanceOptimizer.validateRarityJsonFormat(jsonObject)) {
                for (String itemIdString : jsonObject.keySet()) {
                    JsonElement rarityElement = jsonObject.get(itemIdString);
                    
                    if (rarityElement.isJsonPrimitive() && rarityElement.getAsJsonPrimitive().isNumber()) {
                        int rarity = rarityElement.getAsInt();
                        
                        // 验证稀有度范围 - 支持高级稀有度(大于7)以符合模组包容性设计
                        if (rarity < RarityConstants.MIN_RARITY) {
                            RarityCore.LOGGER.warn("Invalid rarity value {} for item '{}' in file '{}'", 
                                rarity, itemIdString, fileName);
                            continue;
                        }
                        // 注意:不再限制最大稀有度值,允许8-10级等高级稀有度
                        
                        ResourceLocation itemId = ResourceLocation.parse(itemIdString);
                        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemId);
                        
                        if (item == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                            RarityCore.LOGGER.warn("Unknown item '{}' in file '{}'", itemIdString, fileName);
                            continue;
                        }
                        
                        // 处理物品稀有度
                        itemProcessor.accept(itemIdString, rarity);
                        itemCount++;
                    } else {
                        RarityCore.LOGGER.warn("Invalid rarity data format for item '{}' in file '{}'", 
                            itemIdString, fileName);
                    }
                }
                RarityCore.LOGGER.info("Loaded {} items from config file: {}", itemCount, fileName);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot read config file: {}", configFile, e);
        } catch (JsonParseException e) {
            RarityCore.LOGGER.error("Config file format error: {}", fileName, e);
        }
        
        return itemCount;
    }
    
    /**
     * 带批处理支持的配置加载方法
     *
     * <p>{@link SyncBatchManager#addOperation} 的返回值语义是"请求调用方立即排空"（操作已入队，
     * 不会被丢弃）。这里在<b>整个文件加载完成后</b>统一排空一次，既避免逐条回调里频繁发包，
     * 又保证积压到阈值时一个操作都不会丢——即使读取中途异常退出，收尾的排空也一定会执行。</p>
     *
     * @param configFile 配置文件路径
     * @param fileName 文件名
     * @param useBatchProcessing 是否使用批处理
     * @return 成功加载的物品数量
     */
    public static int loadJsonConfigFileWithBatch(Path configFile, String fileName, boolean useBatchProcessing) {
        // lambda 里无法直接写外部局部变量，用单元素数组累积"需要排空"标记
        final boolean[] flushRequested = { false };

        int loadedCount = loadJsonConfigFile(configFile, fileName, (itemIdString, rarity) -> {
            ResourceLocation itemId = ResourceLocation.parse(itemIdString);
            
            if (useBatchProcessing) {
                // 使用批处理管理器
                ChangeOperation operation = new ChangeOperation(
                    rarity == 0 ? ChangeOperation.OperationType.DELETE : ChangeOperation.OperationType.ADD,
                    itemId, 
                    rarity == 0 ? null : rarity
                );
                // 返回值语义是"需要立即排空"（批量阈值/积压阈值/时间窗口），不再表示"是否入队成功"：
                // addOperation 现在保证任何情况下都先入队
                if (SyncBatchManager.addOperation(operation)) {
                    flushRequested[0] = true;
                }
            } else {
                // 直接注册到稀有度注册表
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != null) {
                    RarityRegistry.register(item, rarity, false);
                }
            }
        });

        // 文件加载结束后统一排空并下发（放在回调之外，保证最终一定会排空）
        if (useBatchProcessing && flushRequested[0]) {
            flushPendingBatchOperations(fileName);
        }

        return loadedCount;
    }

    /**
     * 排空 {@link SyncBatchManager} 的积压操作并立即下发给所有在线玩家。
     *
     * <p>与 {@code ConfigReloadService.processPendingBatchOperations()} 保持同一语义：先把操作应用到
     * {@link RarityRegistry}（服务端状态），否则排空后这些操作既没注册也没下发，等于真正丢失。
     * 应用时传 {@code syncToClients=false}，改为把同一批操作一次性交给 {@link SyncManager}，
     * 由 {@code SyncManager.syncIncrementalChangesToClients()} 按
     * {@code NetworkConstants.MAX_INCREMENTAL_OPERATIONS} 分包下发——避免逐条发包。
     * 服务端尚未启动时该方法内部会直接返回，不会抛异常。</p>
     *
     * @param fileName 触发排空的配置文件名（仅用于日志）
     */
    private static void flushPendingBatchOperations(String fileName) {
        List<ChangeOperation> pendingOps = SyncBatchManager.getAndClearPendingOperations();
        if (pendingOps.isEmpty()) {
            return;
        }

        int appliedCount = 0;
        for (ChangeOperation op : pendingOps) {
            Item item = BuiltInRegistries.ITEM.get(op.getItemId());
            if (item == null || op.getItemId().equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                continue;
            }
            switch (op.getType()) {
                case ADD:
                case UPDATE:
                    if (op.getRarity() != null) {
                        RarityRegistry.register(item, op.getRarity(), false);
                        appliedCount++;
                    }
                    break;
                case DELETE:
                    RarityRegistry.unregister(item, false);
                    appliedCount++;
                    break;
                default:
                    break;
            }
        }

        // 一次性把这一批操作交给增量同步缓冲区并下发
        for (ChangeOperation op : pendingOps) {
            SyncManager.addChangeOperation(op);
        }
        SyncManager.syncIncrementalChangesToClients();

        RarityCore.LOGGER.info("Flushed {} pending batch operations while loading '{}' (applied: {})",
            pendingOps.size(), fileName, appliedCount);
    }
    
    /**
     * 确保配置目录存在的工具方法
     * @param configDir 配置目录路径
     * @return 是否成功创建目录
     */
    public static boolean ensureConfigDirectoryExists(Path configDir) {
        try {
            Files.createDirectories(configDir);
            return true;
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", configDir, e);
            return false;
        }
    }
    
    /**
     * 创建默认配置文件的通用方法
     * @param configFile 配置文件路径
     * @param defaultContent 默认内容生成函数
     * @return 是否创建成功
     */
    public static boolean createDefaultConfigFile(Path configFile, Consumer<JsonObject> defaultContent) {
        JsonObject configObject = new JsonObject();
        defaultContent.accept(configObject);
        
        try {
            try (java.io.FileWriter writer = new java.io.FileWriter(configFile.toString())) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Created default config file: {}", configFile);
                return true;
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create default config file: {}", configFile, e);
            return false;
        }
    }
    
    /**
     * 验证配置文件是否存在,不存在则创建
     * @param configFile 配置文件路径
     * @param defaultContent 默认内容生成函数
     * @return 配置文件是否准备就绪
     */
    public static boolean ensureConfigFileExists(Path configFile, Consumer<JsonObject> defaultContent) {
        if (!Files.exists(configFile)) {
            return createDefaultConfigFile(configFile, defaultContent);
        }
        return true;
    }
}