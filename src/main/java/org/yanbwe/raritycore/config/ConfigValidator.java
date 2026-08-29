package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.JsonPerformanceOptimizer;

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

    private static final Gson GSON = JsonPerformanceOptimizer.getOptimizedGson();

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
     * 创建默认客户端配置对象（已精简，仅保留核心配置项）
     * @return 默认客户端配置对象
     */
    public static JsonObject createDefaultClientConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty("enableCacheSystem", true);
        configObject.addProperty("enableSophisticatedCoreAdapter", true);
        configObject.addProperty("enableIronSpellsAdapter", true);
        configObject.addProperty("enableFtbLibraryAdapter", true);
        return configObject;
    }

    /**
     * 创建默认 RarityClientConfig 配置对象。
     *
     * @deprecated 使用 {@link RarityStyleConfigManager} 替代，保留仅为向后兼容
     *
     * <p>结构：
     * <pre>{@code
     * {
     *   "rarities": {
     *     "1": { "color": "#FFFFFF", "texture": "...", "tooltips": true, "renderer": true, "nameColor": true },
     *     ...
     *     "7": { "color": "#FF5555", "texture": "...", "tooltips": true, "renderer": true, "nameColor": true }
     *   }
     * }
     * }</pre>
     *
     * @return 默认的 RarityClientConfig JSON 对象
     */
    @Deprecated
    public static JsonObject createDefaultRarityClientConfig() {
        JsonObject root = new JsonObject();
        JsonObject rarities = new JsonObject();

        // 等级 1 — 普通 (亮灰 #CCCCCC)
        rarities.add("1", createRarityClientEntry("#CCCCCC",
                "raritycore:textures/border/rarity_1.png"));

        // 等级 2 — 稀有 (亮绿 #55FF55)
        rarities.add("2", createRarityClientEntry("#55FF55",
                "raritycore:textures/border/rarity_2.png"));

        // 等级 3 — 罕见 (亮青 #55FFFF)
        rarities.add("3", createRarityClientEntry("#55FFFF",
                "raritycore:textures/border/rarity_3.png"));

        // 等级 4 — 史诗 (亮紫 #FF55FF)
        rarities.add("4", createRarityClientEntry("#FF55FF",
                "raritycore:textures/border/rarity_4.png"));

        // 等级 5 — 传说 (亮金 #FFCC00)
        rarities.add("5", createRarityClientEntry("#FFCC00",
                "raritycore:textures/border/rarity_5.png"));

        // 等级 6 — 神话 (亮红 #FF6666)
        rarities.add("6", createRarityClientEntry("#FF6666",
                "raritycore:textures/border/rarity_6.png"));

        // 等级 7 — 唯一 (深红 #FF3333)
        rarities.add("7", createRarityClientEntry("#FF3333",
                "raritycore:textures/border/rarity_7.png"));

        root.add("rarities", rarities);
        return root;
    }

    /**
     * 创建单个稀有度等级的客户端配置条目。
     *
     * @param colorHex RGB 颜色（#RRGGBB 格式）
     * @param texture  纹理路径
     * @return JSON 对象
     */
    private static JsonObject createRarityClientEntry(String colorHex, String texture) {
        JsonObject entry = new JsonObject();
        entry.addProperty("color", colorHex);
        entry.addProperty("texture", texture);
        entry.addProperty("tooltips", true);
        entry.addProperty("renderer", true);
        entry.addProperty("nameColor", true);
        return entry;
    }

    /**
     * 创建默认 Tag 稀有度配置对象。
     *
     * <p>结构：
     * <pre>{@code
     * {
     *   "tag_rules": [
     *     { "tag": "forge:ingots/netherite", "rarity": 5 },
     *     { "tag": "forge:gems/diamond", "rarity": 4 },
     *     { "tag": "minecraft:swords", "rarity": 2 }
     *   ]
     * }
     * }</pre>
     *
     * <p>规则按稀有度降序排列，运行时找到第一个匹配的 Tag 即返回，
     * 从而自动取最高稀有度。</p>
     *
     * @return 默认的 TagRarity JSON 对象
     */
    public static JsonObject createDefaultTagRarityConfig() {
        JsonObject root = new JsonObject();

        com.google.gson.JsonArray tagRules = new com.google.gson.JsonArray();

        com.google.gson.JsonObject netheriteRule = new com.google.gson.JsonObject();
        netheriteRule.addProperty("tag", "forge:ingots/netherite");
        netheriteRule.addProperty("rarity", 5);
        tagRules.add(netheriteRule);

        com.google.gson.JsonObject diamondRule = new com.google.gson.JsonObject();
        diamondRule.addProperty("tag", "forge:gems/diamond");
        diamondRule.addProperty("rarity", 4);
        tagRules.add(diamondRule);

        com.google.gson.JsonObject swordsRule = new com.google.gson.JsonObject();
        swordsRule.addProperty("tag", "minecraft:swords");
        swordsRule.addProperty("rarity", 2);
        tagRules.add(swordsRule);

        root.add("tag_rules", tagRules);
        return root;
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
        configObject.addProperty("enableComponentRarityControl", false);
        
        return configObject;
    }
}
