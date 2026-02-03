package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 服务端配置管理器
 * 管理影响服务端行为的配置选项
 */
public class ServerConfigManager {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // 服务端配置
    private static boolean checkVanillaRarity = RarityConstants.DEFAULT_CHECK_VANILLA_RARITY; // 是否检查原版稀有度
    private static boolean skipUnconfiguredItems = RarityConstants.DEFAULT_SKIP_UNCONFIGURED_ITEMS; // 是否跳过未配置物品的处理
    
    // 配置文件路径
    private static final Path CONFIG_DIR = Paths.get(RarityConstants.CONFIG_DIR_PARENT).resolve(RarityConstants.CONFIG_DIR_NAME);
    private static final Path SERVER_CONFIG_FILE = CONFIG_DIR.resolve(RarityConstants.SERVER_CONFIG_FILE_NAME);
    
    /**
     * 获取服务端配置路径
     */
    public static Path getServerConfigPath() {
        return SERVER_CONFIG_FILE;
    }
    
    /**
     * 初始化服务端配置
     */
    public static void initializeServerConfigs() {
        try {
            // 确保配置目录存在
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }
        
        // 加载服务端配置
        loadServerConfig();
    }
    
    /**
     * 加载服务端配置
     */
    public static void loadServerConfig() {
        // 如果配置文件不存在，则创建一个默认的
        if (!Files.exists(SERVER_CONFIG_FILE)) {
            createDefaultServerConfig();
        }

        // 读取并加载配置文件
        loadServerConfigFromFile();
    }
    
    /**
     * 从文件加载服务端配置
     */
    private static void loadServerConfigFromFile() {
        try (BufferedReader reader = Files.newBufferedReader(SERVER_CONFIG_FILE)) {
            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
            
            if (jsonObject != null) {
                // 读取原版稀有度检查设置
                if (jsonObject.has("checkVanillaRarity")) {
                    checkVanillaRarity = jsonObject.get("checkVanillaRarity").getAsBoolean();
                } else {
                    checkVanillaRarity = RarityConstants.DEFAULT_CHECK_VANILLA_RARITY;
                }
                
                // 读取未配置物品跳过设置
                if (jsonObject.has("skipUnconfiguredItems")) {
                    skipUnconfiguredItems = jsonObject.get("skipUnconfiguredItems").getAsBoolean();
                } else {
                    skipUnconfiguredItems = RarityConstants.DEFAULT_SKIP_UNCONFIGURED_ITEMS;
                }
                
                RarityCore.LOGGER.info("Server config loaded successfully: checkVanillaRarity={}, skipUnconfiguredItems={}", 
                    checkVanillaRarity, skipUnconfiguredItems);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading server config file, using default config: {}", SERVER_CONFIG_FILE, e);
            // 出错时使用默认值
            checkVanillaRarity = RarityConstants.DEFAULT_CHECK_VANILLA_RARITY;
            skipUnconfiguredItems = RarityConstants.DEFAULT_SKIP_UNCONFIGURED_ITEMS;
            // 重新创建配置文件以恢复默认设置
            createDefaultServerConfig();
        }
    }
    
    /**
     * 创建默认服务端配置文件
     */
    private static void createDefaultServerConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty("checkVanillaRarity", RarityConstants.DEFAULT_CHECK_VANILLA_RARITY);
        configObject.addProperty("skipUnconfiguredItems", RarityConstants.DEFAULT_SKIP_UNCONFIGURED_ITEMS);
        
        // 写入默认配置文件
        try {
            try (FileWriter writer = new FileWriter(SERVER_CONFIG_FILE.toString())) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Created default server config file: {}", SERVER_CONFIG_FILE);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create default server config file: {}", SERVER_CONFIG_FILE, e);
        }
    }
    
    /**
     * 保存服务端配置到文件
     */
    public static void saveServerConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty("checkVanillaRarity", checkVanillaRarity);
        configObject.addProperty("skipUnconfiguredItems", skipUnconfiguredItems);
        
        // 写入配置文件
        try {
            try (FileWriter writer = new FileWriter(SERVER_CONFIG_FILE.toString())) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Server config saved: checkVanillaRarity={}, skipUnconfiguredItems={}", 
                    checkVanillaRarity, skipUnconfiguredItems);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot save server config file: {}", SERVER_CONFIG_FILE, e);
        }
    }
    
    /**
     * 获取是否检查原版稀有度
     */
    public static boolean isCheckVanillaRarity() {
        return checkVanillaRarity;
    }
    
    /**
     * 设置是否检查原版稀有度
     */
    public static void setCheckVanillaRarity(boolean check) {
        if (checkVanillaRarity != check) {
            checkVanillaRarity = check;
            // 通知相关系统配置已变更
            notifyConfigChange();
        }
    }
    
    /**
     * 获取是否跳过未配置物品
     */
    public static boolean isSkipUnconfiguredItems() {
        return skipUnconfiguredItems;
    }
    
    /**
     * 设置是否跳过未配置物品
     */
    public static void setSkipUnconfiguredItems(boolean skip) {
        if (skipUnconfiguredItems != skip) {
            skipUnconfiguredItems = skip;
            // 通知相关系统配置已变更
            notifyConfigChange();
        }
    }
    
    /**
     * 通知配置变更
     */
    private static void notifyConfigChange() {
        // 简单的日志记录，实际的重新加载将在下次数据加载时发生
        RarityCore.LOGGER.info("Server configuration changed, will apply on next data reload");
    }
}