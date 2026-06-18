package org.yanbwe.raritycore.config;

import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityConstants;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一配置管理器
 * 管理所有配置文件的加载、保存和验证
 */
public class ConfigManager {
    
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
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }
        
        // 初始化星星显示配置（先设默认值，随后 ClientConfigManager 会加载真实配置覆盖）
        StarDisplayConfigManager.initialize();

        // 初始化客户端配置
        ClientConfigManager.initialize();
    }
    
    /**
     * 验证稀有度值是否有效
     */
    public static boolean isValidRarity(int rarity) {
        return rarity >= RarityConstants.MIN_RARITY && rarity <= RarityConstants.MAX_RARITY;
    }
}
