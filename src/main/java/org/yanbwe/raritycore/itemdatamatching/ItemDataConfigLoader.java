package org.yanbwe.raritycore.itemdatamatching;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
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

/**
 * 物品数据匹配配置加载器
 * 支持从数据包和config目录加载物品数据匹配规则
 */
public class ItemDataConfigLoader extends SimpleJsonResourceReloadListener {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_PACK_FOLDER = "item_data_matches"; // 数据包中的文件夹名
    
    // 本地配置规则缓存(来自config目录)
    private static final List<ItemDataMatchRule> LOCAL_RULES = new ArrayList<>();
    
    public ItemDataConfigLoader() {
        super(GSON, DATA_PACK_FOLDER);
    }
    
    /**
     * 从数据包加载配置(由Minecraft资源系统调用)
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        RarityCore.LOGGER.info("开始从数据包加载物品数据匹配配置,找到 {} 个配置文件", jsons.size());
        
        // 清空现有数据包规则
        ItemDataRarityMatcher.clearAllRules();
        
        // 加载数据包中的配置
        int loadedCount = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
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
        
        // 加载本地配置文件(优先级更高)
        loadLocalConfigs();
    }
    
    /**
     * 加载所有配置(包括数据包和本地配置)
     */
    public static void loadAllConfigs() {
        // 本地配置会在这个方法中加载
        loadLocalConfigs();
    }
    
    /**
     * 仅加载本地配置文件(config目录)
     */
    private static void loadLocalConfigs() {
        Path configDir = ConfigManager.getConfigDirPath().resolve("item_data_matches");
        
        try {
            // 确保配置目录存在
            Files.createDirectories(configDir);
            RarityCore.LOGGER.info("Item data local config directory: {}", configDir.toAbsolutePath());
            
            // 清空本地规则缓存
            LOCAL_RULES.clear();
            
            // 关键:清空物品数据匹配器的规则缓存
            ItemDataRarityMatcher.clearAllRules();
            
            // 加载所有本地配置文件
            loadLocalConfigFiles(configDir);
            
            // 注册本地规则(覆盖数据包规则)
            for (ItemDataMatchRule rule : LOCAL_RULES) {
                ItemDataRarityMatcher.registerRule(rule);
            }
            
            RarityCore.LOGGER.info("Loaded {} item data matching rules from local config directory", LOCAL_RULES.size());
            
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to create or access item data local config directory: {}", configDir.toAbsolutePath(), e);
        }
    }
    
    /**
     // 加载本地配置目录下的所有配置文件
     */
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
    
    /**
     // 加载单个本地配置文件
     */
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
    
    /**
     // 解析本地配置文件内容
     */
    private static void parseLocalConfig(JsonObject config, String fileName) {
        // 使用简化验证器
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