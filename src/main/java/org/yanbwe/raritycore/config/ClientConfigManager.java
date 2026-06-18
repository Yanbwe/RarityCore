package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.compat.CompatibilityManager;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 客户端配置管理器
 * 负责管理客户端相关的配置
 */
public class ClientConfigManager {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // 客户端配置
    private static boolean enableItemBorderRendering = RarityConstants.DEFAULT_ENABLE_ITEM_BORDER_RENDERING;
    private static int itemBorderStyle = RarityConstants.DEFAULT_ITEM_BORDER_STYLE; // 0为空心,1为实心
    private static boolean useTextureBorder = RarityConstants.DEFAULT_USE_TEXTURE_BORDER; // 是否使用纹理边框
    private static boolean enableItemNameColor = RarityConstants.DEFAULT_ENABLE_ITEM_NAME_COLOR; // 是否启用物品名称变色
    private static boolean enableTooltipInsert = RarityConstants.DEFAULT_ENABLE_TOOLTIP_INSERT; // 是否启用工具提示插入
    private static boolean enableTooltipColor = RarityConstants.DEFAULT_ENABLE_TOOLTIP_COLOR; // 是否启用工具提示稀有度文本变色
    private static boolean skipUnconfiguredItems = RarityConstants.DEFAULT_SKIP_UNCONFIGURED_ITEMS; // 是否跳过未配置物品的渲染
    private static boolean enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM; // 是否启用缓存系统
    
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
     * 初始化客户端配置
     */
    public static void initialize() {
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
        // 验证并更新配置文件
        JsonObject defaultConfig = ConfigValidator.createDefaultClientConfig();
        ConfigValidator.validateConfig(CLIENT_CONFIG_FILE, defaultConfig, "client");
        
        // 如果配置文件不存在,则创建一个默认的
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
                RarityCore.LOGGER.info("Loading client config from {}", CLIENT_CONFIG_FILE.getFileName());
                
                // 加载星星显示配置
                StarDisplayConfigManager.loadStarDisplayConfig(jsonObject);
                
                // 读取边框渲染开关
                if (jsonObject.has("enableItemBorderRendering")) {
                    enableItemBorderRendering = jsonObject.get("enableItemBorderRendering").getAsBoolean();
                } else {
                    // 如果配置项不存在，使用默认值
                    enableItemBorderRendering = RarityConstants.DEFAULT_ENABLE_ITEM_BORDER_RENDERING;
                }
                
                // 读取边框样式
                if (jsonObject.has("itemBorderStyle")) {
                    itemBorderStyle = jsonObject.get("itemBorderStyle").getAsInt();
                    // 确保值在有效范围内
                    if (itemBorderStyle < 0 || itemBorderStyle > 1) {
                        itemBorderStyle = RarityConstants.DEFAULT_ITEM_BORDER_STYLE; // 默认为空心
                        RarityCore.LOGGER.warn("Invalid border style value in client config, reset to default: {}", itemBorderStyle);
                    }
                } else {
                    // 如果配置项不存在，使用默认值
                    itemBorderStyle = RarityConstants.DEFAULT_ITEM_BORDER_STYLE; // 默认为空心
                }
                
                if (jsonObject.has("useTextureBorder")) {
                    useTextureBorder = jsonObject.get("useTextureBorder").getAsBoolean();
                } else {
                    // 如果配置项不存在，使用默认值
                    useTextureBorder = RarityConstants.DEFAULT_USE_TEXTURE_BORDER;
                }
                
                // 读取物品名称颜色设置
                if (jsonObject.has("enableItemNameColor")) {
                    enableItemNameColor = jsonObject.get("enableItemNameColor").getAsBoolean();
                } else {
                    // 如果配置项不存在，使用默认值
                    enableItemNameColor = RarityConstants.DEFAULT_ENABLE_ITEM_NAME_COLOR;
                }
                
                // 读取工具提示插入设置
                if (jsonObject.has("enableTooltipInsert")) {
                    enableTooltipInsert = jsonObject.get("enableTooltipInsert").getAsBoolean();
                } else {
                    // 如果配置项不存在，使用默认值
                    enableTooltipInsert = RarityConstants.DEFAULT_ENABLE_TOOLTIP_INSERT;
                }
                
                // 读取工具提示颜色设置
                if (jsonObject.has("enableTooltipColor")) {
                    enableTooltipColor = jsonObject.get("enableTooltipColor").getAsBoolean();
                } else {
                    // 如果配置项不存在，使用默认值
                    enableTooltipColor = RarityConstants.DEFAULT_ENABLE_TOOLTIP_COLOR;
                }
                
                // 读取跳过未配置物品设置
                if (jsonObject.has("skipUnconfiguredItems")) {
                    skipUnconfiguredItems = jsonObject.get("skipUnconfiguredItems").getAsBoolean();
                } else {
                    // 如果配置项不存在，使用默认值
                    skipUnconfiguredItems = RarityConstants.DEFAULT_SKIP_UNCONFIGURED_ITEMS;
                }
                
                RarityCore.LOGGER.info("Client config loaded successfully: enableItemBorderRendering={}, itemBorderStyle={}, useTextureBorder={}, enableItemNameColor={}, enableTooltipInsert={}, enableTooltipColor={}, skipUnconfiguredItems={}", 
                    enableItemBorderRendering, itemBorderStyle, useTextureBorder, enableItemNameColor, enableTooltipInsert, enableTooltipColor, skipUnconfiguredItems);
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
     * 创建默认客户端配置文件
     */
    private static void createDefaultClientConfig() {
        // 创建默认配置对象
        JsonObject configObject = ConfigValidator.createDefaultClientConfig();
        
        // 写入默认配置文件
        try {
            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(CLIENT_CONFIG_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Created default client config file: {}", CLIENT_CONFIG_FILE);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create default client config file: {}", CLIENT_CONFIG_FILE, e);
        }
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
        configObject.addProperty("enableTooltipColor", enableTooltipColor);
        configObject.addProperty("skipUnconfiguredItems", skipUnconfiguredItems);
        configObject.addProperty("enableCacheSystem", enableCacheSystem);
        
        // 写入配置文件
        try {
            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(CLIENT_CONFIG_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Client config saved: enableItemBorderRendering={}, itemBorderStyle={}, useTextureBorder={}, enableItemNameColor={}, enableTooltipInsert={}, enableTooltipColor={}, skipUnconfiguredItems={}, enableCacheSystem={}", 
                    enableItemBorderRendering, itemBorderStyle, useTextureBorder, enableItemNameColor, enableTooltipInsert, enableTooltipColor, skipUnconfiguredItems, enableCacheSystem);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot save client config file: {}", CLIENT_CONFIG_FILE, e);
        }
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
     * 如果检测到ColorTooltips模组，则强制禁用工具提示插入
     */
    public static boolean isEnableTooltipInsert() {
        // 如果ColorTooltips模组已加载，强制禁用工具提示插入
        if (CompatibilityManager.isColorTooltipsLoaded()) {
            return false;
        }
        return enableTooltipInsert;
    }
    
    /**
     * 获取是否启用工具提示稀有度文本变色
     */
    public static boolean isEnableTooltipColor() {
        return enableTooltipColor;
    }
    
    /**
     * 设置是否启用工具提示稀有度文本变色
     */
    public static void setEnableTooltipColor(boolean enable) {
        enableTooltipColor = enable;
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
}