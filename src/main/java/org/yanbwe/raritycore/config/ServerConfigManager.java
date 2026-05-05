package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RarityCacheCoordinator;
import org.yanbwe.raritycore.util.JsonPerformanceOptimizer;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 服务端配置管理器
 * 管理影响服务端行为的配置选项
 */
public class ServerConfigManager {
    
    private static final Gson GSON = JsonPerformanceOptimizer.getOptimizedGson();
    
    // 服务端配置
    private static boolean checkVanillaRarity = RarityConstants.DEFAULT_CHECK_VANILLA_RARITY; // 是否检查原版稀有度
    private static boolean checkApotheosisRarity = RarityConstants.DEFAULT_CHECK_APOTHEOSIS_RARITY; // 是否检查神化模组稀有度
    private static boolean enableGetRarityWarning = RarityConstants.DEFAULT_ENABLE_GET_RARITY_WARNING; // 是否启用 getRarity() 可用性警告
    private static boolean enableComponentRarityControl = RarityConstants.DEFAULT_ENABLE_COMPONENT_RARITY_CONTROL; // 是否启用组件稀有度控制
    
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
        // 验证并更新配置文件
        JsonObject defaultConfig = ConfigValidator.createDefaultServerConfig();
        ConfigValidator.validateConfig(SERVER_CONFIG_FILE, defaultConfig, "server");
        
        // 如果配置文件不存在,则创建一个默认的
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
        try (BufferedReader reader = Files.newBufferedReader(SERVER_CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
            
            if (jsonObject != null) {
                // 读取原版稀有度检查设置
                if (jsonObject.has("checkVanillaRarity")) {
                    checkVanillaRarity = jsonObject.get("checkVanillaRarity").getAsBoolean();
                } else {
                    checkVanillaRarity = RarityConstants.DEFAULT_CHECK_VANILLA_RARITY;
                }
                
                // 读取神化稀有度检查设置
                if (jsonObject.has("checkApotheosisRarity")) {
                    checkApotheosisRarity = jsonObject.get("checkApotheosisRarity").getAsBoolean();
                } else {
                    checkApotheosisRarity = RarityConstants.DEFAULT_CHECK_APOTHEOSIS_RARITY; // 使用常量
                }
                
                // 读取 getRarity 警告开关设置
                if (jsonObject.has("enableGetRarityWarning")) {
                    enableGetRarityWarning = jsonObject.get("enableGetRarityWarning").getAsBoolean();
                } else {
                    enableGetRarityWarning = RarityConstants.DEFAULT_ENABLE_GET_RARITY_WARNING;
                }
                
                // 读取组件稀有度控制设置
                if (jsonObject.has("enableComponentRarityControl")) {
                    enableComponentRarityControl = jsonObject.get("enableComponentRarityControl").getAsBoolean();
                } else {
                    enableComponentRarityControl = RarityConstants.DEFAULT_ENABLE_COMPONENT_RARITY_CONTROL;
                }
                
                RarityCore.LOGGER.info("Server config loaded successfully: checkVanillaRarity={}, checkApotheosisRarity={}, enableGetRarityWarning={}, enableComponentRarityControl={}", 
                    checkVanillaRarity, checkApotheosisRarity, enableGetRarityWarning, enableComponentRarityControl);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading server config file, using default config: {}", SERVER_CONFIG_FILE, e);
            // 出错时使用默认值
            checkVanillaRarity = RarityConstants.DEFAULT_CHECK_VANILLA_RARITY;
            checkApotheosisRarity = RarityConstants.DEFAULT_CHECK_APOTHEOSIS_RARITY;
            enableGetRarityWarning = RarityConstants.DEFAULT_ENABLE_GET_RARITY_WARNING;
            enableComponentRarityControl = RarityConstants.DEFAULT_ENABLE_COMPONENT_RARITY_CONTROL;
            // 重新创建配置文件以恢复默认设置
            createDefaultServerConfig();
        }
    }
    
    /**
     * 创建默认服务端配置文件
     */
    private static void createDefaultServerConfig() {
        // 创建默认配置对象
        JsonObject configObject = ConfigValidator.createDefaultServerConfig();
        
        // 写入默认配置文件
        try {
            try (Writer writer = Files.newBufferedWriter(SERVER_CONFIG_FILE, StandardCharsets.UTF_8)) {
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
        // 创建配置对象
        JsonObject configObject = new JsonObject();
        configObject.addProperty("checkVanillaRarity", checkVanillaRarity);
        configObject.addProperty("checkApotheosisRarity", checkApotheosisRarity);
        configObject.addProperty("enableGetRarityWarning", enableGetRarityWarning);
        configObject.addProperty("enableComponentRarityControl", enableComponentRarityControl);
        
        // 写入配置文件
        try {
            try (Writer writer = Files.newBufferedWriter(SERVER_CONFIG_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Server config saved: checkVanillaRarity={}, checkApotheosisRarity={}, enableGetRarityWarning={}, enableComponentRarityControl={}", 
                    checkVanillaRarity, checkApotheosisRarity, enableGetRarityWarning, enableComponentRarityControl);
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
     * 获取是否检查神化模组稀有度
     */
    public static boolean isCheckApotheosisRarity() {
        return checkApotheosisRarity;
    }
    
    /**
     * 设置是否检查神化模组稀有度
     */
    public static void setCheckApotheosisRarity(boolean check) {
        if (checkApotheosisRarity != check) {
            checkApotheosisRarity = check;
            // 通知相关系统配置已变更
            notifyConfigChange();
        }
    }
    
    /**
     * 获取是否启用 getRarity() 可用性警告
     */
    public static boolean isEnableGetRarityWarning() {
        return enableGetRarityWarning;
    }
    
    /**
     * 设置是否启用 getRarity() 可用性警告
     */
    public static void setEnableGetRarityWarning(boolean enable) {
        if (enableGetRarityWarning != enable) {
            enableGetRarityWarning = enable;
            // 通知相关系统配置已变更
            notifyConfigChange();
        }
    }
    
    /**
     * 获取是否启用组件稀有度控制
     */
    public static boolean isEnableComponentRarityControl() {
        return enableComponentRarityControl;
    }
    
    /**
     * 设置是否启用组件稀有度控制
     */
    public static void setEnableComponentRarityControl(boolean enable) {
        if (enableComponentRarityControl != enable) {
            enableComponentRarityControl = enable;
            // 通知相关系统配置已变更
            notifyConfigChange();
        }
    }
    
 

    
    /**
     * 通知配置变更
     */
    private static void notifyConfigChange() {
        RarityCore.LOGGER.info("Server configuration changed, invalidating caches...");
        RarityCacheCoordinator.handleConfigReload();
    }
}