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
     * 初始化所有配置
     */
    public static void initializeConfigs() {
        try {
            // 确保配置目录存在
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR.toString(), e);
            return;
        }
        
        // 加载客户端配置
        loadClientConfig();
    }
    
    /**
     * 加载客户端配置
     */
    public static void loadClientConfig() {
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
                
                RarityCore.LOGGER.info("Client config loaded successfully: enableItemBorderRendering={}, itemBorderStyle={}", 
                    enableItemBorderRendering, itemBorderStyle);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading client config file, using default config: {}", CLIENT_CONFIG_FILE.toString(), e);
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
        JsonObject configObject = new JsonObject();
        configObject.addProperty("enableItemBorderRendering", RarityConstants.DEFAULT_ENABLE_ITEM_BORDER_RENDERING);
        configObject.addProperty("itemBorderStyle", RarityConstants.DEFAULT_ITEM_BORDER_STYLE);
        
        // 写入默认配置文件
        try (FileWriter writer = new FileWriter(CLIENT_CONFIG_FILE.toFile())) {
            GSON.toJson(configObject, writer);
            RarityCore.LOGGER.info("Created default client config file: {}", CLIENT_CONFIG_FILE.toString());
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create default client config file: {}", CLIENT_CONFIG_FILE.toString(), e);
        }
    }
    
    /**
     * 保存客户端配置到文件
     */
    public static void saveClientConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty("enableItemBorderRendering", enableItemBorderRendering);
        configObject.addProperty("itemBorderStyle", itemBorderStyle);
        
        // 写入配置文件
        try (FileWriter writer = new FileWriter(CLIENT_CONFIG_FILE.toFile())) {
            GSON.toJson(configObject, writer);
            RarityCore.LOGGER.info("Client config saved: enableItemBorderRendering={}, itemBorderStyle={}", 
                enableItemBorderRendering, itemBorderStyle);
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot save client config file: {}", CLIENT_CONFIG_FILE.toString(), e);
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
}