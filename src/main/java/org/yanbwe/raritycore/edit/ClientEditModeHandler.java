package org.yanbwe.raritycore.edit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.DualCacheManager;
import org.yanbwe.raritycore.command.RarityCoreCommands;

import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.itemdatamatching.ItemDataConfigLoader;
import org.yanbwe.raritycore.network.EditModeRequestPayload;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端编辑模式处理器
 * 包含仅限客户端的方法，在专用服务端上不会加载此类
 * 与 EditModeManager 协作: EditModeManager 管理共享状态, 本类处理客户端的物品修改逻辑
 *
 * v13 新增: FullMatch 模式 — 捕获物品 Data Component 信息生成 Item Data 匹配配置文件
 */
@OnlyIn(Dist.CLIENT)
public class ClientEditModeHandler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 在编辑模式下修改物品稀有度
     * @param itemStack 要修改的物品堆
     * @return 是否成功修改
     */
    @SuppressWarnings("null")
    public static boolean modifyItemRarity(ItemStack itemStack) {
        if (!EditModeManager.isEditModeEnabled() || itemStack.isEmpty()) {
            return false;
        }

        Item item = itemStack.getItem();
        if (item == null) {
            return false;
        }

        // 获取物品 ID
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return false;
        }

        // v13: 根据编辑模式分派处理逻辑
        if (EditModeManager.isFullMatchMode()) {
            return handleFullMatchEdit(itemStack, item, itemId);
        } else {
            return handleNormalEdit(item, itemId);
        }
    }

    /**
     * Normal 模式: 将物品+稀有度写入 FinalRarity.json
     * rarity=0 视为"无稀有度"（配置读取器将 0 映射为默认值 1）
     */
    @SuppressWarnings("null")
    private static boolean handleNormalEdit(Item item, ResourceLocation itemId) {
        boolean deleteModeEnabled = EditModeManager.isDeleteModeEnabled();
        int currentRarity = EditModeManager.getCurrentRarity();

        Minecraft mc = Minecraft.getInstance();
        boolean isSingleplayer = mc.getSingleplayerServer() != null;

        if (!isSingleplayer) {
            // 多人游戏: 发送请求包到服务端
            EditModeRequestPayload payload = new EditModeRequestPayload(
                itemId,
                deleteModeEnabled ? 0 : currentRarity,
                deleteModeEnabled,
                "normal",
                "",
                false
            );
            PacketDistributor.sendToServer(payload);
        } else {
            // 单人游戏: 本地处理并保存配置
            if (deleteModeEnabled) {
                RarityRegistry.unregister(item, false);
                RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), 0);
            } else {
                RarityRegistry.register(item, currentRarity, false);
                RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), currentRarity);
            }
            RarityRegistry.syncRarityToClientsWithRetry();
        }

        // 立即刷新本地缓存
        forceClientCacheUpdate(item, deleteModeEnabled ? 0 : currentRarity);
        return true;
    }

    /**
     * FullMatch 模式: 捕获物品 Data Component 信息，生成 Item Data 匹配配置文件
     */
    private static boolean handleFullMatchEdit(ItemStack itemStack, Item item, ResourceLocation itemId) {
        int currentRarity = EditModeManager.getCurrentRarity();

        // 生成 FullMatch 配置 JSON
        JsonObject config = generateFullMatchConfig(itemStack, itemId.toString(), currentRarity);
        if (config == null) {
            RarityCore.LOGGER.warn("FullMatch: Failed to generate config for {}", itemId);
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        boolean isSingleplayer = mc.getSingleplayerServer() != null;

        if (!isSingleplayer) {
            // 多人游戏: 将生成的配置 JSON 发送到服务端保存
            String configJson = GSON.toJson(config);
            EditModeRequestPayload payload = new EditModeRequestPayload(
                itemId,
                currentRarity,
                false,
                "fullmatch",
                configJson,
                EditModeManager.isAutoReload()
            );
            PacketDistributor.sendToServer(payload);

            // 客户端缓存刷新
            forceClientCacheUpdate(item, currentRarity);
        } else {
            // 单人游戏: 本地保存配置
            saveFullMatchConfigLocally(config, itemId.toString());
            forceClientCacheUpdate(item, currentRarity);
        }

        return true;
    }

    /**
     * 生成 FullMatch 配置 JSON
     * 将物品栈序列化为 NBT，遍历所有 Data Component，
     * 根据类型 (使用 nbtTag.getId()) 生成 equals 或 contains 条件
     *
     * @param itemStack 物品栈
     * @param itemIdStr 物品 ID 字符串
     * @param rarity    目标稀有度
     * @return FullMatch 配置 JSON 对象，失败返回 null
     */
    public static JsonObject generateFullMatchConfig(ItemStack itemStack, String itemIdStr, int rarity) {
        try {
            // 序列化 ItemStack 为 NBT
            Tag rawTag = itemStack.save(RegistryAccess.EMPTY);
            if (!(rawTag instanceof CompoundTag itemNbt)) {
                RarityCore.LOGGER.warn("FullMatch: Failed to serialize item {}", itemIdStr);
                return null;
            }

            CompoundTag components = itemNbt.getCompound("components");
            if (components.isEmpty()) {
                RarityCore.LOGGER.warn("FullMatch: Item {} has no components", itemIdStr);
                // 即使没有组件也生成一个最小配置
            }

            boolean stringContains = EditModeManager.isStringContains();
            JsonArray conditions = new JsonArray();

            // 遍历所有组件
            for (String componentKey : components.getAllKeys()) {
                // 检查是否在忽略列表中
                if (EditModeManager.isComponentIgnored(componentKey)) {
                    continue;
                }

                Tag componentTag = components.get(componentKey);
                if (componentTag == null) {
                    continue;
                }

                // 使用 nbtTag.getId() 判断类型 (比 instanceof 更可靠，混淆后类名不变)
                int tagId = componentTag.getId();
                String path = "components." + componentKey;

                JsonObject condition = new JsonObject();
                condition.addProperty("path", path);
                condition.addProperty("description", componentKey);

                // stringContains=true 时, STRING(8)/COMPOUND(10)/LIST(9) 使用 contains 匹配
                // 数值类型始终使用 equals
                if (stringContains && (tagId == Tag.TAG_STRING || tagId == Tag.TAG_COMPOUND || tagId == Tag.TAG_LIST)) {
                    condition.addProperty("type", "contains");
                    condition.addProperty("substring", componentTag.getAsString());
                } else {
                    condition.addProperty("type", "equals");
                    // 对于数值类型，写入原始数值以便精确匹配
                    writeEqualsValue(condition, componentTag, tagId);
                }

                conditions.add(condition);
            }

            if (conditions.isEmpty()) {
                RarityCore.LOGGER.warn("FullMatch: No conditions generated for {} (all components ignored?)", itemIdStr);
                return null;
            }

            JsonObject config = new JsonObject();
            config.addProperty("item_id", itemIdStr);
            config.add("conditions", conditions);
            config.addProperty("rarity", rarity);
            config.addProperty("priority", 0);
            config.addProperty("enabled", true);
            config.addProperty("description", "Auto-generated by FullMatch edit mode");

            return config;
        } catch (Exception e) {
            RarityCore.LOGGER.error("FullMatch: Error generating config for {}", itemIdStr, e);
            return null;
        }
    }

    /**
     * 将 NBT 标签的值写入 equals 条件的 "value" 字段
     * 数值类型保留原始数值，字符串类型写入字符串
     */
    private static void writeEqualsValue(JsonObject condition, Tag tag, int tagId) {
        switch (tagId) {
            case Tag.TAG_BYTE:
                condition.addProperty("value", ((net.minecraft.nbt.ByteTag) tag).getAsByte());
                break;
            case Tag.TAG_SHORT:
                condition.addProperty("value", ((net.minecraft.nbt.ShortTag) tag).getAsShort());
                break;
            case Tag.TAG_INT:
                condition.addProperty("value", ((net.minecraft.nbt.IntTag) tag).getAsInt());
                break;
            case Tag.TAG_LONG:
                condition.addProperty("value", ((net.minecraft.nbt.LongTag) tag).getAsLong());
                break;
            case Tag.TAG_FLOAT:
                condition.addProperty("value", ((net.minecraft.nbt.FloatTag) tag).getAsFloat());
                break;
            case Tag.TAG_DOUBLE:
                condition.addProperty("value", ((net.minecraft.nbt.DoubleTag) tag).getAsDouble());
                break;
            default:
                // 对于非数值类型 (STRING, COMPOUND, LIST 等), 使用字符串表示
                condition.addProperty("value", tag.getAsString());
                break;
        }
    }

    /**
     * 单人游戏中本地保存 FullMatch 配置
     * 文件命名: edit_<物品id>_1.json (已存在则递增编号)
     * 物品 ID 中的冒号替换为下划线以兼容文件系统
     */
    public static void saveFullMatchConfigLocally(JsonObject config, String itemIdStr) {
        try {
            Path configDir = ConfigManager.getConfigDirPath().resolve("item_data_matches");
            Files.createDirectories(configDir);

            String safeName = itemIdStr.replace(':', '_');
            Path configFile = findNextAvailableFile(configDir, "edit_" + safeName);

            String jsonString = GSON.toJson(config);
            Files.writeString(configFile, jsonString);

            RarityCore.LOGGER.info("FullMatch: Saved config to {}", configFile.getFileName());

            // 重新加载配置使规则立即生效
            ItemDataConfigLoader.loadAllConfigs();

            // 触发缓存失效，确保客户端显示立即更新
            DualCacheManager.handleConfigReload();
        } catch (IOException e) {
            RarityCore.LOGGER.error("FullMatch: Failed to save config for {}", itemIdStr, e);
        }
    }

    /**
     * 查找下一个可用的文件名 (递增编号)
     * edit_minecraft_diamond_sword.json → edit_minecraft_diamond_sword_1.json → edit_minecraft_diamond_sword_2.json ...
     */
    private static Path findNextAvailableFile(Path configDir, String baseName) {
        // 首先尝试无编号的文件名
        Path candidate = configDir.resolve(baseName + ".json");
        if (!Files.exists(candidate)) {
            return candidate;
        }
        // 递增编号
        for (int i = 1; i <= 999; i++) {
            candidate = configDir.resolve(baseName + "_" + i + ".json");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        // 极端情况: 使用时间戳
        return configDir.resolve(baseName + "_" + System.currentTimeMillis() + ".json");
    }

    /**
     * 强制更新客户端本地缓存
     * @param item   要更新的物品
     * @param rarity 新的稀有度等级
     */
    private static void forceClientCacheUpdate(Item item, int rarity) {
        try {
            ItemStack itemStack = new ItemStack(item);
            DualCacheManager.updateIdCache(itemStack, rarity > 0 ? rarity : null);
        } catch (Exception e) {
            // 静默失败, 等待网络同步后自动更新
        }
    }
}
