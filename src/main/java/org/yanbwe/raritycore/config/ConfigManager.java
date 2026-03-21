package org.yanbwe.raritycore.config;

import com.google.gson.JsonObject;
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
        
        // 初始化客户端配置
        ClientConfigManager.initialize();
        
        // 初始化星星显示配置
        StarDisplayConfigManager.initialize();
    }
    
    /**
     * 验证稀有度值是否有效
     */
    public static boolean isValidRarity(int rarity) {
        return rarity >= RarityConstants.MIN_RARITY && rarity <= RarityConstants.MAX_RARITY;
    }
    
    // ===== 客户端配置相关方法 (委托给 ClientConfigManager) =====
    
    /**
     * 获取客户端配置路径
     */
    public static Path getClientConfigPath() {
        return ClientConfigManager.getClientConfigPath();
    }
    
    /**
     * 加载客户端配置
     */
    public static void loadClientConfig() {
        ClientConfigManager.loadClientConfig();
    }
    
    /**
     * 保存客户端配置到文件
     */
    public static void saveClientConfig() {
        ClientConfigManager.saveClientConfig();
    }
    
    /**
     * 获取物品边框渲染开关
     */
    public static boolean isEnableItemBorderRendering() {
        return ClientConfigManager.isEnableItemBorderRendering();
    }
    
    /**
     * 设置物品边框渲染开关
     */
    public static void setEnableItemBorderRendering(boolean enable) {
        ClientConfigManager.setEnableItemBorderRendering(enable);
    }
    
    /**
     * 获取物品边框样式
     */
    public static int getItemBorderStyle() {
        return ClientConfigManager.getItemBorderStyle();
    }
    
    /**
     * 设置物品边框样式
     */
    public static void setItemBorderStyle(int style) {
        ClientConfigManager.setItemBorderStyle(style);
    }
    
    /**
     * 获取是否使用纹理边框
     */
    public static boolean isUseTextureBorder() {
        return ClientConfigManager.isUseTextureBorder();
    }
    
    /**
     * 设置是否使用纹理边框
     */
    public static void setUseTextureBorder(boolean useTexture) {
        ClientConfigManager.setUseTextureBorder(useTexture);
    }
    
    /**
     * 获取是否启用物品名称变色
     */
    public static boolean isEnableItemNameColor() {
        return ClientConfigManager.isEnableItemNameColor();
    }
    
    /**
     * 设置是否启用物品名称变色
     */
    public static void setEnableItemNameColor(boolean enable) {
        ClientConfigManager.setEnableItemNameColor(enable);
    }
    
    /**
     * 获取是否启用工具提示插入
     */
    public static boolean isEnableTooltipInsert() {
        return ClientConfigManager.isEnableTooltipInsert();
    }
    
    /**
     * 获取是否跳过未配置物品
     */
    public static boolean isSkipUnconfiguredItems() {
        return ClientConfigManager.isSkipUnconfiguredItems();
    }
    
    /**
     * 设置是否启用工具提示插入
     */
    public static void setEnableTooltipInsert(boolean enable) {
        ClientConfigManager.setEnableTooltipInsert(enable);
    }
    
    /**
     * 获取是否启用缓存系统
     */
    public static boolean isEnableCacheSystem() {
        return ClientConfigManager.isEnableCacheSystem();
    }
    
    /**
     * 设置是否启用缓存系统
     */
    public static void setEnableCacheSystem(boolean enable) {
        ClientConfigManager.setEnableCacheSystem(enable);
    }
    
    // ===== 星星显示配置相关方法 (委托给 StarDisplayConfigManager) =====
    
    /**
     * 加载星星显示配置
     */
    public static void loadStarDisplayConfig(JsonObject jsonObject) {
        StarDisplayConfigManager.loadStarDisplayConfig(jsonObject);
    }
    
    /**
     * 创建默认星星显示配置
     */
    public static JsonObject createDefaultStarDisplayConfig() {
        return StarDisplayConfigManager.createDefaultStarDisplayConfig();
    }
    
    /**
     * 获取是否启用星星显示
     */
    public static boolean isEnableStarDisplay() {
        return StarDisplayConfigManager.isEnableStarDisplay();
    }
    
    /**
     * 设置是否启用星星显示
     */
    public static void setEnableStarDisplay(boolean enable) {
        StarDisplayConfigManager.setEnableStarDisplay(enable);
    }
    
    /**
     * 获取星星显示模式
     */
    public static String getStarMode() {
        return StarDisplayConfigManager.getStarMode();
    }
    
    /**
     * 设置星星显示模式
     */
    public static void setStarMode(String mode) {
        StarDisplayConfigManager.setStarMode(mode);
    }
    
    /**
     * 获取重复模式字符
     */
    public static String getRepeatCharacter() {
        return StarDisplayConfigManager.getRepeatCharacter();
    }
    
    /**
     * 设置重复模式字符
     */
    public static void setRepeatCharacter(String character) {
        StarDisplayConfigManager.setRepeatCharacter(character);
    }
    
    /**
     * 获取自定义模式字符串映射
     */
    public static java.util.Map<Integer, String> getCustomStarStrings() {
        return StarDisplayConfigManager.getCustomStarStrings();
    }
    
    /**
     * 设置自定义模式字符串映射
     */
    public static void setCustomStarStrings(java.util.Map<Integer, String> strings) {
        StarDisplayConfigManager.setCustomStarStrings(strings);
    }
    
    /**
     * 获取指定稀有度的自定义字符串
     */
    public static String getCustomStarString(int rarity) {
        return StarDisplayConfigManager.getCustomStarString(rarity);
    }
    
    /**
     * 设置指定稀有度的自定义字符串
     */
    public static void setCustomStarString(int rarity, String string) {
        StarDisplayConfigManager.setCustomStarString(rarity, string);
    }
    
    /**
     * 获取特殊稀有度文本映射(大于 7 级)
     */
    public static java.util.Map<Integer, String> getCustomSpecialRarityTexts() {
        return StarDisplayConfigManager.getCustomSpecialRarityTexts();
    }
    
    /**
     * 获取指定稀有度的自定义特殊文本
     * @param rarity 稀有度等级(大于 7)
     * @return 自定义文本,如果没有配置则返回 null
     */
    public static String getCustomSpecialRarityText(int rarity) {
        return StarDisplayConfigManager.getCustomSpecialRarityText(rarity);
    }
    
    /**
     * 设置特殊稀有度文本映射
     */
    public static void setCustomSpecialRarityTexts(java.util.Map<Integer, String> texts) {
        StarDisplayConfigManager.setCustomSpecialRarityTexts(texts);
    }
}