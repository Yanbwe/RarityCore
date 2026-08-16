package org.yanbwe.raritycore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yanbwe.raritycore.util.RarityConstants;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一配置管理器
 * 管理所有配置文件的加载、保存和验证
 */
public class ConfigManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
    
    // 配置文件路径
    private static final Path CONFIG_DIR = Paths.get(RarityConstants.CONFIG_DIR_PARENT).resolve(RarityConstants.CONFIG_DIR_NAME);
    
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
            java.nio.file.Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }
        // ClientConfigManager.initialize() 由各版本的 RarityCore 主类调用，
        // 其内部会初始化 RarityStyleConfigManager（RarityStyle.json）并完成星星显示配置读取。
    }
    
    /**
     * 验证稀有度值是否有效
     */
    public static boolean isValidRarity(int rarity) {
        return rarity >= RarityConstants.MIN_RARITY && rarity <= RarityConstants.MAX_RARITY;
    }
}
