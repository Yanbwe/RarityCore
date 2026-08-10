package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.TextColor;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 稀有度客户端表现自定义配置。
 *
 * <p>管理 {@code config/raritycore/RarityClientConfig.json} 的加载、热重载与查询。
 * JSON 顶层 key 为稀有度等级数字的字符串形式（"1"~"7"），无内部 level 字段。
 *
 * <p>关键规则：
 * <ul>
 *   <li>等级 &gt;7 未配置时沿用等级 7 的值</li>
 *   <li>{@code client.json} 中的 enableXxx 开关作为总闸：
 *       若主开关为 false，则本配置对应字段被忽略</li>
 *   <li>reload 由客户端命令 {@code /raritycore-client reload} 触发</li>
 * </ul>
 */
public class RarityClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_DIR =
            Paths.get(RarityConstants.CONFIG_DIR_PARENT).resolve(RarityConstants.CONFIG_DIR_NAME);
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("RarityClientConfig.json");

    /** 已加载的逐级配置（保持插入顺序，key=等级 int） */
    private static final Map<Integer, RarityLevelConfig> levelConfigs = new LinkedHashMap<>();

    // ================================================================
    //  RarityLevelConfig — 单个稀有度等级的客户端配置项
    // ================================================================

    /**
     * 单个稀有度等级的客户端视觉表现配置。
     *
     * @param color    颜色，格式 {@code #RRGGBB}
     * @param texture  纹理边框资源路径（如 {@code "raritycore:textures/border/rarity_1.png"}）
     * @param tooltips 该等级是否显示工具提示
     * @param renderer 该等级是否渲染物品槽边框
     * @param nameColor 该等级是否修改物品名称颜色
     */
    public record RarityLevelConfig(
            String color,
            String texture,
            boolean tooltips,
            boolean renderer,
            boolean nameColor
    ) {}

    // ================================================================
    //  加载
    // ================================================================

    /**
     * 从文件加载稀有度客户端配置。
     * 若文件不存在则自动生成默认 1~7 级配置。
     * 在 {@link ClientConfigManager#initialize()} 中调用。
     */
    public static void loadRarityClientConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }

        if (!Files.exists(CONFIG_FILE)) {
            createDefaultConfigFile();
        }

        loadFromFile();
    }

    /**
     * 热重载：丢弃现有配置并重新读取文件。
     * 由客户端命令 {@code /raritycore-client reload} 触发。
     */
    public static void reloadRarityClientConfig() {
        levelConfigs.clear();
        if (!Files.exists(CONFIG_FILE)) {
            createDefaultConfigFile();
        }
        loadFromFile();
        RarityCore.LOGGER.info("RarityClientConfig reloaded ({} levels)", levelConfigs.size());
    }

    private static void loadFromFile() {
        levelConfigs.clear();

        try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                RarityCore.LOGGER.warn("RarityClientConfig.json is empty, using defaults");
                loadDefaults();
                return;
            }

            for (String key : root.keySet()) {
                try {
                    int rarity = Integer.parseInt(key);
                    JsonObject entry = root.getAsJsonObject(key);
                    RarityLevelConfig config = new RarityLevelConfig(
                            entry.has("color") ? entry.get("color").getAsString() : "#FFFFFF",
                            entry.has("texture") ? entry.get("texture").getAsString() : "",
                            !entry.has("tooltips") || entry.get("tooltips").getAsBoolean(),
                            !entry.has("renderer") || entry.get("renderer").getAsBoolean(),
                            !entry.has("nameColor") || entry.get("nameColor").getAsBoolean()
                    );
                    levelConfigs.put(rarity, config);
                } catch (NumberFormatException e) {
                    RarityCore.LOGGER.warn("Invalid rarity key in RarityClientConfig.json: '{}'", key);
                } catch (Exception e) {
                    RarityCore.LOGGER.warn("Failed to parse level config for key '{}': {}", key, e.getMessage());
                }
            }

            RarityCore.LOGGER.info("Loaded RarityClientConfig: {} levels configured", levelConfigs.size());
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading RarityClientConfig.json, using defaults", e);
            levelConfigs.clear();
            loadDefaults();
        }
        // 注入颜色到 RarityColorUtil（无论成功还是 fallback）
        injectColorsToRarityColorUtil();
    }

    // ================================================================
    //  查询
    // ================================================================

    /**
     * 查询指定稀有度等级的客户端配置。
     *
     * <p>回退规则：已配置的等级直接返回（支持 &gt;7）；
     * 未配置时向下查找最近已配置的等级，最终兜底等级 1。
     *
     * @param rarity 稀有度等级
     * @return 该等级的 {@link RarityLevelConfig}，永不返回 null
     */
    public static RarityLevelConfig getLevelConfig(int rarity) {
        RarityLevelConfig config = levelConfigs.get(rarity);
        if (config != null) {
            return config;
        }
        // 回退链：向下查找最近已配置的等级（不受 MAX_RARITY 限制）
        for (int l = rarity - 1; l >= 1; l--) {
            config = levelConfigs.get(l);
            if (config != null) {
                return config;
            }
        }
        // 兜底：等级 1 默认值
        return DEFAULT_CONFIGS.get(1);
    }

    /**
     * 获取指定等级的 RGB 颜色字符串（去掉前导 #）。
     */
    public static String getColorHex(int rarity) {
        String color = getLevelConfig(rarity).color();
        return color.startsWith("#") ? color.substring(1) : color;
    }

    /**
     * 获取指定等级的 {@link TextColor}，用于
     * {@code Style.EMPTY.withColor(textColor)}。
     *
     * @return TextColor 或 null（颜色格式无效时）
     */
    @Nullable
    public static TextColor getTextColor(int rarity) {
        String hex = getColorHex(rarity);
        try {
            int rgb = Integer.parseInt(hex, 16);
            return TextColor.fromRgb(rgb);
        } catch (NumberFormatException e) {
            RarityCore.LOGGER.warn("Invalid color format for rarity {}: '{}'", rarity, hex);
            return null;
        }
    }

    // ================================================================
    //  主开关集成 —— client.json enableXxx 作为总闸
    // ================================================================

    /**
     * 检查某等级的工具提示是否启用（含主开关判断）。
     */
    public static boolean isTooltipsEnabled(int rarity) {
        return ClientConfigManager.isEnableTooltipColor()
                && getLevelConfig(rarity).tooltips();
    }

    /**
     * 检查某等级的边框渲染是否启用（含主开关判断）。
     */
    public static boolean isRendererEnabled(int rarity) {
        return ClientConfigManager.isEnableItemBorderRendering()
                && getLevelConfig(rarity).renderer();
    }

    /**
     * 检查某等级的名称颜色修改是否启用（含主开关判断）。
     */
    public static boolean isNameColorEnabled(int rarity) {
        return ClientConfigManager.isEnableItemNameColor()
                && getLevelConfig(rarity).nameColor();
    }

    // ================================================================
    //  默认值
    // ================================================================

    /**
     * 默认 1~7 级配置。
     * 颜色取自 {@link RarityColorUtil#getRarityArgbColor(int)} 的现有映射，
     * 纹理默认使用 BORDER_TEXTURE_PATH + "rarity_N" + TEXTURE_SUFFIX，
     * 其余开关默认全部启用。
     */
    private static final Map<Integer, RarityLevelConfig> DEFAULT_CONFIGS = Map.of(
            1, new RarityLevelConfig(toHex(RarityColorUtil.getRarityArgbColor(1)), RarityConstants.BORDER_TEXTURE_PATH + "rarity_1" + RarityConstants.TEXTURE_SUFFIX, true, true, true),
            2, new RarityLevelConfig(toHex(RarityColorUtil.getRarityArgbColor(2)), RarityConstants.BORDER_TEXTURE_PATH + "rarity_2" + RarityConstants.TEXTURE_SUFFIX, true, true, true),
            3, new RarityLevelConfig(toHex(RarityColorUtil.getRarityArgbColor(3)), RarityConstants.BORDER_TEXTURE_PATH + "rarity_3" + RarityConstants.TEXTURE_SUFFIX, true, true, true),
            4, new RarityLevelConfig(toHex(RarityColorUtil.getRarityArgbColor(4)), RarityConstants.BORDER_TEXTURE_PATH + "rarity_4" + RarityConstants.TEXTURE_SUFFIX, true, true, true),
            5, new RarityLevelConfig(toHex(RarityColorUtil.getRarityArgbColor(5)), RarityConstants.BORDER_TEXTURE_PATH + "rarity_5" + RarityConstants.TEXTURE_SUFFIX, true, true, true),
            6, new RarityLevelConfig(toHex(RarityColorUtil.getRarityArgbColor(6)), RarityConstants.BORDER_TEXTURE_PATH + "rarity_6" + RarityConstants.TEXTURE_SUFFIX, true, true, true),
            7, new RarityLevelConfig(toHex(RarityColorUtil.getRarityArgbColor(7)), RarityConstants.BORDER_TEXTURE_PATH + "rarity_7" + RarityConstants.TEXTURE_SUFFIX, true, true, true)
    );

    private static void loadDefaults() {
        levelConfigs.putAll(DEFAULT_CONFIGS);
    }

    private static void createDefaultConfigFile() {
        JsonObject root = new JsonObject();
        for (int i = 1; i <= 7; i++) {
            RarityLevelConfig cfg = DEFAULT_CONFIGS.get(i);
            JsonObject level = new JsonObject();
            level.addProperty("color", cfg.color());
            level.addProperty("texture", cfg.texture());
            level.addProperty("tooltips", cfg.tooltips());
            level.addProperty("renderer", cfg.renderer());
            level.addProperty("nameColor", cfg.nameColor());
            root.add(String.valueOf(i), level);
        }

        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(CONFIG_FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            RarityCore.LOGGER.info("Created default RarityClientConfig.json with levels 1-7");
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to create default RarityClientConfig.json", e);
        }
    }

    /** 将 ARGB int 转为 #RRGGBB 字符串。 */
    private static String toHex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    // 禁止实例化
    /** 将已加载的配色注入 RarityColorUtil，使 getRarityRgbColor/getRarityChatColor 也能读取自定义颜色 */
    private static void injectColorsToRarityColorUtil() {
        java.util.Map<Integer, Integer> colors = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, RarityLevelConfig> entry : levelConfigs.entrySet()) {
            String hex = entry.getValue().color();
            try {
                int rgb = org.yanbwe.raritycore.util.RarityColorUtil.parseHexColor(hex);
                colors.put(entry.getKey(), rgb);
            } catch (Exception ignored) {
            }
        }
        org.yanbwe.raritycore.util.RarityColorUtil.setCustomColors(colors);
    }

    // 禁止实例化
    private RarityClientConfig() {}
}
