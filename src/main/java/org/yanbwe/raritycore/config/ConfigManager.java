package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一配置管理器
 * 管理所有配置文件的加载、保存和验证
 */
public class ConfigManager {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // 客户端配置
    private static boolean enableItemBorderRendering = RarityConstants.DEFAULT_ENABLE_ITEM_BORDER_RENDERING;
    private static int itemBorderStyle = RarityConstants.DEFAULT_ITEM_BORDER_STYLE; // 0为空心，1为实心
    private static boolean useTextureBorder = RarityConstants.DEFAULT_USE_TEXTURE_BORDER; // 是否使用纹理边框
    private static boolean enableItemNameColor = RarityConstants.DEFAULT_ENABLE_ITEM_NAME_COLOR; // 是否启用物品名称变色
    private static boolean enableTooltipInsert = RarityConstants.DEFAULT_ENABLE_TOOLTIP_INSERT; // 是否启用工具提示插入
    private static boolean checkVanillaRarity = RarityConstants.DEFAULT_CHECK_VANILLA_RARITY; // 是否检查原版稀有度
    private static boolean skipUnconfiguredItems = RarityConstants.DEFAULT_SKIP_UNCONFIGURED_ITEMS; // 是否跳过未配置物品的渲染
    private static boolean enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM; // 是否启用缓存系统
    
    // 星星显示配置
    private static boolean enableStarDisplay = RarityConstants.DEFAULT_ENABLE_STAR_DISPLAY; // 是否启用星星显示
    private static String starMode = RarityConstants.DEFAULT_STAR_MODE; // 星星显示模式
    private static String repeatCharacter = RarityConstants.DEFAULT_REPEAT_CHARACTER; // 重复模式字符
    private static java.util.Map<Integer, String> customStarStrings = new java.util.HashMap<>(); // 自定义模式字符串映射
    
    // 配置文件路径
    private static final Path CONFIG_DIR = Paths.get(RarityConstants.CONFIG_DIR_PARENT).resolve(RarityConstants.CONFIG_DIR_NAME);
    private static final Path CLIENT_CONFIG_FILE = CONFIG_DIR.resolve(RarityConstants.CLIENT_CONFIG_FILE_NAME);
    
    /**
     * 获取客户端配置路径
     */
    public static Path getClientConfigPath() {
        return CLIENT_CONFIG_FILE;
    }
    
    /**
     * 获取配置目录路径
     */
    public static Path getConfigDirPath() {
        return CONFIG_DIR;
    }
    
    /**
     * 获取最终稀有度配置路径
     */
    public static Path getFinalRarityConfigPath() {
        return CONFIG_DIR.resolve(RarityConstants.FINAL_RARITY_FILE_NAME);
    }
    
    /**
     * 获取FinalRarityConfig文件夹路径
     */
    public static Path getFinalRarityConfigFolderPath() {
        return CONFIG_DIR.resolve(RarityConstants.FINAL_RARITY_CONFIG_FOLDER_NAME);
    }
    
    /**
     * 初始化所有配置
     */
    public static void initializeConfigs() {
        try {
            // 确保配置目录存在
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }
        
        // 加载客户端配置
        loadClientConfig();
    }
    
    /**
     * 加载客户端配置
     */
    public static void loadClientConfig() {
        // 检查并升级配置文件
        ConfigVersionManager.checkAndUpgradeConfig(CLIENT_CONFIG_FILE, "client");
        
        // 如果配置文件不存在，则创建一个默认的
        if (!Files.exists(CLIENT_CONFIG_FILE)) {
            createDefaultClientConfig();
        }

        // 读取并加载配置文件
        loadClientConfigFromFile();
    }
    
    /**
     * 从文件加载客户端配置
     */
    private static void loadClientConfigFromFile() {
        try (BufferedReader reader = Files.newBufferedReader(CLIENT_CONFIG_FILE)) {
            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
            
            if (jsonObject != null) {
                // 记录配置版本信息
                int configVersion = 0;
                String modVersion = "unknown";
                if (jsonObject.has("config_version")) {
                    configVersion = jsonObject.get("config_version").getAsInt();
                }
                if (jsonObject.has("mod_version")) {
                    modVersion = jsonObject.get("mod_version").getAsString();
                }
                
                RarityCore.LOGGER.info("Loading client config version {} (mod version: {}) from {}", 
                    configVersion, modVersion, CLIENT_CONFIG_FILE.getFileName());
                // 读取边框渲染开关
                if (jsonObject.has("enableItemBorderRendering")) {
                    enableItemBorderRendering = jsonObject.get("enableItemBorderRendering").getAsBoolean();
                } else {
                    // If the config option doesn't exist, use default value
                    enableItemBorderRendering = RarityConstants.DEFAULT_ENABLE_ITEM_BORDER_RENDERING;
                }
                
                // Read border style
                if (jsonObject.has("itemBorderStyle")) {
                    itemBorderStyle = jsonObject.get("itemBorderStyle").getAsInt();
                    // Ensure value is within valid range
                    if (itemBorderStyle < 0 || itemBorderStyle > 1) {
                        itemBorderStyle = RarityConstants.DEFAULT_ITEM_BORDER_STYLE; // Default to hollow
                        RarityCore.LOGGER.warn("Invalid border style value in client config, reset to default: {}", itemBorderStyle);
                    }
                } else {
                    // If the config option doesn't exist, use default value
                    itemBorderStyle = RarityConstants.DEFAULT_ITEM_BORDER_STYLE; // Default to hollow
                }
                
                if (jsonObject.has("useTextureBorder")) {
                    useTextureBorder = jsonObject.get("useTextureBorder").getAsBoolean();
                } else {
                    // If the config option doesn't exist, use default value
                    useTextureBorder = RarityConstants.DEFAULT_USE_TEXTURE_BORDER;
                }
                
                // Read item name color setting
                if (jsonObject.has("enableItemNameColor")) {
                    enableItemNameColor = jsonObject.get("enableItemNameColor").getAsBoolean();
                } else {
                    // If the config option doesn't exist, use default value
                    enableItemNameColor = RarityConstants.DEFAULT_ENABLE_ITEM_NAME_COLOR;
                }
                
                // Read tooltip insert setting
                if (jsonObject.has("enableTooltipInsert")) {
                    enableTooltipInsert = jsonObject.get("enableTooltipInsert").getAsBoolean();
                } else {
                    // If the config option doesn't exist, use default value
                    enableTooltipInsert = RarityConstants.DEFAULT_ENABLE_TOOLTIP_INSERT;
                }
                
                // 读取跳过未配置物品设置
                if (jsonObject.has("skipUnconfiguredItems")) {
                    skipUnconfiguredItems = jsonObject.get("skipUnconfiguredItems").getAsBoolean();
                } else {
                    // If the config option doesn't exist, use default value
                    skipUnconfiguredItems = RarityConstants.DEFAULT_SKIP_UNCONFIGURED_ITEMS;
                }
                
                // 读取星星显示配置
                loadStarDisplayConfig(jsonObject);
                
                RarityCore.LOGGER.info("Client config loaded successfully: version={}, enableItemBorderRendering={}, itemBorderStyle={}, useTextureBorder={}, enableItemNameColor={}, enableTooltipInsert={}, skipUnconfiguredItems={}, enableStarDisplay={}, starMode={}", 
                    configVersion, enableItemBorderRendering, itemBorderStyle, useTextureBorder, enableItemNameColor, enableTooltipInsert, skipUnconfiguredItems, enableStarDisplay, starMode);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading client config file, using default config: {}", CLIENT_CONFIG_FILE, e);
            // 出错时使用默认值
            enableItemBorderRendering = RarityConstants.DEFAULT_ENABLE_ITEM_BORDER_RENDERING;
            itemBorderStyle = RarityConstants.DEFAULT_ITEM_BORDER_STYLE;
            // 重新创建配置文件以恢复默认设置
            createDefaultClientConfig();
        }
    }
    
    /**
     * 加载星星显示配置
     */
    private static void loadStarDisplayConfig(JsonObject jsonObject) {
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
                        
                        // 解析自定义字符串映射
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
                }
                
                RarityCore.LOGGER.info("Loaded star display config: enabled={}, mode={}, repeatChar='{}', customStrings={}", 
                    enableStarDisplay, starMode, repeatCharacter, customStarStrings.size());
            } else {
                // 如果没有starDisplay配置，使用默认值
                enableStarDisplay = RarityConstants.DEFAULT_ENABLE_STAR_DISPLAY;
                starMode = RarityConstants.DEFAULT_STAR_MODE;
                repeatCharacter = RarityConstants.DEFAULT_REPEAT_CHARACTER;
                customStarStrings.clear();
                RarityCore.LOGGER.info("No star display config found, using defaults");
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading star display config, using defaults: {}", e.getMessage());
            // 出错时使用默认值
            enableStarDisplay = RarityConstants.DEFAULT_ENABLE_STAR_DISPLAY;
            starMode = RarityConstants.DEFAULT_STAR_MODE;
            repeatCharacter = RarityConstants.DEFAULT_REPEAT_CHARACTER;
            customStarStrings.clear();
        }
    }
    
    /**
     * 创建默认客户端配置文件
     */
    private static void createDefaultClientConfig() {
        // 创建带版本信息的配置对象
        JsonObject configObject = ConfigVersionManager.createVersionedConfig();
        
        // 添加所有默认配置项
        configObject.addProperty("enableItemBorderRendering", RarityConstants.DEFAULT_ENABLE_ITEM_BORDER_RENDERING);
        configObject.addProperty("itemBorderStyle", RarityConstants.DEFAULT_ITEM_BORDER_STYLE);
        configObject.addProperty("useTextureBorder", RarityConstants.DEFAULT_USE_TEXTURE_BORDER);
        configObject.addProperty("enableItemNameColor", RarityConstants.DEFAULT_ENABLE_ITEM_NAME_COLOR);
        configObject.addProperty("enableTooltipInsert", RarityConstants.DEFAULT_ENABLE_TOOLTIP_INSERT);
        configObject.addProperty("checkVanillaRarity", RarityConstants.DEFAULT_CHECK_VANILLA_RARITY);
        configObject.addProperty("skipUnconfiguredItems", RarityConstants.DEFAULT_SKIP_UNCONFIGURED_ITEMS);
        configObject.addProperty("enableBatchProcessing", true);
        configObject.addProperty("enableCacheSystem", RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM);
        
        // 添加默认星星显示配置
        configObject.add("starDisplay", createDefaultStarDisplayConfig());
        
        // 写入默认配置文件
        try {
            try (FileWriter writer = new FileWriter(CLIENT_CONFIG_FILE.toString())) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Created default client config file with version {}: {}", 
                    ConfigVersionManager.CURRENT_CONFIG_VERSION, CLIENT_CONFIG_FILE);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create default client config file: {}", CLIENT_CONFIG_FILE, e);
        }
    }
    
    /**
     * 创建默认星星显示配置
     */
    private static JsonObject createDefaultStarDisplayConfig() {
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
        
        // 使用常量数组添加默认的自定义字符串配置
        for (int i = 0; i < RarityConstants.DEFAULT_CUSTOM_STRINGS.length; i++) {
            customStrings.addProperty(String.valueOf(i + 1), RarityConstants.DEFAULT_CUSTOM_STRINGS[i]);
        }
        
        customConfig.add("strings", customStrings);
        starDisplay.add("custom", customConfig);
        
        return starDisplay;
    }
    
    /**
     * 保存客户端配置到文件
     */
    public static void saveClientConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty("enableItemBorderRendering", enableItemBorderRendering);
        configObject.addProperty("itemBorderStyle", itemBorderStyle);
        configObject.addProperty("useTextureBorder", useTextureBorder);
        configObject.addProperty("enableItemNameColor", enableItemNameColor);
        configObject.addProperty("enableTooltipInsert", enableTooltipInsert);
        configObject.addProperty("checkVanillaRarity", checkVanillaRarity);
        configObject.addProperty("skipUnconfiguredItems", skipUnconfiguredItems);
        configObject.addProperty("enableCacheSystem", enableCacheSystem);
        
        // 写入配置文件
        try {
            try (FileWriter writer = new FileWriter(CLIENT_CONFIG_FILE.toString())) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Client config saved: enableItemBorderRendering={}, itemBorderStyle={}, useTextureBorder={}, enableItemNameColor={}, enableTooltipInsert={}, checkVanillaRarity={}, skipUnconfiguredItems={}, enableCacheSystem={}", 
                    enableItemBorderRendering, itemBorderStyle, useTextureBorder, enableItemNameColor, enableTooltipInsert, checkVanillaRarity, skipUnconfiguredItems, enableCacheSystem);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot save client config file: {}", CLIENT_CONFIG_FILE, e);
        }
    }
    
    /**
     * 验证稀有度值是否有效
     */
    public static boolean isValidRarity(int rarity) {
        return rarity >= RarityConstants.MIN_RARITY && rarity <= RarityConstants.MAX_RARITY;
    }
    
    /**
     * 获取物品边框渲染开关
     */
    public static boolean isEnableItemBorderRendering() {
        return enableItemBorderRendering;
    }
    
    /**
     * 设置物品边框渲染开关
     */
    public static void setEnableItemBorderRendering(boolean enable) {
        enableItemBorderRendering = enable;
    }
    
    /**
     * 获取物品边框样式
     */
    public static int getItemBorderStyle() {
        return itemBorderStyle;
    }
    
    /**
     * 设置物品边框样式
     */
    public static void setItemBorderStyle(int style) {
        itemBorderStyle = style;
    }
    
    /**
     * 获取是否使用纹理边框
     */
    public static boolean isUseTextureBorder() {
        return useTextureBorder;
    }
    
    /**
     * 设置是否使用纹理边框
     */
    public static void setUseTextureBorder(boolean useTexture) {
        useTextureBorder = useTexture;
    }
    
    /**
     * 获取是否启用物品名称变色
     */
    public static boolean isEnableItemNameColor() {
        return enableItemNameColor;
    }
    
    /**
     * 设置是否启用物品名称变色
     */
    public static void setEnableItemNameColor(boolean enable) {
        enableItemNameColor = enable;
    }
    
    /**
     * 获取是否启用工具提示插入
     */
    public static boolean isEnableTooltipInsert() {
        return enableTooltipInsert;
    }
    
    /**
     * 获取是否跳过未配置物品
     */
    public static boolean isSkipUnconfiguredItems() {
        return skipUnconfiguredItems;
    }
    
    /**
     * 设置是否启用工具提示插入
     */
    public static void setEnableTooltipInsert(boolean enable) {
        enableTooltipInsert = enable;
    }
    
    /**
     * 获取是否启用缓存系统
     */
    public static boolean isEnableCacheSystem() {
        return enableCacheSystem;
    }
    
    /**
     * 设置是否启用缓存系统
     */
    public static void setEnableCacheSystem(boolean enable) {
        enableCacheSystem = enable;
        // 通知缓存系统配置变更
        notifyCacheOfConfigChange();
    }
    
    /**
     * 通知缓存系统配置已变更
     */
    private static void notifyCacheOfConfigChange() {
        try {
            // 调用缓存失效监听器
            org.yanbwe.raritycore.client.CacheInvalidationListener.onClientConfigChange();
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to notify cache of config change", e);
        }
    }
    
    // ===== 星星显示配置相关方法 =====
    
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
    public static java.util.Map<Integer, String> getCustomStarStrings() {
        return new java.util.HashMap<>(customStarStrings);
    }
    
    /**
     * 设置自定义模式字符串映射
     */
    public static void setCustomStarStrings(java.util.Map<Integer, String> strings) {
        customStarStrings = strings != null ? new java.util.HashMap<>(strings) : new java.util.HashMap<>();
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
}