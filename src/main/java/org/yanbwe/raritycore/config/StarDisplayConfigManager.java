package org.yanbwe.raritycore.config;

import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * 星星显示配置管理器
 * 负责管理星星显示相关的配置
 */
public class StarDisplayConfigManager {
    
    // 星星显示配置
    private static boolean enableStarDisplay = RarityConstants.DEFAULT_ENABLE_STAR_DISPLAY; // 是否启用星星显示
    private static String starMode = RarityConstants.DEFAULT_STAR_MODE; // 星星显示模式
    private static String repeatCharacter = RarityConstants.DEFAULT_REPEAT_CHARACTER; // 重复模式字符
    private static Map<Integer, String> customStarStrings = new HashMap<>(); // 自定义模式字符串映射
    private static Map<Integer, String> customSpecialRarityTexts = new HashMap<>(); // 特殊稀有度文本映射(大于 7 级)
    
    /**
     * 初始化星星显示配置
     */
    public static void initialize() {
        // 初始化默认值
        resetToDefaults();
    }
    
    /**
     * 加载星星显示配置
     */
    public static void loadStarDisplayConfig(JsonObject jsonObject) {
        try {
            // 读取星星显示总开关
            if (jsonObject.has("starDisplay")) {
                JsonObject starDisplayObj = jsonObject.getAsJsonObject("starDisplay");
                
                // 读取启用状态
                if (starDisplayObj.has("enabled")) {
                    enableStarDisplay = starDisplayObj.get("enabled").getAsBoolean();
                } else {
                    enableStarDisplay = RarityConstants.DEFAULT_ENABLE_STAR_DISPLAY;
                }
                
                // 读取显示模式
                if (starDisplayObj.has("mode")) {
                    starMode = starDisplayObj.get("mode").getAsString();
                } else {
                    starMode = RarityConstants.DEFAULT_STAR_MODE;
                }
                
                // 读取重复模式配置
                if (starDisplayObj.has("repeat")) {
                    JsonObject repeatObj = starDisplayObj.getAsJsonObject("repeat");
                    if (repeatObj.has("character")) {
                        repeatCharacter = repeatObj.get("character").getAsString();
                    } else {
                        repeatCharacter = RarityConstants.DEFAULT_REPEAT_CHARACTER;
                    }
                } else {
                    repeatCharacter = RarityConstants.DEFAULT_REPEAT_CHARACTER;
                }
                
                // 读取自定义模式配置
                if (starDisplayObj.has("custom")) {
                    JsonObject customObj = starDisplayObj.getAsJsonObject("custom");
                    if (customObj.has("strings")) {
                        JsonObject stringsObj = customObj.getAsJsonObject("strings");
                        customStarStrings.clear();
                        
                        // 解析自定义字符串映射(用于星星显示)
                        for (String key : stringsObj.keySet()) {
                            try {
                                int rarity = Integer.parseInt(key);
                                String stringValue = stringsObj.get(key).getAsString();
                                if (stringValue != null && !stringValue.isEmpty()) {
                                    customStarStrings.put(rarity, stringValue);
                                }
                            } catch (NumberFormatException e) {
                                RarityCore.LOGGER.warn("Invalid rarity key in custom star strings: {}", key);
                            }
                        }
                    }
                    
                    // 读取特殊稀有度文本配置(大于 7 级)
                    if (customObj.has("specialRarityTexts")) {
                        JsonObject specialTextsObj = customObj.getAsJsonObject("specialRarityTexts");
                        customSpecialRarityTexts.clear();
                        
                        // 解析特殊稀有度文本映射
                        for (String key : specialTextsObj.keySet()) {
                            try {
                                int rarity = Integer.parseInt(key);
                                String textValue = specialTextsObj.get(key).getAsString();
                                if (textValue != null && !textValue.isEmpty()) {
                                    customSpecialRarityTexts.put(rarity, textValue);
                                }
                            } catch (NumberFormatException e) {
                                RarityCore.LOGGER.warn("Invalid rarity key in special rarity texts: {}", key);
                            }
                        }
                    }
                }
                
                RarityCore.LOGGER.info("Loaded star display config: enabled={}, mode={}, repeatChar='{}', customStrings={}, specialRarityTexts={}", 
                    enableStarDisplay, starMode, repeatCharacter, customStarStrings.size(), customSpecialRarityTexts.size());
            } else {
                // 如果没有starDisplay配置,使用默认值
                resetToDefaults();
                RarityCore.LOGGER.info("No star display config found, using defaults");
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading star display config, using defaults: {}", e.getMessage());
            // 出错时使用默认值
            resetToDefaults();
        } finally {
            // 无论成功、默认回退还是异常回退，都通知 StarDisplayManager 更新策略
            try {
                org.yanbwe.raritycore.util.StarDisplayManager.getInstance().reloadConfiguration();
            } catch (Exception ignored) {
                // 单例可能尚未初始化，忽略
            }
        }
    }
    
    /**
     * 重置为默认配置
     */
    private static void resetToDefaults() {
        enableStarDisplay = RarityConstants.DEFAULT_ENABLE_STAR_DISPLAY;
        starMode = RarityConstants.DEFAULT_STAR_MODE;
        repeatCharacter = RarityConstants.DEFAULT_REPEAT_CHARACTER;
        customStarStrings.clear();
        customSpecialRarityTexts.clear();
    }
    
    /**
     * 创建默认星星显示配置
     */
    public static JsonObject createDefaultStarDisplayConfig() {
        JsonObject starDisplay = new JsonObject();
        starDisplay.addProperty("enabled", RarityConstants.DEFAULT_ENABLE_STAR_DISPLAY);
        starDisplay.addProperty("mode", RarityConstants.DEFAULT_STAR_MODE); // 引用常量
        
        // 重复模式配置
        JsonObject repeatConfig = new JsonObject();
        repeatConfig.addProperty("character", RarityConstants.DEFAULT_REPEAT_CHARACTER);
        starDisplay.add("repeat", repeatConfig);
        
        // 自定义模式配置
        JsonObject customConfig = new JsonObject();
        JsonObject customStrings = new JsonObject();
        
        // 使用常量数组添加默认的自定义字符串配置(用于星星显示)
        for (int i = 0; i < RarityConstants.DEFAULT_CUSTOM_STRINGS.length; i++) {
            customStrings.addProperty(String.valueOf(i + 1), RarityConstants.DEFAULT_CUSTOM_STRINGS[i]);
        }
        
        // 添加空的特殊稀有度文本配置对象(大于 7 级,由用户自行定义)
        JsonObject specialRarityTexts = new JsonObject();
        
        customConfig.add("strings", customStrings);
        customConfig.add("specialRarityTexts", specialRarityTexts);
        starDisplay.add("custom", customConfig);
        
        return starDisplay;
    }
    
    /**
     * 获取是否启用星星显示
     */
    public static boolean isEnableStarDisplay() {
        return enableStarDisplay;
    }
    
    /**
     * 设置是否启用星星显示
     */
    public static void setEnableStarDisplay(boolean enable) {
        enableStarDisplay = enable;
    }
    
    /**
     * 获取星星显示模式
     */
    public static String getStarMode() {
        return starMode;
    }
    
    /**
     * 设置星星显示模式
     */
    public static void setStarMode(String mode) {
        starMode = mode;
    }
    
    /**
     * 获取重复模式字符
     */
    public static String getRepeatCharacter() {
        return repeatCharacter;
    }
    
    /**
     * 设置重复模式字符
     */
    public static void setRepeatCharacter(String character) {
        repeatCharacter = character;
    }
    
    /**
     * 获取自定义模式字符串映射
     */
    public static Map<Integer, String> getCustomStarStrings() {
        return new HashMap<>(customStarStrings);
    }
    
    /**
     * 设置自定义模式字符串映射
     */
    public static void setCustomStarStrings(Map<Integer, String> strings) {
        customStarStrings = strings != null ? new HashMap<>(strings) : new HashMap<>();
    }
    
    /**
     * 获取指定稀有度的自定义字符串
     */
    public static String getCustomStarString(int rarity) {
        return customStarStrings.get(rarity);
    }
    
    /**
     * 设置指定稀有度的自定义字符串
     */
    public static void setCustomStarString(int rarity, String string) {
        if (string != null && !string.isEmpty()) {
            customStarStrings.put(rarity, string);
        } else {
            customStarStrings.remove(rarity);
        }
    }
    
    /**
     * 获取特殊稀有度文本映射(大于 7 级)
     */
    public static Map<Integer, String> getCustomSpecialRarityTexts() {
        return new HashMap<>(customSpecialRarityTexts);
    }
    
    /**
     * 获取指定稀有度的自定义特殊文本
     * @param rarity 稀有度等级(大于 7)
     * @return 自定义文本,如果没有配置则返回 null
     */
    public static String getCustomSpecialRarityText(int rarity) {
        return customSpecialRarityTexts.get(rarity);
    }
    
    /**
     * 设置特殊稀有度文本映射
     */
    public static void setCustomSpecialRarityTexts(Map<Integer, String> texts) {
        customSpecialRarityTexts = texts != null ? new HashMap<>(texts) : new HashMap<>();
    }
}