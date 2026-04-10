package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置验证器
 * 负责检测和添加缺失的配置项
 */
public class ConfigValidator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 验证并更新配置文件
     * @param configFile 配置文件路径
     * @param defaultConfig 默认配置对象
     * @param configType 配置类型描述(用于日志)
     * @return 验证后的配置对象
     */
    public static JsonObject validateConfig(Path configFile, JsonObject defaultConfig, String configType) {
        try {
            // 如果配置文件不存在,创建默认配置文件
            if (!Files.exists(configFile)) {
                RarityCore.LOGGER.info("Config file {} does not exist, creating with default values", configType);
                saveConfigFile(configFile, defaultConfig);
                return defaultConfig;
            }

            // 读取现有配置
            JsonObject configObject;
            try (BufferedReader reader = Files.newBufferedReader(configFile)) {
                configObject = GSON.fromJson(reader, JsonObject.class);
            }

            if (configObject == null) {
                RarityCore.LOGGER.warn("Config file {} is invalid, recreating with default values", configType);
                saveConfigFile(configFile, defaultConfig);
                return defaultConfig;
            }

            // 递归检测并添加缺失的配置项
            boolean[] hasMissingItems = {false};
            JsonObject updatedConfig = mergeConfigRecursive(configObject, defaultConfig, configType, hasMissingItems);

            // 如果有缺失项,保存更新后的配置
            if (hasMissingItems[0]) {
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile.toFile()), StandardCharsets.UTF_8)) {
                    GSON.toJson(updatedConfig, writer);
                    RarityCore.LOGGER.info("{} config updated with missing options", configType);
                }
            }

            return updatedConfig;

        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to validate {} config: {}", configType, e.getMessage());
            return defaultConfig;
        }
    }

    /**
     * 保存配置到文件
     * @param configFile 配置文件路径
     * @param config 配置对象
     */
    private static void saveConfigFile(Path configFile, JsonObject config) {
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            RarityCore.LOGGER.info("Config file saved: {}", configFile);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to save config file: {}", configFile, e);
        }
    }

    /**
     * 递归合并配置对象,添加缺失的配置项
     * @param existingConfig 现有配置对象
     * @param defaultConfig 默认配置对象
     * @param configType 配置类型描述
     * @param hasMissingItems 是否有缺失项的标志
     * @return 合并后的配置对象
     */
    private static JsonObject mergeConfigRecursive(JsonObject existingConfig, JsonObject defaultConfig, String configType, boolean[] hasMissingItems) {
        JsonObject result = existingConfig.deepCopy();

        for (String key : defaultConfig.keySet()) {
            if (!result.has(key)) {
                // 配置项不存在,直接添加
                result.add(key, defaultConfig.get(key));
                RarityCore.LOGGER.info("Added missing config option '{}' to {} config", key, configType);
                hasMissingItems[0] = true;
            } else if (defaultConfig.get(key).isJsonObject() && result.get(key).isJsonObject()) {
                // 两个都是JSON对象,递归合并
                JsonObject merged = mergeConfigRecursive(
                    result.getAsJsonObject(key),
                    defaultConfig.getAsJsonObject(key),
                    configType + "." + key,
                    hasMissingItems
                );
                result.add(key, merged);
            }
        }

        return result;
    }

    /**
     * 创建默认客户端配置对象
     * @return 默认客户端配置对象
     */
    public static JsonObject createDefaultClientConfig() {
        JsonObject configObject = new JsonObject();
        
        // 基本配置
        configObject.addProperty("enableItemBorderRendering", true);
        configObject.addProperty("itemBorderStyle", 1);
        configObject.addProperty("useTextureBorder", true);
        configObject.addProperty("enableItemNameColor", true);
        configObject.addProperty("enableTooltipColor", true);
        configObject.addProperty("enableTooltipInsert", true);
        configObject.addProperty("skipUnconfiguredItems", false);
        configObject.addProperty("enableBatchProcessing", true);
        configObject.addProperty("enableCacheSystem", true);
        
        // 星星显示配置
        JsonObject starDisplay = new JsonObject();
        starDisplay.addProperty("enabled", true);
        starDisplay.addProperty("mode", "repeat");
        
        JsonObject repeatConfig = new JsonObject();
        repeatConfig.addProperty("character", "★");
        starDisplay.add("repeat", repeatConfig);
        
        JsonObject customConfig = new JsonObject();
        JsonObject customStrings = new JsonObject();
        customStrings.addProperty("1", "☆☆☆☆☆");
        customStrings.addProperty("2", "★☆☆☆☆");
        customStrings.addProperty("3", "★★☆☆☆");
        customStrings.addProperty("4", "★★★☆☆");
        customStrings.addProperty("5", "★★★★☆");
        customStrings.addProperty("6", "★★★★★");
        customStrings.addProperty("7", "★★★★★★");
        customStrings.addProperty("8", "★★★★★★★");
        customConfig.add("strings", customStrings);
        customConfig.add("specialRarityTexts", new JsonObject());
        starDisplay.add("custom", customConfig);
        
        configObject.add("starDisplay", starDisplay);
        
        return configObject;
    }

    /**
     * 创建默认服务端配置对象
     * @return 默认服务端配置对象
     */
    public static JsonObject createDefaultServerConfig() {
        JsonObject configObject = new JsonObject();
        
        configObject.addProperty("checkVanillaRarity", true);
        configObject.addProperty("checkApotheosisRarity", true);
        configObject.addProperty("enableGetRarityWarning", true);
        
        return configObject;
    }
}
