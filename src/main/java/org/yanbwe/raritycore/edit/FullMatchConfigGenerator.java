package org.yanbwe.raritycore.edit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.itemdatamatching.ItemDataConfigLoader;
import org.yanbwe.raritycore.itemdatamatching.SimpleConfigValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
     * 在服务端生成 FullMatch 配置并写入文件系统。
     *
     * <p>根据参数映射构建 Item Data 匹配配置 JSON，通过 {@link SimpleConfigValidator}
     * 验证后写入 {@code config/raritycore/item_data_matches/edit_<item>_N.json}，
     * 最后调用 {@link ItemDataConfigLoader#loadAllConfigs()} 触发热重载。</p>
     *
     * <p>参数映射支持以下 key：</p>
     * <ul>
     *   <li><b>stringContains</b> — 子字符串包含匹配条件，生成 {@code contains} 条件，
     *       path 固定为 {@code "minecraft:custom_name"}</li>
     *   <li><b>ignore</b> — {@code |} 分隔的多值，每个值生成一个 {@code equals} 条件，
     *       path 固定为 {@code "custom_data.ignore"}</li>
     *   <li><b>autoReload</b> — 布尔字符串（{@code "true"/"false"}），
     *       作为 {@code auto_reload} 元数据写入配置顶层</li>
     * </ul>
     *
     * @param itemId  目标物品的注册表 ID（如 {@code "minecraft:diamond_sword"}）
     * @param rarity  稀有度等级（1~7）
     * @param params  编辑参数映射，可为 null（视为空 Map）
     */
    public static void generateOnServer(Identifier itemId, int rarity, Map<String, String> params) {
        if (itemId == null) {
            RarityCore.LOGGER.warn("FullMatchConfigGenerator: itemId is null, skipping config generation");
            return;
        }

        // 构建配置 JSON
        JsonObject config = buildConfig(itemId, rarity, params);

        // 写入前通过 SimpleConfigValidator 验证
        if (!SimpleConfigValidator.isValidConfig(config)) {
            RarityCore.LOGGER.warn("FullMatchConfigGenerator: generated config failed validation, skipping write for item {}",
                itemId);
            return;
        }

        // 写入文件并触发热重载
        writeConfigAndReload(itemId, config);
    }

    /**
     * 根据物品 ID 与参数映射构建配置 JSON 对象。
     *
     * <p>所有条件均按 AND 逻辑组合（即物品必须满足所有条件才能匹配此规则）。
     * 若 params 中不包含任何条件参数，则 conditions 数组为空，
     * 但配置仍可通过 {@link SimpleConfigValidator#isValidConfig} 验证
     * （仅要求字段存在，不要求数组非空）。</p>
     *
     * @param itemId 物品标识符
     * @param rarity 稀有度等级
     * @param params 参数映射（可为 null）
     * @return 构建完成的配置 JSON 对象
     */
    static JsonObject buildConfig(Identifier itemId, int rarity, Map<String, String> params) {
        JsonObject config = new JsonObject();
        config.addProperty("item_id", itemId.toString());
        config.addProperty("rarity", rarity);

        Map<String, String> effectiveParams = params != null ? params : Map.of();

        // 构建条件数组
        JsonArray conditions = buildConditions(effectiveParams);
        config.add("conditions", conditions);

        // autoReload 作为元数据写入顶层
        String autoReload = effectiveParams.get("autoReload");
        if (autoReload != null && !autoReload.isBlank()) {
            config.addProperty("auto_reload", Boolean.parseBoolean(autoReload.trim()));
        }

        return config;
    }

    /**
     * 从参数映射构建条件 JSON 数组。
     *
     * <p>stringContains 参数生成 contains 条件，path 固定为
     * {@code "minecraft:custom_name"}，值写入 {@code substring} 字段。</p>
     *
     * <p>ignore 参数按 {@code |} 分割，每个非空片段生成一个 equals 条件，
     * path 固定为 {@code "custom_data.ignore"}。</p>
     *
     * @param params 有效的参数映射（已判空）
     * @return 条件 JSON 数组，可能为空数组
     */
    private static JsonArray buildConditions(Map<String, String> params) {
        JsonArray conditions = new JsonArray();

        // stringContains → contains 条件，path 固定为 minecraft:custom_name
        String stringContains = params.get("stringContains");
        if (stringContains != null && !stringContains.isBlank()) {
            JsonObject cond = new JsonObject();
            cond.addProperty("type", "contains");
            cond.addProperty("path", "minecraft:custom_name");
            cond.addProperty("substring", stringContains.trim());
            conditions.add(cond);
        }

        // ignore → | 分隔的多值，每个生成 equals 条件
        String ignore = params.get("ignore");
        if (ignore != null && !ignore.isBlank()) {
            for (String part : ignore.split("\\|")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    JsonObject cond = new JsonObject();
                    cond.addProperty("type", "equals");
                    cond.addProperty("path", "custom_data.ignore");
                    cond.addProperty("value", trimmed);
                    conditions.add(cond);
                }
            }
        }

        return conditions;
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
