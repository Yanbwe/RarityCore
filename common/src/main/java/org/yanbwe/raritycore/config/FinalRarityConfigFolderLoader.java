package org.yanbwe.raritycore.config;


import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.ConfigLoaderUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * FinalRarityConfig文件夹加载器
 * 负责加载config/raritycore/FinalRarityConfig文件夹中的所有JSON配置文件
 */
public class FinalRarityConfigFolderLoader {


    /**
     * 加载FinalRarityConfig文件夹中的所有JSON文件
     * 文件按字母顺序加载,后加载的会覆盖先加载的同名物品配置
     */
    public static void loadFinalRarityConfigFolder() {
        Path configFolder = ConfigManager.getFinalRarityConfigFolderPath();
        
        // 确保目录存在
        try {
            Files.createDirectories(configFolder);
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create FinalRarityConfig directory: {}", configFolder, e);
            return;
        }

        // 检查目录是否存在且是目录
        if (!Files.exists(configFolder) || !Files.isDirectory(configFolder)) {
            RarityCore.LOGGER.info("FinalRarityConfig folder not found, skipping: {}", configFolder);
            return;
        }

        try {
            // 获取所有JSON文件并按名称排序
            Path[] jsonFiles = Files.list(configFolder)
                .filter(path -> path.toString().endsWith(".json"))
                .sorted()
                .toArray(Path[]::new);
            
            RarityCore.LOGGER.info("Found {} JSON files in FinalRarityConfig folder", jsonFiles.length);
            
            // 按顺序加载所有JSON文件
            for (Path jsonFile : jsonFiles) {
                RarityCore.LOGGER.info("Loading FinalRarityConfig file: {}", jsonFile.getFileName());
                loadRarityDataFromFile(jsonFile);
            }
            
        } catch (IOException e) {
            RarityCore.LOGGER.error("Error reading FinalRarityConfig folder: {}", configFolder, e);
        }
    }

    /**
     * 从单个JSON文件加载稀有度数据
     */
    private static void loadRarityDataFromFile(Path configFile) {
        ConfigLoaderUtils.loadJsonConfigFileWithBatch(configFile, configFile.getFileName().toString(), true);
    }
}