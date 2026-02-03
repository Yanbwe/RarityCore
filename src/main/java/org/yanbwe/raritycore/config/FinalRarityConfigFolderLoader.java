package org.yanbwe.raritycore.config;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * FinalRarityConfig文件夹加载器
 * 负责加载config/raritycore/FinalRarityConfig文件夹中的所有JSON配置文件
 */
public class FinalRarityConfigFolderLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 加载FinalRarityConfig文件夹中的所有JSON文件
     * 文件按字母顺序加载，后加载的会覆盖先加载的同名物品配置
     */
    public static void loadFinalRarityConfigFolder() {
        Path configFolder = ConfigManager.getFinalRarityConfigFolderPath();
        
        // 确保目录存在
        try {
            Files.createDirectories(configFolder);
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create FinalRarityConfig directory: {}", configFolder, e);
            return;
        }

        // 检查目录是否存在且是目录
        if (!Files.exists(configFolder) || !Files.isDirectory(configFolder)) {
            RarityCore.LOGGER.info("FinalRarityConfig folder not found, skipping: {}", configFolder);
            return;
        }

        try {
            // 获取所有JSON文件并按名称排序
            Path[] jsonFiles = Files.list(configFolder)
                .filter(path -> path.toString().endsWith(".json"))
                .sorted()
                .toArray(Path[]::new);
            
            RarityCore.LOGGER.info("Found {} JSON files in FinalRarityConfig folder", jsonFiles.length);
            
            // 按顺序加载所有JSON文件
            for (Path jsonFile : jsonFiles) {
                RarityCore.LOGGER.info("Loading FinalRarityConfig file: {}", jsonFile.getFileName());
                loadRarityDataFromFile(jsonFile);
            }
            
        } catch (IOException e) {
            RarityCore.LOGGER.error("Error reading FinalRarityConfig folder: {}", configFolder, e);
        }
    }

    /**
     * 从单个JSON文件加载稀有度数据
     */
    private static void loadRarityDataFromFile(Path configFile) {
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
            
            if (jsonObject != null) {
                int itemCount = 0;
                for (String itemIdString : jsonObject.keySet()) {
                    JsonElement rarityElement = jsonObject.get(itemIdString);
                    
                    if (rarityElement.isJsonPrimitive() && rarityElement.getAsJsonPrimitive().isNumber()) {
                        int rarity = rarityElement.getAsInt();
                        
                        ResourceLocation itemId = new ResourceLocation(itemIdString);
                        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(itemId);

                        if (item == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                            RarityCore.LOGGER.debug("Unknown item '{}' in FinalRarityConfig file '{}'", 
                                itemIdString, configFile.getFileName());
                            continue; // 跳过未知物品
                        }
                        
                        // 使用批处理管理器注册稀有度
                        org.yanbwe.raritycore.network.ChangeOperation operation = 
                            new org.yanbwe.raritycore.network.ChangeOperation(
                                org.yanbwe.raritycore.network.ChangeOperation.OperationType.ADD,
                                itemId, 
                                rarity
                            );
                        org.yanbwe.raritycore.network.SyncBatchManager.addOperation(operation);
                        itemCount++;
                    } else {
                        RarityCore.LOGGER.warn("Invalid rarity data format for item '{}' in file '{}'", 
                            itemIdString, configFile.getFileName());
                    }
                }
                RarityCore.LOGGER.info("Loaded {} items from FinalRarityConfig file: {}", 
                    itemCount, configFile.getFileName());
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot read FinalRarityConfig file: {}", configFile, e);
        } catch (JsonParseException e) {
            RarityCore.LOGGER.error("FinalRarityConfig file format error: {}", configFile.toString(), e);
        }
    }
}