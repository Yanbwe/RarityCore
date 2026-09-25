package org.yanbwe.raritycore.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 配置加载工具类
 * 统一封装JSON配置文件的加载、解析和验证逻辑
 * 消除重复代码,提高代码可维护性
 */
public class ConfigLoaderUtils {
    private static final Gson GSON = new Gson();
    
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
                        
                        Identifier itemId = Identifier.parse(itemIdString);
                        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemId)
                            .map(holder -> holder.value())
                            .orElse(null);

                        if (item == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                            RarityCore.LOGGER.debug("Unknown item '{}' in file '{}'", itemIdString, fileName);
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
     * <p>批处理路径下操作只入队，因此必须响应 {@link SyncBatchManager#addOperation(ChangeOperation)}
     * 的返回值（true = 积压已达阈值，请求立即排空）：本方法在<b>整份文件</b>加载完成后统一排空一次，
     * 既避免了逐条回调频繁发包，又保证积压到阈值时不丢任何操作。
     * 若本文件没有收到排空请求，残留操作由
     * {@code ConfigReloadService.processPendingBatchOperations()} 在重载流程末尾兜底排空。</p>
     *
     * @param configFile 配置文件路径
     * @param fileName 文件名
     * @param useBatchProcessing 是否使用批处理
     * @return 成功加载的物品数量
     */
    public static int loadJsonConfigFileWithBatch(Path configFile, String fileName, boolean useBatchProcessing) {
        // 本文件加载期间是否收到过"立即排空"请求（lambda 内需要 effectively final 的容器）
        AtomicBoolean flushRequested = new AtomicBoolean(false);

        int loadedCount = loadJsonConfigFile(configFile, fileName, (itemIdString, rarity) -> {
            Identifier itemId = Identifier.parse(itemIdString);

            if (useBatchProcessing) {
                // 使用批处理管理器
                ChangeOperation operation = new ChangeOperation(
                    rarity == 0 ? ChangeOperation.OperationType.DELETE : ChangeOperation.OperationType.ADD,
                    itemId,
                    rarity == 0 ? null : rarity
                );
                if (SyncBatchManager.addOperation(operation)) {
                    flushRequested.set(true);
                }
            } else {
                // 直接注册到稀有度注册表
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemId)
                    .map(holder -> holder.value())
                    .orElse(null);
                if (item != null) {
                    RarityRegistry.register(item, rarity, false);
                }
            }
        });

        // 整份文件加载完成，响应该文件的排空请求
        if (useBatchProcessing && flushRequested.get()) {
            flushPendingBatchOperations(fileName);
        }

        return loadedCount;
    }

    /**
     * 排空 {@link SyncBatchManager} 的积压操作：应用到稀有度注册表并下发到客户端。
     *
     * <p>与 {@code ConfigReloadService.processPendingBatchOperations()} 保持相同的应用语义
     * （ADD/UPDATE → {@link RarityRegistry#register}，DELETE → {@link RarityRegistry#unregister}，
     * 均以 {@code syncToClients=false} 注册以免逐条触发全量同步）；
     * 随后把已应用的操作投递到 {@link SyncManager} 的增量缓冲区，
     * 并通过<b>活的</b>下发入口 {@link SyncManager#syncIncrementalChangesToClients()}
     * 一次性发出增量包（该入口由 ServerTickListener / SchedulerService / RarityRegistry 调用）。
     * 若服务器尚未就绪，该方法会保留缓冲区数据、由后续 tick 或登录时的全量同步补上，不会丢失。</p>
     *
     * @param fileName 触发排空的文件名（仅用于日志）
     */
    private static void flushPendingBatchOperations(String fileName) {
        List<ChangeOperation> pendingOps = SyncBatchManager.getAndClearPendingOperations();
        if (pendingOps.isEmpty()) {
            return;
        }

        int appliedCount = 0;
        for (ChangeOperation op : pendingOps) {
            // 逐条隔离异常：单个操作失败（例如事件监听器抛异常）不应连累剩余操作，
            // 也不应让异常冒泡出去中断整个配置重载流程
            try {
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(op.getItemId())
                    .map(holder -> holder.value())
                    .orElse(null);
                if (item == null || op.getItemId().equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                    continue;
                }

                switch (op.getType()) {
                    case ADD:
                    case UPDATE:
                        RarityRegistry.register(item, op.getRarity(), false);
                        appliedCount++;
                        break;
                    case DELETE:
                        RarityRegistry.unregister(item, false);
                        appliedCount++;
                        break;
                }

                // 同步投递到增量同步缓冲区，供下面的立即下发使用
                SyncManager.addChangeOperation(op);
            } catch (Exception e) {
                RarityCore.LOGGER.error("Failed to apply pending batch operation for '{}'", op.getItemId(), e);
            }
        }

        if (appliedCount > 0) {
            RarityCore.LOGGER.info("Flushed {} pending batch operations after loading '{}' (applied: {})",
                pendingOps.size(), fileName, appliedCount);
            try {
                SyncManager.syncIncrementalChangesToClients();
            } catch (Exception e) {
                // 注册表数据已应用，客户端会通过后续增量 tick / 登录时的全量同步补齐
                RarityCore.LOGGER.error("Failed to dispatch incremental sync after flushing batch operations", e);
            }
        }
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