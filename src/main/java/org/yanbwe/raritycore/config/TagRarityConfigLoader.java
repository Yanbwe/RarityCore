package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.JsonPerformanceOptimizer;
import org.yanbwe.raritycore.util.RarityConstants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tag 稀有度配置加载器。
 *
 * <p>从 {@code config/raritycore/TagRarity.json} 加载 TagKey → 稀有度映射规则。
 * 规则按稀有度降序排列，运行时找到第一个匹配的 Tag 即可返回最高稀有度。</p>
 *
 * <p>加载流程：
 * <ol>
 *   <li>调用 {@link ConfigValidator#validateConfig(Path, JsonObject, String)} 验证并补充缺失项</li>
 *   <li>解析 {@code tag_rules} 数组中的每条规则（{@code tag} + {@code rarity}）</li>
 *   <li>按稀有度降序排列后写入 {@link TagRarityConfig}</li>
 *   <li>无效条目跳过并记录 WARN 日志</li>
 * </ol>
 */
public class TagRarityConfigLoader {

    private static final Gson GSON = JsonPerformanceOptimizer.getOptimizedGson();

    /**
     * 初始化 Tag 稀有度配置。
     * 应在 ConfigManager.initializeConfigs() 中调用。
     */
    public static void initialize() {
        try {
            Path configDir = ConfigManager.getConfigDirPath();
            Files.createDirectories(configDir);

            Path tagConfigFile = configDir.resolve(RarityConstants.TAG_RARITY_CONFIG_FILE_NAME);

            // 验证并更新配置文件（缺失项自动补全）
            JsonObject defaultConfig = ConfigValidator.createDefaultTagRarityConfig();
            JsonObject config = ConfigValidator.validateConfig(tagConfigFile, defaultConfig, "TagRarity");

            // 解析 tag_rules 数组
            List<TagRarityConfig.TagRarityEntry> rules = parseTagRules(config);

            // 按稀有度降序排列（最高优先）
            rules.sort(Comparator.comparingInt(TagRarityConfig.TagRarityEntry::rarity).reversed());

            // 写入全局配置
            TagRarityConfig.setRules(rules);

            RarityCore.LOGGER.info("TagRarity config loaded: {} rules", rules.size());
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize TagRarity config: {}", e.getMessage());
            // 加载失败时清空规则，避免使用过期数据
            TagRarityConfig.clearRules();
        }
    }

    /**
     * 重新加载 Tag 稀有度配置。
     * 用于配置热重载场景。
     */
    public static void reload() {
        RarityCore.LOGGER.info("Reloading TagRarity config...");
        initialize();
        // 使所有缓存失效，确保 Tag 规则变更生效
        org.yanbwe.raritycore.cache.RarityCacheCoordinator.handleConfigReload();
    }

    /**
     * 从 JSON 配置中解析 tag_rules 数组。
     *
     * @param config JSON 配置对象
     * @return 解析后的规则列表（未排序，可能为空）
     */
    private static List<TagRarityConfig.TagRarityEntry> parseTagRules(JsonObject config) {
        List<TagRarityConfig.TagRarityEntry> rules = new ArrayList<>();

        if (!config.has("tag_rules")) {
            RarityCore.LOGGER.warn("TagRarity config missing 'tag_rules' array");
            return rules;
        }

        JsonElement tagRulesElement = config.get("tag_rules");
        if (!tagRulesElement.isJsonArray()) {
            RarityCore.LOGGER.warn("TagRarity config 'tag_rules' is not an array");
            return rules;
        }

        JsonArray tagRules = tagRulesElement.getAsJsonArray();
        for (JsonElement element : tagRules) {
            if (!element.isJsonObject()) {
                RarityCore.LOGGER.warn("Skipping non-object entry in tag_rules");
                continue;
            }

            JsonObject rule = element.getAsJsonObject();
            TagRarityConfig.TagRarityEntry entry = parseRule(rule);
            if (entry != null) {
                rules.add(entry);
            }
        }

        return rules;
    }

    /**
     * 解析单条 Tag 稀有度规则。
     *
     * @param rule JSON 规则对象，需包含 {@code tag} (string) 和 {@code rarity} (int) 字段
     * @return 解析后的规则条目，解析失败返回 null
     */
    private static TagRarityConfig.TagRarityEntry parseRule(JsonObject rule) {
        // 验证必填字段
        if (!rule.has("tag") || !rule.has("rarity")) {
            RarityCore.LOGGER.warn("Skipping tag rule missing 'tag' or 'rarity' field: {}", rule);
            return null;
        }

        // 解析 tag 字符串
        String tagString = rule.get("tag").getAsString();
        if (tagString == null || tagString.isEmpty()) {
            RarityCore.LOGGER.warn("Skipping tag rule with empty 'tag' field");
            return null;
        }

        // 解析 rarity 值
        int rarity = rule.get("rarity").getAsInt();
        if (rarity < RarityConstants.MIN_RARITY || rarity > RarityConstants.MAX_RARITY) {
            RarityCore.LOGGER.warn("Skipping tag rule with out-of-range rarity ({}): tag={}, rarity={}",
                    rarity, tagString, rarity);
            return null;
        }

        // 创建 TagKey（tagString 格式: "namespace:path"）
        try {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagString));
            return new TagRarityConfig.TagRarityEntry(tagKey, rarity);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to create TagKey for '{}': {}", tagString, e.getMessage());
            return null;
        }
    }

    /**
     * 获取已加载的规则数量。
     *
     * @return 规则数量
     */
    public static int getLoadedRuleCount() {
        return TagRarityConfig.getRules().size();
    }
}
