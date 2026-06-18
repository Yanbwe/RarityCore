package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.network.RaritySyncPacket;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tag 稀有度配置管理器
 * 加载 config/raritycore/TagRarity.json，按 Minecraft TagKey 批量分配稀有度
 * 优先级：ITEM_RARITY_MAP > Tag > AUTO_RARITY_MAP > 原版
 * 物品匹配多个 Tag 时取最高稀有度
 */
public class TagRarityConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Paths.get(RarityConstants.CONFIG_DIR_PARENT).resolve(RarityConstants.CONFIG_DIR_NAME);
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve(RarityConstants.TAG_RARITY_CONFIG_FILE_NAME);

    /** Tag 到稀有度的映射（加载后不再变化，用 volatile 保证可见性） */
    private static volatile List<TagRule> loadedRules = new ArrayList<>();

    /** 物品稀有度缓存：避免每次查询都迭代所有规则，按稀有度降序存储规则以便提前返回 */
    private static volatile TagRule[] sortedRules = new TagRule[0];

    public static class TagRule {
        public final TagKey<Item> tagKey;
        public final int rarity;

        TagRule(TagKey<Item> tagKey, int rarity) {
            this.tagKey = tagKey;
            this.rarity = rarity;
        }
    }

    /**
     * 获取物品匹配的所有 Tag 中最高的稀有度
     * @param item 物品
     * @return 稀有度等级，无匹配返回 0（由调用方回退）
     */
    public static int getHighestTagRarity(Item item) {
        // 规则已按稀有度降序排列，第一个匹配的就是最高稀有度
        for (TagRule rule : sortedRules) {
            if (item.builtInRegistryHolder().is(rule.tagKey)) {
                return rule.rarity;
            }
        }
        return 0;
    }

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
        ConfigValidator.validateConfig(CONFIG_FILE, defaultConfig, "TagRarity");

        if (!Files.exists(CONFIG_FILE)) {
            createDefaultConfigFile();
        }
        loadFromFile();
    }

    private static void loadFromFile() {
        try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null && json.has("tag_rules")) {
                List<TagRule> rules = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("tag_rules");
                for (JsonElement elem : array) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();
                    String tagStr = obj.has("tag") ? obj.get("tag").getAsString() : "";
                    int rarity = obj.has("rarity") ? obj.get("rarity").getAsInt() : 0;
                    if (tagStr.isEmpty() || rarity < 1) continue;

                    try {
                        ResourceLocation tagId = new ResourceLocation(tagStr);
                        TagKey<Item> tagKey = TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(), tagId);
                        rules.add(new TagRule(tagKey, RarityValidator.normalizeRarity(rarity)));
                    } catch (Throwable t) {
                        RarityCore.LOGGER.warn("Invalid tag rule: tag={}, rarity={}, error={}", tagStr, rarity, t.getMessage());
                    }
                }
                loadedRules = rules;
                // 按稀有度降序排列：高稀有度优先匹配，找到第一个即可返回
                sortedRules = rules.stream()
                    .sorted(Comparator.comparingInt((TagRule r) -> r.rarity).reversed())
                    .toArray(TagRule[]::new);
                RarityCore.LOGGER.info("Loaded {} tag rarity rules", rules.size());
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading TagRarity config, using empty rules", e);
            loadedRules = new ArrayList<>();
            sortedRules = new TagRule[0];
        }
    }

    private static void createDefaultConfigFile() {
        JsonObject root = createDefaultConfigJson();
        try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
            RarityCore.LOGGER.info("Created default TagRarity config: {}", CONFIG_FILE);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create default TagRarity config", e);
        }
    }

    private static JsonObject createDefaultConfigJson() {
        JsonObject root = new JsonObject();
        JsonArray rules = new JsonArray();
        // 提供一些示例规则
        addExampleRule(rules, "forge:ingots/netherite", 5);
        addExampleRule(rules, "forge:gems/diamond", 4);
        root.add("tag_rules", rules);
        return root;
    }

    private static void addExampleRule(JsonArray array, String tag, int rarity) {
        JsonObject rule = new JsonObject();
        rule.addProperty("tag", tag);
        rule.addProperty("rarity", rarity);
        array.add(rule);
    }


    /**
     * 应用来自服务端同步的 TagRarity 规则（仅客户端）
     * <p>
     * 直接替换当前的 loadedRules / sortedRules。
     * 客户端原本加载自本地 TagRarity.json，本方法会将它覆盖为服务端的版本。
     */
    @OnlyIn(Dist.CLIENT)
    public static void applySyncedRules(List<RaritySyncPacket.TagRuleEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            loadedRules = new ArrayList<>();
            sortedRules = new TagRule[0];
            RarityCore.LOGGER.debug("TagRarity: applied empty synced rules (cleared)");
            return;
        }

        List<TagRule> rules = new ArrayList<>(entries.size());
        for (RaritySyncPacket.TagRuleEntry entry : entries) {
            try {
                TagKey<Item> tagKey = TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(),
                    new ResourceLocation(entry.tagLocation()));
                rules.add(new TagRule(tagKey,
                    RarityValidator.normalizeRarity(entry.rarity())));
            } catch (Throwable t) {
                RarityCore.LOGGER.warn("Skipping invalid synced tag rule: tag={}, rarity={}, error={}",
                    entry.tagLocation(), entry.rarity(), t.getMessage());
            }
        }
        loadedRules = rules;
        sortedRules = rules.stream()
            .sorted(Comparator.comparingInt((TagRule r) -> r.rarity).reversed())
            .toArray(TagRule[]::new);
        RarityCore.LOGGER.debug("TagRarity: applied {} synced rules", rules.size());
    }


    /**
     * 将当前加载的 TagRarity 规则转换为网络同步用的 TagRuleEntry 列表（服务端用）
     * @return 非 null 的 List；可能为空列表
     */
    public static List<RaritySyncPacket.TagRuleEntry> getSyncedRules() {
        TagRule[] rules = sortedRules;
        if (rules.length == 0) {
            return new ArrayList<>();
        }
        List<RaritySyncPacket.TagRuleEntry> entries = new ArrayList<>(rules.length);
        for (TagRule rule : rules) {
            entries.add(new RaritySyncPacket.TagRuleEntry(
                rule.tagKey.location().toString(), rule.rarity));
        }
        return entries;
    }

    public static int getRuleCount() {
        return sortedRules.length;
    }
}
