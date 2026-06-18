package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.network.RaritySyncPayload;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Loads tag-based rarity assignment rules from config/raritycore/TagRarity.json.
 * <p>
 * Each rule maps a Minecraft item tag (e.g. "minecraft:swords") to a rarity level (1-7).
 * When multiple tags match an item, the rule with the highest rarity takes precedence.
 * Rules are kept sorted by rarity descending so iteration naturally picks the best match first.
 * <p>
 * Called from {@link RarityConfigLoader#loadConfigRarityData()} on server startup
 * and via reload commands when configuration changes.
 */
public class TagRarityLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "TagRarity.json";
    private static final String JSON_ARRAY_KEY = "tag_rules";
    private static final String JSON_TAG_FIELD = "tag";
    private static final String JSON_RARITY_FIELD = "rarity";

    /** Thread-safe, sorted list of parsed tag rules (highest rarity first). */
    private static final List<TagRarityEntry> TAG_RULES = Collections.synchronizedList(new ArrayList<>());

    /**
     * A single tag-to-rarity mapping rule.
     *
     * @param tagNamespace the tag namespace (e.g. "minecraft")
     * @param tagPath      the tag path (e.g. "swords")
     * @param rarity       the assigned rarity level (1-7)
     */
    public record TagRarityEntry(String tagNamespace, String tagPath, int rarity) {

        /** Creates a TagKey&lt;Item&gt; from this entry's namespace and path. */
        public TagKey<Item> toTagKey() {
            try {
                Identifier id = Identifier.fromNamespaceAndPath(tagNamespace, tagPath);
                return TagKey.create(Registries.ITEM, id);
            } catch (Throwable t) {
                RarityCore.LOGGER.warn("Failed to create TagKey for {}:{}: {}", tagNamespace, tagPath, t.getMessage());
                return null;
            }
        }

        /** Returns "namespace:path" representation of the tag. */
        public String toTagString() {
            return tagNamespace + ":" + tagPath;
        }
    }

    /**
     * 将当前加载的 TagRarity 规则转换为网络同步用的 TagRuleTransfer 列表（服务端用）
     */
    public static List<RaritySyncPayload.TagRuleTransfer> getSyncedRules() {
        synchronized (TAG_RULES) {
            if (TAG_RULES.isEmpty()) return Collections.emptyList();
            List<RaritySyncPayload.TagRuleTransfer> list = new ArrayList<>(TAG_RULES.size());
            for (TagRarityEntry e : TAG_RULES) {
                list.add(new RaritySyncPayload.TagRuleTransfer(e.tagNamespace(), e.tagPath(), e.rarity()));
            }
            return list;
        }
    }

    /**
     * 应用来自服务端同步的 TagRarity 规则（仅客户端）
     */
    public static void applySyncedRules(List<RaritySyncPayload.TagRuleTransfer> transfers) {
        synchronized (TAG_RULES) {
            TAG_RULES.clear();
            if (transfers != null && !transfers.isEmpty()) {
                List<TagRarityEntry> entries = new ArrayList<>(transfers.size());
                for (var t : transfers) {
                    entries.add(t.toTagRarityEntry());
                }
                entries.sort(java.util.Comparator.comparingInt(TagRarityEntry::rarity).reversed());
                TAG_RULES.addAll(entries);
            }
        }
        RarityCore.LOGGER.debug("TagRarity: applied {} synced rules",
            transfers != null ? transfers.size() : 0);
    }

    public static List<TagRarityEntry> getTagRules() {
        return List.copyOf(TAG_RULES);
    }

    /**
     * Clears existing rules and loads tag rarity configuration from disk.
     * Called on server startup and during /reload.
     */
    public static void loadTagRarityConfig() {
        TAG_RULES.clear();

        Path configDir = ConfigManager.getConfigDirPath();

        if (!ensureConfigDir(configDir)) {
            return;
        }

        Path configFile = configDir.resolve(CONFIG_FILE_NAME);

        if (!Files.exists(configFile)) {
            createDefaultConfig(configFile);
            return; // default config has empty rules — no need to parse
        }

        parseConfigFile(configFile);
    }

    /**
     * Reloads the tag rarity configuration. Equivalent to clear + load.
     */
    public static void reloadTagRarityConfig() {
        loadTagRarityConfig();
    }

    // --- private helpers ---

    private static boolean ensureConfigDir(Path configDir) {
        try {
            Files.createDirectories(configDir);
            return true;
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", configDir, e);
            return false;
        }
    }

    /**
     * Creates a default TagRarity.json with an empty rule array.
     */
    private static void createDefaultConfig(Path configFile) {
        JsonObject root = new JsonObject();
        root.add(JSON_ARRAY_KEY, new JsonArray());

        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            GSON.toJson(root, writer);
            RarityCore.LOGGER.info("Created default TagRarity config: {}", configFile);
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create default TagRarity config: {}", configFile, e);
        }
    }

    /**
     * Parses the TagRarity.json file and populates the rule list.
     */
    private static void parseConfigFile(Path configFile) {
        JsonObject root;
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            root = GSON.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot read TagRarity config: {}", configFile, e);
            return;
        } catch (JsonParseException e) {
            RarityCore.LOGGER.error("TagRarity config format error: {}", configFile, e);
            return;
        }

        if (root == null || !root.has(JSON_ARRAY_KEY)) {
            RarityCore.LOGGER.warn("TagRarity config missing '{}' array, treating as empty", JSON_ARRAY_KEY);
            return;
        }

        JsonElement arrayElement = root.get(JSON_ARRAY_KEY);
        if (!arrayElement.isJsonArray()) {
            RarityCore.LOGGER.warn("TagRarity config '{}' is not a JSON array, treating as empty", JSON_ARRAY_KEY);
            return;
        }

        JsonArray rulesArray = arrayElement.getAsJsonArray();
        List<TagRarityEntry> parsed = new ArrayList<>();

        for (JsonElement elem : rulesArray) {
            if (!elem.isJsonObject()) {
                RarityCore.LOGGER.warn("Skipping non-object entry in TagRarity config");
                continue;
            }

            JsonObject ruleObj = elem.getAsJsonObject();
            TagRarityEntry entry = parseRuleEntry(ruleObj);
            if (entry != null) {
                parsed.add(entry);
            }
        }

        // Sort by rarity descending so higher-rarity rules come first
        parsed.sort(Comparator.comparingInt(TagRarityEntry::rarity).reversed());

        TAG_RULES.addAll(parsed);
        RarityCore.LOGGER.info("Loaded {} tag rarity rules from {}", parsed.size(), CONFIG_FILE_NAME);
    }

    /**
     * Parses a single rule JSON object into a TagRarityEntry.
     * Returns null if the entry is invalid (logs a warning and skips).
     */
    private static TagRarityEntry parseRuleEntry(JsonObject ruleObj) {
        // Validate and parse the tag string
        if (!ruleObj.has(JSON_TAG_FIELD)) {
            RarityCore.LOGGER.warn("TagRarity rule missing '{}' field, skipping entry", JSON_TAG_FIELD);
            return null;
        }

        String tagStr = ruleObj.get(JSON_TAG_FIELD).getAsString();
        Identifier tagId = Identifier.tryParse(tagStr);
        if (tagId == null) {
            RarityCore.LOGGER.warn("Invalid tag identifier '{}' in TagRarity config, skipping entry", tagStr);
            return null;
        }

        // Validate and parse the rarity value
        if (!ruleObj.has(JSON_RARITY_FIELD)) {
            RarityCore.LOGGER.warn("TagRarity rule for '{}' missing '{}' field, skipping entry", tagStr, JSON_RARITY_FIELD);
            return null;
        }

        int rarity = ruleObj.get(JSON_RARITY_FIELD).getAsInt();
        if (rarity < 1) {
            RarityCore.LOGGER.warn("TagRarity rule for '{}' has invalid rarity {}, skipping entry", tagStr, rarity);
            return null;
        }
        // Note: rarity > 7 is allowed for forward compatibility with extended rarity systems

        return new TagRarityEntry(tagId.getNamespace(), tagId.getPath(), rarity);
    }
}
