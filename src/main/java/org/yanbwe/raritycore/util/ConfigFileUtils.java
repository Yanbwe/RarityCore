package org.yanbwe.raritycore.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.yanbwe.raritycore.RarityCore;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置文件工具类
 * 统一处理配置文件的读写操作,避免重复代码
 */
public class ConfigFileUtils {
    
    private static final Gson GSON = JsonPerformanceOptimizer.getOptimizedGson();
    
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
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
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

    /**
     * 构建 TacZ 子物品匹配配置文件名（确定性，重复编辑同一子物品时覆盖写）
     * 格式：editTacZ_<namespace>_<itemPath>_<sanitizedSubId>.json
     * 文件名包含子物品 ID，以区分同一物品 ID 下不同的 TacZ 子物品，不互相覆盖
     *
     * @param itemId 物品注册 ID（如 tacz:modern_kinetic_gun）
     * @param subId  TacZ 子物品 ID（如 tacz:m4a1）
     * @return 配置文件名（不含目录）
     */
    public static String buildTacZConfigFileName(ResourceLocation itemId, String subId) {
        // 清理子物品 ID 中的非文件名字符（冒号等），与 1.20.1 行为一致
        String safeValue = subId == null ? "" : subId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return "editTacZ_" + itemId.getNamespace() + "_" + itemId.getPath() + "_" + safeValue + ".json";
    }
}