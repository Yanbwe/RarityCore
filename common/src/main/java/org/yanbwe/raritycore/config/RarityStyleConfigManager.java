package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.TextColor;
import net.neoforged.neoforge.common.NeoForge;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.event.RarityStyleChangedEvent;
import org.yanbwe.raritycore.util.JsonPerformanceOptimizer;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RarityStyle.json 配置管理器（V14，26.X 核心）。
 *
 * <p>集中管理所有稀有度视觉表现配置，整合旧版 client.json 与 RarityClientConfig.json。
 *
 * <h3>继承规则</h3>
 * 任一可配置字段在某稀有度缺失（或 color 显式为 "inherit"）时，
 * 从该稀有度向低等级逐级查找首个显式指定的值；
 * 若 1~当前级均无可继承值，则使用 defaults 顶层对应默认。
 *
 * <p>颜色注入：每次加载/保存后调用 {@link RarityColorUtil#setCustomColors(Map)}，
 * 使 {@link RarityColorUtil#getRarityRgbColor(int)} 读取 V14 配置。
 */
public class RarityStyleConfigManager {

    private static final Gson GSON = JsonPerformanceOptimizer.getOptimizedGson();

    private static final Path CONFIG_DIR = Paths.get(RarityConstants.CONFIG_DIR_PARENT)
            .resolve(RarityConstants.CONFIG_DIR_NAME);
    private static final Path STYLE_CONFIG_FILE = CONFIG_DIR.resolve(RarityConstants.RARITY_STYLE_FILE_NAME);
    private static final Path CLIENT_CONFIG_FILE = CONFIG_DIR.resolve(RarityConstants.CLIENT_CONFIG_FILE_NAME);
    private static final Path LEGACY_CLIENT_CONFIG_FILE = CONFIG_DIR.resolve(RarityConstants.RARITY_CLIENT_CONFIG_FILE_NAME);

    /** V14 配置版本（写入 RarityStyle.json 的 version 字段） */
    public static final int API_VERSION = 1400;

    // ================================================================
    //  公开 record 类型
    // ================================================================

    public record BorderStyle(boolean useTexture, String defaultTexture, int style, boolean show, String fallback) {}
    public record LevelStyle(boolean colored, String translationKey, String fallback) {}
    public record StarStyle(boolean colored, String mode, String repeatChar, String custom) {}
    public record TooltipStyle(boolean show, String content, boolean colored, LevelStyle level, StarStyle star) {}
    public record NoRarityStyle(boolean skip, int defaultRarity) {}
    public record RarityStyleEntry(String color, BorderStyle border, TooltipStyle tooltip, Boolean itemNameColor) {}
    public record StyleSnapshot(int level, String color, BorderStyle border, TooltipStyle tooltip, boolean itemNameColor) {}
    public record StylePatch(String color, BorderStyle border, TooltipStyle tooltip, Boolean itemNameColor) {}

    // ================================================================
    //  内部可变配置（用于字段级继承，携带 Specified 标志）
    // ================================================================

    private static final class BorderCfg {
        boolean useTexture = true;
        String defaultTexture = "raritycore:textures/border/rarity_{level}.png";
        int style = 1;
        boolean show = true;
        String fallback = "inherit";
        boolean useTextureSpecified;
        boolean defaultTextureSpecified;
        boolean styleSpecified;
        boolean showSpecified;
        boolean fallbackSpecified;

        BorderCfg copy() {
            BorderCfg c = new BorderCfg();
            c.useTexture = useTexture;
            c.defaultTexture = defaultTexture;
            c.style = style;
            c.show = show;
            c.fallback = fallback;
            c.useTextureSpecified = useTextureSpecified;
            c.defaultTextureSpecified = defaultTextureSpecified;
            c.styleSpecified = styleSpecified;
            c.showSpecified = showSpecified;
            c.fallbackSpecified = fallbackSpecified;
            return c;
        }
    }

    private static final class LevelCfg {
        boolean colored = true;
        String translationKey = "$(rarity.core.{level})";
        String fallback = "{level}$(rarity.core.special.rarity.prefix)";
        boolean coloredSpecified;
        boolean translationKeySpecified;
        boolean fallbackSpecified;
    }

    private static final class StarCfg {
        boolean colored = true;
        String mode = "repeat";
        String repeatChar = "★";
        String custom = "";
        boolean coloredSpecified;
        boolean modeSpecified;
        boolean repeatCharSpecified;
        boolean customSpecified;
    }

    private static final class TooltipCfg {
        boolean show = true;
        String content = "[@{level}] @{star}";
        boolean colored = true;
        LevelCfg level = new LevelCfg();
        StarCfg star = new StarCfg();
        boolean showSpecified;
        boolean contentSpecified;
        boolean coloredSpecified;
        boolean levelSpecified;
        boolean starSpecified;
    }

    private static final class NoRarityCfg {
        boolean skip = false;
        int defaultRarity = 1;
    }

    /** 某稀有度的显式覆盖；字段为 null 表示该级未显式配置（继承） */
    private static final class PerRarity {
        String color;
        BorderCfg border;
        TooltipCfg tooltip;
        Boolean itemNameColor;
    }

    // ================================================================
    //  运行时状态
    // ================================================================

    private static volatile boolean available;

    private static volatile int version = 1;

    private static boolean enableBorder = true;
    private static boolean enableTooltip = true;
    private static boolean tooltipColorEnabled = true;

    private static String defaultsColor = "inherit";
    private static BorderCfg defaultBorder = new BorderCfg();
    private static TooltipCfg defaultTooltip = new TooltipCfg();
    private static boolean defaultItemNameColor = true;
    private static NoRarityCfg defaultNoRarity = new NoRarityCfg();

    /** level(int) → 显式覆盖；线程安全且保持插入顺序 */
    private static final Map<Integer, PerRarity> RARITIES = Collections.synchronizedMap(new LinkedHashMap<>());

    /** 批量写入深度；>0 时 setter 延迟写盘与事件发布 */
    private static int batchDepth = 0;

    /** 批量写入期间被修改的等级（去重，endStyleBatch 统一发布聚合事件） */
    private static final Set<Integer> batchAffectedLevels = new LinkedHashSet<>();

    /** 批量写入期间被修改的目标类型（聚合） */
    private static final Set<RarityStyleChangedEvent.StyleChangeTarget> batchTargets = new LinkedHashSet<>();

    private RarityStyleConfigManager() {
    }

    // ================================================================
    //  加载 / 保存 / 迁移
    // ================================================================

    /**
     * 初始化样式配置管理器。
     * <ol>
     *   <li>确保配置目录存在</li>
     *   <li>删除旧 RarityClientConfig.json</li>
     *   <li>裁剪 client.json 仅保留 enableCacheSystem 与 enableSophisticatedCoreAdapter</li>
     *   <li>生成默认 RarityStyle.json（若不存在）</li>
     *   <li>加载配置并注入颜色</li>
     * </ol>
     */
    public static void initialize() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            available = false;
            return;
        }
        deleteLegacyFiles();
        trimClientConfig();
        if (!Files.exists(STYLE_CONFIG_FILE)) {
            saveDefaultConfig();
        }
        load();
    }

    /** 清空内存并重新加载 RarityStyle.json */
    public static void reload() {
        clearState();
        load();
    }

    /**
     * 加载 RarityStyle.json（若目录不存在则创建；若文件缺失则生成默认并加载）。
     */
    private static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            available = false;
            return;
        }
        if (!Files.exists(STYLE_CONFIG_FILE)) {
            saveDefaultConfig();
        }
        try (BufferedReader reader = Files.newBufferedReader(STYLE_CONFIG_FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                RarityCore.LOGGER.warn("RarityStyle.json is empty or malformed, recreating defaults");
                saveDefaultConfig();
                root = GSON.fromJson(Files.newBufferedReader(STYLE_CONFIG_FILE), JsonObject.class);
                if (root == null) {
                    available = false;
                    return;
                }
            }
            parseRoot(root);
            injectColors();
            available = true;
            RarityCore.LOGGER.info("RarityStyle.json loaded: {} rarity levels configured", RARITIES.size());
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to load RarityStyle.json", e);
            available = false;
        }
    }

    /**
     * 使用 {@link JsonPerformanceOptimizer#getOptimizedGson()} 将当前配置写回 RarityStyle.json。
     */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = buildConfigJson();
            try (Writer writer = Files.newBufferedWriter(STYLE_CONFIG_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            injectColors();
            RarityCore.LOGGER.info("RarityStyle.json saved");
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot save RarityStyle.json: {}", STYLE_CONFIG_FILE, e);
        }
    }

    /** 删除旧版 RarityClientConfig.json */
    public static void deleteLegacyFiles() {
        if (Files.exists(LEGACY_CLIENT_CONFIG_FILE)) {
            try {
                Files.delete(LEGACY_CLIENT_CONFIG_FILE);
                RarityCore.LOGGER.info("Deleted old config file: {}", LEGACY_CLIENT_CONFIG_FILE);
            } catch (IOException e) {
                RarityCore.LOGGER.warn("Failed to delete old config file: {}", LEGACY_CLIENT_CONFIG_FILE, e);
            }
        }
    }

    /**
     * 裁剪 client.json，仅保留 enableCacheSystem 与 enableSophisticatedCoreAdapter，其余删除并写回。
     */
    public static void trimClientConfig() {
        if (!Files.exists(CLIENT_CONFIG_FILE)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(CLIENT_CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                return;
            }
            JsonObject trimmed = new JsonObject();
            if (json.has("enableCacheSystem")) {
                trimmed.add("enableCacheSystem", json.get("enableCacheSystem"));
            }
            if (json.has("enableSophisticatedCoreAdapter")) {
                trimmed.add("enableSophisticatedCoreAdapter", json.get("enableSophisticatedCoreAdapter"));
            }
            try (Writer writer = Files.newBufferedWriter(CLIENT_CONFIG_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(trimmed, writer);
            }
            RarityCore.LOGGER.info("Trimmed client.json to only cache/adapter switches");
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to trim client.json", e);
        }
    }

    // ================================================================
    //  默认配置
    // ================================================================

    /** 生成 V14 schema 的默认 RarityStyle.json（JsonObject） */
    public static JsonObject createDefaultConfigJson() {
        JsonObject root = new JsonObject();
        root.addProperty("enableBorder", true);
        root.addProperty("enableTooltip", true);
        root.addProperty("tooltipColorEnabled", true);

        JsonObject defaults = new JsonObject();
        defaults.addProperty("color", "inherit");

        JsonObject border = new JsonObject();
        border.addProperty("useTexture", true);
        border.addProperty("defaultTexture", "raritycore:textures/border/rarity_{level}.png");
        border.addProperty("style", 1);
        border.addProperty("show", true);
        border.addProperty("fallback", "inherit");
        defaults.add("border", border);

        JsonObject tooltip = new JsonObject();
        tooltip.addProperty("show", true);
        tooltip.addProperty("content", "[@{level}] @{star}");
        tooltip.addProperty("colored", true);

        JsonObject level = new JsonObject();
        level.addProperty("colored", true);
        level.addProperty("translationKey", "$(rarity.core.{level})");
        level.addProperty("fallback", "{level}$(rarity.core.special.rarity.prefix)");
        tooltip.add("level", level);

        JsonObject star = new JsonObject();
        star.addProperty("colored", true);
        star.addProperty("mode", "repeat");
        star.addProperty("repeatChar", "★");
        star.addProperty("custom", "");
        tooltip.add("star", star);

        defaults.add("tooltip", tooltip);
        defaults.addProperty("itemNameColor", true);

        JsonObject noRarity = new JsonObject();
        noRarity.addProperty("skip", false);
        noRarity.addProperty("defaultRarity", 1);
        defaults.add("noRarity", noRarity);

        root.add("defaults", defaults);

        JsonObject rarities = new JsonObject();
        JsonObject entry1 = new JsonObject();
        entry1.addProperty("color", "#CCCCCC");
        rarities.add("1", entry1);
        JsonObject entry2 = new JsonObject();
        entry2.addProperty("color", "#55FF55");
        rarities.add("2", entry2);
        JsonObject entry3 = new JsonObject();
        entry3.addProperty("color", "#55FFFF");
        rarities.add("3", entry3);
        JsonObject entry4 = new JsonObject();
        entry4.addProperty("color", "#FF55FF");
        rarities.add("4", entry4);
        JsonObject entry5 = new JsonObject();
        entry5.addProperty("color", "#FFCC00");
        rarities.add("5", entry5);
        JsonObject entry6 = new JsonObject();
        entry6.addProperty("color", "#FF6666");
        rarities.add("6", entry6);
        JsonObject entry7 = new JsonObject();
        entry7.addProperty("color", "#FF3333");
        rarities.add("7", entry7);
        root.add("rarities", rarities);

        return root;
    }

    private static void saveDefaultConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = Files.newBufferedWriter(STYLE_CONFIG_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(createDefaultConfigJson(), writer);
            }
            RarityCore.LOGGER.info("Created default RarityStyle.json: {}", STYLE_CONFIG_FILE);
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create default RarityStyle.json: {}", STYLE_CONFIG_FILE, e);
        }
    }

    /** 当前 RarityStyle.json 内版本字段；无则返回 1 */
    public static int getConfigVersion() {
        return version;
    }

    // ================================================================
    //  解析
    // ================================================================

    private static void parseRoot(JsonObject root) {
        enableBorder = getBoolean(root, "enableBorder", true);
        enableTooltip = getBoolean(root, "enableTooltip", true);
        tooltipColorEnabled = getBoolean(root, "tooltipColorEnabled", true);

        if (root.has("defaults") && root.get("defaults").isJsonObject()) {
            parseDefaults(root.getAsJsonObject("defaults"));
        }

        if (root.has("rarities") && root.get("rarities").isJsonObject()) {
            JsonObject raritiesObj = root.getAsJsonObject("rarities");
            for (Map.Entry<String, JsonElement> e : raritiesObj.entrySet()) {
                try {
                    int level = Integer.parseInt(e.getKey());
                    PerRarity pr = new PerRarity();
                    JsonElement val = e.getValue();
                    if (val.isJsonPrimitive() && val.getAsJsonPrimitive().isString()) {
                        pr.color = normalizeColor(val.getAsString());
                    } else if (val.isJsonObject()) {
                        parsePerRarity(val.getAsJsonObject(), pr);
                    }
                    RARITIES.put(level, pr);
                } catch (NumberFormatException ex) {
                    RarityCore.LOGGER.warn("Invalid rarity key in RarityStyle: {}", e.getKey());
                }
            }
        }
    }

    private static void parseDefaults(JsonObject d) {
        if (d.has("color")) {
            defaultsColor = normalizeColor(d.get("color").getAsString());
        }
        if (d.has("border") && d.get("border").isJsonObject()) {
            parseBorder(d.getAsJsonObject("border"), defaultBorder);
        }
        if (d.has("tooltip") && d.get("tooltip").isJsonObject()) {
            parseTooltip(d.getAsJsonObject("tooltip"), defaultTooltip);
        }
        if (d.has("itemNameColor")) {
            defaultItemNameColor = d.get("itemNameColor").getAsBoolean();
        }
        if (d.has("noRarity") && d.get("noRarity").isJsonObject()) {
            JsonObject nr = d.getAsJsonObject("noRarity");
            if (nr.has("skip")) {
                defaultNoRarity.skip = nr.get("skip").getAsBoolean();
            }
            if (nr.has("defaultRarity")) {
                defaultNoRarity.defaultRarity = nr.get("defaultRarity").getAsInt();
            }
        }
    }

    private static void parsePerRarity(JsonObject o, PerRarity pr) {
        if (o.has("color")) {
            pr.color = normalizeColor(o.get("color").getAsString());
        }
        if (o.has("border") && o.get("border").isJsonObject()) {
            pr.border = new BorderCfg();
            parseBorder(o.getAsJsonObject("border"), pr.border);
        }
        if (o.has("tooltip") && o.get("tooltip").isJsonObject()) {
            pr.tooltip = new TooltipCfg();
            parseTooltip(o.getAsJsonObject("tooltip"), pr.tooltip);
        }
        if (o.has("itemNameColor")) {
            pr.itemNameColor = o.get("itemNameColor").getAsBoolean();
        }
    }

    private static void parseBorder(JsonObject b, BorderCfg cfg) {
        if (b.has("useTexture")) {
            cfg.useTexture = b.get("useTexture").getAsBoolean();
            cfg.useTextureSpecified = true;
        }
        if (b.has("defaultTexture")) {
            cfg.defaultTexture = b.get("defaultTexture").getAsString();
            cfg.defaultTextureSpecified = true;
        }
        if (b.has("style")) {
            cfg.style = b.get("style").getAsInt();
            cfg.styleSpecified = true;
        }
        if (b.has("show")) {
            cfg.show = b.get("show").getAsBoolean();
            cfg.showSpecified = true;
        }
        if (b.has("fallback")) {
            cfg.fallback = b.get("fallback").getAsString();
            cfg.fallbackSpecified = true;
        }
    }

    private static void parseTooltip(JsonObject t, TooltipCfg cfg) {
        if (t.has("show")) {
            cfg.show = t.get("show").getAsBoolean();
            cfg.showSpecified = true;
        }
        if (t.has("content")) {
            cfg.content = t.get("content").getAsString();
            cfg.contentSpecified = true;
        }
        if (t.has("colored")) {
            cfg.colored = t.get("colored").getAsBoolean();
            cfg.coloredSpecified = true;
        }
        if (t.has("level") && t.get("level").isJsonObject()) {
            JsonObject l = t.getAsJsonObject("level");
            cfg.levelSpecified = true;
            if (l.has("colored")) {
                cfg.level.colored = l.get("colored").getAsBoolean();
                cfg.level.coloredSpecified = true;
            }
            if (l.has("translationKey")) {
                cfg.level.translationKey = l.get("translationKey").getAsString();
                cfg.level.translationKeySpecified = true;
            }
            if (l.has("fallback")) {
                cfg.level.fallback = l.get("fallback").getAsString();
                cfg.level.fallbackSpecified = true;
            }
        }
        if (t.has("star") && t.get("star").isJsonObject()) {
            JsonObject s = t.getAsJsonObject("star");
            cfg.starSpecified = true;
            if (s.has("colored")) {
                cfg.star.colored = s.get("colored").getAsBoolean();
                cfg.star.coloredSpecified = true;
            }
            if (s.has("mode")) {
                cfg.star.mode = s.get("mode").getAsString();
                cfg.star.modeSpecified = true;
            }
            if (s.has("repeatChar")) {
                cfg.star.repeatChar = s.get("repeatChar").getAsString();
                cfg.star.repeatCharSpecified = true;
            }
            if (s.has("custom")) {
                cfg.star.custom = s.get("custom").getAsString();
                cfg.star.customSpecified = true;
            }
        }
    }

    private static String normalizeColor(String color) {
        if (color == null || color.isEmpty()) {
            return "inherit";
        }
        return color;
    }

    // ================================================================
    //  继承查询 — 颜色
    // ================================================================

    /**
     * 获取某稀有度最终生效的 RGB 颜色（含继承与内置兜底）。
     *
     * @param level 稀有度等级
     * @return RGB 颜色 (0xRRGGBB)
     */
    public static int getColor(int level) {
        String hex = resolveColorHex(level);
        if (hex == null) {
            return RarityColorUtil.getRarityRgbColor(level);
        }
        return RarityColorUtil.parseHexColor(hex);
    }

    /** 获取某稀有度最终生效的颜色十六进制字符串（形如 #RRGGBB） */
    public static String getColorHex(int level) {
        String hex = resolveColorHex(level);
        if (hex == null) {
            return String.format("#%06X", RarityColorUtil.getRarityRgbColor(level));
        }
        return hex.startsWith("#") ? hex : "#" + hex;
    }

    /** 获取某稀有度最终生效的 {@link TextColor} */
    public static TextColor getTextColor(int level) {
        return TextColor.fromRgb(getColor(level));
    }

    /**
     * 解析某稀有度的生效颜色。
     * <ol>
     *   <li>从 level 向低等级逐级查找首个显式非 inherit 颜色</li>
     *   <li>若无，则使用 defaults.color</li>
     *   <li>若 defaults.color 为 inherit/缺失，则返回 null（由调用方回退到内置 RGB）</li>
     * </ol>
     */
    private static String resolveColorHex(int level) {
        for (int l = level; l >= RarityConstants.MIN_RARITY; l--) {
            PerRarity pr = RARITIES.get(l);
            if (pr != null && pr.color != null && !"inherit".equalsIgnoreCase(pr.color) && !pr.color.isEmpty()) {
                return pr.color;
            }
        }
        if (defaultsColor != null && !"inherit".equalsIgnoreCase(defaultsColor) && !defaultsColor.isEmpty()) {
            return defaultsColor;
        }
        return null;
    }

    // ================================================================
    //  继承查询 — 边框 / 工具提示 / 名称颜色
    // ================================================================

    /** 获取某稀有度最终生效的边框样式（含字段级继承） */
    public static BorderStyle getBorder(int level) {
        BorderCfg acc = defaultBorder.copy();
        for (int l = RarityConstants.MIN_RARITY; l <= level; l++) {
            PerRarity pr = RARITIES.get(l);
            if (pr != null && pr.border != null) {
                mergeBorder(pr.border, acc);
            }
        }
        return new BorderStyle(acc.useTexture, acc.defaultTexture, acc.style, acc.show, acc.fallback);
    }

    /** 获取某稀有度最终生效的工具提示样式（含字段级继承） */
    public static TooltipStyle getTooltip(int level) {
        TooltipCfg acc = copyTooltip(defaultTooltip);
        for (int l = RarityConstants.MIN_RARITY; l <= level; l++) {
            PerRarity pr = RARITIES.get(l);
            if (pr != null && pr.tooltip != null) {
                mergeTooltip(pr.tooltip, acc);
            }
        }
        return new TooltipStyle(
            acc.show,
            acc.content,
            acc.colored,
            new LevelStyle(acc.level.colored, acc.level.translationKey, acc.level.fallback),
            new StarStyle(acc.star.colored, acc.star.mode, acc.star.repeatChar, acc.star.custom)
        );
    }

    /** 获取某稀有度工具提示内容模板 */
    public static String getTooltipContent(int level) {
        return getTooltip(level).content();
    }

    /** 获取某稀有度等级翻译键模板 */
    public static String getLevelTranslationKey(int level) {
        return getTooltip(level).level().translationKey();
    }

    /** 获取某稀有度等级回退模板 */
    public static String getLevelFallbackKey(int level) {
        return getTooltip(level).level().fallback();
    }

    /**
     * 获取某稀有度等级显示名称。
     * <p>
     * 默认配置下 translationKey 为 {@code $(rarity.core.{level})}，
     * 会通过翻译键显示文字（如“普通 / Common”）；若翻译缺失则回退到 fallback 模板。
     *
     * @param level 稀有度等级
     * @return 等级显示名称，永不为 null
     */
    public static String getLevelDisplayName(int level) {
        String keyTemplate = getLevelTranslationKey(level);
        if (keyTemplate == null || keyTemplate.isEmpty()) {
            return resolveLevelTemplate(getLevelFallbackKey(level), level);
        }
        // 整串 $(key) 形式：优先按翻译键解析
        if (keyTemplate.startsWith("$(") && keyTemplate.endsWith(")") && keyTemplate.indexOf("$(", 1) < 0) {
            String key = keyTemplate.substring(2, keyTemplate.length() - 1).replace("{level}", String.valueOf(level));
            if (!key.isEmpty()) {
                String translated = net.minecraft.network.chat.Component.translatable(key).getString();
                if (!translated.equals(key)) {
                    return translated;
                }
            }
            return resolveLevelTemplate(getLevelFallbackKey(level), level);
        }
        // 非整串翻译键：作为字面量模板解析
        return resolveLevelTemplate(keyTemplate, level);
    }

    /** 解析等级名称模板：替换 {level}，并翻译其中嵌入的 $(key) 片段 */
    private static String resolveLevelTemplate(String template, int level) {
        if (template == null || template.isEmpty()) {
            return String.valueOf(level);
        }
        String result = template.replace("{level}", String.valueOf(level));
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < result.length()) {
            if (result.startsWith("$(", i)) {
                int end = result.indexOf(')', i);
                if (end > i + 2) {
                    String key = result.substring(i + 2, end);
                    sb.append(net.minecraft.network.chat.Component.translatable(key).getString());
                    i = end + 1;
                    continue;
                }
            }
            sb.append(result.charAt(i));
            i++;
        }
        return sb.toString();
    }

    /** 获取某稀有度星星配置 */
    public static StarStyle getStarConfig(int level) {
        return getTooltip(level).star();
    }

    /** 获取某稀有度物品名称颜色是否启用（defaults 与逐级显式配置取与语义） */
    public static boolean isNameColorEnabled(int level) {
        boolean acc = defaultItemNameColor;
        for (int l = RarityConstants.MIN_RARITY; l <= level; l++) {
            PerRarity pr = RARITIES.get(l);
            if (pr != null && pr.itemNameColor != null) {
                acc = acc && pr.itemNameColor;
            }
        }
        return acc;
    }

    /** 顶层总开关：边框渲染 */
    public static boolean isBorderEnabled() {
        return enableBorder;
    }

    /** 顶层总开关：工具提示 */
    public static boolean isTooltipEnabled() {
        return enableTooltip;
    }

    /** 顶层总开关：工具提示着色 */
    public static boolean isTooltipColorEnabled() {
        return tooltipColorEnabled;
    }

    /** 无稀有度回退：是否跳过 */
    public static boolean isNoRaritySkip() {
        return defaultNoRarity.skip;
    }

    /** 无稀有度回退：默认稀有度 */
    public static int getNoRarityDefaultRarity() {
        return defaultNoRarity.defaultRarity;
    }

    /** 某稀有度边框是否使用纹理 */
    public static boolean isBorderUseTexture(int level) {
        return getBorder(level).useTexture();
    }

    /** 某稀有度边框样式（1=实心，0=空心） */
    public static int getBorderStyle(int level) {
        return getBorder(level).style();
    }

    /** 默认边框回退纹理（defaults.border.fallback） */
    public static String getBorderFallback() {
        return defaultBorder.fallback;
    }

    /** 星星显示是否可用（defaults.tooltip.show == true 且 star.mode 非空） */
    public static boolean isStarDisplayEnabled() {
        return defaultTooltip.show && defaultTooltip.star.mode != null && !defaultTooltip.star.mode.isEmpty();
    }

    /** 获取边框纹理路径（含 >7 回退逻辑） */
    public static String getBorderTexture(int level) {
        BorderCfg resolved = resolveBorderCfg(level);
        String path = resolved.defaultTexture.replace("{level}", String.valueOf(level));
        if (level <= RarityConstants.MAX_RARITY) {
            return path;
        }
        // defaults.defaultTexture 只是全局默认值，不能算作逐级显式覆盖；
        // 只有 rarities 中显式配置的 defaultTexture 才应优先于 8+ 回退。
        if (hasExplicitDefaultTexture(level)) {
            return path;
        }
        String fb = resolved.fallback;
        if (fb != null && fb.equalsIgnoreCase("inherit")) {
            return defaultBorder.defaultTexture.replace("{level}", String.valueOf(RarityConstants.MAX_RARITY));
        }
        if (fb != null && !fb.isEmpty()) {
            return fb.replace("{level}", String.valueOf(level));
        }
        return path;
    }

    private static boolean hasExplicitDefaultTexture(int level) {
        for (int l = RarityConstants.MIN_RARITY; l <= level; l++) {
            PerRarity pr = RARITIES.get(l);
            if (pr != null && pr.border != null && pr.border.defaultTextureSpecified) {
                return true;
            }
        }
        return false;
    }

    private static BorderCfg resolveBorderCfg(int level) {
        BorderCfg acc = defaultBorder.copy();
        for (int l = RarityConstants.MIN_RARITY; l <= level; l++) {
            PerRarity pr = RARITIES.get(l);
            if (pr != null && pr.border != null) {
                mergeBorder(pr.border, acc);
            }
        }
        return acc;
    }

    // ================================================================
    //  写入口
    // ================================================================

    /** 顶层边框渲染总开关 */
    public static void setBorderEnabled(boolean enable) {
        enableBorder = enable;
        persistStyleChange(Collections.emptySet(), RarityStyleChangedEvent.StyleChangeTarget.BORDER);
    }

    /** 顶层工具提示总开关 */
    public static void setTooltipEnabled(boolean enable) {
        enableTooltip = enable;
        persistStyleChange(Collections.emptySet(), RarityStyleChangedEvent.StyleChangeTarget.TOOLTIP);
    }

    /** 顶层工具提示着色总开关 */
    public static void setTooltipColorEnabled(boolean enable) {
        tooltipColorEnabled = enable;
        persistStyleChange(Collections.emptySet(), RarityStyleChangedEvent.StyleChangeTarget.COLOR);
    }

    /** 某稀有度边框是否使用纹理 */
    public static void setBorderUseTexture(int level, boolean useTexture) {
        PerRarity pr = ensureOverride(level);
        if (pr.border == null) {
            pr.border = new BorderCfg();
        }
        pr.border.useTexture = useTexture;
        pr.border.useTextureSpecified = true;
        persistStyleChange(Set.of(level), RarityStyleChangedEvent.StyleChangeTarget.BORDER);
    }

    /** 某稀有度边框样式（1=实心，0=空心） */
    public static void setBorderStyle(int level, int style) {
        PerRarity pr = ensureOverride(level);
        if (pr.border == null) {
            pr.border = new BorderCfg();
        }
        pr.border.style = style;
        pr.border.styleSpecified = true;
        persistStyleChange(Set.of(level), RarityStyleChangedEvent.StyleChangeTarget.BORDER);
    }

    /** 某稀有度工具提示内容模板 */
    public static void setTooltipContent(int level, String content) {
        PerRarity pr = ensureOverride(level);
        if (pr.tooltip == null) {
            pr.tooltip = new TooltipCfg();
        }
        pr.tooltip.content = content;
        pr.tooltip.contentSpecified = true;
        persistStyleChange(Set.of(level), RarityStyleChangedEvent.StyleChangeTarget.TOOLTIP);
    }

    /** 某稀有度星星模式 */
    public static void setStarMode(int level, String mode) {
        PerRarity pr = ensureOverride(level);
        if (pr.tooltip == null) {
            pr.tooltip = new TooltipCfg();
        }
        pr.tooltip.star.mode = mode;
        pr.tooltip.star.modeSpecified = true;
        pr.tooltip.starSpecified = true;
        persistStyleChange(Set.of(level), RarityStyleChangedEvent.StyleChangeTarget.TOOLTIP);
    }

    /** 某稀有度星星重复字符 */
    public static void setStarRepeatChar(int level, String repeatChar) {
        PerRarity pr = ensureOverride(level);
        if (pr.tooltip == null) {
            pr.tooltip = new TooltipCfg();
        }
        pr.tooltip.star.repeatChar = repeatChar;
        pr.tooltip.star.repeatCharSpecified = true;
        pr.tooltip.starSpecified = true;
        persistStyleChange(Set.of(level), RarityStyleChangedEvent.StyleChangeTarget.TOOLTIP);
    }

    /**
     * 以单个补丁整体写入某稀有度的视觉表现配置。
     * 补丁内 null 字段表示保留现有值。
     */
    public static void setStyle(int level, StylePatch patch) {
        if (patch == null) {
            return;
        }
        boolean changed = false;
        if (patch.color() != null) {
            PerRarity pr = ensureOverride(level);
            pr.color = normalizeColor(patch.color());
            changed = true;
        }
        if (patch.border() != null) {
            PerRarity pr = ensureOverride(level);
            if (pr.border == null) {
                pr.border = new BorderCfg();
            }
            BorderStyle bs = patch.border();
            pr.border.useTexture = bs.useTexture();
            pr.border.useTextureSpecified = true;
            pr.border.defaultTexture = bs.defaultTexture();
            pr.border.defaultTextureSpecified = true;
            pr.border.style = bs.style();
            pr.border.styleSpecified = true;
            pr.border.show = bs.show();
            pr.border.showSpecified = true;
            pr.border.fallback = bs.fallback();
            pr.border.fallbackSpecified = true;
            changed = true;
        }
        if (patch.tooltip() != null) {
            PerRarity pr = ensureOverride(level);
            if (pr.tooltip == null) {
                pr.tooltip = new TooltipCfg();
            }
            TooltipStyle ts = patch.tooltip();
            pr.tooltip.show = ts.show();
            pr.tooltip.showSpecified = true;
            pr.tooltip.content = ts.content();
            pr.tooltip.contentSpecified = true;
            pr.tooltip.colored = ts.colored();
            pr.tooltip.coloredSpecified = true;
            if (ts.level() != null) {
                pr.tooltip.level.colored = ts.level().colored();
                pr.tooltip.level.coloredSpecified = true;
                pr.tooltip.level.translationKey = ts.level().translationKey();
                pr.tooltip.level.translationKeySpecified = true;
                pr.tooltip.level.fallback = ts.level().fallback();
                pr.tooltip.level.fallbackSpecified = true;
            }
            if (ts.star() != null) {
                pr.tooltip.star.colored = ts.star().colored();
                pr.tooltip.star.coloredSpecified = true;
                pr.tooltip.star.mode = ts.star().mode();
                pr.tooltip.star.modeSpecified = true;
                pr.tooltip.star.repeatChar = ts.star().repeatChar();
                pr.tooltip.star.repeatCharSpecified = true;
                pr.tooltip.star.custom = ts.star().custom();
                pr.tooltip.star.customSpecified = true;
            }
            changed = true;
        }
        if (patch.itemNameColor() != null) {
            PerRarity pr = ensureOverride(level);
            pr.itemNameColor = patch.itemNameColor();
            changed = true;
        }
        if (changed) {
            persistStyleChange(Set.of(level), RarityStyleChangedEvent.StyleChangeTarget.ALL);
        }
    }

    /** 开启批量写入；期间 setter 不写盘、不发布事件。可嵌套调用。 */
    public static void beginStyleBatch() {
        batchDepth++;
    }

    /** 结束批量写入；归零时统一保存并发布一次聚合事件。 */
    public static void endStyleBatch() {
        if (batchDepth <= 0) {
            return;
        }
        batchDepth--;
        if (batchDepth == 0) {
            if (!batchAffectedLevels.isEmpty() || !batchTargets.isEmpty()) {
                save();
                Set<Integer> levels = new LinkedHashSet<>(batchAffectedLevels);
                Set<RarityStyleChangedEvent.StyleChangeTarget> targets = new LinkedHashSet<>(batchTargets);
                Set<Integer> affected = levels.isEmpty() ? Collections.emptySet() : levels;
                RarityStyleChangedEvent.StyleChangeTarget target = targets.size() == 1
                    ? targets.iterator().next()
                    : RarityStyleChangedEvent.StyleChangeTarget.ALL;
                NeoForge.EVENT_BUS.post(new RarityStyleChangedEvent(affected, target));
            }
            batchAffectedLevels.clear();
            batchTargets.clear();
        }
    }

    /** 遍历当前已配置等级，批量设置边框是否使用纹理。 */
    public static void setAllBorderUseTexture(boolean useTexture) {
        List<Integer> levels;
        synchronized (RARITIES) {
            if (RARITIES.isEmpty()) {
                return;
            }
            levels = new ArrayList<>(RARITIES.keySet());
        }
        beginStyleBatch();
        try {
            for (Integer level : levels) {
                setBorderUseTexture(level, useTexture);
            }
        } finally {
            endStyleBatch();
        }
    }

    private static PerRarity ensureOverride(int level) {
        synchronized (RARITIES) {
            PerRarity pr = RARITIES.get(level);
            if (pr == null) {
                pr = new PerRarity();
                RARITIES.put(level, pr);
            }
            return pr;
        }
    }

    private static void persistStyleChange(Set<Integer> affectedLevels, RarityStyleChangedEvent.StyleChangeTarget target) {
        if (batchDepth > 0) {
            batchAffectedLevels.addAll(affectedLevels);
            batchTargets.add(target);
            return;
        }
        save();
        NeoForge.EVENT_BUS.post(new RarityStyleChangedEvent(affectedLevels, target));
    }

    // ================================================================
    //  校验 / 快照 / 可用性
    // ================================================================

    /** 校验稀有度值是否有效（无上限，>= MIN_RARITY） */
    public static boolean validateRarity(int level) {
        return RarityValidator.isValidRarity(level);
    }

    /** 获取某稀有度的完整生效视觉表现快照 */
    public static StyleSnapshot getStyleSnapshot(int level) {
        String hex = getColorHex(level);
        BorderStyle border = getBorder(level);
        TooltipStyle tooltip = getTooltip(level);
        boolean itemNameColor = isNameColorEnabled(level);
        return new StyleSnapshot(level, hex, border, tooltip, itemNameColor);
    }

    /** 配置是否已成功初始化 */
    public static boolean isAvailable() {
        return available;
    }

    // ================================================================
    //  颜色注入
    // ================================================================

    private static void injectColors() {
        Map<Integer, Integer> colors = new LinkedHashMap<>();
        synchronized (RARITIES) {
            for (Map.Entry<Integer, PerRarity> e : RARITIES.entrySet()) {
                PerRarity pr = e.getValue();
                if (pr != null && pr.color != null && !"inherit".equalsIgnoreCase(pr.color) && !pr.color.isEmpty()) {
                    colors.put(e.getKey(), RarityColorUtil.parseHexColor(pr.color));
                }
            }
        }
        RarityColorUtil.setCustomColors(colors);
    }

    // ================================================================
    //  JSON 序列化（保存时）
    // ================================================================

    private static JsonObject buildConfigJson() {
        JsonObject root = new JsonObject();
        root.addProperty("enableBorder", enableBorder);
        root.addProperty("enableTooltip", enableTooltip);
        root.addProperty("tooltipColorEnabled", tooltipColorEnabled);

        JsonObject defaults = new JsonObject();
        defaults.addProperty("color", defaultsColor);

        JsonObject border = new JsonObject();
        border.addProperty("useTexture", defaultBorder.useTexture);
        border.addProperty("defaultTexture", defaultBorder.defaultTexture);
        border.addProperty("style", defaultBorder.style);
        border.addProperty("show", defaultBorder.show);
        border.addProperty("fallback", defaultBorder.fallback);
        defaults.add("border", border);

        JsonObject tooltip = new JsonObject();
        tooltip.addProperty("show", defaultTooltip.show);
        tooltip.addProperty("content", defaultTooltip.content);
        tooltip.addProperty("colored", defaultTooltip.colored);

        JsonObject level = new JsonObject();
        level.addProperty("colored", defaultTooltip.level.colored);
        level.addProperty("translationKey", defaultTooltip.level.translationKey);
        level.addProperty("fallback", defaultTooltip.level.fallback);
        tooltip.add("level", level);

        JsonObject star = new JsonObject();
        star.addProperty("colored", defaultTooltip.star.colored);
        star.addProperty("mode", defaultTooltip.star.mode);
        star.addProperty("repeatChar", defaultTooltip.star.repeatChar);
        star.addProperty("custom", defaultTooltip.star.custom);
        tooltip.add("star", star);

        defaults.add("tooltip", tooltip);
        defaults.addProperty("itemNameColor", defaultItemNameColor);

        JsonObject noRarity = new JsonObject();
        noRarity.addProperty("skip", defaultNoRarity.skip);
        noRarity.addProperty("defaultRarity", defaultNoRarity.defaultRarity);
        defaults.add("noRarity", noRarity);

        root.add("defaults", defaults);

        JsonObject rarities = new JsonObject();
        synchronized (RARITIES) {
            for (Map.Entry<Integer, PerRarity> e : RARITIES.entrySet()) {
                rarities.add(String.valueOf(e.getKey()), buildPerRarityJson(e.getValue()));
            }
        }
        root.add("rarities", rarities);
        return root;
    }

    private static JsonElement buildPerRarityJson(PerRarity pr) {
        JsonObject o = new JsonObject();
        if (pr.color != null) {
            o.addProperty("color", pr.color);
        }
        if (pr.border != null) {
            JsonObject b = new JsonObject();
            if (pr.border.useTextureSpecified) {
                b.addProperty("useTexture", pr.border.useTexture);
            }
            if (pr.border.defaultTextureSpecified) {
                b.addProperty("defaultTexture", pr.border.defaultTexture);
            }
            if (pr.border.styleSpecified) {
                b.addProperty("style", pr.border.style);
            }
            if (pr.border.showSpecified) {
                b.addProperty("show", pr.border.show);
            }
            if (pr.border.fallbackSpecified) {
                b.addProperty("fallback", pr.border.fallback);
            }
            o.add("border", b);
        }
        if (pr.tooltip != null) {
            JsonObject t = new JsonObject();
            if (pr.tooltip.showSpecified) {
                t.addProperty("show", pr.tooltip.show);
            }
            if (pr.tooltip.contentSpecified) {
                t.addProperty("content", pr.tooltip.content);
            }
            if (pr.tooltip.coloredSpecified) {
                t.addProperty("colored", pr.tooltip.colored);
            }
            if (pr.tooltip.levelSpecified) {
                JsonObject l = new JsonObject();
                if (pr.tooltip.level.coloredSpecified) {
                    l.addProperty("colored", pr.tooltip.level.colored);
                }
                if (pr.tooltip.level.translationKeySpecified) {
                    l.addProperty("translationKey", pr.tooltip.level.translationKey);
                }
                if (pr.tooltip.level.fallbackSpecified) {
                    l.addProperty("fallback", pr.tooltip.level.fallback);
                }
                t.add("level", l);
            }
            if (pr.tooltip.starSpecified) {
                JsonObject s = new JsonObject();
                if (pr.tooltip.star.coloredSpecified) {
                    s.addProperty("colored", pr.tooltip.star.colored);
                }
                if (pr.tooltip.star.modeSpecified) {
                    s.addProperty("mode", pr.tooltip.star.mode);
                }
                if (pr.tooltip.star.repeatCharSpecified) {
                    s.addProperty("repeatChar", pr.tooltip.star.repeatChar);
                }
                if (pr.tooltip.star.customSpecified) {
                    s.addProperty("custom", pr.tooltip.star.custom);
                }
                t.add("star", s);
            }
            o.add("tooltip", t);
        }
        if (pr.itemNameColor != null) {
            o.addProperty("itemNameColor", pr.itemNameColor);
        }
        return o;
    }

    // ================================================================
    //  内部合并辅助
    // ================================================================

    private static void mergeBorder(BorderCfg src, BorderCfg acc) {
        if (src.useTextureSpecified) {
            acc.useTexture = src.useTexture;
            acc.useTextureSpecified = true;
        }
        if (src.defaultTextureSpecified) {
            acc.defaultTexture = src.defaultTexture;
            acc.defaultTextureSpecified = true;
        }
        if (src.styleSpecified) {
            acc.style = src.style;
            acc.styleSpecified = true;
        }
        if (src.showSpecified) {
            acc.show = src.show;
            acc.showSpecified = true;
        }
        if (src.fallbackSpecified) {
            acc.fallback = src.fallback;
            acc.fallbackSpecified = true;
        }
    }

    private static TooltipCfg copyTooltip(TooltipCfg src) {
        TooltipCfg c = new TooltipCfg();
        c.show = src.show;
        c.content = src.content;
        c.colored = src.colored;
        c.level = new LevelCfg();
        c.level.colored = src.level.colored;
        c.level.translationKey = src.level.translationKey;
        c.level.fallback = src.level.fallback;
        c.level.coloredSpecified = src.level.coloredSpecified;
        c.level.translationKeySpecified = src.level.translationKeySpecified;
        c.level.fallbackSpecified = src.level.fallbackSpecified;
        c.star = new StarCfg();
        c.star.colored = src.star.colored;
        c.star.mode = src.star.mode;
        c.star.repeatChar = src.star.repeatChar;
        c.star.custom = src.star.custom;
        c.star.coloredSpecified = src.star.coloredSpecified;
        c.star.modeSpecified = src.star.modeSpecified;
        c.star.repeatCharSpecified = src.star.repeatCharSpecified;
        c.star.customSpecified = src.star.customSpecified;
        c.showSpecified = src.showSpecified;
        c.contentSpecified = src.contentSpecified;
        c.coloredSpecified = src.coloredSpecified;
        c.levelSpecified = src.levelSpecified;
        c.starSpecified = src.starSpecified;
        return c;
    }

    private static void mergeTooltip(TooltipCfg src, TooltipCfg acc) {
        if (src.showSpecified) {
            acc.show = src.show;
            acc.showSpecified = true;
        }
        if (src.contentSpecified) {
            acc.content = src.content;
            acc.contentSpecified = true;
        }
        if (src.coloredSpecified) {
            acc.colored = src.colored;
            acc.coloredSpecified = true;
        }
        if (src.levelSpecified) {
            if (src.level.coloredSpecified) {
                acc.level.colored = src.level.colored;
                acc.level.coloredSpecified = true;
            }
            if (src.level.translationKeySpecified) {
                acc.level.translationKey = src.level.translationKey;
                acc.level.translationKeySpecified = true;
            }
            if (src.level.fallbackSpecified) {
                acc.level.fallback = src.level.fallback;
                acc.level.fallbackSpecified = true;
            }
        }
        if (src.starSpecified) {
            if (src.star.coloredSpecified) {
                acc.star.colored = src.star.colored;
                acc.star.coloredSpecified = true;
            }
            if (src.star.modeSpecified) {
                acc.star.mode = src.star.mode;
                acc.star.modeSpecified = true;
            }
            if (src.star.repeatCharSpecified) {
                acc.star.repeatChar = src.star.repeatChar;
                acc.star.repeatCharSpecified = true;
            }
            if (src.star.customSpecified) {
                acc.star.custom = src.star.custom;
                acc.star.customSpecified = true;
            }
        }
    }

    private static void clearState() {
        synchronized (RARITIES) {
            RARITIES.clear();
        }
        version = 1;
        enableBorder = true;
        enableTooltip = true;
        tooltipColorEnabled = true;
        defaultsColor = "inherit";
        defaultBorder = new BorderCfg();
        defaultTooltip = new TooltipCfg();
        defaultItemNameColor = true;
        defaultNoRarity = new NoRarityCfg();
        batchDepth = 0;
        batchAffectedLevels.clear();
        batchTargets.clear();
        available = false;
    }

    private static boolean getBoolean(JsonObject o, String key, boolean def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : def;
    }

    private static int getInt(JsonObject o, String key, int def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : def;
    }
}
