package org.yanbwe.raritycore.calc;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 自动稀有度配置管理器
 * 管理 auto_rarity.json 配置文件
 */
public class AutoRarityConfigManager {
    
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private static final Path AUTO_CONFIG_DIR = Paths.get("config/raritycore/auto");
    private static final Path AUTO_RARITY_FILE = AUTO_CONFIG_DIR.resolve("auto_rarity.json");
    
    // 跟踪上次加载的 auto 配置物品 ID(用于 reload 时清理)
    private static java.util.Set<ResourceLocation> lastLoadedAutoItems = new java.util.HashSet<>();
    
    /**
     * 获取自动配置目录路径
     */
    public static Path getAutoConfigDirPath() {
        return AUTO_CONFIG_DIR;
    }
    
    /**
     * 获取自动稀有度文件路径
     */
    public static Path getAutoRarityFilePath() {
        return AUTO_RARITY_FILE;
    }
    
    /**
     * 加载 auto_rarity.json 到注册表
     */
    public static void loadAutoRarityConfig() {
        try {
            // 1. 先清理上次加载的 auto 配置
            clearAutoLoadedItems();
            
            // 2. 如果文件不存在,直接返回
            if (!Files.exists(AUTO_RARITY_FILE)) {
                RarityCore.LOGGER.debug("Auto rarity config file not found: {}", AUTO_RARITY_FILE);
                return;
            }
            
            String content = Files.readString(AUTO_RARITY_FILE);
            if (content.trim().isEmpty()) {
                RarityCore.LOGGER.debug("Auto rarity config file is empty: {}", AUTO_RARITY_FILE);
                return;
            }
            
            com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            int loadedCount = 0;
            
            for (String key : jsonObject.keySet()) {
                try {
                    ResourceLocation itemId = ResourceLocation.parse(key);
                    int rarity = jsonObject.get(key).getAsInt();
                    
                    net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    if (item != null) {
                        // 写入自动计算的稀有度映射(优先级低于 FinalRarity.json)
                        org.yanbwe.raritycore.registry.RarityRegistry.putAutoRarity(itemId, rarity);
                        lastLoadedAutoItems.add(itemId); // 记录已加载的物品
                        loadedCount++;
                    }
                } catch (Exception e) {
                    RarityCore.LOGGER.debug("Failed to load auto rarity for item: {}", key, e);
                }
            }
            
            RarityCore.LOGGER.info("Loaded {} auto rarity configurations from {}", loadedCount, AUTO_RARITY_FILE);
            
            // 3. 清空批处理缓冲区(避免之前的操作影响)
            SyncManager.clearChangeBuffer();
            
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to load auto rarity config", e);
        }
    }
    
    /**
     * 清理上次加载的 auto 配置物品
     */
    private static void clearAutoLoadedItems() {
        for (ResourceLocation itemId : lastLoadedAutoItems) {
            // 从自动计算映射中移除
            RarityRegistry.removeAutoRarity(itemId);
        }
        lastLoadedAutoItems.clear();
        RarityCore.LOGGER.debug("Cleared {} auto-loaded items", lastLoadedAutoItems.size());
    }
    
    /**
     * 清理旧的 auto_*.json 文件
     */
    public static void cleanupAutoNbtFiles() {
        try {
            // 清理 auto 目录下的 auto_*.json
            if (Files.exists(AUTO_CONFIG_DIR)) {
                Files.list(AUTO_CONFIG_DIR)
                    .filter(p -> p.getFileName().toString().startsWith("auto_") && 
                                !p.getFileName().toString().equals("auto_rarity.json"))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            RarityCore.LOGGER.debug("Failed to delete old auto file: {}", p, e);
                        }
                    });
            }
                
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to cleanup auto NBT files", e);
        }
    }
    
    /**
     * 删除自动稀有度文件,并返回被删除的物品 ID 列表
     * @return 被删除的物品 ID 列表
     */
    public static java.util.List<ResourceLocation> deleteAutoRarityFile() {
        java.util.List<ResourceLocation> removedIds = new java.util.ArrayList<>();
        try {
            if (Files.exists(AUTO_RARITY_FILE)) {
                // 读取文件内容,获取所有物品 ID
                String content = Files.readString(AUTO_RARITY_FILE);
                if (!content.trim().isEmpty()) {
                    com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                    for (String key : jsonObject.keySet()) {
                        try {
                            removedIds.add(ResourceLocation.parse(key));
                        } catch (Exception e) {
                            // 忽略无效的 ID
                        }
                    }
                }
                
                Files.delete(AUTO_RARITY_FILE);
                RarityCore.LOGGER.info("Deleted auto rarity file: {}", AUTO_RARITY_FILE);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to delete auto rarity file", e);
        }
        return removedIds;
    }
    
    /**
     * 写入 auto_rarity.json
     */
    public static void writeAutoRarityJson(Map<net.minecraft.world.item.Item, Integer> computedRarities) {
        try {
            Files.createDirectories(AUTO_CONFIG_DIR);
            
            if (computedRarities.isEmpty()) {
                RarityCore.LOGGER.warn("No items to write in auto_rarity.json!");
                return;
            }
            
            JsonObject jsonObject = new JsonObject();
            int writtenCount = 0;
            
            for (Map.Entry<net.minecraft.world.item.Item, Integer> entry : computedRarities.entrySet()) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(entry.getKey());
                if (itemId != null) {
                    jsonObject.addProperty(itemId.toString(), entry.getValue());
                    writtenCount++;
                } else {
                    RarityCore.LOGGER.debug("Skipping item with null ID: {}", entry.getKey());
                }
            }
            
            try (FileWriter writer = new FileWriter(AUTO_RARITY_FILE.toFile())) {
                GSON.toJson(jsonObject, writer);
            }
            
            RarityCore.LOGGER.info("Wrote auto rarity config: {} items to {}", writtenCount, AUTO_RARITY_FILE);
            
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to write auto rarity config", e);
        }
    }
}
