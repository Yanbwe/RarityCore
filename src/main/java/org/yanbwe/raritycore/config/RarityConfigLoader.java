package org.yanbwe.raritycore.config;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RarityConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 从配置文件加载稀有度数据
     */
    public static void loadConfigRarityData() {
        Path configDir = ConfigManager.getConfigDirPath();
        
        // 确保配置目录存在
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", configDir, e);
            return;
        }

        Path configFile = ConfigManager.getFinalRarityConfigPath();
        
        // 如果配置文件不存在，则创建默认配置文件
        if (!Files.exists(configFile)) {
            createDefaultConfig(configFile);
        }

        // 读取并加载配置文件内容
        loadRarityDataFromFile(configFile);
    }

    /**
     * 从文件加载稀有度数据
     */
    private static void loadRarityDataFromFile(Path configFile) {
        // 首先尝试使用优化的解析器
        if (tryOptimizedParsing(configFile)) {
            return;
        }
        
        // 回退到传统解析方式
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
            
            if (jsonObject != null && org.yanbwe.raritycore.util.JsonPerformanceOptimizer.validateRarityJsonFormat(jsonObject)) {
                for (String itemIdString : jsonObject.keySet()) {
                    JsonElement rarityElement = jsonObject.get(itemIdString);
                    
                    if (rarityElement.isJsonPrimitive() && rarityElement.getAsJsonPrimitive().isNumber()) {
                        int rarity = rarityElement.getAsInt();
                        
                        // 验证稀有度范围
                        if (rarity < RarityConstants.MIN_RARITY || rarity > RarityConstants.MAX_RARITY) {
                            continue;
                        }
                        
                        ResourceLocation itemId = new ResourceLocation(itemIdString);
                        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(itemId);

                        if (item == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                            continue; // 跳过未知物品
                        }
                        
                        // 注册稀有度，不自动同步到客户端
                        RarityRegistry.register(item, rarity, false);
                    }
                }
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("无法读取配置文件: {}", configFile, e);
        } catch (JsonParseException e) {
            RarityCore.LOGGER.error("配置文件格式错误: {}", configFile.toString(), e);
            // 尝试创建默认配置文件
            try {
                createDefaultConfig(configFile);
            } catch (Exception ex) {
                RarityCore.LOGGER.error("无法创建默认配置文件: {}", configFile, ex);
            }
        }
    }
    
    /**
     * 尝试使用优化的JSON解析
     */
    private static boolean tryOptimizedParsing(Path configFile) {
        try {
            org.yanbwe.raritycore.util.JsonPerformanceOptimizer.parseRarityConfigOptimized(configFile);
            return true;
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Optimized parsing failed, falling back to traditional method: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 创建默认配置文件
     */
    private static void createDefaultConfig(Path configFile) {
        JsonObject configObject = new JsonObject();
        
        // 写入默认配置内容
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            GSON.toJson(configObject, writer);
        } catch (IOException e) {
            RarityCore.LOGGER.error("无法创建默认配置文件: {}", configFile, e);
        }
    }
}