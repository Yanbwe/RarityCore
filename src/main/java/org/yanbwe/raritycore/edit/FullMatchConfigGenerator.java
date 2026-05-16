package org.yanbwe.raritycore.edit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.itemdatamatching.ItemDataConfigLoader;
import org.yanbwe.raritycore.itemdatamatching.SimpleConfigValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * FULLMATCH 模式的服务端配置生成器。
 *
 * <p>当编辑模式为 {@code FULLMATCH} 时，由 {@link org.yanbwe.raritycore.network.EditModeRequestPayload}
 * 的服务端处理器调用此生成器，为指定物品创建 Item Data 匹配配置文件并持久化到磁盘。</p>
 *
 * <p>生成的 JSON 格式符合 {@link SimpleConfigValidator#isValidConfig} 与
 * {@link org.yanbwe.raritycore.itemdatamatching.ItemDataMatchRule} 的要求：</p>
 * <pre>
 * {
 *   "item_id": "namespace:path",
 *   "rarity": 5,
 *   "conditions": [
 *     { "type": "contains", "path": "minecraft:custom_name", "substring": "..." },
 *     { "type": "equals",  "path": "custom_data.ignore", "value": "..." }
 *   ],
 *   "auto_reload": true
 * }
 * </pre>
 *
 * <p>文件写入后立即调用 {@link ItemDataConfigLoader#loadAllConfigs()} 使新规则即时生效，
 * 无需重启服务端或手动执行 reload 命令。</p>
 *
 * <p><b>线程安全说明：</b>此方法在服务端网络线程中通过
 * {@code IPayloadContext#enqueueWork} 调用，已有同步保护。
 * 文件系统写入依赖于操作系统级别的原子性保证。</p>
 */
public final class FullMatchConfigGenerator {

    /** Gson 实例，启用 pretty printing 以生成可读 JSON。 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FullMatchConfigGenerator() {
        // 工具类，禁止实例化
    }

    /**
     * 在服务端将客户端预生成的配置 JSON 写入文件系统并触发热重载。
     *
     * @param itemId     目标物品的注册表 ID
     * @param rarity     稀有度等级
     * @param configJson 客户端预生成的完整配置 JSON 字符串
     */
    public static void generateOnServer(Identifier itemId, int rarity, String configJson) {
        if (itemId == null || configJson == null || configJson.isEmpty()) {
            RarityCore.LOGGER.warn("FullMatchConfigGenerator: invalid input (itemId={}, configJson present={})",
                itemId, configJson != null && !configJson.isEmpty());
            return;
        }

        // 解析 JSON
        JsonObject config;
        try {
            config = GSON.fromJson(configJson, JsonObject.class);
        } catch (Exception e) {
            RarityCore.LOGGER.warn("FullMatchConfigGenerator: failed to parse config JSON: {}", e.getMessage());
            return;
        }

        // 验证
        if (!SimpleConfigValidator.isValidConfig(config)) {
            RarityCore.LOGGER.warn("FullMatchConfigGenerator: config failed validation for item {}", itemId);
            return;
        }

        // 写入文件并触发热重载
        writeConfigAndReload(itemId, config);
    }

    /**
     * 将配置 JSON 写入文件系统并触发热重载。
     *
     * <p>文件路径为 {@code config/raritycore/item_data_matches/edit_<itemPath>_N.json}，
     * 其中 N 从 1 开始递增，确保不覆盖已有文件。
     * 父目录不存在时自动创建。</p>
     *
     * @param itemId 物品标识符（用于构造文件名）
     * @param config 要写入的配置 JSON 对象（已通过验证）
     */
    private static void writeConfigAndReload(Identifier itemId, JsonObject config) {
        Path outputPath = resolveOutputPath(itemId);

        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, GSON.toJson(config));
            RarityCore.LOGGER.info("FullMatchConfigGenerator: wrote config for item {} to {}",
                itemId, outputPath.getFileName());

            // 触发热重载使新规则即时生效
            ItemDataConfigLoader.loadAllConfigs();
            RarityCore.LOGGER.info("FullMatchConfigGenerator: reloaded all configs, new rule for {} is now active",
                itemId);

        } catch (IOException e) {
            RarityCore.LOGGER.warn("FullMatchConfigGenerator: failed to write config for item {}: {}",
                itemId, e.getMessage());
        }
    }

    /**
     * 为目标物品解析输出文件路径。
     *
     * <p>文件名格式：{@code edit_<itemPath>_<counter>.json}。
     * 使用 {@link Identifier#getPath()} 提取路径部分，
     * 并将任何 {@code :} 字符替换为 {@code _} 以防止平台文件系统冲突。
     * 计数器从 1 开始递增，直到找到一个不存在的文件名。</p>
     *
     * <p>目录路径：{@code config/raritycore/item_data_matches/}</p>
     *
     * @param itemId 物品标识符
     * @return 可写入的目标文件路径（文件尚未创建）
     */
    private static Path resolveOutputPath(Identifier itemId) {
        Path dir = ConfigManager.getConfigDirPath().resolve("item_data_matches");

        // 安全化文件名：提取 path 部分并将 : 替换为 _
        String safeName = itemId.getPath().replace(':', '_');
        String baseName = "edit_" + safeName;

        // 递增计数器避免覆盖已有文件
        int counter = 1;
        Path candidate;
        do {
            candidate = dir.resolve(baseName + "_" + counter + ".json");
            counter++;
        } while (Files.exists(candidate));

        return candidate;
    }
}
