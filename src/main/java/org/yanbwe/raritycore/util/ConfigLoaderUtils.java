package org.yanbwe.raritycore.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.network.ChangeOperation;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * @param configFile 配置文件路径
     * @param fileName 文件名
     * @param useBatchProcessing 是否使用批处理
     * @return 成功加载的物品数量
     */
    public static int loadJsonConfigFileWithBatch(Path configFile, String fileName, boolean useBatchProcessing) {
        return loadJsonConfigFile(configFile, fileName, (itemIdString, rarity) -> {
            ResourceLocation itemId = ResourceLocation.parse(itemIdString);
            
            if (useBatchProcessing) {
                // 使用批处理管理器
                ChangeOperation operation = new ChangeOperation(
                    rarity == 0 ? ChangeOperation.OperationType.DELETE : ChangeOperation.OperationType.ADD,
                    itemId, 
                    rarity == 0 ? null : rarity
                );
                org.yanbwe.raritycore.network.SyncBatchManager.addOperation(operation);
            } else {
                // 直接注册到稀有度注册表
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != null) {
                    org.yanbwe.raritycore.registry.RarityRegistry.register(item, rarity, false);
                }
            }
        });
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