package org.yanbwe.raritycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 客户端配置管理器（V14 裁剪版）
 * 仅保留缓存系统总开关；其余视觉表现配置已迁移至 RarityStyleConfigManager
 */
public class ClientConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM;

    private static final Path CONFIG_DIR = Paths.get(RarityConstants.CONFIG_DIR_PARENT).resolve(RarityConstants.CONFIG_DIR_NAME);
    private static final Path CLIENT_CONFIG_FILE = CONFIG_DIR.resolve(RarityConstants.CLIENT_CONFIG_FILE_NAME);

    public static Path getClientConfigPath() {
        return CLIENT_CONFIG_FILE;
    }

    public static void initialize() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot create config directory: {}", CONFIG_DIR, e);
            return;
        }
        loadClientConfig();
    }

    public static void loadClientConfig() {
        try {
            if (!Files.exists(CLIENT_CONFIG_FILE)) {
                createDefaultClientConfig();
            }
            try (BufferedReader reader = Files.newBufferedReader(CLIENT_CONFIG_FILE)) {
                JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
                if (jsonObject != null && jsonObject.has("enableCacheSystem")) {
                    enableCacheSystem = jsonObject.get("enableCacheSystem").getAsBoolean();
                } else {
                    enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM;
                }
            }
            RarityCore.LOGGER.info("Client config loaded: enableCacheSystem={}", enableCacheSystem);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading client config, using defaults", e);
            enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM;
            createDefaultClientConfig();
        }
    }

    private static void createDefaultClientConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty("enableCacheSystem", enableCacheSystem);
        try {
            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(CLIENT_CONFIG_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Created default client config file: {}", CLIENT_CONFIG_FILE);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create default client config file: {}", CLIENT_CONFIG_FILE, e);
        }
    }

    public static void saveClientConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty("enableCacheSystem", enableCacheSystem);
        try {
            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(CLIENT_CONFIG_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(configObject, writer);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot save client config file: {}", CLIENT_CONFIG_FILE, e);
        }
    }

    public static boolean isEnableCacheSystem() {
        return enableCacheSystem;
    }

    public static void setEnableCacheSystem(boolean enable) {
        enableCacheSystem = enable;
        try {
            org.yanbwe.raritycore.client.CacheInvalidationListener.onClientConfigChange();
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to notify cache of config change", e);
        }
    }

    // ───────────────────────── 旧 API 兼容委托 ─────────────────────────
    // 以下方法代理到 RarityStyleConfigManager，保持外部调用与公共 API 不变

    @Deprecated
    public static boolean isEnableItemBorderRendering() {
        return RarityStyleConfigManager.isBorderEnabled();
    }

    @Deprecated
    public static boolean isEnableTooltipInsert() {
        return RarityStyleConfigManager.isTooltipEnabled();
    }

    @Deprecated
    public static boolean isEnableTooltipColor() {
        return RarityStyleConfigManager.isTooltipColorEnabled();
    }

    @Deprecated
    public static boolean isEnableItemNameColor() {
        return RarityStyleConfigManager.isItemNameColorEnabled(RarityConstants.RARITY_COMMON);
    }

    @Deprecated
    public static boolean isSkipUnconfiguredItems() {
        return RarityStyleConfigManager.getDefaultsNoRaritySkip();
    }

    @Deprecated
    public static boolean isUseTextureBorder() {
        return RarityStyleConfigManager.getDefaultUseTexture();
    }

    @Deprecated
    public static int getItemBorderStyle() {
        return RarityStyleConfigManager.getDefaultBorderStyle();
    }

    @Deprecated
    public static void setUseTextureBorder(boolean useTexture) {
        RarityStyleConfigManager.setDefaultUseTexture(useTexture);
    }

    @Deprecated
    public static void setEnableItemNameColor(boolean enable) {
        RarityStyleConfigManager.setDefaultItemNameColor(enable);
    }
}
