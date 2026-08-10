package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.ConfigLoaderUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RarityConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 从配置文件加载稀有度数据
     */
    public static void loadConfigRarityData() {
        Path configDir = ConfigManager.getConfigDirPath();
        
        // 确保配置目录存在
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", configDir, e);
            return;
        }

        Path configFile = ConfigManager.getFinalRarityConfigPath();
        
        // 如果配置文件不存在,则创建默认配置文件
        if (!Files.exists(configFile)) {
            createDefaultConfig(configFile);
        }

        // 读取并加载配置文件内容
        loadRarityDataFromFile(configFile);

        // 加载 Tag 稀有度配置
        TagRarityLoader.loadTagRarityConfig();
    }

    /**
     * 从文件加载稀有度数据
     */
    private static void loadRarityDataFromFile(Path configFile) {
        // 首先尝试使用高性能解析器
        if (tryOptimizedParsing(configFile)) {
            return;
        }
        
        // 使用通用工具类加载配置
        ConfigLoaderUtils.loadJsonConfigFileWithBatch(configFile, configFile.getFileName().toString(), true);
    }
    
    /**
     * 尝试使用高性能JSON解析
     */
    private static boolean tryOptimizedParsing(Path configFile) {
        try {
            org.yanbwe.raritycore.util.JsonPerformanceOptimizer.parseRarityConfigOptimized(configFile);
            return true;
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Optimized parsing failed, falling back to traditional method: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 创建默认配置文件
     */
    private static void createDefaultConfig(Path configFile) {
        JsonObject configObject = new JsonObject();
        
        // 写入默认配置内容
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            GSON.toJson(configObject, writer);
        } catch (IOException e) {
            RarityCore.LOGGER.error("无法创建默认配置文件: {}", configFile, e);
        }
    }
}