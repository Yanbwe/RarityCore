package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 稀有度客户端配置管理器
 * 按稀有度等级逐级定义客户端的视觉表现（颜色、纹理、开关）
 * client.json 中的各项总开关优先级更高——若总开关关闭，则本配置无效
 */
public class RarityClientConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Paths.get(RarityConstants.CONFIG_DIR_PARENT).resolve(RarityConstants.CONFIG_DIR_NAME);
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve(RarityConstants.RARITY_CLIENT_CONFIG_FILE_NAME);

    /** 每级配置缓存（支持 >7 稀有度等级） */
    private static final Map<Integer, RarityLevelConfig> LEVEL_CONFIGS = new ConcurrentHashMap<>();

    static {
        // 用默认值预填充 1-7 级
        for (int i = 1; i <= RarityConstants.MAX_RARITY; i++) {
            LEVEL_CONFIGS.put(i, createDefaultConfig(i));
        }
    }

    /**
     * 单级配置数据
     */
    public static class RarityLevelConfig {
        public final int rgbColor;
        public final String texture;
        public final boolean tooltips;
        public final boolean renderer;
        public final boolean nameColor;

        RarityLevelConfig(int rgbColor, String texture, boolean tooltips, boolean renderer, boolean nameColor) {
            this.rgbColor = rgbColor;
            this.texture = texture;
            this.tooltips = tooltips;
            this.renderer = renderer;
            this.nameColor = nameColor;
        }
    }

    /**
     * 创建默认配置
     */
    private static RarityLevelConfig createDefaultConfig(int level) {
        int defaultRgb = RarityColorUtil.getRarityRgbColor(level);
        String defaultTexture = RarityConstants.BORDER_TEXTURE_PATH + "rarity_" + level + RarityConstants.TEXTURE_SUFFIX;
        return new RarityLevelConfig(defaultRgb, defaultTexture, true, true, true);
    }

    // ---- 公共查询方法（未配置 >7 稀有度时自动生成默认配置）----

    public static int getRarityColor(int rarity) {
        return getOrCreateConfig(rarity).rgbColor;
    }

    public static String getRarityTexture(int rarity) {
        return getOrCreateConfig(rarity).texture;
    }

    public static boolean isTooltipsEnabled(int rarity) {
        return getOrCreateConfig(rarity).tooltips;
    }

    public static boolean isRendererEnabled(int rarity) {
        return getOrCreateConfig(rarity).renderer;
    }

    public static boolean isNameColorEnabled(int rarity) {
        return getOrCreateConfig(rarity).nameColor;
    }

    /**
     * 获取或惰性创建指定稀有度等级的配置
     * 未配置的等级自动生成默认配置（支持 >7 稀有度等级）
     */
    private static RarityLevelConfig getOrCreateConfig(int rarity) {
        RarityLevelConfig config = LEVEL_CONFIGS.get(rarity);
        if (config != null) {
            return config;
        }
        return LEVEL_CONFIGS.computeIfAbsent(rarity, level -> createDefaultConfig(level));
    }

    // ---- 初始化与加载 ----

    public static void initialize() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }
        loadConfig();
    }

    public static void loadConfig() {
        JsonObject defaultConfig = createDefaultConfigJson();
        ConfigValidator.validateConfig(CONFIG_FILE, defaultConfig, "RarityClientConfig");

        if (!Files.exists(CONFIG_FILE)) {
            createDefaultConfigFile();
        }
        loadFromFile();
    }

    private static void loadFromFile() {
        try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            // 清空并重新加载，确保配置与文件完全同步
            LEVEL_CONFIGS.clear();
            if (json != null && json.has("rarities")) {
                JsonObject rarities = json.getAsJsonObject("rarities");
                // 读取所有稀有度等级的配置（包括 7 级以上）
                for (String key : rarities.keySet()) {
                    try {
                        int level = Integer.parseInt(key);
                        JsonObject entry = rarities.getAsJsonObject(key);
                        int rgb = RarityColorUtil.parseRgbColor(getStringOrDefault(entry, "color", "#" + String.format("%06X", RarityColorUtil.getRarityRgbColor(level))));
                        String texture = getStringOrDefault(entry, "texture", RarityConstants.BORDER_TEXTURE_PATH + "rarity_" + level + RarityConstants.TEXTURE_SUFFIX);
                        boolean tooltips = getBoolOrDefault(entry, "tooltips", true);
                        boolean renderer = getBoolOrDefault(entry, "renderer", true);
                        boolean nameColor = getBoolOrDefault(entry, "nameColor", true);
                        LEVEL_CONFIGS.put(level, new RarityLevelConfig(rgb, texture, tooltips, renderer, nameColor));
                    } catch (NumberFormatException e) {
                        RarityCore.LOGGER.warn("Invalid rarity key in RarityClientConfig: {}", key);
                    }
                }
            }
            // 确保 1-7 级始终有配置
            for (int i = 1; i <= RarityConstants.MAX_RARITY; i++) {
                LEVEL_CONFIGS.putIfAbsent(i, createDefaultConfig(i));
            }
            // 注入颜色到 RarityColorUtil
            Map<Integer, Integer> colors = new java.util.HashMap<>();
            for (Map.Entry<Integer, RarityLevelConfig> entry : LEVEL_CONFIGS.entrySet()) {
                colors.put(entry.getKey(), entry.getValue().rgbColor);
            }
            RarityColorUtil.setCustomColors(colors);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading RarityClientConfig, using defaults", e);
            // 出错时重建默认配置，并清空自定义颜色避免状态不一致
            LEVEL_CONFIGS.clear();
            for (int i = 1; i <= RarityConstants.MAX_RARITY; i++) {
                LEVEL_CONFIGS.put(i, createDefaultConfig(i));
            }
            RarityColorUtil.setCustomColors(Collections.emptyMap());
        }
    }

    public static void saveConfig() {
        JsonObject root = new JsonObject();
        JsonObject rarities = new JsonObject();
        // 按稀有度等级升序排列，方便用户阅读
        List<Integer> sortedLevels = new ArrayList<>(LEVEL_CONFIGS.keySet());
        Collections.sort(sortedLevels);
        for (int level : sortedLevels) {
            RarityLevelConfig cfg = LEVEL_CONFIGS.get(level);
            if (cfg == null) cfg = createDefaultConfig(level);
            JsonObject entry = new JsonObject();
            entry.addProperty("color", RarityColorUtil.formatRgbColor(cfg.rgbColor));
            entry.addProperty("texture", cfg.texture);
            entry.addProperty("tooltips", cfg.tooltips);
            entry.addProperty("renderer", cfg.renderer);
            entry.addProperty("nameColor", cfg.nameColor);
            rarities.add(String.valueOf(level), entry);
        }
        root.add("rarities", rarities);

        Path tmpFile = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp");
        try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(tmpFile), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot save RarityClientConfig", e);
            return;
        }
        try {
            Files.move(tmpFile, CONFIG_FILE, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to atomically move RarityClientConfig", e);
        }
    }

    private static void createDefaultConfigFile() {
        saveConfig();
        RarityCore.LOGGER.info("Created default RarityClientConfig: {}", CONFIG_FILE);
    }

    private static JsonObject createDefaultConfigJson() {
        JsonObject root = new JsonObject();
        JsonObject rarities = new JsonObject();
        for (int i = 1; i <= RarityConstants.MAX_RARITY; i++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("color", "#" + String.format("%06X", RarityColorUtil.getRarityRgbColor(i)));
            entry.addProperty("texture", RarityConstants.BORDER_TEXTURE_PATH + "rarity_" + i + RarityConstants.TEXTURE_SUFFIX);
            entry.addProperty("tooltips", true);
            entry.addProperty("renderer", true);
            entry.addProperty("nameColor", true);
            rarities.add(String.valueOf(i), entry);
        }
        root.add("rarities", rarities);
        return root;
    }

    private static String getStringOrDefault(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    private static boolean getBoolOrDefault(JsonObject obj, String key, boolean def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsBoolean() : def;
    }
}
