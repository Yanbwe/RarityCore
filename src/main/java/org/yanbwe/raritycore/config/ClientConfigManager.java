package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.client.CacheInvalidationListener;
import org.yanbwe.raritycore.util.JsonPerformanceOptimizer;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 客户端配置管理器
 * 负责管理客户端相关的配置（已精简，视觉配置迁移至 RarityStyleConfigManager）
 */
public class ClientConfigManager {

    private static final Gson GSON = JsonPerformanceOptimizer.getOptimizedGson();

    // 客户端配置
    private static boolean enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM;
    private static boolean enableSophisticatedCoreAdapter = RarityConstants.DEFAULT_ENABLE_SOPHISTICATED_CORE_ADAPTER;
    private static boolean enableIronSpellsAdapter = RarityConstants.DEFAULT_ENABLE_IRON_SPELLS_ADAPTER;

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
     */
    public static void initialize() {
        try {
            // 确保配置目录存在
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }

        // 加载 client.json（仅 enableCacheSystem + enableSophisticatedCoreAdapter + enableIronSpellsAdapter）
        loadClientConfig();
    }

    /**
     * 加载客户端配置
     */
    public static void loadClientConfig() {
        // 验证并更新配置文件
        JsonObject defaultConfig = ConfigValidator.createDefaultClientConfig();
        ConfigValidator.validateConfig(CLIENT_CONFIG_FILE, defaultConfig, "client");

        // 如果配置文件不存在，则创建一个默认的
        if (!Files.exists(CLIENT_CONFIG_FILE)) {
            createDefaultClientConfig();
        }

        // 读取并加载配置文件
        loadClientConfigFromFile();
    }

    /** V14 需裁剪的旧配置键集合 */
    private static final Set<String> OBSOLETE_CLIENT_KEYS = Set.of(
        "enableItemBorderRendering", "itemBorderStyle", "useTextureBorder",
        "enableItemNameColor", "enableTooltipColor", "enableTooltipInsert",
        "skipUnconfiguredItems", "starDisplay"
    );

    /**
     * 从文件加载客户端配置。
     * 自动检测并裁剪掉旧版配置项，仅保留 enableCacheSystem、enableSophisticatedCoreAdapter 与 enableIronSpellsAdapter。
     */
    private static void loadClientConfigFromFile() {
        try (BufferedReader reader = Files.newBufferedReader(CLIENT_CONFIG_FILE)) {
            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);

            if (jsonObject != null) {
                boolean hasObsoleteKeys = false;
                for (String key : OBSOLETE_CLIENT_KEYS) {
                    if (jsonObject.has(key)) {
                        hasObsoleteKeys = true;
                        break;
                    }
                }

                // 读取缓存系统开关
                if (jsonObject.has("enableCacheSystem")) {
                    enableCacheSystem = jsonObject.get("enableCacheSystem").getAsBoolean();
                } else {
                    enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM;
                }

                // 读取 SophisticatedCore 适配开关
                if (jsonObject.has("enableSophisticatedCoreAdapter")) {
                    enableSophisticatedCoreAdapter = jsonObject.get("enableSophisticatedCoreAdapter").getAsBoolean();
                } else {
                    enableSophisticatedCoreAdapter = RarityConstants.DEFAULT_ENABLE_SOPHISTICATED_CORE_ADAPTER;
                }

                // 读取 Iron's Spells 适配开关
                if (jsonObject.has("enableIronSpellsAdapter")) {
                    enableIronSpellsAdapter = jsonObject.get("enableIronSpellsAdapter").getAsBoolean();
                } else {
                    enableIronSpellsAdapter = RarityConstants.DEFAULT_ENABLE_IRON_SPELLS_ADAPTER;
                }

                // 检测到旧版配置项存在，自动裁剪并重新保存
                if (hasObsoleteKeys) {
                    RarityCore.LOGGER.info("Detected obsolete keys in client.json, trimming...");
                    saveClientConfig();
                }

                RarityCore.LOGGER.info("Client config loaded: enableCacheSystem={}, enableSophisticatedCoreAdapter={}, enableIronSpellsAdapter={}",
                    enableCacheSystem, enableSophisticatedCoreAdapter, enableIronSpellsAdapter);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading client config file, using defaults: {}", CLIENT_CONFIG_FILE, e);
            enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM;
            enableSophisticatedCoreAdapter = RarityConstants.DEFAULT_ENABLE_SOPHISTICATED_CORE_ADAPTER;
            enableIronSpellsAdapter = RarityConstants.DEFAULT_ENABLE_IRON_SPELLS_ADAPTER;
            createDefaultClientConfig();
        }
    }

    /**
     * 创建默认客户端配置文件
     */
    private static void createDefaultClientConfig() {
        JsonObject configObject = ConfigValidator.createDefaultClientConfig();

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
     * 保存客户端配置到文件（仅保存当前有效的三项配置）
     */
    public static void saveClientConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty("enableCacheSystem", enableCacheSystem);
        configObject.addProperty("enableSophisticatedCoreAdapter", enableSophisticatedCoreAdapter);
        configObject.addProperty("enableIronSpellsAdapter", enableIronSpellsAdapter);

        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(CLIENT_CONFIG_FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Client config saved: enableCacheSystem={}, enableSophisticatedCoreAdapter={}, enableIronSpellsAdapter={}",
                    enableCacheSystem, enableSophisticatedCoreAdapter, enableIronSpellsAdapter);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot save client config file: {}", CLIENT_CONFIG_FILE, e);
        }
    }

    // ================================================================
    //  当前有效配置项的 getter / setter
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
        notifyCacheOfConfigChange();
    }

    /**
     * 获取是否启用 SophisticatedCore 适配
     */
    public static boolean isEnableSophisticatedCoreAdapter() {
        return enableSophisticatedCoreAdapter;
    }

    /**
     * 设置是否启用 SophisticatedCore 适配
     */
    public static void setEnableSophisticatedCoreAdapter(boolean enable) {
        enableSophisticatedCoreAdapter = enable;
    }

    /**
     * 获取是否启用 Iron's Spells 适配
     */
    public static boolean isEnableIronSpellsAdapter() {
        return enableIronSpellsAdapter;
    }

    /**
     * 设置是否启用 Iron's Spells 适配
     */
    public static void setEnableIronSpellsAdapter(boolean enable) {
        enableIronSpellsAdapter = enable;
        notifyCacheOfConfigChange();
    }

    // ================================================================
    //  @Deprecated 委托方法（委托到 RarityStyleConfigManager）
    // ================================================================

    /**
     * @deprecated 使用 {@link RarityStyleConfigManager#isBorderEnabled()}
     */
    @Deprecated
    public static boolean isEnableItemBorderRendering() {
        return RarityStyleConfigManager.getInstance().isBorderEnabled();
    }

    /**
     * @deprecated 边框样式由 RarityStyle.json 逐级管理，此处固定返回 1（实心）
     */
    @Deprecated
    public static int getItemBorderStyle() {
        return 1;
    }

    /**
     * @deprecated 纹理边框由 RarityStyle.json 管理，此处固定返回 true
     */
    @Deprecated
    public static boolean isUseTextureBorder() {
        return true;
    }

    /**
     * @deprecated 物品名称颜色待后续实现，暂返回 true
     */
    @Deprecated
    public static boolean isEnableItemNameColor() {
        return true;
    }

    /**
     * @deprecated 使用 {@link RarityStyleConfigManager#isTooltipColorEnabled()}
     */
    @Deprecated
    public static boolean isEnableTooltipColor() {
        return RarityStyleConfigManager.getInstance().isTooltipColorEnabled();
    }

    /**
     * @deprecated 使用 {@link RarityStyleConfigManager#isTooltipEnabled()}
     */
    @Deprecated
    public static boolean isEnableTooltipInsert() {
        return RarityStyleConfigManager.getInstance().isTooltipEnabled();
    }

    /**
     * @deprecated 使用 {@link RarityStyleConfigManager#isNoRaritySkip()}
     */
    @Deprecated
    public static boolean isSkipUnconfiguredItems() {
        return RarityStyleConfigManager.getInstance().isNoRaritySkip();
    }

    // ================================================================
    //  内部辅助
    // ================================================================

    /**
     * 通知缓存系统配置已变更
     */
    private static void notifyCacheOfConfigChange() {
        try {
            CacheInvalidationListener.onClientConfigChange();
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to notify cache of config change", e);
        }
    }
}
