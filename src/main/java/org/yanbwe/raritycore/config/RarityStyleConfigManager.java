package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraftforge.common.MinecraftForge;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 稀有度视觉表现配置管理器（V14）
 * 集中管理 RarityStyle.json 中的全部视觉表现配置，支持字段级继承
 */
public class RarityStyleConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Paths.get(RarityConstants.CONFIG_DIR_PARENT).resolve(RarityConstants.CONFIG_DIR_NAME);
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve(RarityConstants.RARITY_STYLE_CONFIG_FILE_NAME);

    /**
     * V14 起已由 RarityStyle.json（defaults 段）接管、需要从 client.json 中剔除的旧总开关键
     * 仅收录"删除后不改变任何行为"的键：这两个旧键与新键 enableBorder / enableTooltip 的
     * 默认值一致（都为 true），且已无任何读取方，属于纯粹的死键
     *
     * 刻意不收录 itemBorderStyle / useTextureBorder / enableItemNameColor / skipUnconfiguredItems /
     * starDisplay 等旧键：迁移逻辑并不读取它们的值来实现迁移，直接删除等于静默丢弃用户的设置
     * （例如用户手动设过 useTextureBorder=true 时会因删键而回退）。将它们原样保留，零副作用
     */
    private static final String[] LEGACY_CLIENT_KEYS = {
        "enableItemBorderRendering",
        "enableTooltipInsert"
    };

    // 全局主开关（对应旧 client.json 总开关）
    private static boolean enableBorder = true;
    private static boolean enableTooltip = true;
    private static boolean tooltipColorEnabled = true;

    // 默认值根（defaults）
    private static DefaultsConfig defaults = new DefaultsConfig();

    // 各稀有度覆盖配置（缺失字段向低等级继承）
    private static final Map<Integer, LevelOverride> RARITIES = new ConcurrentHashMap<>();

    static {
        for (int i = 1; i <= RarityConstants.MAX_RARITY; i++) {
            RARITIES.put(i, new LevelOverride());
        }
    }

    /**
     * 边框配置
     */
    public static class BorderConfig {
        public boolean useTexture = true;
        public String defaultTexture = RarityConstants.BORDER_TEXTURE_PATH + "rarity_{level}.png";
        public int style = 1; // 1=实心, 0=空心
        public boolean show = true;
        // 未显式配置边框的等级的纹理回退："inherit"=沿用最高已配置档位纹理；否则为具体纹理路径（支持 {level}）
        public String fallback = "inherit";
        // 显式指定标记（用于继承判断）
        public boolean useTextureSpecified;
        public boolean defaultTextureSpecified;
        public boolean styleSpecified;
        public boolean showSpecified;
        public boolean fallbackSpecified;
    }

    /**
     * 工具提示 level 段配置
     */
    public static class LevelSegmentConfig {
        public boolean colored = true;
        public String translationKey = "$(rarity.core.{level})";
        public String fallback = "$(rarity.core.special.rarity.prefix)";
        public boolean coloredSpecified;
        public boolean translationKeySpecified;
        public boolean fallbackSpecified;
    }

    /**
     * 工具提示 star 段配置
     */
    public static class StarSegmentConfig {
        public boolean colored = true;
        public String mode = "repeat"; // repeat / custom
        public String repeatChar = "★";
        public String custom = "";
        public boolean coloredSpecified;
        public boolean modeSpecified;
        public boolean repeatCharSpecified;
        public boolean customSpecified;
    }

    /**
     * 工具提示配置
     */
    public static class TooltipConfig {
        public boolean show = true;
        public String content = "[@{level}] @{star}";
        public boolean colored = true; // 对整个工具提示行染色
        public LevelSegmentConfig level = new LevelSegmentConfig();
        public StarSegmentConfig star = new StarSegmentConfig();
        public boolean showSpecified;
        public boolean contentSpecified;
        public boolean coloredSpecified;
    }

    /**
     * 无稀有度物品配置
     */
    public static class NoRarityConfig {
        public boolean skip = false;
        public int defaultRarity = 1;
    }

    /**
     * 默认值根配置
     */
    public static class DefaultsConfig {
        public String color = "inherit";
        public BorderConfig border = new BorderConfig();
        public TooltipConfig tooltip = new TooltipConfig();
        public boolean itemNameColor = true;
        public NoRarityConfig noRarity = new NoRarityConfig();
    }

    /**
     * 单级覆盖配置（仅记录显式指定的字段）
     */
    public static class LevelOverride {
        public String color; // null = 继承；"inherit" = 继承；"#RRGGBB" = 指定
        public boolean colorSpecified;
        public BorderConfig border;
        public TooltipConfig tooltip;
        public Boolean itemNameColor;
    }

    // ───────────────────────── 初始化与加载 ─────────────────────────

    public static void initialize() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }
        // 顺序很重要：先清理 client.json 的旧键，再加载 RarityStyle 配置，
        // 最后重新加载客户端配置，确保补入的开关（如 enableIronSpellsAdapter）当次启动即生效
        handleLegacyFiles();
        loadConfig();
        ClientConfigManager.loadClientConfig();
    }

    public static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_FILE)) {
                generateDefaultConfigFile();
            }
            loadFromFile();
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading RarityStyle config, using defaults", e);
            resetToDefaults();
        }
    }

    private static void resetToDefaults() {
        defaults = new DefaultsConfig();
        RARITIES.clear();
        for (int i = 1; i <= RarityConstants.MAX_RARITY; i++) {
            RARITIES.put(i, new LevelOverride());
        }
        injectColors();
    }

    private static void loadFromFile() {
        try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                RarityCore.LOGGER.warn("RarityStyle config is empty, using defaults");
                resetToDefaults();
                return;
            }

            // 全局主开关
            if (json.has("enableBorder")) enableBorder = json.get("enableBorder").getAsBoolean();
            if (json.has("enableTooltip")) enableTooltip = json.get("enableTooltip").getAsBoolean();
            if (json.has("tooltipColorEnabled")) tooltipColorEnabled = json.get("tooltipColorEnabled").getAsBoolean();

            // defaults
            defaults = json.has("defaults") ? parseDefaults(json.getAsJsonObject("defaults")) : new DefaultsConfig();

            // rarities
            RARITIES.clear();
            for (int i = 1; i <= RarityConstants.MAX_RARITY; i++) {
                RARITIES.put(i, new LevelOverride());
            }
            if (json.has("rarities")) {
                JsonObject rarities = json.getAsJsonObject("rarities");
                for (String key : rarities.keySet()) {
                    try {
                        int level = Integer.parseInt(key);
                        if (level < 1) continue;
                        RARITIES.put(level, parseLevelOverride(rarities.getAsJsonObject(key)));
                    } catch (NumberFormatException e) {
                        RarityCore.LOGGER.warn("Invalid rarity key in RarityStyle: {}", key);
                    }
                }
            }

            injectColors();
            RarityCore.LOGGER.info("RarityStyle config loaded: enableBorder={}, enableTooltip={}, tooltipColorEnabled={}",
                enableBorder, enableTooltip, tooltipColorEnabled);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error parsing RarityStyle config file", e);
        }
    }

    private static DefaultsConfig parseDefaults(JsonObject obj) {
        DefaultsConfig d = new DefaultsConfig();
        if (obj.has("color")) d.color = obj.get("color").getAsString();
        if (obj.has("border")) d.border = parseBorder(obj.getAsJsonObject("border"), d.border);
        if (obj.has("tooltip")) d.tooltip = parseTooltip(obj.getAsJsonObject("tooltip"), d.tooltip);
        if (obj.has("itemNameColor")) d.itemNameColor = obj.get("itemNameColor").getAsBoolean();
        if (obj.has("noRarity")) {
            JsonObject nr = obj.getAsJsonObject("noRarity");
            if (nr.has("skip")) d.noRarity.skip = nr.get("skip").getAsBoolean();
            if (nr.has("defaultRarity")) d.noRarity.defaultRarity = nr.get("defaultRarity").getAsInt();
        }
        return d;
    }

    private static BorderConfig parseBorder(JsonObject obj, BorderConfig base) {
        BorderConfig b = new BorderConfig();
        b.useTexture = base.useTexture;
        b.defaultTexture = base.defaultTexture;
        b.style = base.style;
        b.show = base.show;
        b.fallback = base.fallback;
        if (obj.has("useTexture")) { b.useTexture = obj.get("useTexture").getAsBoolean(); b.useTextureSpecified = true; }
        if (obj.has("defaultTexture")) { b.defaultTexture = obj.get("defaultTexture").getAsString(); b.defaultTextureSpecified = true; }
        if (obj.has("style")) { b.style = obj.get("style").getAsInt(); b.styleSpecified = true; }
        if (obj.has("show")) { b.show = obj.get("show").getAsBoolean(); b.showSpecified = true; }
        if (obj.has("fallback")) { b.fallback = obj.get("fallback").getAsString(); b.fallbackSpecified = true; }
        return b;
    }

    private static TooltipConfig parseTooltip(JsonObject obj, TooltipConfig base) {
        TooltipConfig t = new TooltipConfig();
        t.show = base.show;
        t.content = base.content;
        t.level.colored = base.level.colored;
        t.level.translationKey = base.level.translationKey;
        t.level.fallback = base.level.fallback;
        t.star.colored = base.star.colored;
        t.star.mode = base.star.mode;
        t.star.repeatChar = base.star.repeatChar;
        t.star.custom = base.star.custom;
        if (obj.has("show")) { t.show = obj.get("show").getAsBoolean(); t.showSpecified = true; }
        if (obj.has("content")) { t.content = obj.get("content").getAsString(); t.contentSpecified = true; }
        if (obj.has("colored")) { t.colored = obj.get("colored").getAsBoolean(); t.coloredSpecified = true; }
        if (obj.has("level")) {
            JsonObject l = obj.getAsJsonObject("level");
            if (l.has("colored")) { t.level.colored = l.get("colored").getAsBoolean(); t.level.coloredSpecified = true; }
            if (l.has("translationKey")) { t.level.translationKey = l.get("translationKey").getAsString(); t.level.translationKeySpecified = true; }
            if (l.has("fallback")) { t.level.fallback = l.get("fallback").getAsString(); t.level.fallbackSpecified = true; }
        }
        if (obj.has("star")) {
            JsonObject s = obj.getAsJsonObject("star");
            if (s.has("colored")) { t.star.colored = s.get("colored").getAsBoolean(); t.star.coloredSpecified = true; }
            if (s.has("mode")) { t.star.mode = s.get("mode").getAsString(); t.star.modeSpecified = true; }
            if (s.has("repeatChar")) { t.star.repeatChar = s.get("repeatChar").getAsString(); t.star.repeatCharSpecified = true; }
            if (s.has("custom")) { t.star.custom = s.get("custom").getAsString(); t.star.customSpecified = true; }
        }
        return t;
    }

    private static LevelOverride parseLevelOverride(JsonObject obj) {
        LevelOverride o = new LevelOverride();
        if (obj.has("color")) {
            String c = obj.get("color").getAsString();
            o.color = c == null || c.isEmpty() ? "inherit" : c;
            o.colorSpecified = true;
        }
        if (obj.has("border")) o.border = parseBorder(obj.getAsJsonObject("border"), new BorderConfig());
        if (obj.has("tooltip")) o.tooltip = parseTooltip(obj.getAsJsonObject("tooltip"), new TooltipConfig());
        if (obj.has("itemNameColor")) o.itemNameColor = obj.get("itemNameColor").getAsBoolean();
        return o;
    }

    // ───────────────────────── 继承解析 ─────────────────────────

    /**
     * 获取某稀有度最终生效的颜色（含继承与内置兜底）
     */
    public static int getColor(int rarity) {
        Integer explicit = findExplicitColor(rarity);
        if (explicit != null) {
            if (explicit == -1) return RarityColorUtil.getRarityRgbColor(rarity);
            return explicit;
        }
        return RarityColorUtil.getRarityRgbColor(rarity);
    }

    /**
     * 向上查找首个显式指定的颜色
     * @return 指定色返回 0xRRGGBB；inherit 返回 -1；全无返回 null
     */
    private static Integer findExplicitColor(int rarity) {
        for (int l = rarity; l >= 1; l--) {
            LevelOverride o = RARITIES.get(l);
            if (o != null && o.colorSpecified) {
                if ("inherit".equalsIgnoreCase(o.color)) return -1;
                return RarityColorUtil.parseRgbColor(o.color);
            }
        }
        if (defaults.color != null) {
            if ("inherit".equalsIgnoreCase(defaults.color)) return -1;
            if (!defaults.color.isEmpty()) return RarityColorUtil.parseRgbColor(defaults.color);
        }
        return null;
    }

    public static BorderConfig getBorder(int rarity) {
        BorderConfig acc = new BorderConfig();
        acc.useTexture = defaults.border.useTexture;
        acc.defaultTexture = defaults.border.defaultTexture;
        acc.style = defaults.border.style;
        acc.show = defaults.border.show;
        acc.fallback = defaults.border.fallback;
        for (int l = 1; l <= rarity; l++) {
            LevelOverride o = RARITIES.get(l);
            if (o != null && o.border != null) {
                if (o.border.useTextureSpecified) {
                    acc.useTexture = o.border.useTexture;
                    acc.useTextureSpecified = true;
                }
                if (o.border.defaultTextureSpecified) {
                    acc.defaultTexture = o.border.defaultTexture;
                    acc.defaultTextureSpecified = true;
                }
                if (o.border.styleSpecified) {
                    acc.style = o.border.style;
                    acc.styleSpecified = true;
                }
                if (o.border.showSpecified) {
                    acc.show = o.border.show;
                    acc.showSpecified = true;
                }
                if (o.border.fallbackSpecified) {
                    acc.fallback = o.border.fallback;
                    acc.fallbackSpecified = true;
                }
            }
        }
        return acc;
    }

    public static TooltipConfig getTooltip(int rarity) {
        TooltipConfig acc = new TooltipConfig();
        copyTooltip(defaults.tooltip, acc);
        for (int l = 1; l <= rarity; l++) {
            LevelOverride o = RARITIES.get(l);
            if (o != null && o.tooltip != null) mergeTooltip(o.tooltip, acc);
        }
        return acc;
    }

    public static boolean isItemNameColorEnabled(int rarity) {
        boolean acc = defaults.itemNameColor;
        for (int l = 1; l <= rarity; l++) {
            LevelOverride o = RARITIES.get(l);
            if (o != null && o.itemNameColor != null) acc = o.itemNameColor;
        }
        return acc;
    }

    public static boolean isBorderEnabled() { return enableBorder; }
    public static boolean isTooltipEnabled() { return enableTooltip; }
    public static boolean isTooltipColorEnabled() { return tooltipColorEnabled; }

    public static boolean isLevelTooltipEnabled(int rarity) {
        return isTooltipEnabled() && getTooltip(rarity).show;
    }

    public static boolean isLevelRendererEnabled(int rarity) {
        return isBorderEnabled() && getBorder(rarity).show;
    }

    public static boolean isLevelNameColorEnabled(int rarity) {
        return isItemNameColorEnabled(rarity);
    }

    // ── 旧 client.json 全局开关委托入口 ──

    public static boolean getDefaultsNoRaritySkip() {
        return defaults.noRarity.skip;
    }

    public static int getDefaultsNoRarityDefaultRarity() {
        return defaults.noRarity.defaultRarity;
    }

    public static boolean getDefaultUseTexture() {
        return defaults.border.useTexture;
    }

    public static int getDefaultBorderStyle() {
        return defaults.border.style;
    }

    public static void setDefaultUseTexture(boolean useTexture) {
        defaults.border.useTexture = useTexture;
        defaults.border.useTextureSpecified = true;
        saveToFile();
        injectColors();
    }

    public static void setDefaultItemNameColor(boolean enable) {
        defaults.itemNameColor = enable;
        saveToFile();
    }

    public static String getLevelTranslationKey(int rarity) {
        return getTooltip(rarity).level.translationKey;
    }

    public static String getLevelFallbackKey(int rarity) {
        return getTooltip(rarity).level.fallback;
    }

    public static String getTooltipContent(int rarity) {
        return getTooltip(rarity).content;
    }

    /**
     * 依据 content 模板构建无样式工具提示文本（供 API 与渲染共用）
     * 按 @{level}/@{star} 占位符替换，并对模板中的 $(key) 嵌入键做翻译解析
     */
    public static String buildTooltipText(int rarity) {
        String content = getTooltip(rarity).content;
        String levelText = buildLevelText(rarity);
        String starText = org.yanbwe.raritycore.util.ComponentBuilder.getStars(rarity);
        final String levelToken = "@{level}";
        final String starToken = "@{star}";
        int idxLevel = content.indexOf(levelToken);
        int idxStar = content.indexOf(starToken);
        if (idxLevel < 0 && idxStar < 0) {
            return org.yanbwe.raritycore.util.StringResolver.resolveEmbeddedKeys(content);
        }
        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        while (cursor < content.length()) {
            int nextLevel = content.indexOf(levelToken, cursor);
            int nextStar = content.indexOf(starToken, cursor);
            int next = content.length();
            boolean isLevel = false;
            boolean isStar = false;
            if (nextLevel >= 0) {
                next = nextLevel;
                isLevel = true;
            }
            if (nextStar >= 0 && nextStar < next) {
                next = nextStar;
                isLevel = false;
                isStar = true;
            }
            if (next == cursor) {
                if (isLevel) {
                    sb.append(levelText);
                    cursor += levelToken.length();
                } else {
                    sb.append(starText);
                    cursor += starToken.length();
                }
                continue;
            }
            sb.append(org.yanbwe.raritycore.util.StringResolver.resolveEmbeddedKeys(content.substring(cursor, next)));
            cursor = next;
        }
        return sb.toString();
    }

    /**
     * 构建 level 段文本（翻译键解析 + 缺失回退），与渲染路径行为一致
     */
    private static String buildLevelText(int rarity) {
        String keyTemplate = getLevelTranslationKey(rarity);
        String key = keyTemplate.replace("{level}", String.valueOf(rarity));
        if (org.yanbwe.raritycore.util.StringResolver.isTranslationKey(key)) {
            String realKey = org.yanbwe.raritycore.util.StringResolver.extractKey(key);
            if (org.yanbwe.raritycore.util.StringResolver.isKeyMissing(realKey)) {
                String fb = getLevelFallbackKey(rarity).replace("{level}", String.valueOf(rarity));
                if (org.yanbwe.raritycore.util.StringResolver.isTranslationKey(fb)) {
                    return org.yanbwe.raritycore.util.StringResolver.translateAsString(
                            org.yanbwe.raritycore.util.StringResolver.extractKey(fb));
                }
                return org.yanbwe.raritycore.util.StringResolver.resolveEmbeddedKeys(fb);
            }
            return org.yanbwe.raritycore.util.StringResolver.translateAsString(realKey);
        }
        return org.yanbwe.raritycore.util.StringResolver.resolveEmbeddedKeys(key);
    }

    public static StarSegmentConfig getStarConfig(int rarity) {
        return getTooltip(rarity).star;
    }

    /**
     * 获取边框纹理路径
     * 内置档位（rarity ≤ MAX_RARITY）直接使用各自 {level} 模板解析出的纹理；
     * 超出内置档位且未显式配置边框纹理的等级按逐级回退值（getBorder 合并结果）回退：
     * inherit 复用最高已配置档位（MAX_RARITY）纹理，具体路径则使用该路径（支持 {level}）
     */
    public static String getBorderTexture(int rarity) {
        String path = getBorder(rarity).defaultTexture.replace("{level}", String.valueOf(rarity));
        if (rarity <= RarityConstants.MAX_RARITY) {
            return path;
        }
        if (getBorder(rarity).defaultTextureSpecified) {
            return path;
        }
        String fb = getBorder(rarity).fallback;
        if (fb != null && fb.equalsIgnoreCase("inherit")) {
            return getBorder(RarityConstants.MAX_RARITY).defaultTexture
                .replace("{level}", String.valueOf(RarityConstants.MAX_RARITY));
        }
        if (fb != null && !fb.isEmpty()) {
            return fb.replace("{level}", String.valueOf(rarity));
        }
        return path;
    }

    // ───────────────────────── 公共读写接口 ─────────────────────────

    /** 边框回退纹理（defaults.border.fallback） */
    public static String getBorderFallback() {
        return defaults.border.fallback;
    }

    /** 逐级边框是否使用纹理 */
    public static boolean isBorderUseTexture(int rarity) {
        return getBorder(rarity).useTexture;
    }

    /** 逐级边框样式（1=实心，0=空心） */
    public static int getBorderStyle(int rarity) {
        return getBorder(rarity).style;
    }

    /**
     * 某等级的完整生效视觉表现快照（border/tooltip/star 合并结果）
     * 便于运行时诊断，不反映写入未保存的缓冲状态
     */
    public static StyleSnapshot getStyleSnapshot(int rarity) {
        BorderConfig b = getBorder(rarity);
        TooltipConfig t = getTooltip(rarity);
        return new StyleSnapshot(rarity, b.useTexture, b.style, b.fallback, t.show, t.content,
                t.colored, t.star.mode, t.star.repeatChar, t.star.custom, t.star.colored);
    }

    // ── 批量写入（防抖） ──

    /** 批量写入进行中的计数器；大于 0 时跳过逐条 saveToFile 与事件发布 */
    private static int batchDepth = 0;

    /** 批量写入期间被修改的等级与变更项组合（用于去重，结束时按真实变更类型发布事件） */
    private static final java.util.Set<BatchChange> batchTouched = new java.util.HashSet<>();

    /** 批量写入的单个变更标记：等级 + 变更项 */
    private static final class BatchChange {
        final int rarity;
        final org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget target;

        BatchChange(int rarity, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget target) {
            this.rarity = rarity;
            this.target = target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BatchChange other)) return false;
            return rarity == other.rarity && target == other.target;
        }

        @Override
        public int hashCode() {
            return 31 * rarity + target.hashCode();
        }
    }

    /** 开始批量写入，期间 setter 不逐条写盘与发布事件 */
    public static void beginStyleBatch() {
        batchDepth++;
    }

    /** 结束批量写入，统一写盘并发布一次聚合事件 */
    public static void endStyleBatch() {
        if (batchDepth <= 0) {
            return;
        }
        batchDepth--;
        if (batchDepth == 0) {
            saveToFile();
            for (BatchChange c : batchTouched) {
                notifyStyleChanged(c.rarity, c.target);
            }
            batchTouched.clear();
        }
    }

    /**
     * 以单个数据对象整体写入某等级的视觉表现配置
     * 未设置的字段（null/未设置）保留现有值
     */
    public static void setStyle(StylePatch patch) {
        if (patch == null) {
            return;
        }
        if (patch.borderUseTexture != null) {
            setBorderUseTexture(patch.rarity, patch.borderUseTexture);
        }
        if (patch.borderStyle != null) {
            setBorderStyle(patch.rarity, patch.borderStyle);
        }
        if (patch.tooltipContent != null) {
            setTooltipContent(patch.rarity, patch.tooltipContent);
        }
        if (patch.starMode != null) {
            setStarMode(patch.rarity, patch.starMode);
        }
        if (patch.starRepeatChar != null) {
            setStarRepeatChar(patch.rarity, patch.starRepeatChar);
        }
    }

    /** 某等级生效视觉表现的不可变快照 */
    public static class StyleSnapshot {
        public final int rarity;
        public final boolean borderUseTexture;
        public final int borderStyle;
        public final String borderFallback;
        public final boolean tooltipShow;
        public final String tooltipContent;
        public final boolean tooltipColored;
        public final String starMode;
        public final String starRepeatChar;
        public final String starCustom;
        public final boolean starColored;

        StyleSnapshot(int rarity, boolean borderUseTexture, int borderStyle, String borderFallback,
                      boolean tooltipShow, String tooltipContent, boolean tooltipColored, String starMode,
                      String starRepeatChar, String starCustom, boolean starColored) {
            this.rarity = rarity;
            this.borderUseTexture = borderUseTexture;
            this.borderStyle = borderStyle;
            this.borderFallback = borderFallback;
            this.tooltipShow = tooltipShow;
            this.tooltipContent = tooltipContent;
            this.tooltipColored = tooltipColored;
            this.starMode = starMode;
            this.starRepeatChar = starRepeatChar;
            this.starCustom = starCustom;
            this.starColored = starColored;
        }
    }

    /** 结构化视觉表现写入补丁；null 字段表示保留现有值 */
    public static class StylePatch {
        public int rarity;
        public Boolean borderUseTexture;
        public Integer borderStyle;
        public String tooltipContent;
        public String starMode;
        public String starRepeatChar;
    }

    // ── 主开关写入 ──

    public static void setBorderEnabled(boolean enable) {
        enableBorder = enable;
        persistStyleChange(0, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget.BORDER_ENABLED);
    }

    public static void setTooltipEnabled(boolean enable) {
        enableTooltip = enable;
        persistStyleChange(0, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget.TOOLTIP_ENABLED);
    }

    public static void setTooltipColorEnabled(boolean enable) {
        tooltipColorEnabled = enable;
        persistStyleChange(0, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget.TOOLTIP_COLOR_ENABLED);
    }

    // ── 无稀有度回退写入 ──

    public static void setNoRaritySkip(boolean skip) {
        defaults.noRarity.skip = skip;
        persistStyleChange(0, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget.NO_RARITY_SKIP);
    }

    public static void setNoRarityDefaultRarity(int rarity) {
        defaults.noRarity.defaultRarity = rarity;
        persistStyleChange(0, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget.NO_RARITY_DEFAULT_RARITY);
    }

    // ── 逐级边框写入 ──

    public static void setBorderUseTexture(int rarity, boolean useTexture) {
        LevelOverride o = ensureOverride(rarity);
        o.border = o.border == null ? new BorderConfig() : o.border;
        BorderConfig b = o.border;
        BorderConfig base = previousBorder(rarity);
        b.useTexture = useTexture;
        b.useTextureSpecified = true;
        b.defaultTexture = base.defaultTexture;
        b.style = base.style;
        b.show = base.show;
        b.fallback = base.fallback;
        persistStyleChange(rarity, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget.BORDER_USE_TEXTURE);
    }

    public static void setBorderStyle(int rarity, int style) {
        LevelOverride o = ensureOverride(rarity);
        o.border = o.border == null ? new BorderConfig() : o.border;
        BorderConfig b = o.border;
        BorderConfig base = previousBorder(rarity);
        b.style = style;
        b.styleSpecified = true;
        b.useTexture = base.useTexture;
        b.defaultTexture = base.defaultTexture;
        b.show = base.show;
        b.fallback = base.fallback;
        persistStyleChange(rarity, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget.BORDER_STYLE);
    }

    // ── 工具提示内容写入 ──

    public static void setTooltipContent(int rarity, String content) {
        LevelOverride o = ensureOverride(rarity);
        o.tooltip = o.tooltip == null ? new TooltipConfig() : o.tooltip;
        TooltipConfig t = o.tooltip;
        TooltipConfig base = previousTooltip(rarity);
        copyTooltip(base, t);
        t.content = content;
        t.contentSpecified = true;
        persistStyleChange(rarity, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget.TOOLTIP_CONTENT);
    }

    // ── 星星写入 ──

    public static void setStarMode(int rarity, String mode) {
        LevelOverride o = ensureOverride(rarity);
        o.tooltip = o.tooltip == null ? new TooltipConfig() : o.tooltip;
        StarSegmentConfig s = o.tooltip.star;
        s.mode = mode;
        s.modeSpecified = true;
        persistStyleChange(rarity, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget.STAR_MODE);
    }

    public static void setStarRepeatChar(int rarity, String repeatChar) {
        LevelOverride o = ensureOverride(rarity);
        o.tooltip = o.tooltip == null ? new TooltipConfig() : o.tooltip;
        StarSegmentConfig s = o.tooltip.star;
        s.repeatChar = repeatChar;
        s.repeatCharSpecified = true;
        persistStyleChange(rarity, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget.STAR_REPEAT_CHAR);
    }

    private static void notifyStyleChanged(int rarity, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget target) {
        MinecraftForge.EVENT_BUS.post(
            new org.yanbwe.raritycore.event.RarityStyleChangedEvent(rarity, target));
    }

    /** setter 写入后调用：批量进行中时仅记录变更，否则立即写盘并发布事件 */
    private static void persistStyleChange(int rarity, org.yanbwe.raritycore.event.RarityStyleChangedEvent.ChangeTarget target) {
        if (batchDepth > 0) {
            batchTouched.add(new BatchChange(rarity, target));
            return;
        }
        saveToFile();
        notifyStyleChanged(rarity, target);
    }

    // ── 逐级覆盖辅助 ──

    private static LevelOverride ensureOverride(int rarity) {
        LevelOverride o = RARITIES.get(rarity);
        if (o == null) {
            o = new LevelOverride();
            RARITIES.put(rarity, o);
        }
        return o;
    }

    private static BorderConfig previousBorder(int rarity) {
        BorderConfig base = new BorderConfig();
        base.useTexture = defaults.border.useTexture;
        base.defaultTexture = defaults.border.defaultTexture;
        base.style = defaults.border.style;
        base.show = defaults.border.show;
        base.fallback = defaults.border.fallback;
        for (int l = 1; l < rarity; l++) {
            LevelOverride o = RARITIES.get(l);
            if (o != null && o.border != null) {
                if (o.border.useTextureSpecified) base.useTexture = o.border.useTexture;
                if (o.border.defaultTextureSpecified) base.defaultTexture = o.border.defaultTexture;
                if (o.border.styleSpecified) base.style = o.border.style;
                if (o.border.showSpecified) base.show = o.border.show;
                if (o.border.fallbackSpecified) base.fallback = o.border.fallback;
            }
        }
        return base;
    }

    private static TooltipConfig previousTooltip(int rarity) {
        TooltipConfig base = new TooltipConfig();
        copyTooltip(defaults.tooltip, base);
        for (int l = 1; l < rarity; l++) {
            LevelOverride o = RARITIES.get(l);
            if (o != null && o.tooltip != null) mergeTooltip(o.tooltip, base);
        }
        return base;
    }

    // ───────────────────────── 内部工具 ─────────────────────────

    private static void copyTooltip(TooltipConfig src, TooltipConfig dst) {
        dst.show = src.show;
        dst.content = src.content;
        dst.colored = src.colored;
        dst.level.colored = src.level.colored;
        dst.level.translationKey = src.level.translationKey;
        dst.level.fallback = src.level.fallback;
        dst.star.colored = src.star.colored;
        dst.star.mode = src.star.mode;
        dst.star.repeatChar = src.star.repeatChar;
        dst.star.custom = src.star.custom;
    }

    private static void mergeTooltip(TooltipConfig src, TooltipConfig acc) {
        if (src.showSpecified) acc.show = src.show;
        if (src.contentSpecified) acc.content = src.content;
        if (src.coloredSpecified) acc.colored = src.colored;
        if (src.level.coloredSpecified) acc.level.colored = src.level.colored;
        if (src.level.translationKeySpecified) acc.level.translationKey = src.level.translationKey;
        if (src.level.fallbackSpecified) acc.level.fallback = src.level.fallback;
        if (src.star.coloredSpecified) acc.star.colored = src.star.colored;
        if (src.star.modeSpecified) acc.star.mode = src.star.mode;
        if (src.star.repeatCharSpecified) acc.star.repeatChar = src.star.repeatChar;
        if (src.star.customSpecified) acc.star.custom = src.star.custom;
    }

    // ── 逐级序列化（只写显式指定的字段，保住"未指定即向低等级继承"语义）──
    //
    // 历史缺陷：buildCurrentConfigJson 的 rarities 循环写 border 时无条件写全 4 个字段，
    // 而 tooltip 整个字段根本没被写。后果有二：
    //   1. 任何一次保存（含 setTooltipContent/setStarMode 等 setter 触发的 saveToFile）
    //      都会把用户在 RarityStyle.json 里手写的 rarities.<n>.tooltip 段从文件里抹掉，
    //      且 parseLevelOverride 读不回来 → 手写配置永久失效、setter 改动重启即失。
    //   2. border 全字段写出后，重载时 parseBorder 把它们全部标为 *Specified=true，
    //      于是"未指定即继承低等级/默认值"在首次保存后永久失效。
    // 现在两个方法都只写 *Specified 为真的键，上述两个问题一并消除。

    /** 按 *Specified 标记精确写 border 段；无任何显式字段时返回 null（不写出该键） */
    private static JsonObject writeBorder(BorderConfig b) {
        if (b == null) {
            return null;
        }
        JsonObject o = new JsonObject();
        if (b.useTextureSpecified) o.addProperty("useTexture", b.useTexture);
        if (b.defaultTextureSpecified) o.addProperty("defaultTexture", b.defaultTexture);
        if (b.styleSpecified) o.addProperty("style", b.style);
        if (b.showSpecified) o.addProperty("show", b.show);
        if (b.fallbackSpecified) o.addProperty("fallback", b.fallback);
        return o.size() == 0 ? null : o;
    }

    /** 按 *Specified 标记精确写 tooltip 段（含 level / star 子段）；无显式字段时返回 null */
    private static JsonObject writeTooltip(TooltipConfig t) {
        if (t == null) {
            return null;
        }
        JsonObject o = new JsonObject();
        if (t.showSpecified) o.addProperty("show", t.show);
        if (t.contentSpecified) o.addProperty("content", t.content);
        if (t.coloredSpecified) o.addProperty("colored", t.colored);

        JsonObject level = new JsonObject();
        if (t.level.coloredSpecified) level.addProperty("colored", t.level.colored);
        if (t.level.translationKeySpecified) level.addProperty("translationKey", t.level.translationKey);
        if (t.level.fallbackSpecified) level.addProperty("fallback", t.level.fallback);
        if (level.size() > 0) o.add("level", level);

        JsonObject star = new JsonObject();
        if (t.star.coloredSpecified) star.addProperty("colored", t.star.colored);
        if (t.star.modeSpecified) star.addProperty("mode", t.star.mode);
        if (t.star.repeatCharSpecified) star.addProperty("repeatChar", t.star.repeatChar);
        if (t.star.customSpecified) star.addProperty("custom", t.star.custom);
        if (star.size() > 0) o.add("star", star);

        return o.size() == 0 ? null : o;
    }

    private static void injectColors() {
        Map<Integer, Integer> colors = new HashMap<>();
        for (int i = 1; i <= RarityConstants.MAX_RARITY; i++) {
            colors.put(i, getColor(i));
        }
        RarityColorUtil.setCustomColors(colors);
    }

    // ───────────────────────── 默认配置生成 ─────────────────────────

    public static void saveToFile() {
        JsonObject root = buildCurrentConfigJson();
        try {
            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot save RarityStyle config: {}", CONFIG_FILE, e);
        }
    }

    private static JsonObject buildCurrentConfigJson() {
        JsonObject root = createDefaultConfigJson();
        root.addProperty("enableBorder", enableBorder);
        root.addProperty("enableTooltip", enableTooltip);
        root.addProperty("tooltipColorEnabled", tooltipColorEnabled);
        // defaults
        JsonObject d = new JsonObject();
        d.addProperty("color", defaults.color);
        JsonObject border = new JsonObject();
        border.addProperty("useTexture", defaults.border.useTexture);
        border.addProperty("defaultTexture", defaults.border.defaultTexture);
        border.addProperty("style", defaults.border.style);
        border.addProperty("show", defaults.border.show);
        border.addProperty("fallback", defaults.border.fallback);
        d.add("border", border);
        JsonObject tooltip = new JsonObject();
        tooltip.addProperty("show", defaults.tooltip.show);
        tooltip.addProperty("content", defaults.tooltip.content);
        tooltip.addProperty("colored", defaults.tooltip.colored);
        JsonObject level = new JsonObject();
        level.addProperty("colored", defaults.tooltip.level.colored);
        level.addProperty("translationKey", defaults.tooltip.level.translationKey);
        level.addProperty("fallback", defaults.tooltip.level.fallback);
        tooltip.add("level", level);
        JsonObject star = new JsonObject();
        star.addProperty("colored", defaults.tooltip.star.colored);
        star.addProperty("mode", defaults.tooltip.star.mode);
        star.addProperty("repeatChar", defaults.tooltip.star.repeatChar);
        star.addProperty("custom", defaults.tooltip.star.custom);
        tooltip.add("star", star);
        d.add("tooltip", tooltip);
        d.addProperty("itemNameColor", defaults.itemNameColor);
        JsonObject noRarity = new JsonObject();
        noRarity.addProperty("skip", defaults.noRarity.skip);
        noRarity.addProperty("defaultRarity", defaults.noRarity.defaultRarity);
        d.add("noRarity", noRarity);
        root.add("defaults", d);
        // rarities：只写显式指定的字段，未指定的字段保持缺省以便向低等级继承
        JsonObject rarities = new JsonObject();
        for (Map.Entry<Integer, LevelOverride> e : RARITIES.entrySet()) {
            LevelOverride o = e.getValue();
            JsonObject entry = new JsonObject();
            if (o.colorSpecified) entry.addProperty("color", o.color);
            if (o.itemNameColor != null) entry.addProperty("itemNameColor", o.itemNameColor);
            JsonObject b = writeBorder(o.border);
            if (b != null) entry.add("border", b);
            JsonObject t = writeTooltip(o.tooltip);
            if (t != null) entry.add("tooltip", t);
            rarities.add(String.valueOf(e.getKey()), entry);
        }
        root.add("rarities", rarities);
        return root;
    }

    private static void generateDefaultConfigFile() {
        JsonObject root = createDefaultConfigJson();
        try {
            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
                RarityCore.LOGGER.info("Created default RarityStyle config: {}", CONFIG_FILE);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create default RarityStyle config: {}", CONFIG_FILE, e);
        }
    }

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
        border.addProperty("fallback", "inherit");
        border.addProperty("style", 1);
        border.addProperty("show", true);
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
        String[] colors = {"#CCCCCC", "#55FF55", "#55FFFF", "#FF55FF", "#FFCC00", "#FF6666", "#FF3333"};
        for (int i = 1; i <= 7; i++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("color", colors[i - 1]);
            rarities.add(String.valueOf(i), entry);
        }
        root.add("rarities", rarities);

        return root;
    }

    // ───────────────────────── 旧文件处理 ─────────────────────────

    private static void handleLegacyFiles() {
        // client.json：清除 V14 已迁入 RarityStyle.json 的旧总开关键
        // 注意：这里只做"定向剔除"——仅删除下面 LEGACY_CLIENT_KEYS 中列出的键，
        // 文件中其余任何键（enableCacheSystem、enableIronSpellsAdapter 等本模组开关，
        // 以及用户自定义键）一律原样保留。
        //
        // 历史缺陷：本方法曾把 client.json 整体重写为"只含 enableCacheSystem"的对象，
        // 导致 1201.14.1 新增的 enableIronSpellsAdapter 每次启动都被删除，
        // 用户手动添加该键也会被吞掉，铁魔法动态映射因此永远无法关闭。
        Path clientFile = CONFIG_DIR.resolve(RarityConstants.CLIENT_CONFIG_FILE_NAME);
        try {
            if (Files.exists(clientFile)) {
                JsonObject existing;
                try (BufferedReader r = Files.newBufferedReader(clientFile)) {
                    existing = GSON.fromJson(r, JsonObject.class);
                }

                if (existing != null) {
                    boolean changed = false;
                    for (String legacyKey : LEGACY_CLIENT_KEYS) {
                        if (existing.has(legacyKey)) {
                            existing.remove(legacyKey);
                            changed = true;
                            RarityCore.LOGGER.info("Removed legacy client.json option '{}' (migrated to RarityStyle.json)", legacyKey);
                        }
                    }
                    if (changed) {
                        try (OutputStreamWriter w = new OutputStreamWriter(Files.newOutputStream(clientFile), StandardCharsets.UTF_8)) {
                            GSON.toJson(existing, w);
                        }
                    }
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to clean legacy client.json options", e);
        }

        // RarityClientConfig.json：检测即直接删除（V14 已迁移至 RarityStyle.json）
        Path legacyFile = CONFIG_DIR.resolve(RarityConstants.RARITY_CLIENT_CONFIG_FILE_NAME);
        try {
            if (Files.exists(legacyFile)) {
                Files.deleteIfExists(legacyFile);
                RarityCore.LOGGER.info("Removed legacy RarityClientConfig.json (config migrated to RarityStyle.json)");
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to remove legacy RarityClientConfig.json", e);
        }
    }
}
