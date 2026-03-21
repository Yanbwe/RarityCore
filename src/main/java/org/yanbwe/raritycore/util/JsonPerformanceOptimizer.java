package org.yanbwe.raritycore.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON处理器
 * 提供高效的JSON解析和处理方法
 */
public class JsonPerformanceOptimizer {
    
    // 预配置的Gson实例,避免重复创建
    private static final Gson OPTIMIZED_GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()  // 禁用HTML转义以提升性能
        .create();
    
    /**
     * 高性能JSON文件解析
     * 使用流式解析减少内存占用
     */
    public static void parseRarityConfigOptimized(Path configFile) {
        try (BufferedReader reader = Files.newBufferedReader(configFile);
             JsonReader jsonReader = new JsonReader(reader)) {
            
            jsonReader.beginObject();
            
            int itemCount = 0;
            while (jsonReader.hasNext()) {
                String itemIdString = jsonReader.nextName();
                int rarity = jsonReader.nextInt();
                
                // 验证稀有度范围 - 支持高级稀有度(大于7)以符合模组包容性设计
                if (rarity < 0) { // 只限制负数,允许0表示删除,不限制上限
                    RarityCore.LOGGER.debug("Skipping invalid rarity {} for item {}", rarity, itemIdString);
                    continue;
                }
                
                // 注册物品稀有度或删除稀有度
                ResourceLocation itemId = ResourceLocation.parse(itemIdString);
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemId);
                
                if (item != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                    if (rarity == 0) {
                        // 稀有度为0表示删除该物品的稀有度配置
                        RarityRegistry.unregister(item, false);
                        RarityCore.LOGGER.debug("Removed rarity configuration for item: {}", itemIdString);
                    } else {
                        // 正常注册稀有度
                        RarityRegistry.register(item, rarity, false);
                    }
                    itemCount++;
                } else {
                    RarityCore.LOGGER.debug("Unknown item '{}' in config file '{}'", itemIdString, configFile.getFileName());
                }
            }
            
            jsonReader.endObject();
            RarityCore.LOGGER.info("Loaded {} items from optimized JSON config: {}", itemCount, configFile.getFileName());
            
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot read optimized JSON config file: {}", configFile, e);
        } catch (Exception e) {
            RarityCore.LOGGER.error("JSON parsing error in file: {}", configFile.toString(), e);
        }
    }
    
    /**
     * 获取高性能Gson实例
     */
    public static Gson getOptimizedGson() {
        return OPTIMIZED_GSON;
    }
    
    /**
     * 批量验证JSON对象的有效性
     * 在解析前快速检查数据格式
     */
    public static boolean validateRarityJsonFormat(JsonObject jsonObject) {
        if (jsonObject == null || jsonObject.size() == 0) {
            return false;
        }
        
        // 快速验证:检查前几个条目格式
        int sampleSize = Math.min(5, jsonObject.size());
        int validCount = 0;
        
        for (String key : jsonObject.keySet()) {
            if (validCount >= sampleSize) break;
            
            try {
                // 检查值是否为有效的整数 - 支持高级稀有度(大于7)
                int value = jsonObject.get(key).getAsInt();
                if (value >= 0) { // 只验证非负数,不限制上限
                    validCount++;
                }
            } catch (Exception e) {
                return false; // 格式无效
            }
        }
        
        // 如果样本中有足够的有效条目,则认为格式正确
        return validCount > 0;
    }
}