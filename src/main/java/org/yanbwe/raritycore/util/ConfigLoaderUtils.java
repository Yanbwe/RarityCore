package org.yanbwe.raritycore.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.Identifier;
import org.yanbwe.raritycore.Raritycore;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * 配置加载工具类
 * 统一封装JSON配置文件的加载、解析和验证逻辑
 * 移除重复代码，提高代码可维护性
 */
public class ConfigLoaderUtils {
    private static final Gson GSON = new Gson();
    
    /**
     * 通用的JSON配置文件加载方法
     * @param configFile 配置文件路径
     * @param fileName 文件名（用于日志）
     * @param itemProcessor 物品处理回调函数 (itemId, rarity) -> void
     * @return 成功加载的物品数量
     */
    public static int loadJsonConfigFile(Path configFile, String fileName, BiConsumer<String, Integer> itemProcessor) {
        int itemCount = 0;
        
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
            
            if (jsonObject != null && validateRarityJsonFormat(jsonObject)) {
                for (String itemIdString : jsonObject.keySet()) {
                    JsonElement rarityElement = jsonObject.get(itemIdString);
                    
                    if (rarityElement.isJsonPrimitive() && rarityElement.getAsJsonPrimitive().isNumber()) {
                        int rarity = rarityElement.getAsInt();
                        
                        // 验证稀有度范围
                        if (rarity < 1 || rarity > 7) {
                            Raritycore.LOGGER.warn("Invalid rarity value {} for item '{}' in file '{}'", 
                                rarity, itemIdString, fileName);
                            continue;
                        }
                        
                        Identifier itemId = new Identifier(itemIdString);
                        
                        // 处理物品稀有度
                        itemProcessor.accept(itemIdString, rarity);
                        itemCount++;
                    } else {
                        Raritycore.LOGGER.warn("Invalid rarity data format for item '{}' in file '{}'", 
                            itemIdString, fileName);
                    }
                }
                Raritycore.LOGGER.info("Loaded {} items from config file: {}", itemCount, fileName);
            }
        } catch (IOException e) {
            Raritycore.LOGGER.error("Cannot read config file: {}", configFile, e);
        } catch (JsonParseException e) {
            Raritycore.LOGGER.error("Config file format error: {}", fileName, e);
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
            Identifier itemId = new Identifier(itemIdString);
            
            if (useBatchProcessing) {
                // 使用批处理管理器
                // TODO: 实现批处理逻辑
                org.yanbwe.raritycore.registry.RarityRegistry.registerDatapackRarity(itemId, rarity);
            } else {
                // 直接注册到稀有度注册表
                org.yanbwe.raritycore.registry.RarityRegistry.registerDatapackRarity(itemId, rarity);
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
            Raritycore.LOGGER.error("Cannot create config directory: {}", configDir, e);
            return false;
        }
    }
    
    /**
     * 快速验证JSON对象的有效性
     * 在解析前快速检查数据格式
     */
    public static boolean validateRarityJsonFormat(JsonObject jsonObject) {
        if (jsonObject == null || jsonObject.size() == 0) {
            return false;
        }
        
        // 快速验证：检查前几个条目值格式
        int sampleSize = Math.min(5, jsonObject.size());
        int validCount = 0;
        
        for (String key : jsonObject.keySet()) {
            if (validCount >= sampleSize) break;
            
            try {
                // 检查值是否为有效的整数
                int value = jsonObject.get(key).getAsInt();
                if (value >= 1 && value <= 7) {
                    validCount++;
                }
            } catch (Exception e) {
                // 忽略无效条目
            }
        }
        
        // 如果大部分样本有效，则认为格式正确
        return validCount > 0;
    }
}