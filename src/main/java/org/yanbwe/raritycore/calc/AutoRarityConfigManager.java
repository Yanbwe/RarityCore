package org.yanbwe.raritycore.calc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.nbtmatching.ContainsCondition;
import org.yanbwe.raritycore.nbtmatching.EqualsCondition;
import org.yanbwe.raritycore.nbtmatching.NbtCondition;
import org.yanbwe.raritycore.nbtmatching.RangeCondition;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 自动稀有度配置管理器
 * 管理 auto_rarity.json 和 nbt_matches 中的 auto_*.json 文件
 */
public class AutoRarityConfigManager {
    
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private static final Path AUTO_CONFIG_DIR = Paths.get("config/raritycore/auto");
    private static final Path AUTO_RARITY_FILE = AUTO_CONFIG_DIR.resolve("auto_rarity.json");
    private static final Path NBT_MATCHES_DIR = Paths.get("config/raritycore/nbt_matches"); // NBT 匹配配置目录
    
    // 跟踪上次加载的 auto 配置物品 ID（用于 reload 时清理）
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
            
            // 2. 如果文件不存在，直接返回
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
                    ResourceLocation itemId = new ResourceLocation(key);
                    int rarity = jsonObject.get(key).getAsInt();
                    
                    net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    if (item != null) {
                        // 直接写入注册表，不触发任何同步操作
                        org.yanbwe.raritycore.registry.RarityRegistry.ITEM_RARITY_MAP.put(itemId, rarity);
                        lastLoadedAutoItems.add(itemId); // 记录已加载的物品
                        loadedCount++;
                    }
                } catch (Exception e) {
                    RarityCore.LOGGER.debug("Failed to load auto rarity for item: {}", key, e);
                }
            }
            
            RarityCore.LOGGER.info("Loaded {} auto rarity configurations from {}", loadedCount, AUTO_RARITY_FILE);
            
            // 3. 清空批处理缓冲区（避免之前的操作影响）
            org.yanbwe.raritycore.registry.RarityRegistry.clearChangeBuffer();
            
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to load auto rarity config", e);
        }
    }
    
    /**
     * 清理上次加载的 auto 配置物品
     */
    private static void clearAutoLoadedItems() {
        for (ResourceLocation itemId : lastLoadedAutoItems) {
            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null) {
                RarityRegistry.ITEM_RARITY_MAP.remove(itemId);
            }
        }
        lastLoadedAutoItems.clear();
        RarityCore.LOGGER.debug("Cleared {} auto-loaded items", lastLoadedAutoItems.size());
    }
    
    /**
     * 清理旧的 auto_*.json 文件（包括 nbt_matches 目录）
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
            
            // 清理 nbt_matches 目录下的 auto_*.json
            if (Files.exists(NBT_MATCHES_DIR)) {
                Files.list(NBT_MATCHES_DIR)
                    .filter(p -> p.getFileName().toString().startsWith("auto_"))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            RarityCore.LOGGER.debug("Failed to delete old NBT file: {}", p, e);
                        }
                    });
            }
                
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to cleanup auto NBT files", e);
        }
    }
    
    /**
     * 删除自动稀有度文件，并返回被删除的物品 ID 列表
     * @return 被删除的物品 ID 列表
     */
    public static java.util.List<ResourceLocation> deleteAutoRarityFile() {
        java.util.List<ResourceLocation> removedIds = new java.util.ArrayList<>();
        try {
            if (Files.exists(AUTO_RARITY_FILE)) {
                // 读取文件内容，获取所有物品 ID
                String content = Files.readString(AUTO_RARITY_FILE);
                if (!content.trim().isEmpty()) {
                    com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                    for (String key : jsonObject.keySet()) {
                        try {
                            removedIds.add(new ResourceLocation(key));
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
            
            JsonObject jsonObject = new JsonObject();
            
            for (Map.Entry<net.minecraft.world.item.Item, Integer> entry : computedRarities.entrySet()) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(entry.getKey());
                if (itemId != null) {
                    jsonObject.addProperty(itemId.toString(), entry.getValue());
                }
            }
            
            try (FileWriter writer = new FileWriter(AUTO_RARITY_FILE.toFile())) {
                GSON.toJson(jsonObject, writer);
            }
            
            RarityCore.LOGGER.info("Wrote auto rarity config: {} items", computedRarities.size());
            
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to write auto rarity config", e);
        }
    }
    
    /**
     * 写入单个 NBT 规则文件到 nbt_matches 目录
     */
    public static void writeNbtRuleFile(ResourceLocation itemId, List<NbtCondition> conditions, int rarity) {
        try {
            Files.createDirectories(NBT_MATCHES_DIR);
            
            // 生成文件名：auto_itemid.json
            String safeItemId = itemId.toString().replace(":", "_").replace("/", "_");
            String fileName = "auto_" + safeItemId + ".json";
            Path filePath = NBT_MATCHES_DIR.resolve(fileName);
            
            // 创建符合 SimpleConfigValidator 格式的 JSON
            JsonObject rootObject = new JsonObject();
            
            // 添加 item_id
            rootObject.addProperty("item_id", itemId.toString());
            
            // 添加 rarity（使用传入的值）
            rootObject.addProperty("rarity", rarity);
            
            // 添加 fuzzy_match
            rootObject.addProperty("fuzzy_match", true);
            
            // 添加 priority
            rootObject.addProperty("priority", 100);
            
            // 添加 enabled
            rootObject.addProperty("enabled", true);
            
            // 添加 conditions 数组
            JsonArray conditionsArray = new JsonArray();
            for (NbtCondition condition : conditions) {
                JsonObject conditionObj = serializeCondition(condition);
                if (conditionObj != null) {
                    conditionsArray.add(conditionObj);
                }
            }
            
            rootObject.add("conditions", conditionsArray);
            
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                GSON.toJson(rootObject, writer);
            }
            
            RarityCore.LOGGER.debug("Wrote auto NBT rule for item: {} to {}", itemId, filePath);
            
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to write NBT rule file for item: {}", itemId, e);
        }
    }
    
    /**
     * 序列化 NBT 条件为 JSON 对象
     */
    private static JsonObject serializeCondition(NbtCondition condition) {
        try {
            JsonObject obj = new JsonObject();
            
            // 使用 getter 方法获取条件字段
            obj.addProperty("nbtPath", condition.getPath());
            obj.addProperty("conditionType", condition.getType().name());
            
            // 根据条件类型序列化特定值
            if (condition instanceof EqualsCondition) {
                Object value = ((EqualsCondition) condition).getExpectedValue();
                if (value instanceof String) {
                    obj.addProperty("value", (String) value);
                } else if (value instanceof Number) {
                    obj.addProperty("value", (Number) value);
                } else if (value instanceof Boolean) {
                    obj.addProperty("value", (Boolean) value);
                } else {
                    obj.addProperty("value", value != null ? value.toString() : "");
                }
            } else if (condition instanceof RangeCondition) {
                RangeCondition rangeCond = (RangeCondition) condition;
                obj.addProperty("minValue", rangeCond.getMinValue());
                obj.addProperty("maxValue", rangeCond.getMaxValue());
            } else if (condition instanceof ContainsCondition) {
                obj.addProperty("substring", ((ContainsCondition) condition).getSubstring());
            }
            // ExistsCondition 不需要额外的值
            
            return obj;
            
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to serialize NBT condition", e);
            return null;
        }
    }
}
