package org.yanbwe.raritycore.nbtmatching;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.yanbwe.raritycore.RarityCore;

import java.util.ArrayList;
import java.util.List;

/**
 * 简化配置验证器
 * 对格式错误的配置项直接忽略,不进行恢复
 */
public class SimpleConfigValidator {
    
    /**
     * 验证配置是否有效
     * @param config 配置对象
     * @return 是否有效
     */
    public static boolean isValidConfig(JsonObject config) {
        // 验证必需字段
        if (!config.has("item_id")) {
            RarityCore.LOGGER.debug("配置缺少必需字段: item_id");
            return false;
        }
        
        if (!config.has("conditions")) {
            RarityCore.LOGGER.debug("配置缺少必需字段: conditions");
            return false;
        }
        
        if (!config.has("rarity")) {
            RarityCore.LOGGER.debug("配置缺少必需字段: rarity");
            return false;
        }
        
        // 验证字段类型
        if (!config.get("item_id").isJsonPrimitive()) {
            RarityCore.LOGGER.debug("item_id必须是字符串");
            return false;
        }
        
        if (!config.get("conditions").isJsonArray()) {
            RarityCore.LOGGER.debug("conditions必须是数组");
            return false;
        }
        
        if (!config.get("rarity").isJsonPrimitive()) {
            RarityCore.LOGGER.debug("rarity必须是数字");
            return false;
        }
        
        return true;
    }
    
    /**
     * 解析配置为匹配规则
     * @param config 配置对象
     * @return 匹配规则,如果解析失败返回null
     */
    public static NbtMatchRule parseRule(JsonObject config) {
        if (!isValidConfig(config)) {
            return null;
        }
        
        try {
            String itemIdStr = config.get("item_id").getAsString();
            ResourceLocation itemId = new ResourceLocation(itemIdStr);
            
            List<NbtCondition> conditions = parseConditions(config.getAsJsonArray("conditions"));
            if (conditions.isEmpty()) {
                RarityCore.LOGGER.debug("配置中没有有效的条件");
                return null;
            }
            
            int rarity = config.get("rarity").getAsInt();
            int priority = config.has("priority") ? config.get("priority").getAsInt() : 0;
            boolean enabled = !config.has("enabled") || config.get("enabled").getAsBoolean();
            boolean fuzzyMatch = config.has("fuzzy_match") && config.get("fuzzy_match").getAsBoolean();
            String description = config.has("description") ? config.get("description").getAsString() : "";
            
            return NbtMatchRule.builder()
                .itemId(itemId)
                .conditions(conditions)
                .priority(priority)
                .rarity(rarity)
                .enabled(enabled)
                .fuzzyMatch(fuzzyMatch)
                .description(description)
                .build();
                
        } catch (Exception e) {
            RarityCore.LOGGER.debug("解析配置时出错: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 解析条件数组
     * @param conditionsArray 条件数组
     * @return 条件列表
     */
    private static List<NbtCondition> parseConditions(com.google.gson.JsonArray conditionsArray) {
        List<NbtCondition> conditions = new ArrayList<>();
        
        for (int i = 0; i < conditionsArray.size(); i++) {
            try {
                com.google.gson.JsonObject conditionObj = conditionsArray.get(i).getAsJsonObject();
                NbtCondition condition = parseCondition(conditionObj);
                if (condition != null) {
                    conditions.add(condition);
                }
            } catch (Exception e) {
                RarityCore.LOGGER.debug("解析第{}个条件时出错: {}", i + 1, e.getMessage());
            }
        }
        
        return conditions;
    }
    
    /**
     * 解析单个条件
     * @param conditionObj 条件对象
     * @return 条件对象,解析失败返回null
     */
    private static NbtCondition parseCondition(com.google.gson.JsonObject conditionObj) {
        if (!conditionObj.has("path") || !conditionObj.has("type")) {
            RarityCore.LOGGER.debug("条件缺少必需字段: path或type");
            return null;
        }
        
        String path = conditionObj.get("path").getAsString();
        String typeStr = conditionObj.get("type").getAsString();
        String description = conditionObj.has("description") ? 
                           conditionObj.get("description").getAsString() : "";
        
        switch (typeStr.toLowerCase()) {
            case "equals":
                return parseEqualsCondition(conditionObj, path, description);
            case "exists":
                return new ExistsCondition(path, description);
            case "range":
                return parseRangeCondition(conditionObj, path, description);
            case "contains":
                return parseContainsCondition(conditionObj, path, description);
            default:
                RarityCore.LOGGER.debug("不支持的条件类型: {}", typeStr);
                return null;
        }
    }
    
    /**
     * 解析等值条件
     */
    private static NbtCondition parseEqualsCondition(com.google.gson.JsonObject conditionObj, 
                                                   String path, String description) {
        if (!conditionObj.has("value")) {
            RarityCore.LOGGER.debug("等值条件缺少value字段");
            return null;
        }
        
        Object value = parseValue(conditionObj.get("value"));
        return new EqualsCondition(path, value, description);
    }
    
    /**
     * 解析范围条件
     */
    private static NbtCondition parseRangeCondition(com.google.gson.JsonObject conditionObj, 
                                                  String path, String description) {
        if (!conditionObj.has("min") || !conditionObj.has("max")) {
            RarityCore.LOGGER.debug("范围条件缺少min或max字段");
            return null;
        }
        
        Number min = conditionObj.get("min").getAsNumber();
        Number max = conditionObj.get("max").getAsNumber();
        return new RangeCondition(path, min, max, description);
    }
    
    /**
     * 解析包含条件
     */
    private static NbtCondition parseContainsCondition(com.google.gson.JsonObject conditionObj, 
                                                     String path, String description) {
        if (!conditionObj.has("substring")) {
            RarityCore.LOGGER.debug("包含条件缺少substring字段");
            return null;
        }
        
        String substring = conditionObj.get("substring").getAsString();
        return new ContainsCondition(path, substring, description);
    }
    
    /**
     * 解析值
     */
    private static Object parseValue(com.google.gson.JsonElement element) {
        if (element.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else if (primitive.isNumber()) {
                return primitive.getAsNumber();
            } else if (primitive.isString()) {
                return primitive.getAsString();
            }
        }
        return element.toString();
    }
}