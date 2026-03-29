package org.yanbwe.raritycore.itemdatamatching;

import com.google.gson.*;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ConfigManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemDataConfigLoader extends SimpleJsonResourceReloadListener<JsonElement> {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_PACK_FOLDER = "item_data_matches";

    private static final List<ItemDataMatchRule> LOCAL_RULES = new ArrayList<>();

    public ItemDataConfigLoader() {
        super(ExtraCodecs.JSON, FileToIdConverter.json(DATA_PACK_FOLDER));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        RarityCore.LOGGER.info("开始从数据包加载物品数据匹配配置,找到 {} 个配置文件", jsons.size());

        ItemDataRarityMatcher.clearAllRules();

        int loadedCount = 0;
        for (Map.Entry<Identifier, JsonElement> entry : jsons.entrySet()) {
            try {
                if (entry.getValue().isJsonObject()) {
                    JsonObject config = entry.getValue().getAsJsonObject();
                    ItemDataMatchRule rule = SimpleConfigValidator.parseRule(config);

                    if (rule != null && ItemDataRarityMatcher.validateRule(rule)) {
                        ItemDataRarityMatcher.registerRule(rule);
                        loadedCount++;
                        RarityCore.LOGGER.debug("从数据包加载规则: {} -> 稀有度{}",
                            rule.getItemId(), rule.getRarity());
                    }
                }
            } catch (Exception e) {
                RarityCore.LOGGER.warn("加载数据包配置 {} 时出错: {}", entry.getKey(), e.getMessage());
            }
        }

        RarityCore.LOGGER.info("从数据包成功加载 {} 个物品数据匹配规则", loadedCount);

        loadLocalConfigs();
    }

    public static void loadAllConfigs() {
        loadLocalConfigs();
    }

    private static void loadLocalConfigs() {
        Path configDir = ConfigManager.getConfigDirPath().resolve("item_data_matches");

        try {
            Files.createDirectories(configDir);
            RarityCore.LOGGER.info("Item data local config directory: {}", configDir.toAbsolutePath());

            LOCAL_RULES.clear();

            ItemDataRarityMatcher.clearAllRules();

            loadLocalConfigFiles(configDir);

            for (ItemDataMatchRule rule : LOCAL_RULES) {
                ItemDataRarityMatcher.registerRule(rule);
            }

            RarityCore.LOGGER.info("Loaded {} item data matching rules from local config directory", LOCAL_RULES.size());

        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to create or access item data local config directory: {}", configDir.toAbsolutePath(), e);
        }
    }

    private static void loadLocalConfigFiles(Path configDir) {
        try {
            Files.walk(configDir)
                 .filter(path -> path.toString().endsWith(".json"))
                 .filter(Files::isRegularFile)
                 .forEach(ItemDataConfigLoader::loadLocalConfigFile);

        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to walk local config directory: {}", configDir, e);
        }
    }

    private static void loadLocalConfigFile(Path configFile) {
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            JsonObject config = GSON.fromJson(reader, JsonObject.class);

            if (config == null) {
                RarityCore.LOGGER.warn("Local config file {} is empty", configFile.getFileName());
                return;
            }

            parseLocalConfig(config, configFile.getFileName().toString());

        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to read local config file: {}", configFile, e);
        } catch (JsonSyntaxException e) {
            RarityCore.LOGGER.error("Local config file {} has invalid format: {}", configFile.getFileName(), e.getMessage());
        }
    }

    private static void parseLocalConfig(JsonObject config, String fileName) {
        ItemDataMatchRule rule = SimpleConfigValidator.parseRule(config);

        if (rule != null && ItemDataRarityMatcher.validateRule(rule)) {
            LOCAL_RULES.add(rule);
            RarityCore.LOGGER.info("Successfully loaded local item data matching rule: {} -> rarity {} (file: {})",
                rule.getItemId(), rule.getRarity(), fileName);
        } else {
            RarityCore.LOGGER.debug("Rule in local config file {} is invalid or validation failed, skipped", fileName);
        }
    }
}