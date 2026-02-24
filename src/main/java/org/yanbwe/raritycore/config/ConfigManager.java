package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.yanbwe.raritycore.Raritycore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("raritycore");
    private static final Path CLIENT_CONFIG_FILE = CONFIG_DIR.resolve("client.json");
    
    // 客户端配置选项
    private static boolean enableItemBorderRendering = true;
    private static int itemBorderStyle = 0; // 0: 空心, 1: 实心
    private static boolean useTextureBorder = true; // 默认启用纹理边框
    private static boolean enableItemNameColor = true;
    
    public static void initializeConfigs() {
        try {
            // 确保配置目录存在
            Files.createDirectories(CONFIG_DIR);
            
            // 加载客户端配置
            loadClientConfig();
            
            Raritycore.LOGGER.info("Configuration system initialized");
        } catch (Exception e) {
            Raritycore.LOGGER.error("Failed to initialize configuration system", e);
        }
    }
    
    private static void loadClientConfig() {
        if (!Files.exists(CLIENT_CONFIG_FILE)) {
            createDefaultClientConfig();
        }
        
        try {
            String content = Files.readString(CLIENT_CONFIG_FILE);
            JsonObject config = GSON.fromJson(content, JsonObject.class);
            
            if (config != null) {
                enableItemBorderRendering = config.has("enableItemBorderRendering") ? 
                    config.get("enableItemBorderRendering").getAsBoolean() : true;
                itemBorderStyle = config.has("itemBorderStyle") ? 
                    config.get("itemBorderStyle").getAsInt() : 0;
                useTextureBorder = config.has("useTextureBorder") ? 
                    config.get("useTextureBorder").getAsBoolean() : true;
                enableItemNameColor = config.has("enableItemNameColor") ? 
                    config.get("enableItemNameColor").getAsBoolean() : true;
            }
            
            Raritycore.LOGGER.info("Client config loaded: border rendering={}, style={}, texture={}, name color={}",
                enableItemBorderRendering, itemBorderStyle, useTextureBorder, enableItemNameColor);
        } catch (Exception e) {
            Raritycore.LOGGER.error("Failed to load client config, using defaults", e);
            createDefaultClientConfig();
        }
    }
    
    private static void createDefaultClientConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("enableItemBorderRendering", true);
        config.addProperty("itemBorderStyle", 0);
        config.addProperty("useTextureBorder", true);
        config.addProperty("enableItemNameColor", true);
        
        try {
            Files.writeString(CLIENT_CONFIG_FILE, GSON.toJson(config));
            Raritycore.LOGGER.info("Created default client config file");
        } catch (IOException e) {
            Raritycore.LOGGER.error("Failed to create default client config", e);
        }
    }
    
    public static void saveClientConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("enableItemBorderRendering", enableItemBorderRendering);
        config.addProperty("itemBorderStyle", itemBorderStyle);
        config.addProperty("useTextureBorder", useTextureBorder);
        config.addProperty("enableItemNameColor", enableItemNameColor);
        
        try {
            Files.writeString(CLIENT_CONFIG_FILE, GSON.toJson(config));
            Raritycore.LOGGER.info("Client config saved");
        } catch (IOException e) {
            Raritycore.LOGGER.error("Failed to save client config", e);
        }
    }
    
    // Getter和Setter方法
    public static boolean isEnableItemBorderRendering() {
        return enableItemBorderRendering;
    }
    
    public static void setEnableItemBorderRendering(boolean enable) {
        enableItemBorderRendering = enable;
        saveClientConfig();
    }
    
    public static int getItemBorderStyle() {
        return itemBorderStyle;
    }
    
    public static void setItemBorderStyle(int style) {
        itemBorderStyle = style;
        saveClientConfig();
    }
    
    public static boolean isUseTextureBorder() {
        return useTextureBorder;
    }
    
    public static void setUseTextureBorder(boolean useTexture) {
        useTextureBorder = useTexture;
        saveClientConfig();
    }
    
    public static boolean isEnableItemNameColor() {
        return enableItemNameColor;
    }
    
    public static void setEnableItemNameColor(boolean enable) {
        enableItemNameColor = enable;
        saveClientConfig();
    }
}