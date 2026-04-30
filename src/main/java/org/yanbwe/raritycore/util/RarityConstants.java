package org.yanbwe.raritycore.util;

/**
 * 稀有度常量类
 * 定义所有稀有度相关的常量值
 */
public class RarityConstants {
    
    // 稀有度等级常量
    public static final int RARITY_COMMON = 1;      // 普通
    public static final int RARITY_UNCOMMON = 2;    // 稀有
    public static final int RARITY_RARE = 3;        // 罕见
    public static final int RARITY_EPIC = 4;        // 史诗
    public static final int RARITY_LEGENDARY = 5;   // 传说
    public static final int RARITY_MYTHICAL = 6;    // 神话
    public static final int RARITY_UNIQUE = 7;      // 唯一
    
    // 配置文件相关常量
    public static final String CONFIG_DIR_PARENT = "config";
    public static final String CONFIG_DIR_NAME = "raritycore";
    public static final String CLIENT_CONFIG_FILE_NAME = "client.json";
    public static final String SERVER_CONFIG_FILE_NAME = "server.json";
    public static final String FINAL_RARITY_FILE_NAME = "FinalRarity.json";
    public static final String FINAL_RARITY_CONFIG_FOLDER_NAME = "FinalRarityConfig";
    
    // 验证范围
    public static final int MIN_RARITY = 1;
    public static final int MAX_RARITY = 7;
    
    // 默认值
    public static final boolean DEFAULT_ENABLE_ITEM_BORDER_RENDERING = true;
    public static final int DEFAULT_ITEM_BORDER_STYLE = 1; // 0为空心,1为实心
    public static final boolean DEFAULT_ENABLE_ITEM_NAME_COLOR = true; // 默认启用物品名称变色
    public static final boolean DEFAULT_ENABLE_TOOLTIP_COLOR = true; // 默认启用工具提示变色
    public static final boolean DEFAULT_CHECK_VANILLA_RARITY = true; // 默认检查原版稀有度
    public static final boolean DEFAULT_SKIP_UNCONFIGURED_ITEMS = false; // 默认不禁用未配置物品的渲染
    public static final boolean DEFAULT_ENABLE_TOOLTIP_INSERT = true; // 默认启用工具提示插入
    
    // 星星显示相关默认值
    public static final boolean DEFAULT_ENABLE_STAR_DISPLAY = true; // 默认启用星星显示
    public static final String DEFAULT_STAR_EMOJI = "⭐"; // 默认星星emoji
    
    // 新星星显示系统常量
    public static final String DEFAULT_STAR_MODE = "repeat";
    public static final String DEFAULT_REPEAT_CHARACTER = "⭐"; // 默认重复字符
    
    // 星星显示自定义字符串默认值
    public static final String[] DEFAULT_CUSTOM_STRINGS = {
        "·",
        "••",
        "●●●",
        "◆◆◆◆",
        "◇◇◇◇◇",
        "◈◈◈◈◈◈",
        "✦✦✦✦✦✦✦",
        "✦✦✦✦✦✦✦✦"
    };
    
    // 纹理相关常量
    public static final String BORDER_TEXTURE_PATH = "raritycore:textures/border/";
    public static final String TEXTURE_SUFFIX = ".png";
    public static final int TEXTURE_WIDTH = 16;
    public static final int TEXTURE_HEIGHT = 16;
    public static final boolean DEFAULT_USE_TEXTURE_BORDER = true; // 默认使用纹理边框
    
    // 缓存系统相关常量
    public static final boolean DEFAULT_ENABLE_CACHE_SYSTEM = true; // 默认启用缓存系统
    
    // 精妙核心适配器相关常量
    public static final boolean DEFAULT_ENABLE_SOPHISTICATED_CORE_ADAPTER = true; // 默认启用精妙核心适配器
    
    // 服务端配置默认值
    public static final boolean DEFAULT_CHECK_APOTHEOSIS_RARITY = true; // 默认启用神化模组稀有度检查
    public static final boolean DEFAULT_ENABLE_GET_RARITY_WARNING = true; // 默认启用 getRarity() 可用性警告
}