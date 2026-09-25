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
 * 仅保留缓存系统总开关与铁魔法联动显示开关；其余视觉表现配置已迁移至 RarityStyleConfigManager
 *
 * <p><b>本文件是纯粹的客户端显示配置。</b>它位于 {@code config/raritycore/client.json}，
 * 而该文件在服务端与每个玩家客户端上各存一份、互不相同。因此这里的开关只能影响
 * <b>本地显示</b>，不得参与稀有度解析——否则服务端与客户端会算出不同稀有度且无法对齐。
 * {@code enableIronSpellsAdapter} 正是按此语义处理的：解析链两端一致地解析铁魔法物品，
 * 关闭该开关仅表示本地不显示（见 {@link org.yanbwe.raritycore.client.IronSpellsDisplaySwitch}）。</p>
 */
public class ClientConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM;

    /**
     * 铁魔法（Iron's Spellbooks）稀有度联动的<b>客户端显示开关</b>。
     * 关闭后仅本地不再显示法术等级派生的稀有度（边框/提示/名称颜色），
     * 解析链结果不变，因此不会与服务端产生分歧。
     *
     * @see org.yanbwe.raritycore.client.IronSpellsDisplaySwitch
     */
    private static boolean enableIronSpellsAdapter = RarityConstants.DEFAULT_ENABLE_IRON_SPELLS_ADAPTER;

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
                if (jsonObject != null && jsonObject.has("enableIronSpellsAdapter")) {
                    enableIronSpellsAdapter = jsonObject.get("enableIronSpellsAdapter").getAsBoolean();
                } else {
                    enableIronSpellsAdapter = RarityConstants.DEFAULT_ENABLE_IRON_SPELLS_ADAPTER;
                }

                // 自愈：旧版本生成的 client.json 可能缺键，或键曾被旧版迁移逻辑误删。
                // 这里把本管理器负责的键补进文件，保留文件中其余的既有键，避免用户手动添加的
                // 配置项因键缺失而静默回退到默认值（表现为"改了配置不生效"）。
                JsonObject healed = new JsonObject();
                healed.addProperty("enableCacheSystem", enableCacheSystem);
                healed.addProperty("enableIronSpellsAdapter", enableIronSpellsAdapter);
                int addedKeys = writeMissingKnownKeys(healed);
                if (addedKeys > 0) {
                    RarityCore.LOGGER.warn("client.json 缺少 {} 个配置项并已自动补入（原文件其余内容保留）: {}", addedKeys, CLIENT_CONFIG_FILE);
                }
            }
            RarityCore.LOGGER.info("Client config loaded: enableCacheSystem={}, enableIronSpellsAdapter={}", enableCacheSystem, enableIronSpellsAdapter);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error loading client config, using defaults", e);
            enableCacheSystem = RarityConstants.DEFAULT_ENABLE_CACHE_SYSTEM;
            enableIronSpellsAdapter = RarityConstants.DEFAULT_ENABLE_IRON_SPELLS_ADAPTER;
            createDefaultClientConfig();
        }
    }

    private static void createDefaultClientConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty("enableCacheSystem", enableCacheSystem);
        configObject.addProperty("enableIronSpellsAdapter", enableIronSpellsAdapter);
        try {
            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(CLIENT_CONFIG_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(configObject, writer);
                RarityCore.LOGGER.info("Created default client config file: {}", CLIENT_CONFIG_FILE);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Cannot create default client config file: {}", CLIENT_CONFIG_FILE, e);
        }
    }

    /**
     * 把已知配置项中缺失的键补写进 client.json，保留文件中其余既有键
     * 与 {@link #createDefaultClientConfig()} 不同：本方法不重建文件，
     * 只做"缺什么补什么"，因此用户自定义键与既有配置值不会被丢弃
     *
     * @param knownConfig 已知配置项（键 + 缺失时的回退值）
     * @return 实际补入的键数量，未发生写入时返回 0
     */
    private static int writeMissingKnownKeys(JsonObject knownConfig) {
        try {
            JsonObject existing = new JsonObject();
            if (Files.exists(CLIENT_CONFIG_FILE)) {
                try (BufferedReader reader = Files.newBufferedReader(CLIENT_CONFIG_FILE)) {
                    JsonObject loaded = GSON.fromJson(reader, JsonObject.class);
                    if (loaded != null) {
                        existing = loaded;
                    }
                }
            }

            int added = 0;
            for (String key : knownConfig.keySet()) {
                if (!existing.has(key)) {
                    existing.add(key, knownConfig.get(key));
                    added++;
                }
            }

            if (added > 0) {
                try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(CLIENT_CONFIG_FILE), StandardCharsets.UTF_8)) {
                    GSON.toJson(existing, writer);
                }
            }
            return added;
        } catch (Exception e) {
            RarityCore.LOGGER.error("Cannot sync known keys into client config: {}", CLIENT_CONFIG_FILE, e);
            return 0;
        }
    }

    public static void saveClientConfig() {
        JsonObject configObject = new JsonObject();
        configObject.addProperty("enableCacheSystem", enableCacheSystem);
        configObject.addProperty("enableIronSpellsAdapter", enableIronSpellsAdapter);
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

    /** 铁魔法联动的客户端显示开关是否开启（默认开启） */
    public static boolean isEnableIronSpellsAdapter() {
        return enableIronSpellsAdapter;
    }

    /**
     * 设置铁魔法联动的客户端显示开关。
     * 仅影响本地显示；解析链不受影响，故不会导致服务端/客户端稀有度分歧。
     */
    public static void setEnableIronSpellsAdapter(boolean enable) {
        enableIronSpellsAdapter = enable;
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
        return RarityStyleConfigManager.isItemNameColorEnabled(RarityConstants.MIN_RARITY);
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
