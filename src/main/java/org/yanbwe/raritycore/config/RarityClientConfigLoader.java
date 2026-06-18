package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.JsonPerformanceOptimizer;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * RarityClientConfig.json 加载器。
 *
 * <p>职责：
 * <ul>
 *   <li>加载 config/raritycore/RarityClientConfig.json</li>
 *   <li>解析 per-rarity 视觉配置（color/texture/tooltips/renderer/nameColor）</li>
 *   <li>缺失时生成默认配置</li>
 *   <li>注册到 {@link RarityClientConfig} 单例</li>
 * </ul>
 *
 * <p><b>注意</b>：此加载仅在客户端命令 {@code /raritycore-client reload} 中触发，不在服务端 reload 中加载。
 */
public class RarityClientConfigLoader {

    private static final Gson GSON = JsonPerformanceOptimizer.getOptimizedGson();

    private static final Path CONFIG_DIR = Paths.get(
            RarityConstants.CONFIG_DIR_PARENT).resolve(RarityConstants.CONFIG_DIR_NAME);
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve(
            RarityConstants.RARITY_CLIENT_CONFIG_FILE_NAME);

    /**
     * 加载 RarityClientConfig 配置。
     *
     * <p>流程：确保目录 → 验证/创建配置文件 → 解析 JSON → 注册到单例。
     */
    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }

        RarityClientConfig config = new RarityClientConfig();

        // 验证并确保配置文件存在
        JsonObject defaultConfig = ConfigValidator.createDefaultRarityClientConfig();
        JsonObject loadedJson = ConfigValidator.validateConfig(CONFIG_FILE, defaultConfig, "RarityClient");

        // 解析 JSON 中的 "rarities" 对象
        if (loadedJson != null && loadedJson.has("rarities")) {
            parseRarities(loadedJson.getAsJsonObject("rarities"), config);
        } else {
            // 如果整体格式异常，回退解析默认配置
            parseRarities(defaultConfig.getAsJsonObject("rarities"), config);
        }

        RarityClientConfig.replaceInstance(config);
        // Inject colors into RarityColorUtil
        injectColorsToRarityColorUtil(config);
        RarityCore.LOGGER.info("RarityClientConfig loaded: {} rarity levels configured", config.size());
    }

    /**
     * 强制以默认配置重新加载（用于配置文件损坏时的恢复）。
     */
    public static void reloadWithDefaults() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject defaultConfig = ConfigValidator.createDefaultRarityClientConfig();
            saveToFile(CONFIG_FILE, defaultConfig);
            load();
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to reload RarityClientConfig with defaults", e);
        }
    }

    /**
     * 获取配置文件路径。
     */
    public static Path getConfigFilePath() {
        return CONFIG_FILE;
    }

    // ---------------------------------------------------------------
    //  内部解析方法
    // ---------------------------------------------------------------

    /**
     * 解析 "rarities" JSON 对象中每个等级的配置。
     *
     * <p>JSON key 即稀有度等级的字符串（"1"~"7" 及更高）。
     *
     * @param raritiesObj "rarities" JSON 对象
     * @param config      目标配置对象
     */
    private static void parseRarities(JsonObject raritiesObj, RarityClientConfig config) {
        for (String key : raritiesObj.keySet()) {
            int level;
            try {
                level = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                RarityCore.LOGGER.warn("Invalid rarity level key '{}' in RarityClientConfig, skipping", key);
                continue;
            }
            if (level < 1) {
                RarityCore.LOGGER.warn("Rarity level {} < 1 in RarityClientConfig, skipping", key);
                continue;
            }

            JsonElement element = raritiesObj.get(key);
            if (!element.isJsonObject()) {
                RarityCore.LOGGER.warn("RarityClientConfig entry for level {} is not a JSON object, skipping", level);
                continue;
            }

            JsonObject entryObj = element.getAsJsonObject();
            RarityClientConfig.RarityEntry entry = parseRarityEntry(level, entryObj);
            if (entry != null) {
                config.putConfig(level, entry);
            }
        }
    }

    /**
     * 解析单个等级的配置项。
     *
     * @param level    稀有度等级（用于日志）
     * @param entryObj 该等级对应的 JSON 对象
     * @return 解析后的 RarityEntry，解析失败返回 null
     */
    private static RarityClientConfig.RarityEntry parseRarityEntry(int level, JsonObject entryObj) {
        try {
            // 解析颜色：期望 "#RRGGBB" 格式
            int color = parseRgbColor(entryObj, "color", 0xFFFFFF);
            // 解析纹理路径
            String texture = getStringOrDefault(entryObj, "texture",
                    RarityConstants.BORDER_TEXTURE_PATH + "rarity_" + level + RarityConstants.TEXTURE_SUFFIX);
            // 解析布尔标记
            boolean tooltips = getBooleanOrDefault(entryObj, "tooltips", true);
            boolean renderer = getBooleanOrDefault(entryObj, "renderer", true);
            boolean nameColor = getBooleanOrDefault(entryObj, "nameColor", true);

            return new RarityClientConfig.RarityEntry(color, texture, tooltips, renderer, nameColor);
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to parse RarityClientConfig entry for level {}: {}", level, e.getMessage());
            return null;
        }
    }

    /**
     * 从 JSON 对象中解析 RGB 颜色字符串（#RRGGBB）为 int。
     *
     * @param obj          JSON 对象
     * @param key          字段名
     * @param defaultColor 解析失败时的默认颜色
     * @return RGB int 值
     */
    static int parseRgbColor(JsonObject obj, String key, int defaultColor) {
        if (!obj.has(key)) {
            return defaultColor;
        }
        String hex = obj.get(key).getAsString();
        if (hex == null || hex.isEmpty()) {
            return defaultColor;
        }
        // 去掉 # 前缀，解析十六进制
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            RarityCore.LOGGER.warn("Invalid RGB color '{}' for key '{}', using default", obj.get(key).getAsString(), key);
            return defaultColor;
        }
    }

    private static String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }

    private static boolean getBooleanOrDefault(JsonObject obj, String key, boolean defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsBoolean();
        }
        return defaultValue;
    }

    /**
     * 将 JSON 对象写入文件。
     */
    /** Inject loaded colors into RarityColorUtil (including >7 configured levels) */
    private static void injectColorsToRarityColorUtil(RarityClientConfig config) {
        java.util.Map<Integer, Integer> colors = new java.util.HashMap<>();
        int maxLevel = Math.max(RarityConstants.MAX_RARITY, config.size());
        for (int i = 1; i <= maxLevel; i++) {
            try {
                int rgb = config.getConfig(i).getColor();
                colors.put(i, rgb);
            } catch (Exception ignored) {
            }
        }
        org.yanbwe.raritycore.util.RarityColorUtil.setCustomColors(colors);
    }

    private static void saveToFile(Path file, JsonObject json) {
        try {
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(file.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to save config file: {}", file, e);
        }
    }
}
