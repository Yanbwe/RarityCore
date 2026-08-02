package org.yanbwe.raritycore.config;

import com.google.gson.*;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * RarityStyle.json 配置管理器（V14）。
 *
 * <p>集中管理所有稀有度视觉表现配置，整合旧版 client.json 与 RarityClientConfig.json。
 *
 * <h3>继承规则</h3>
 * 任一可配置字段在某稀有度缺失（或 color 显式为 "inherit"）时，
 * 从该稀有度向低等级逐级查找首个显式指定的值；
 * 若 1~当前级均无可继承值，则使用 defaults 顶层对应默认。
 */
public class RarityStyleConfigManager {

    private static final Gson GSON = JsonPerformanceOptimizer.getOptimizedGson();

    private static final Path CONFIG_DIR = Paths.get(
            RarityConstants.CONFIG_DIR_PARENT).resolve(RarityConstants.CONFIG_DIR_NAME);
    private static final Path STYLE_CONFIG_FILE = CONFIG_DIR.resolve(
            RarityConstants.RARITY_STYLE_CONFIG_FILE_NAME);
    private static final Path OLD_CLIENT_CONFIG_FILE = CONFIG_DIR.resolve(
            RarityConstants.RARITY_CLIENT_CONFIG_FILE_NAME);

    private static volatile RarityStyleConfigManager instance;

    // 全局开关
    private boolean enableBorder = true;
    private boolean enableTooltip = true;
    private boolean tooltipColorEnabled = true;

    // 默认值
    private DefaultsConfig defaults = new DefaultsConfig();

    // 逐级配置：level(int) → PerRarityStyle
    private final Map<Integer, PerRarityStyle> rarities = new HashMap<>();

    // 继承解析缓存
    private final Map<Integer, Integer> colorCache = new HashMap<>();
    private final Map<Integer, BorderConfig> borderCache = new HashMap<>();
    private final Map<Integer, TooltipConfig> tooltipCache = new HashMap<>();
    private final Map<Integer, Boolean> nameColorCache = new HashMap<>();

    // ================================================================
    //  数据模型
    // ================================================================

    public static class BorderConfig {
        public boolean useTexture = true;
        public int style = 1;
        public boolean show = true;
        public String texturePath;
        public String fallback = "inherit";

        public BorderConfig() {}
        public BorderConfig(boolean useTexture, int style, boolean show, String texturePath, String fallback) {
            this.useTexture = useTexture; this.style = style; this.show = show;
            this.texturePath = texturePath; this.fallback = fallback;
        }
        public BorderConfig copy() {
            return new BorderConfig(useTexture, style, show, texturePath, fallback);
        }
    }

    public static class TooltipLevelConfig {
        public boolean colored = true;
        public String translationKey = "$(rarity.core.{level})";
        public String fallback = "{level}$(rarity.core.special.rarity.prefix)";

        public TooltipLevelConfig() {}
        public TooltipLevelConfig(boolean colored, String translationKey, String fallback) {
            this.colored = colored; this.translationKey = translationKey; this.fallback = fallback;
        }
        public TooltipLevelConfig copy() {
            return new TooltipLevelConfig(colored, translationKey, fallback);
        }
    }

    public static class TooltipStarConfig {
        public boolean colored = true;
        public String mode = "repeat";
        public String repeatChar = "★";
        public String custom = "";

        public TooltipStarConfig() {}
        public TooltipStarConfig(boolean colored, String mode, String repeatChar, String custom) {
            this.colored = colored; this.mode = mode; this.repeatChar = repeatChar; this.custom = custom;
        }
        public TooltipStarConfig copy() {
            return new TooltipStarConfig(colored, mode, repeatChar, custom);
        }
    }

    public static class TooltipConfig {
        public boolean show = true;
        public String content = "[@{level}] @{star}";
        public boolean colored = true;
        public TooltipLevelConfig level = new TooltipLevelConfig();
        public TooltipStarConfig star = new TooltipStarConfig();

        public TooltipConfig() {}
        public TooltipConfig copy() {
            TooltipConfig c = new TooltipConfig();
            c.show = show; c.content = content; c.colored = colored;
            c.level = level.copy(); c.star = star.copy();
            return c;
        }
    }

    public static class NoRarityConfig {
        public boolean skip = false;
        public int defaultRarity = 1;
    }

    public static class DefaultsConfig {
        public String color = "inherit";
        public BorderConfig border = new BorderConfig();
        public TooltipConfig tooltip = new TooltipConfig();
        public boolean itemNameColor = true;
        public NoRarityConfig noRarity = new NoRarityConfig();
    }

    public static class PerRarityStyle {
        public Integer color;        // null 表示继承
        public BorderConfig border;   // null 表示继承
        public TooltipConfig tooltip; // null 表示继承
        public Boolean itemNameColor; // null 表示继承
    }

    // ================================================================
    //  单例
    // ================================================================

    private RarityStyleConfigManager() {
        load();
    }

    public static RarityStyleConfigManager getInstance() {
        if (instance == null) {
            synchronized (RarityStyleConfigManager.class) {
                if (instance == null) {
                    instance = new RarityStyleConfigManager();
                }
            }
        }
        return instance;
    }

    public static void replaceInstance(RarityStyleConfigManager newInstance) {
        synchronized (RarityStyleConfigManager.class) {
            instance = newInstance;
        }
    }

    // ================================================================
    //  加载与保存
    // ================================================================

    public void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }

        // 删除旧 RarityClientConfig.json
        if (Files.exists(OLD_CLIENT_CONFIG_FILE)) {
            try {
                Files.delete(OLD_CLIENT_CONFIG_FILE);
                RarityCore.LOGGER.info("Deleted old config file: {}", OLD_CLIENT_CONFIG_FILE);
            } catch (Exception e) {
                RarityCore.LOGGER.warn("Failed to delete old config file: {}", OLD_CLIENT_CONFIG_FILE, e);
            }
        }

        if (!Files.exists(STYLE_CONFIG_FILE)) {
            createDefaultAndSave();
        }

        try (BufferedReader reader = Files.newBufferedReader(STYLE_CONFIG_FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                RarityCore.LOGGER.warn("RarityStyle.json is empty or malformed, recreating defaults");
                createDefaultAndSave();
                return;
            }

            parseRoot(root);
            RarityCore.LOGGER.info("RarityStyle.json loaded: {} rarity levels configured", rarities.size());
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to load RarityStyle.json, using defaults", e);
            createDefaultAndSave();
        }

        // 注入颜色到 RarityColorUtil
        injectColors();
    }

    private void parseRoot(JsonObject root) {
        enableBorder = JsonHelper.getBoolean(root, "enableBorder", true);
        enableTooltip = JsonHelper.getBoolean(root, "enableTooltip", true);
        tooltipColorEnabled = JsonHelper.getBoolean(root, "tooltipColorEnabled", true);

        if (root.has("defaults") && root.get("defaults").isJsonObject()) {
            parseDefaults(root.getAsJsonObject("defaults"));
        }

        rarities.clear();
        if (root.has("rarities") && root.get("rarities").isJsonObject()) {
            JsonObject raritiesObj = root.getAsJsonObject("rarities");
            for (String key : raritiesObj.keySet()) {
                try {
                    int level = Integer.parseInt(key);
                    if (level < 1) continue;
                    JsonObject entry = raritiesObj.getAsJsonObject(key);
                    PerRarityStyle style = parsePerRarity(entry);
                    rarities.put(level, style);
                } catch (NumberFormatException e) {
                    RarityCore.LOGGER.warn("Invalid rarity level key '{}' in RarityStyle.json", key);
                }
            }
        }

        invalidateCaches();
    }

    private void parseDefaults(JsonObject obj) {
        defaults = new DefaultsConfig();
        if (obj.has("color")) {
            defaults.color = obj.get("color").getAsString();
        }
        if (obj.has("border") && obj.get("border").isJsonObject()) {
            defaults.border = parseBorder(obj.getAsJsonObject("border"));
        }
        if (obj.has("tooltip") && obj.get("tooltip").isJsonObject()) {
            defaults.tooltip = parseTooltip(obj.getAsJsonObject("tooltip"));
        }
        if (obj.has("itemNameColor")) {
            defaults.itemNameColor = obj.get("itemNameColor").getAsBoolean();
        }
        if (obj.has("noRarity") && obj.get("noRarity").isJsonObject()) {
            JsonObject nr = obj.getAsJsonObject("noRarity");
            defaults.noRarity.skip = JsonHelper.getBoolean(nr, "skip", false);
            defaults.noRarity.defaultRarity = JsonHelper.getInt(nr, "defaultRarity", 1);
        }
    }

    private BorderConfig parseBorder(JsonObject obj) {
        BorderConfig b = new BorderConfig();
        b.useTexture = JsonHelper.getBoolean(obj, "useTexture", true);
        b.style = JsonHelper.getInt(obj, "style", 1);
        b.show = JsonHelper.getBoolean(obj, "show", true);
        if (obj.has("defaultTexture")) {
            b.texturePath = obj.get("defaultTexture").getAsString();
        } else {
            b.texturePath = "raritycore:textures/border/rarity_{level}.png";
        }
        if (obj.has("fallback")) {
            b.fallback = obj.get("fallback").getAsString();
        }
        return b;
    }

    private TooltipConfig parseTooltip(JsonObject obj) {
        TooltipConfig t = new TooltipConfig();
        t.show = JsonHelper.getBoolean(obj, "show", true);
        if (obj.has("content")) {
            t.content = obj.get("content").getAsString();
        }
        t.colored = JsonHelper.getBoolean(obj, "colored", true);

        if (obj.has("level") && obj.get("level").isJsonObject()) {
            JsonObject lvl = obj.getAsJsonObject("level");
            t.level.colored = JsonHelper.getBoolean(lvl, "colored", true);
            if (lvl.has("translationKey")) {
                t.level.translationKey = lvl.get("translationKey").getAsString();
            }
            if (lvl.has("fallback")) {
                t.level.fallback = lvl.get("fallback").getAsString();
            }
        }
        if (obj.has("star") && obj.get("star").isJsonObject()) {
            JsonObject star = obj.getAsJsonObject("star");
            t.star.colored = JsonHelper.getBoolean(star, "colored", true);
            if (star.has("mode")) t.star.mode = star.get("mode").getAsString();
            if (star.has("repeatChar")) t.star.repeatChar = star.get("repeatChar").getAsString();
            if (star.has("custom")) t.star.custom = star.get("custom").getAsString();
        }
        return t;
    }

    private PerRarityStyle parsePerRarity(JsonObject obj) {
        PerRarityStyle s = new PerRarityStyle();
        if (obj.has("color")) {
            String c = obj.get("color").getAsString();
            if ("inherit".equalsIgnoreCase(c)) {
                s.color = null;
            } else {
                s.color = RarityColorUtil.parseRgbColor(c);
            }
        }
        if (obj.has("border") && obj.get("border").isJsonObject()) {
            s.border = parseBorder(obj.getAsJsonObject("border"));
        }
        if (obj.has("tooltip") && obj.get("tooltip").isJsonObject()) {
            s.tooltip = parseTooltip(obj.getAsJsonObject("tooltip"));
        }
        if (obj.has("itemNameColor")) {
            s.itemNameColor = obj.get("itemNameColor").getAsBoolean();
        }
        return s;
    }

    private void createDefaultAndSave() {
        JsonObject root = createDefaultConfigJson();
        saveJson(root);
        parseRoot(root);
    }

    /**
     * 生成默认配置的 JsonObject。
     */
    public JsonObject createDefaultConfigJson() {
        JsonObject root = new JsonObject();
        root.addProperty("enableBorder", true);
        root.addProperty("enableTooltip", true);
        root.addProperty("tooltipColorEnabled", true);

        // defaults
        JsonObject defaultsObj = new JsonObject();
        defaultsObj.addProperty("color", "inherit");

        JsonObject borderObj = new JsonObject();
        borderObj.addProperty("useTexture", true);
        borderObj.addProperty("defaultTexture", "raritycore:textures/border/rarity_{level}.png");
        borderObj.addProperty("style", 1);
        borderObj.addProperty("show", true);
        borderObj.addProperty("fallback", "inherit");
        defaultsObj.add("border", borderObj);

        JsonObject tooltipObj = new JsonObject();
        tooltipObj.addProperty("show", true);
        tooltipObj.addProperty("content", "[@{level}] @{star}");
        tooltipObj.addProperty("colored", true);

        JsonObject levelObj = new JsonObject();
        levelObj.addProperty("colored", true);
        levelObj.addProperty("translationKey", "$(rarity.core.{level})");
        levelObj.addProperty("fallback", "{level}$(rarity.core.special.rarity.prefix)");
        tooltipObj.add("level", levelObj);

        JsonObject starObj = new JsonObject();
        starObj.addProperty("colored", true);
        starObj.addProperty("mode", "repeat");
        starObj.addProperty("repeatChar", "★");
        starObj.addProperty("custom", "");
        tooltipObj.add("star", starObj);
        defaultsObj.add("tooltip", tooltipObj);

        defaultsObj.addProperty("itemNameColor", true);

        JsonObject noRarityObj = new JsonObject();
        noRarityObj.addProperty("skip", false);
        noRarityObj.addProperty("defaultRarity", 1);
        defaultsObj.add("noRarity", noRarityObj);

        root.add("defaults", defaultsObj);

        // rarities 1-7 预置颜色
        JsonObject raritiesObj = new JsonObject();
        raritiesObj.add("1", createRarityEntry("#CCCCCC"));
        raritiesObj.add("2", createRarityEntry("#55FF55"));
        raritiesObj.add("3", createRarityEntry("#55FFFF"));
        raritiesObj.add("4", createRarityEntry("#FF55FF"));
        raritiesObj.add("5", createRarityEntry("#FFCC00"));
        raritiesObj.add("6", createRarityEntry("#FF6666"));
        raritiesObj.add("7", createRarityEntry("#FF3333"));
        root.add("rarities", raritiesObj);

        return root;
    }

    private JsonObject createRarityEntry(String hexColor) {
        JsonObject entry = new JsonObject();
        entry.addProperty("color", hexColor);
        return entry;
    }

    public void saveToFile() {
        JsonObject root = buildJson();
        saveJson(root);
    }

    private JsonObject buildJson() {
        JsonObject root = new JsonObject();
        root.addProperty("enableBorder", enableBorder);
        root.addProperty("enableTooltip", enableTooltip);
        root.addProperty("tooltipColorEnabled", tooltipColorEnabled);

        // defaults
        JsonObject defaultsObj = new JsonObject();
        defaultsObj.addProperty("color", defaults.color);
        defaultsObj.add("border", buildBorderJson(defaults.border));
        defaultsObj.add("tooltip", buildTooltipJson(defaults.tooltip));
        defaultsObj.addProperty("itemNameColor", defaults.itemNameColor);
        JsonObject nrObj = new JsonObject();
        nrObj.addProperty("skip", defaults.noRarity.skip);
        nrObj.addProperty("defaultRarity", defaults.noRarity.defaultRarity);
        defaultsObj.add("noRarity", nrObj);
        root.add("defaults", defaultsObj);

        // rarities
        JsonObject raritiesObj = new JsonObject();
        for (Map.Entry<Integer, PerRarityStyle> entry : rarities.entrySet()) {
            PerRarityStyle s = entry.getValue();
            JsonObject entryObj = new JsonObject();
            if (s.color != null) {
                entryObj.addProperty("color", String.format("#%06X", s.color & 0xFFFFFF));
            }
            if (s.border != null) {
                entryObj.add("border", buildBorderJson(s.border));
            }
            if (s.tooltip != null) {
                entryObj.add("tooltip", buildTooltipJson(s.tooltip));
            }
            if (s.itemNameColor != null) {
                entryObj.addProperty("itemNameColor", s.itemNameColor);
            }
            raritiesObj.add(String.valueOf(entry.getKey()), entryObj);
        }
        root.add("rarities", raritiesObj);

        return root;
    }

    private JsonObject buildBorderJson(BorderConfig b) {
        JsonObject obj = new JsonObject();
        obj.addProperty("useTexture", b.useTexture);
        obj.addProperty("style", b.style);
        obj.addProperty("show", b.show);
        if (b.texturePath != null) {
            obj.addProperty("defaultTexture", b.texturePath);
        }
        obj.addProperty("fallback", b.fallback);
        return obj;
    }

    private JsonObject buildTooltipJson(TooltipConfig t) {
        JsonObject obj = new JsonObject();
        obj.addProperty("show", t.show);
        obj.addProperty("content", t.content);
        obj.addProperty("colored", t.colored);
        JsonObject lvl = new JsonObject();
        lvl.addProperty("colored", t.level.colored);
        lvl.addProperty("translationKey", t.level.translationKey);
        lvl.addProperty("fallback", t.level.fallback);
        obj.add("level", lvl);
        JsonObject star = new JsonObject();
        star.addProperty("colored", t.star.colored);
        star.addProperty("mode", t.star.mode);
        star.addProperty("repeatChar", t.star.repeatChar);
        star.addProperty("custom", t.star.custom);
        obj.add("star", star);
        return obj;
    }

    private void saveJson(JsonObject json) {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(STYLE_CONFIG_FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to save RarityStyle.json", e);
        }
    }

    // ================================================================
    //  颜色注入
    // ================================================================

    private void injectColors() {
        Map<Integer, Integer> colors = new HashMap<>();
        for (int i = 1; i <= Math.max(RarityConstants.MAX_RARITY, rarities.size()); i++) {
            int rgb = resolveColor(i);
            colors.put(i, rgb);
        }
        RarityColorUtil.setCustomColors(colors);
    }

    // ================================================================
    //  继承解析
    // ================================================================

    /** 内置色表，用于 defaults.color=inherit 时的回退 */
    private static final int[] BUILTIN_COLORS = {
        0,                  // index 0 unused
        0xCCCCCC,           // 1
        0x55FF55,           // 2
        0x55FFFF,           // 3
        0xFF55FF,           // 4
        0xFFCC00,           // 5
        0xFF6666,           // 6
        0xFF3333            // 7
    };

    /**
     * 解析指定等级的颜色（带继承）。
     * 继承路径：该等级显式配置 → 向下找 → defaults.color → 内置色表 → MAX_RARITY 色
     */
    public int resolveColor(int level) {
        if (level < 1) level = 1;
        Integer cached = colorCache.get(level);
        if (cached != null) return cached;

        // 从该等级向下查找
        for (int l = level; l >= 1; l--) {
            PerRarityStyle s = rarities.get(l);
            if (s != null && s.color != null) {
                colorCache.put(level, s.color);
                return s.color;
            }
        }

        // 回退到 defaults.color
        if (!"inherit".equalsIgnoreCase(defaults.color)) {
            int rgb = RarityColorUtil.parseRgbColor(defaults.color);
            colorCache.put(level, rgb);
            return rgb;
        }

        // defaults.color 为 inherit，使用内置色表
        int rgb;
        if (level <= RarityConstants.MAX_RARITY) {
            rgb = BUILTIN_COLORS[level];
        } else {
            rgb = BUILTIN_COLORS[RarityConstants.MAX_RARITY]; // >7 回退到 7
        }
        colorCache.put(level, rgb);
        return rgb;
    }

    /**
     * 解析指定等级的边框配置（带继承）。
     */
    public BorderConfig resolveBorder(int level) {
        if (level < 1) level = 1;
        BorderConfig cached = borderCache.get(level);
        if (cached != null) return cached;

        BorderConfig result = defaults.border.copy();

        // 逐级覆盖
        for (int l = 1; l <= level; l++) {
            PerRarityStyle s = rarities.get(l);
            if (s != null && s.border != null) {
                BorderConfig b = s.border;
                result.useTexture = b.useTexture;
                result.style = b.style;
                result.show = b.show;
                if (b.texturePath != null) result.texturePath = b.texturePath;
                result.fallback = b.fallback;
            }
        }

        borderCache.put(level, result);
        return result;
    }

    /**
     * 获取边框纹理路径（文档 3.3 节逻辑）。
     */
    public String getBorderTexture(int level) {
        if (level < 1) level = 1;

        // 内置档位：使用模板
        if (level <= RarityConstants.MAX_RARITY) {
            return defaults.border.texturePath.replace("{level}", String.valueOf(level));
        }

        // 超出内置档位：检查是否显式配置了纹理
        PerRarityStyle s = rarities.get(level);
        if (s != null && s.border != null && s.border.texturePath != null) {
            return s.border.texturePath.replace("{level}", String.valueOf(level));
        }

        // 未显式配置：按 fallback 处理
        BorderConfig resolved = resolveBorder(level);
        if ("inherit".equals(resolved.fallback)) {
            // 复用 MAX_RARITY 纹理
            return defaults.border.texturePath.replace("{level}", String.valueOf(RarityConstants.MAX_RARITY));
        } else {
            return resolved.fallback.replace("{level}", String.valueOf(level));
        }
    }

    /**
     * 解析指定等级的工具提示配置（带继承）。
     */
    public TooltipConfig resolveTooltip(int level) {
        if (level < 1) level = 1;
        TooltipConfig cached = tooltipCache.get(level);
        if (cached != null) return cached.copy();

        TooltipConfig result = defaults.tooltip.copy();

        for (int l = 1; l <= level; l++) {
            PerRarityStyle s = rarities.get(l);
            if (s != null && s.tooltip != null) {
                TooltipConfig t = s.tooltip;
                result.show = t.show;
                if (t.content != null) result.content = t.content;
                result.colored = t.colored;
                if (t.level != null) {
                    result.level.colored = t.level.colored;
                    if (t.level.translationKey != null) result.level.translationKey = t.level.translationKey;
                    if (t.level.fallback != null) result.level.fallback = t.level.fallback;
                }
                if (t.star != null) {
                    result.star.colored = t.star.colored;
                    if (t.star.mode != null) result.star.mode = t.star.mode;
                    if (t.star.repeatChar != null) result.star.repeatChar = t.star.repeatChar;
                    if (t.star.custom != null) result.star.custom = t.star.custom;
                }
            }
        }

        tooltipCache.put(level, result);
        return result.copy();
    }

    /**
     * 解析指定等级的 itemNameColor（带继承）。
     */
    public boolean resolveItemNameColor(int level) {
        if (level < 1) level = 1;
        Boolean cached = nameColorCache.get(level);
        if (cached != null) return cached;

        for (int l = level; l >= 1; l--) {
            PerRarityStyle s = rarities.get(l);
            if (s != null && s.itemNameColor != null) {
                nameColorCache.put(level, s.itemNameColor);
                return s.itemNameColor;
            }
        }

        nameColorCache.put(level, defaults.itemNameColor);
        return defaults.itemNameColor;
    }

    /**
     * 解析等级名称组件。根据 translationKey 模板生成可翻译组件。
     * 若 translationKey 对应翻译不存在（getString 返回键名本身），则回退到 fallback 模板。
     */
    public Component resolveLevelNameComponent(int level) {
        TooltipConfig t = resolveTooltip(level);
        String template = t.level.translationKey.replace("{level}", String.valueOf(level));
        Component result = StringResolver.resolveTranslation(template, level);

        // 提取翻译键：$(key) → key，否则为整个模板
        String expectedKey;
        if (template.startsWith("$(") && template.endsWith(")")) {
            expectedKey = template.substring(2, template.length() - 1);
        } else {
            expectedKey = template;
        }

        // 翻译缺失时 Component.translatable(key).getString() 直接返回键名本身
        if (result.getString().equals(expectedKey)) {
            String fallbackTemplate = t.level.fallback.replace("{level}", String.valueOf(level));
            return StringResolver.resolveTranslation(fallbackTemplate, level);
        }
        return result;
    }

    // ================================================================
    //  逐级查询便捷方法
    // ================================================================

    public boolean isLevelRendererEnabled(int level) {
        return resolveBorder(level).show;
    }

    public boolean isLevelTooltipEnabled(int level) {
        return resolveTooltip(level).show;
    }

    public boolean isLevelNameColorEnabled(int level) {
        return resolveItemNameColor(level);
    }

    // ================================================================
    //  缓存管理
    // ================================================================

    public void invalidateCaches() {
        colorCache.clear();
        borderCache.clear();
        tooltipCache.clear();
        nameColorCache.clear();
    }

    public void reload() {
        invalidateCaches();
        rarities.clear();
        load();
    }

    public Set<Integer> getConfiguredLevels() {
        return new HashSet<>(rarities.keySet());
    }

    // ================================================================
    //  全局开关
    // ================================================================

    public boolean isBorderEnabled() { return enableBorder; }
    public void setBorderEnabled(boolean v) { enableBorder = v; saveToFile(); invalidateCaches(); }

    public boolean isTooltipEnabled() { return enableTooltip; }
    public void setTooltipEnabled(boolean v) { enableTooltip = v; saveToFile(); invalidateCaches(); }

    public boolean isTooltipColorEnabled() { return tooltipColorEnabled; }
    public void setTooltipColorEnabled(boolean v) { tooltipColorEnabled = v; saveToFile(); invalidateCaches(); }

    public boolean isNoRaritySkip() { return defaults.noRarity.skip; }
    public int getNoRarityDefaultRarity() { return defaults.noRarity.defaultRarity; }

    // ================================================================
    //  逐级 Setter（自动保存）
    // ================================================================

    public void setColor(int level, String hex) {
        ensureLevel(level).color = RarityColorUtil.parseRgbColor(hex);
        saveToFile(); invalidateCaches();
    }

    public void setBorderUseTexture(int level, boolean v) {
        ensureBorder(level).useTexture = v;
        saveToFile(); invalidateCaches();
    }

    public void setBorderStyle(int level, int style) {
        ensureBorder(level).style = style;
        saveToFile(); invalidateCaches();
    }

    public void setBorderShow(int level, boolean v) {
        ensureBorder(level).show = v;
        saveToFile(); invalidateCaches();
    }

    public void setTooltipShow(int level, boolean v) {
        ensureTooltip(level).show = v;
        saveToFile(); invalidateCaches();
    }

    public void setTooltipContent(int level, String content) {
        ensureTooltip(level).content = content;
        saveToFile(); invalidateCaches();
    }

    public void setTooltipColored(int level, boolean v) {
        ensureTooltip(level).colored = v;
        saveToFile(); invalidateCaches();
    }

    public void setTooltipStarMode(int level, String mode) {
        ensureTooltip(level).star.mode = mode;
        saveToFile(); invalidateCaches();
    }

    public void setItemNameColorEnabled(int level, boolean v) {
        ensureLevel(level).itemNameColor = v;
        saveToFile(); invalidateCaches();
    }

    // ================================================================
    //  内部辅助
    // ================================================================

    private PerRarityStyle ensureLevel(int level) {
        PerRarityStyle s = rarities.get(level);
        if (s == null) {
            s = new PerRarityStyle();
            rarities.put(level, s);
        }
        return s;
    }

    private BorderConfig ensureBorder(int level) {
        PerRarityStyle s = ensureLevel(level);
        if (s.border == null) {
            s.border = new BorderConfig();
        }
        return s.border;
    }

    private TooltipConfig ensureTooltip(int level) {
        PerRarityStyle s = ensureLevel(level);
        if (s.tooltip == null) {
            s.tooltip = new TooltipConfig();
        }
        return s.tooltip;
    }

    // ================================================================
    //  简单的 JSON Helper
    // ================================================================

    private static class JsonHelper {
        static boolean getBoolean(JsonObject obj, String key, boolean def) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsBoolean();
            }
            return def;
        }
        static int getInt(JsonObject obj, String key, int def) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsInt();
            }
            return def;
        }
    }
}
