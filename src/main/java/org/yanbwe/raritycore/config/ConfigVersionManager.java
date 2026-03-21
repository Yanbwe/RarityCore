package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置版本管理器
 * 负责配置文件的版本控制、升级和迁移
 */
public class ConfigVersionManager {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // 当前配置版本号
    public static final int CURRENT_CONFIG_VERSION = 6;
    
    // 版本升级处理器映射
    private static final Map<Integer, ConfigUpgradeHandler> UPGRADE_HANDLERS = new HashMap<>();
    
    // 配置文件中的版本键名
    private static final String VERSION_KEY = "config_version";
    private static final String MOD_VERSION_KEY = "mod_version";
    
    static {
        // 注册版本升级处理器
        registerUpgradeHandlers();
    }
    
    /**
     * 配置升级处理器接口
     */
    @FunctionalInterface
    public interface ConfigUpgradeHandler {
        /**
         * 升级配置
         * @param oldConfig 旧配置对象
         * @param fromVersion 来源版本
         * @param toVersion 目标版本
         * @return 升级后的配置对象
         */
        JsonObject upgrade(JsonObject oldConfig, int fromVersion, int toVersion);
    }
    
    /**
     * 注册所有版本升级处理器
     */
    private static void registerUpgradeHandlers() {
        // 从版本1升级到版本2的处理器
        UPGRADE_HANDLERS.put(1, (oldConfig, from, to) -> {
            RarityCore.LOGGER.info("Upgrading config from version {} to {}", from, to);
            
            // 版本1到2的升级:添加checkVanillaRarity配置项
            if (!oldConfig.has("checkVanillaRarity")) {
                oldConfig.addProperty("checkVanillaRarity", RarityConstants.DEFAULT_CHECK_VANILLA_RARITY);
                RarityCore.LOGGER.info("Added checkVanillaRarity config option");
            }
            
            // 添加skipUnconfiguredItems配置项
            if (!oldConfig.has("skipUnconfiguredItems")) {
                oldConfig.addProperty("skipUnconfiguredItems", RarityConstants.DEFAULT_SKIP_UNCONFIGURED_ITEMS);
                RarityCore.LOGGER.info("Added skipUnconfiguredItems config option");
            }
            
            return oldConfig;
        });
        
        // 从版本2升级到版本3的处理器
        UPGRADE_HANDLERS.put(2, (oldConfig, from, to) -> {
            RarityCore.LOGGER.info("Upgrading config from version {} to {}", from, to);
            // 版本2到3的升级:添加批量处理配置(客户端)
            if (!oldConfig.has("enableBatchProcessing")) {
                oldConfig.addProperty("enableBatchProcessing", true);
                RarityCore.LOGGER.info("Added enableBatchProcessing config option");
            }
            
            // 版本2到3的升级:添加缓存系统配置
            if (!oldConfig.has("enableCacheSystem")) {
                oldConfig.addProperty("enableCacheSystem", RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM);
                RarityCore.LOGGER.info("Added enableCacheSystem config option");
            }
            
            return oldConfig;
        });
        
        // 从版本 3 升级到版本 4 的处理器
        UPGRADE_HANDLERS.put(3, (oldConfig, from, to) -> {
            RarityCore.LOGGER.info("Upgrading config from version {} to {}", from, to);
                    
            // 版本 3 到 4 的升级:添加星星显示配置
            JsonObject starDisplay = new JsonObject();
            starDisplay.addProperty("enabled", true);
            starDisplay.addProperty("mode", RarityConstants.DEFAULT_STAR_MODE); // 引用常量
                    
            JsonObject repeatConfig = new JsonObject();
            repeatConfig.addProperty("character", RarityConstants.DEFAULT_REPEAT_CHARACTER);
            starDisplay.add("repeat", repeatConfig);
                    
            JsonObject customConfig = new JsonObject();
            JsonObject customStrings = new JsonObject();
            // 使用常量数组添加默认的自定义字符串配置
            for (int i = 0; i < RarityConstants.DEFAULT_CUSTOM_STRINGS.length; i++) {
                customStrings.addProperty(String.valueOf(i + 1), RarityConstants.DEFAULT_CUSTOM_STRINGS[i]);
            }
            customConfig.add("strings", customStrings);
            starDisplay.add("custom", customConfig);
                    
            oldConfig.add("starDisplay", starDisplay);
            RarityCore.LOGGER.info("Added star display configuration");
                    
            return oldConfig;
        });
                
        // 从版本 4 升级到版本 5 的处理器
        UPGRADE_HANDLERS.put(4, (oldConfig, from, to) -> {
            RarityCore.LOGGER.info("Upgrading config from version {} to {}", from, to);
                    
            // 版本 4 到 5 的升级:添加 enableGetRarityWarning 配置项(服务端)
            if (!oldConfig.has("enableGetRarityWarning")) {
                oldConfig.addProperty("enableGetRarityWarning", RarityConstants.DEFAULT_ENABLE_GET_RARITY_WARNING);
                RarityCore.LOGGER.info("Added enableGetRarityWarning config option");
            }
                    
            return oldConfig;
        });
        
        // 从版本 5 升级到版本 6 的处理器
        UPGRADE_HANDLERS.put(5, (oldConfig, from, to) -> {
            RarityCore.LOGGER.info("Upgrading config from version {} to {}", from, to);
            
            // 版本 5 到 6 的升级:添加特殊稀有度文本自定义配置
            if (oldConfig.has("starDisplay")) {
                JsonObject starDisplayObj = oldConfig.getAsJsonObject("starDisplay");
                
                if (starDisplayObj.has("custom")) {
                    JsonObject customObj = starDisplayObj.getAsJsonObject("custom");
                    
                    // 添加空的特殊稀有度文本配置对象(如果不存在)
                    if (!customObj.has("specialRarityTexts")) {
                        JsonObject specialRarityTexts = new JsonObject();
                        customObj.add("specialRarityTexts", specialRarityTexts);
                        RarityCore.LOGGER.info("Added specialRarityTexts config for custom rarity display (> level 7)");
                    }
                }
            }
            
            return oldConfig;
        });
    }
    
    /**
     * 检查并升级配置文件
     * @param configFile 配置文件路径
     * @param configType 配置类型描述(用于日志)
     * @return 升级后的配置版本号
     */
    public static int checkAndUpgradeConfig(Path configFile, String configType) {
        try {
            // 如果配置文件不存在,直接返回当前版本(将创建默认配置)
            if (!Files.exists(configFile)) {
                RarityCore.LOGGER.info("Config file {} does not exist, will create with version {}", 
                    configType, CURRENT_CONFIG_VERSION);
                return CURRENT_CONFIG_VERSION;
            }
            
            // 读取现有配置
            JsonObject configObject;
            try (BufferedReader reader = Files.newBufferedReader(configFile)) {
                configObject = GSON.fromJson(reader, JsonObject.class);
            }
            
            if (configObject == null) {
                RarityCore.LOGGER.warn("Config file {} is invalid, recreating with current version", configType);
                return CURRENT_CONFIG_VERSION;
            }
            
            // 获取当前配置版本
            int currentVersion = getConfigVersion(configObject);
            
            // 如果已经是最新版本,无需升级
            if (currentVersion >= CURRENT_CONFIG_VERSION) {
                RarityCore.LOGGER.debug("Config {} is already at latest version {}", configType, currentVersion);
                return currentVersion;
            }
            
            // 执行版本升级
            RarityCore.LOGGER.info("Upgrading {} config from version {} to {}", 
                configType, currentVersion, CURRENT_CONFIG_VERSION);
            
            JsonObject upgradedConfig = upgradeConfig(configObject, currentVersion, CURRENT_CONFIG_VERSION);
            
            // 添加版本信息
            upgradedConfig.addProperty(VERSION_KEY, CURRENT_CONFIG_VERSION);
            // 不再添加mod_version字段
            
            // 保存升级后的配置
            try (FileWriter writer = new FileWriter(configFile.toFile())) {
                GSON.toJson(upgradedConfig, writer);
            }
            
            RarityCore.LOGGER.info("{} config successfully upgraded to version {}", configType, CURRENT_CONFIG_VERSION);
            return CURRENT_CONFIG_VERSION;
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to upgrade {} config, using current version: {}", configType, e.getMessage());
            return CURRENT_CONFIG_VERSION;
        }
    }
    
    /**
     * 获取配置对象的版本号
     * @param configObject 配置对象
     * @return 配置版本号,如果不存在则返回0
     */
    private static int getConfigVersion(JsonObject configObject) {
        if (configObject.has(VERSION_KEY)) {
            return configObject.get(VERSION_KEY).getAsInt();
        }
        // 如果没有版本号,默认为版本0(最老的版本)
        return 0;
    }
    
    /**
     * 升级配置对象
     * @param configObject 要升级的配置对象
     * @param fromVersion 起始版本
     * @param toVersion 目标版本
     * @return 升级后的配置对象
     */
    private static JsonObject upgradeConfig(JsonObject configObject, int fromVersion, int toVersion) {
        JsonObject currentConfig = configObject.deepCopy();
        
        // 逐步升级,从fromVersion到toVersion
        for (int version = fromVersion + 1; version <= toVersion; version++) {
            ConfigUpgradeHandler handler = UPGRADE_HANDLERS.get(version - 1);
            if (handler != null) {
                currentConfig = handler.upgrade(currentConfig, version - 1, version);
            } else {
                RarityCore.LOGGER.warn("No upgrade handler found for version {} to {}", version - 1, version);
            }
        }
        
        return currentConfig;
    }
    
    /**
     * 创建带版本信息的新配置对象
     * @return 包含版本信息的默认配置对象
     */
    public static JsonObject createVersionedConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty(VERSION_KEY, CURRENT_CONFIG_VERSION);
        // 不再添加mod_version字段,只保留config_version就足够了
        return configObject;
    }
    
    /**
     * 验证配置版本兼容性
     * @param configVersion 配置文件版本
     * @return 是否兼容
     */
    public static boolean isVersionCompatible(int configVersion) {
        // 当前实现:只要配置版本不超过当前版本就是兼容的
        // 未来可以实现更复杂的兼容性检查
        return configVersion <= CURRENT_CONFIG_VERSION;
    }
    
    /**
     * 获取配置升级统计信息
     * @return 升级统计信息字符串
     */
    public static String getUpgradeStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Config Version Manager Stats:\n");
        stats.append("Current Config Version: ").append(CURRENT_CONFIG_VERSION).append("\n");
        stats.append("Registered Upgrade Handlers: ").append(UPGRADE_HANDLERS.size()).append("\n");
        stats.append("Supported Versions: 0 to ").append(CURRENT_CONFIG_VERSION).append("\n");
        
        for (Map.Entry<Integer, ConfigUpgradeHandler> entry : UPGRADE_HANDLERS.entrySet()) {
            stats.append("  Version ").append(entry.getKey()).append(" -> ").append(entry.getKey() + 1).append(" handler registered\n");
        }
        
        return stats.toString();
    }
}