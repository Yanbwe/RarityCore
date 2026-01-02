package org.yanbwe.raritycore.config;

import com.google.gson.*;
import net.minecraftforge.fml.loading.FMLPaths;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
            RarityCore.LOGGER.error("无法创建配置目录: {}", CONFIG_DIR.toString(), e);
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
                    // 如果配置项不存在，使用默认值
                    enableItemBorderRendering = RarityConstants.DEFAULT_ENABLE_ITEM_BORDER_RENDERING;
                }
                
                // 读取边框样式
                if (jsonObject.has("itemBorderStyle")) {
                    itemBorderStyle = jsonObject.get("itemBorderStyle").getAsInt();
                    // 确保值在有效范围内
                    if (itemBorderStyle < 0 || itemBorderStyle > 1) {
                        itemBorderStyle = RarityConstants.DEFAULT_ITEM_BORDER_STYLE; // 默认为空心
                        RarityCore.LOGGER.warn("客户端配置中边框样式值无效，已重置为默认值: {}", itemBorderStyle);
                    }
                } else {
                    // 如果配置项不存在，使用默认值
                    itemBorderStyle = RarityConstants.DEFAULT_ITEM_BORDER_STYLE; // 默认为空心
                }
                
                RarityCore.LOGGER.info("客户端配置加载成功: enableItemBorderRendering={}, itemBorderStyle={}", 
                    enableItemBorderRendering, itemBorderStyle);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("加载客户端配置文件时发生错误，使用默认配置: {}", CLIENT_CONFIG_FILE.toString(), e);
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
            RarityCore.LOGGER.info("已创建默认客户端配置文件: {}", CLIENT_CONFIG_FILE.toString());
        } catch (IOException e) {
            RarityCore.LOGGER.error("无法创建默认客户端配置文件: {}", CLIENT_CONFIG_FILE.toString(), e);
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
            RarityCore.LOGGER.info("客户端配置已保存: enableItemBorderRendering={}, itemBorderStyle={}", 
                enableItemBorderRendering, itemBorderStyle);
        } catch (IOException e) {
            RarityCore.LOGGER.error("无法保存客户端配置文件: {}", CLIENT_CONFIG_FILE.toString(), e);
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