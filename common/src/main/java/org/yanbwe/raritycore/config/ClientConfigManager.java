package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.JsonPerformanceOptimizer;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 客户端配置管理器
 * 负责管理客户端相关的配置
 *
 * <p>V14 起仅持有 {@code client.json} 中的两个开关：
 * {@code enableCacheSystem} 与 {@code enableSophisticatedCoreAdapter}。
 * 其余旧视觉配置全部迁移至 {@link RarityStyleConfigManager}（RarityStyle.json）。
 */
public class ClientConfigManager {

    private static final Gson GSON = JsonPerformanceOptimizer.getOptimizedGson();

    // 客户端配置
    private static boolean enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM; // 是否启用缓存系统
    private static boolean enableSophisticatedCoreAdapter = RarityConstants.DEFAULT_ENABLE_SOPHISTICATED_CORE_ADAPTER; // 是否启用精妙核心适配器

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
     * 初始化客户端配置
     * 先加载裁剪后的 client.json，再初始化 RarityStyle.json。
     */
    public static void initialize() {
        try {
            // 确保配置目录存在
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }

        // 加载客户端配置
        loadClientConfig();

        // 初始化 V14 样式配置（RarityStyle.json）
        RarityStyleConfigManager.initialize();
    }

    /**
     * 加载客户端配置
     */
    public static void loadClientConfig() {
        // 验证并更新配置文件
        JsonObject defaultConfig = ConfigValidator.createDefaultClientConfig();
        ConfigValidator.validateConfig(CLIENT_CONFIG_FILE, defaultConfig, "client");

        // 如果配置文件不存在,则创建一个默认的
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
                RarityCore.LOGGER.info("Loading client config from {}", CLIENT_CONFIG_FILE.getFileName());

                // 读取启用缓存系统设置
                if (jsonObject.has("enableCacheSystem")) {
                    enableCacheSystem = jsonObject.get("enableCacheSystem").getAsBoolean();
                } else {
                    // 如果配置项不存在，使用默认值
                    enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM;
                }

                // 读取精妙核心适配器设置
                if (jsonObject.has("enableSophisticatedCoreAdapter")) {
                    enableSophisticatedCoreAdapter = jsonObject.get("enableSophisticatedCoreAdapter").getAsBoolean();
                } else {
                    // 如果配置项不存在，使用默认值
                    enableSophisticatedCoreAdapter = RarityConstants.DEFAULT_ENABLE_SOPHISTICATED_CORE_ADAPTER;
                }

                RarityCore.LOGGER.info("Client config loaded successfully: enableCacheSystem={}, enableSophisticatedCoreAdapter={}",
                    enableCacheSystem, enableSophisticatedCoreAdapter);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading client config file, using default config: {}", CLIENT_CONFIG_FILE, e);
            // 出错时使用默认值
            enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM;
            enableSophisticatedCoreAdapter = RarityConstants.DEFAULT_ENABLE_SOPHISTICATED_CORE_ADAPTER;
            // 重新创建配置文件以恢复默认设置
            createDefaultClientConfig();
        }
    }

    /**
     * 创建默认客户端配置文件
     */
    private static void createDefaultClientConfig() {
        // 创建默认配置对象
        JsonObject configObject = ConfigValidator.createDefaultClientConfig();

        // 写入默认配置文件
        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(CLIENT_CONFIG_FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Created default client config file: {}", CLIENT_CONFIG_FILE);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create default client config file: {}", CLIENT_CONFIG_FILE, e);
        }
    }

    /**
     * 保存客户端配置到文件
     */
    public static void saveClientConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty("enableCacheSystem", enableCacheSystem);
        configObject.addProperty("enableSophisticatedCoreAdapter", enableSophisticatedCoreAdapter);

        // 写入配置文件
        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(CLIENT_CONFIG_FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Client config saved: enableCacheSystem={}, enableSophisticatedCoreAdapter={}",
                    enableCacheSystem, enableSophisticatedCoreAdapter);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot save client config file: {}", CLIENT_CONFIG_FILE, e);
        }
    }

    // ================================================================
    //  保留字段
    // ================================================================

    /**
     * 获取是否启用缓存系统
     */
    public static boolean isEnableCacheSystem() {
        return enableCacheSystem;
    }

    /**
     * 设置是否启用缓存系统
     */
    public static void setEnableCacheSystem(boolean enable) {
        enableCacheSystem = enable;
        // 通知缓存系统配置变更
        notifyCacheOfConfigChange();
    }

    /**
     * 通知缓存系统配置已变更
     */
    private static void notifyCacheOfConfigChange() {
        try {
            // 调用缓存失效监听器
            org.yanbwe.raritycore.client.CacheInvalidationListener.onClientConfigChange();
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to notify cache of config change", e);
        }
    }

    /**
     * 获取是否启用精妙核心适配器
     */
    public static boolean isEnableSophisticatedCoreAdapter() {
        return enableSophisticatedCoreAdapter;
    }

    /**
     * 设置是否启用精妙核心适配器
     */
    public static void setEnableSophisticatedCoreAdapter(boolean enable) {
        enableSophisticatedCoreAdapter = enable;
    }

    // ================================================================
    //  V14 旧方法兼容桥接（已由 RarityStyleConfigManager 接管）
    // ================================================================

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#isBorderEnabled()} 接管。
     */
    @Deprecated
    public static boolean isEnableItemBorderRendering() {
        return RarityStyleConfigManager.isBorderEnabled();
    }

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#setBorderEnabled(boolean)} 接管。
     */
    @Deprecated
    public static void setEnableItemBorderRendering(boolean enable) {
        RarityStyleConfigManager.setBorderEnabled(enable);
    }

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#getBorderStyle(int)} 接管。
     */
    @Deprecated
    public static int getItemBorderStyle() {
        return RarityStyleConfigManager.getBorderStyle(RarityConstants.MIN_RARITY);
    }

    /**
     * @deprecated 旧全局样式桥接，当前仅委托等级 1 的边框样式。
     */
    @Deprecated
    public static void setItemBorderStyle(int style) {
        RarityStyleConfigManager.setBorderStyle(RarityConstants.MIN_RARITY, style);
    }

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#isBorderUseTexture(int)} 接管。
     */
    @Deprecated
    public static boolean isUseTextureBorder() {
        return RarityStyleConfigManager.isBorderUseTexture(RarityConstants.MIN_RARITY);
    }

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#setAllBorderUseTexture(boolean)} 接管。
     */
    @Deprecated
    public static void setUseTextureBorder(boolean useTexture) {
        RarityStyleConfigManager.setAllBorderUseTexture(useTexture);
    }

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#isNameColorEnabled(int)} 接管（桥接默认等级 1）。
     */
    @Deprecated
    public static boolean isEnableItemNameColor() {
        return RarityStyleConfigManager.isNameColorEnabled(RarityConstants.MIN_RARITY);
    }

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#setItemNameColorEnabled(int, boolean)} 接管，
     * 桥接默认等级 1 的 itemNameColor 写入。
     */
    @Deprecated
    public static void setEnableItemNameColor(boolean enable) {
        RarityStyleConfigManager.setItemNameColorEnabled(RarityConstants.MIN_RARITY, enable);
    }

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#isTooltipColorEnabled()} 接管。
     */
    @Deprecated
    public static boolean isEnableTooltipColor() {
        return RarityStyleConfigManager.isTooltipColorEnabled();
    }

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#setTooltipColorEnabled(boolean)} 接管。
     */
    @Deprecated
    public static void setEnableTooltipColor(boolean enable) {
        RarityStyleConfigManager.setTooltipColorEnabled(enable);
    }

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#isTooltipEnabled()} 接管。
     */
    @Deprecated
    public static boolean isEnableTooltipInsert() {
        return RarityStyleConfigManager.isTooltipEnabled();
    }

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#setTooltipEnabled(boolean)} 接管。
     */
    @Deprecated
    public static void setEnableTooltipInsert(boolean enable) {
        RarityStyleConfigManager.setTooltipEnabled(enable);
    }

    /**
     * @deprecated 由 {@link RarityStyleConfigManager#isNoRaritySkip()} 接管。
     */
    @Deprecated
    public static boolean isSkipUnconfiguredItems() {
        return RarityStyleConfigManager.isNoRaritySkip();
    }
}
