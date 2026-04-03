package org.yanbwe.raritycore.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.yanbwe.raritycore.RarityCore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置文件工具类
 * 统一处理配置文件的读写操作,避免重复代码
 */
public class ConfigFileUtils {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * 确保目录存在,如果不存在则创建
     * @param directoryPath 目录路径
     * @param operationName 操作名称(用于日志)
     * @return 是否成功创建或目录已存在
     */
    public static boolean ensureDirectoryExists(Path directoryPath, String operationName) {
        try {
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
                RarityCore.LOGGER.info("Created directory for {}: {}", operationName, directoryPath);
            }
            return true;
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to create directory for {}: {}", operationName, directoryPath, e);
            return false;
        }
    }
    
    /**
     * 读取JSON配置文件
     * @param configFile 配置文件路径
     * @param operationName 操作名称(用于日志)
     * @return JsonObject对象,如果文件不存在或读取失败返回null
     */
    public static JsonObject readJsonConfig(Path configFile, String operationName) {
        if (!Files.exists(configFile)) {
            return null;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            String content = Files.readString(configFile);
            if (content.trim().isEmpty()) {
                return new JsonObject();
            }
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to parse {} config file, will recreate: {}", operationName, configFile, e);
            return new JsonObject();
        }
    }
    
    /**
     * 写入JSON配置文件
     * @param configFile 配置文件路径
     * @param jsonObject 要写入的JSON对象
     * @param operationName 操作名称(用于日志)
     * @return 是否写入成功
     */
    public static boolean writeJsonConfig(Path configFile, JsonObject jsonObject, String operationName) {
        try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(configFile), StandardCharsets.UTF_8)) {
            GSON.toJson(jsonObject, writer);
            RarityCore.LOGGER.info("Successfully wrote {} config file: {}", operationName, configFile);
            return true;
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to write {} config file: {}", operationName, configFile, e);
            return false;
        }
    }
    
    /**
     * 安全地读取并更新JSON配置文件
     * @param configFile 配置文件路径
     * @param updater 更新函数
     * @param operationName 操作名称(用于日志)
     * @return 是否操作成功
     */
    public static boolean updateJsonConfig(Path configFile, JsonUpdater updater, String operationName) {
        // 读取现有配置
        JsonObject jsonObject = readJsonConfig(configFile, operationName);
        if (jsonObject == null) {
            jsonObject = new JsonObject();
        }
        
        // 应用更新
        try {
            updater.update(jsonObject);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error updating {} config: {}", operationName, e.getMessage(), e);
            return false;
        }
        
        // 写入更新后的配置
        return writeJsonConfig(configFile, jsonObject, operationName);
    }
    
    /**
     * JSON更新器接口
     */
    @FunctionalInterface
    public interface JsonUpdater {
        void update(JsonObject jsonObject) throws Exception;
    }
}