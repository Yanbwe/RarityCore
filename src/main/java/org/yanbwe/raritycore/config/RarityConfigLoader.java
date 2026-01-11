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
     * 加载配置文件中的稀有度数据
     */
    public static void loadConfigRarityData() {
        Path configDir = ConfigManager.getConfigDirPath();
        
        // 确保目录存在
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            RarityCore.LOGGER.error("无法创建配置目录: {}", configDir.toString(), e);
            return;
        }

        Path configFile = ConfigManager.getFinalRarityConfigPath();
        
        // 如果配置文件不存在，则创建一个默认的
        if (!Files.exists(configFile)) {
            createDefaultConfig(configFile);
        }

        // 读取并加载配置文件
        loadRarityDataFromFile(configFile);
    }

    /**
     * 从文件加载稀有度数据
     */
    private static void loadRarityDataFromFile(Path configFile) {
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
            
            if (jsonObject != null) {
                for (String itemIdString : jsonObject.keySet()) {
                    JsonElement rarityElement = jsonObject.get(itemIdString);
                    
                    if (rarityElement.isJsonPrimitive() && rarityElement.getAsJsonPrimitive().isNumber()) {
                        int rarity = rarityElement.getAsInt();
                        
                        ResourceLocation itemId = new ResourceLocation(itemIdString);
                        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(itemId);
                        
                        if (item == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                            RarityCore.LOGGER.warn("未知物品 '{}' 在配置文件 '{}'", itemIdString, configFile.toString());
                            continue;
                        }
                        
                        if (rarity < RarityConstants.MIN_RARITY || rarity > RarityConstants.MAX_RARITY) {
                            RarityCore.LOGGER.warn("无效的稀有度值 {} 对于物品 '{}' 在配置文件 '{}'", rarity, itemIdString, configFile.toString());
                            continue;
                        }
                        
                        // 注册稀有度（这将覆盖之前加载的任何数据），不自动同步到客户端
                        RarityRegistry.register(item, rarity, false);
                        RarityCore.LOGGER.debug("从配置文件加载物品稀有度: {} -> {}", itemIdString, rarity);
                    } else {
                        RarityCore.LOGGER.warn("无效的稀有度数据格式 对于物品 '{}' 在配置文件 '{}'", itemIdString, configFile.toString());
                    }
                }
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("无法读取配置文件: {}", configFile.toString(), e);
        } catch (JsonParseException e) {
            RarityCore.LOGGER.error("配置文件格式错误: {}", configFile.toString(), e);
            // 尝试创建默认配置文件
            try {
                createDefaultConfig(configFile);
            } catch (Exception ex) {
                RarityCore.LOGGER.error("无法创建默认配置文件: {}", configFile.toString(), ex);
            }
        }
    }

    /**
     * 创建默认配置文件
     */
    private static void createDefaultConfig(Path configFile) {
        JsonObject configObject = new JsonObject();
        
        // 写入默认配置文件
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            GSON.toJson(configObject, writer);
        } catch (IOException e) {
            RarityCore.LOGGER.error("无法创建默认配置文件: {}", configFile.toString(), e);
        }
    }
}