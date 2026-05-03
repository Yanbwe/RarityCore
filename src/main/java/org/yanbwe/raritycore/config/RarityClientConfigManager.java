package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 稀有度客户端配置管理器
 * 按稀有度等级逐级定义客户端的视觉表现（颜色、纹理、开关）
 * client.json 中的各项总开关优先级更高——若总开关关闭，则本配置无效
 */
public class RarityClientConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Paths.get(RarityConstants.CONFIG_DIR_PARENT).resolve(RarityConstants.CONFIG_DIR_NAME);
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve(RarityConstants.RARITY_CLIENT_CONFIG_FILE_NAME);

    /** 每级配置缓存 */
    private static final RarityLevelConfig[] LEVEL_CONFIGS = new RarityLevelConfig[RarityConstants.MAX_RARITY + 1];

    static {
        // 用默认值预填充 1-7 级
        for (int i = 1; i <= RarityConstants.MAX_RARITY; i++) {
            LEVEL_CONFIGS[i] = createDefaultConfig(i);
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

    // ---- 公共查询方法（>7 回退到等级 7）----

    public static int getRarityColor(int rarity) {
        int effectiveRarity = clampToMax(rarity);
        return LEVEL_CONFIGS[effectiveRarity] != null ? LEVEL_CONFIGS[effectiveRarity].rgbColor : RarityColorUtil.DEFAULT_RGB_COLOR;
    }

    public static String getRarityTexture(int rarity) {
        int effectiveRarity = clampToMax(rarity);
        return LEVEL_CONFIGS[effectiveRarity] != null ? LEVEL_CONFIGS[effectiveRarity].texture : RarityConstants.BORDER_TEXTURE_PATH + "rarity_7" + RarityConstants.TEXTURE_SUFFIX;
    }

    public static boolean isTooltipsEnabled(int rarity) {
        int effectiveRarity = clampToMax(rarity);
        return LEVEL_CONFIGS[effectiveRarity] != null && LEVEL_CONFIGS[effectiveRarity].tooltips;
    }

    public static boolean isRendererEnabled(int rarity) {
        int effectiveRarity = clampToMax(rarity);
        return LEVEL_CONFIGS[effectiveRarity] != null && LEVEL_CONFIGS[effectiveRarity].renderer;
    }

    public static boolean isNameColorEnabled(int rarity) {
        int effectiveRarity = clampToMax(rarity);
        return LEVEL_CONFIGS[effectiveRarity] != null && LEVEL_CONFIGS[effectiveRarity].nameColor;
    }

    private static int clampToMax(int rarity) {
        return Math.min(rarity, RarityConstants.MAX_RARITY);
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
            if (json != null && json.has("rarities")) {
                JsonObject rarities = json.getAsJsonObject("rarities");
                for (int i = 1; i <= RarityConstants.MAX_RARITY; i++) {
                    String key = String.valueOf(i);
                    if (rarities.has(key)) {
                        JsonObject entry = rarities.getAsJsonObject(key);
                        int rgb = RarityColorUtil.parseRgbColor(getStringOrDefault(entry, "color", "#" + String.format("%06X", RarityColorUtil.getRarityRgbColor(i))));
                        String texture = getStringOrDefault(entry, "texture", RarityConstants.BORDER_TEXTURE_PATH + "rarity_" + i + RarityConstants.TEXTURE_SUFFIX);
                        boolean tooltips = getBoolOrDefault(entry, "tooltips", true);
                        boolean renderer = getBoolOrDefault(entry, "renderer", true);
                        boolean nameColor = getBoolOrDefault(entry, "nameColor", true);
                        LEVEL_CONFIGS[i] = new RarityLevelConfig(rgb, texture, tooltips, renderer, nameColor);
                    } else {
                        LEVEL_CONFIGS[i] = createDefaultConfig(i);
                    }
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading RarityClientConfig, using defaults", e);
        }
    }

    public static void saveConfig() {
        JsonObject root = new JsonObject();
        JsonObject rarities = new JsonObject();
        for (int i = 1; i <= RarityConstants.MAX_RARITY; i++) {
            RarityLevelConfig cfg = LEVEL_CONFIGS[i] != null ? LEVEL_CONFIGS[i] : createDefaultConfig(i);
            JsonObject entry = new JsonObject();
            entry.addProperty("color", RarityColorUtil.formatRgbColor(cfg.rgbColor));
            entry.addProperty("texture", cfg.texture);
            entry.addProperty("tooltips", cfg.tooltips);
            entry.addProperty("renderer", cfg.renderer);
            entry.addProperty("nameColor", cfg.nameColor);
            rarities.add(String.valueOf(i), entry);
        }
        root.add("rarities", rarities);

        try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot save RarityClientConfig", e);
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
