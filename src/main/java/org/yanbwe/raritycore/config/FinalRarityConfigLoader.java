package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.yanbwe.raritycore.Raritycore;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.ConfigLoaderUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * FinalRarity配置文件加载器
 * 直接复制Forge版本的核心逻辑
 * 负责加载和管理FinalRarity.json配置文件
 * 按照Forge版本的优先级顺序：数据包 > FinalRarityConfig文件夹 > FinalRarity.json
 */
public class FinalRarityConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("raritycore");
    private static final Path FINAL_RARITY_FILE = CONFIG_DIR.resolve("FinalRarity.json");
    private static final Path FINAL_RARITY_CONFIG_FOLDER = CONFIG_DIR.resolve("FinalRarityConfig");
    
    /**
     * 从配置文件加载稀有度数据
     */
    public static void loadFinalRarityConfig() {
        Path configDir = CONFIG_DIR;
        
        // 确保配置目录存在
        ConfigLoaderUtils.ensureConfigDirectoryExists(configDir);

        Path configFile = FINAL_RARITY_FILE;
        
        // 如果配置文件不存在，则创建默认配置
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
        // 首先尝试使用高性能解析器
        if (tryOptimizedParsing(configFile)) {
            return;
        }
        
        // 使用通用工具类加载配置
        ConfigLoaderUtils.loadJsonConfigFileWithBatch(configFile, configFile.getFileName().toString(), true);
    }
    
    /**
     * 尝试使用高性能JSON解析
     */
    private static boolean tryOptimizedParsing(Path configFile) {
        try {
            // TODO: 实现高性能解析逻辑
            return false;
        } catch (Exception e) {
            Raritycore.LOGGER.debug("Optimized parsing failed, falling back to traditional method: {}", e.getMessage());
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
            Raritycore.LOGGER.error("无法创建默认配置文件: {}", configFile, e);
        }
    }
    
    /**
     * 加载FinalRarityConfig文件夹中的配置文件（中等优先级）
     * 文件按字母顺序加载，后面的文件会覆盖前面的配置
     */
    public static void loadFinalRarityConfigFolder() {
        try {
            // 确保配置目录存在
            Files.createDirectories(FINAL_RARITY_CONFIG_FOLDER);
            
            // 检查文件夹是否存在且是目录
            if (!Files.exists(FINAL_RARITY_CONFIG_FOLDER) || !Files.isDirectory(FINAL_RARITY_CONFIG_FOLDER)) {
                Raritycore.LOGGER.info("FinalRarityConfig folder not found, skipping: {}", FINAL_RARITY_CONFIG_FOLDER);
                return;
            }
            
            // 获取所有JSON文件并按名称排序
            Path[] jsonFiles = Files.list(FINAL_RARITY_CONFIG_FOLDER)
                .filter(path -> path.toString().endsWith(".json"))
                .sorted()
                .toArray(Path[]::new);
            
            Raritycore.LOGGER.info("Found {} JSON files in FinalRarityConfig folder", jsonFiles.length);
            
            // 按顺序加载所有JSON文件
            for (Path jsonFile : jsonFiles) {
                Raritycore.LOGGER.info("Loading FinalRarityConfig file: {}", jsonFile.getFileName());
                loadRarityDataFromFile(jsonFile);
            }
            
        } catch (IOException e) {
            Raritycore.LOGGER.error("Error reading FinalRarityConfig folder: {}", FINAL_RARITY_CONFIG_FOLDER, e);
        }
    }
    
    /**
     * 创建默认的FinalRarity.json配置文件
     */
    private static void createDefaultFinalRarityConfig() {
        JsonObject config = new JsonObject();
        // 简单格式：直接是物品ID到稀有度的映射
        config.addProperty("minecraft:diamond", 4);
        config.addProperty("minecraft:netherite_ingot", 5);
        config.addProperty("minecraft:enchanted_golden_apple", 6);
        config.addProperty("minecraft:dragon_egg", 7);
        config.addProperty("minecraft:emerald", 4);
        config.addProperty("minecraft:gold_ingot", 3);
        config.addProperty("minecraft:iron_ingot", 2);
        
        try {
            Files.writeString(FINAL_RARITY_FILE, GSON.toJson(config));
            Raritycore.LOGGER.info("Created default FinalRarity.json configuration file");
        } catch (IOException e) {
            Raritycore.LOGGER.error("Failed to create default FinalRarity.json file", e);
        }
    }
    
    /**
     * 检查FinalRarity.json文件是否存在
     */
    public static boolean isFinalRarityConfigExists() {
        return Files.exists(FINAL_RARITY_FILE);
    }
    
    /**
     * 获取FinalRarity.json文件路径
     */
    public static Path getFinalRarityFilePath() {
        return FINAL_RARITY_FILE;
    }
    
    /**
     * 获取FinalRarityConfig文件夹路径
     */
    public static Path getFinalRarityConfigFolderPath() {
        return FINAL_RARITY_CONFIG_FOLDER;
    }
    
    /**
     * 保存稀有度配置到FinalRarity.json文件
     * @param itemId 物品ID
     * @param rarity 稀有度等级
     */
    public static void saveRarityToFinalRarity(String itemId, int rarity) {
        try {
            // 确保配置目录存在
            Files.createDirectories(CONFIG_DIR);
            
            JsonObject config;
            
            // 如果文件存在，读取现有配置
            if (Files.exists(FINAL_RARITY_FILE)) {
                String content = Files.readString(FINAL_RARITY_FILE);
                config = GSON.fromJson(content, JsonObject.class);
                if (config == null) {
                    config = new JsonObject();
                }
            } else {
                // 创建新的配置对象
                config = new JsonObject();
            }
            
            // 更新或添加稀有度配置
            config.addProperty(itemId, rarity);
            
            // 保存到文件
            Files.writeString(FINAL_RARITY_FILE, GSON.toJson(config));
            Raritycore.LOGGER.info("Saved rarity {} for item {} to FinalRarity.json", rarity, itemId);
            
        } catch (Exception e) {
            Raritycore.LOGGER.error("Failed to save rarity {} for item {} to FinalRarity.json", rarity, itemId, e);
        }
    }
    
    /**
     * 从FinalRarity.json中移除指定物品的稀有度配置
     * @param itemId 物品ID
     */
    public static void removeRarityFromFinalRarity(String itemId) {
        try {
            // 如果文件不存在，直接返回
            if (!Files.exists(FINAL_RARITY_FILE)) {
                return;
            }
            
            String content = Files.readString(FINAL_RARITY_FILE);
            JsonObject config = GSON.fromJson(content, JsonObject.class);
            
            if (config != null && config.has(itemId)) {
                config.remove(itemId);
                Files.writeString(FINAL_RARITY_FILE, GSON.toJson(config));
                Raritycore.LOGGER.info("Removed rarity configuration for item {} from FinalRarity.json", itemId);
            }
            
        } catch (Exception e) {
            Raritycore.LOGGER.error("Failed to remove rarity for item {} from FinalRarity.json", itemId, e);
        }
    }
}