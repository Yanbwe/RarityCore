package org.yanbwe.raritycore.edit;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.network.ChangeOperation;
import org.yanbwe.raritycore.network.DelayedSyncManager;
import org.yanbwe.raritycore.network.EditModeRequestPacket;
import org.yanbwe.raritycore.network.SyncBatchManager;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.*;

/**
 * 编辑模式管理器
 * 管理编辑模式状态、模式和参数
 * 支持 Normal 模式（写入 FinalRarity.json）和 FullMatch 模式（NBT 匹配配置）
 */
public class EditModeManager {

    /** 编辑模式枚举 */
    public enum EditMode {
        NORMAL,    // 普通模式：写入 FinalRarity.json
        FULLMATCH  // 全匹配模式：创建 NBT 匹配配置
    }

    // 编辑模式开关
    private static boolean editModeEnabled = false;

    // 当前编辑模式
    private static EditMode currentMode = EditMode.NORMAL;

    // 当前选中的稀有度等级 (≥0，0 表示"无稀有度")
    private static int currentRarity = 1;

    // FullMatch 模式参数
    private static boolean autoReload = false;
    private static String ignoreTags = "";
    private static boolean stringContains = true; // 字符串类型 NBT 是否用 contains 匹配

    // 可用的稀有度等级列表
    private static final List<Integer> AVAILABLE_RARITIES = new ArrayList<>();

    static {
        for (int i = 0; i <= 7; i++) {
            AVAILABLE_RARITIES.add(i);
        }
    }

    // ---- 模式控制 ----

    public static void setEditMode(boolean enabled) {
        editModeEnabled = enabled;
        if (enabled) {
            currentRarity = 1;
        }
    }

    public static boolean toggleEditMode() {
        editModeEnabled = !editModeEnabled;
        if (editModeEnabled) {
            currentRarity = 1;
        }
        return editModeEnabled;
    }

    public static boolean isEditModeEnabled() {
        return editModeEnabled;
    }

    public static void setMode(EditMode mode) {
        currentMode = mode;
    }

    public static EditMode getCurrentMode() {
        return currentMode;
    }

    // ---- 稀有度控制 ----

    public static void setRarity(int rarity) {
        if (rarity >= 0) {
            currentRarity = rarity;
        }
    }

    public static int getCurrentRarity() {
        return currentRarity;
    }

    public static void nextRarity() {
        if (!editModeEnabled) return;
        currentRarity++;
    }

    public static void previousRarity() {
        if (!editModeEnabled) return;
        if (currentRarity > 0) {
            currentRarity--;
        }
    }

    public static List<Integer> getAvailableRarities() {
        return new ArrayList<>(AVAILABLE_RARITIES);
    }

    // ---- FullMatch 参数 ----

    public static void setAutoReload(boolean value) {
        autoReload = value;
    }

    public static boolean isAutoReload() {
        return autoReload;
    }

    public static void setIgnoreTags(String tags) {
        ignoreTags = tags != null ? tags : "";
    }

    public static String getIgnoreTags() {
        return ignoreTags;
    }

    public static void setStringContains(boolean value) {
        stringContains = value;
    }

    public static boolean isStringContains() {
        return stringContains;
    }

    // ---- 兼容旧 API（删除模式已合并为 rarity=0）----

    @Deprecated
    public static boolean isDeleteModeEnabled() {
        return currentRarity == 0;
    }

    @Deprecated
    public static void setDeleteMode(boolean enabled) {
        if (enabled) currentRarity = 0;
    }

    @Deprecated
    public static boolean toggleDeleteMode() {
        if (!editModeEnabled) return false;
        currentRarity = (currentRarity == 0) ? 1 : 0;
        return currentRarity == 0;
    }

    // ---- 核心编辑逻辑 ----

    /**
     * 在编辑模式下修改物品稀有度
     */
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("null")
    public static boolean modifyItemRarity(ItemStack itemStack) {
        if (!editModeEnabled || itemStack.isEmpty()) return false;

        Item item = itemStack.getItem();
        if (item == null) return false;

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) return false;

        // TacZ 物品在 Normal 模式下走特殊处理
        if (currentMode == EditMode.NORMAL && org.yanbwe.raritycore.compat.tacz.TacZAdapter.isInitialized()) {
            org.yanbwe.raritycore.compat.tacz.TacZAdapter.TacZItemType tacZType =
                org.yanbwe.raritycore.compat.tacz.TacZAdapter.getTacZItemType(itemStack);
            if (tacZType != org.yanbwe.raritycore.compat.tacz.TacZAdapter.TacZItemType.NONE) {
                return handleTacZEdit(itemId, itemStack, item);
            }
        }

        if (currentMode == EditMode.FULLMATCH) {
            return handleFullMatchEdit(itemId, itemStack, item);
        } else {
            return handleNormalEdit(itemId, item);
        }
    }

    private static boolean handleNormalEdit(ResourceLocation itemId, Item item) {
        boolean isSingleplayer = Minecraft.getInstance().getSingleplayerServer() != null;
        if (!isSingleplayer) {
            EditModeRequestPacket packet = new EditModeRequestPacket(itemId, currentRarity, false,
                EditMode.NORMAL.ordinal(), autoReload, ignoreTags);
            EditModeRequestPacket.INSTANCE.sendToServer(packet);
        } else {
            handleNormalEditServer(itemId, item);
        }
        forceClientCacheUpdate(item, currentRarity);
        return true;
    }

    /** TacZ 物品编辑：写入 editTacZ_<itemId>.json */
    private static boolean handleTacZEdit(ResourceLocation itemId, ItemStack itemStack, Item item) {
        if (currentRarity == 0) return false;
        boolean isSingleplayer = Minecraft.getInstance().getSingleplayerServer() != null;
        if (!isSingleplayer) {
            // TODO: 多人 TacZ 支持（通过扩展包传送 NBT 信息）
            EditModeRequestPacket packet = new EditModeRequestPacket(itemId, currentRarity, false,
                EditMode.NORMAL.ordinal(), autoReload, ignoreTags);
            EditModeRequestPacket.INSTANCE.sendToServer(packet);
        } else {
            handleTacZEditServer(itemId, itemStack, item);
        }
        forceClientCacheUpdate(item, currentRarity);
        return true;
    }

    /** 服务端 TacZ 编辑处理 */
    public static void handleTacZEditServer(ResourceLocation itemId, ItemStack itemStack, Item item) {
        String nbtKey = org.yanbwe.raritycore.compat.tacz.TacZAdapter.getTacZNbtPath(itemStack);
        String nbtValue = org.yanbwe.raritycore.compat.tacz.TacZAdapter.getTacZNbtValue(itemStack);
        if (nbtKey == null || nbtValue == null) return;

        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("item_id", itemId.toString());
        root.addProperty("rarity", currentRarity);
        com.google.gson.JsonArray conditions = new com.google.gson.JsonArray();
        com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
        cond.addProperty("path", nbtKey);
        cond.addProperty("type", "equals");
        cond.addProperty("value", nbtValue);
        conditions.add(cond);
        root.add("conditions", conditions);

        String fileName = "editTacZ_" + itemId.getNamespace() + "_" + itemId.getPath();
        java.nio.file.Path nbtDir = org.yanbwe.raritycore.config.ConfigManager.getConfigDirPath().resolve("nbt_matches");
        try {
            java.nio.file.Files.createDirectories(nbtDir);
            java.nio.file.Path file = nbtDir.resolve(fileName + ".json");
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                java.nio.file.Files.newOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
            RarityCore.LOGGER.info("Created TacZ NBT config: {}", file);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to create TacZ NBT config", e);
        }

        // 使用增量同步，避免每次编辑都发送全量稀有度映射
        addEditChangeOperation(itemId, ChangeOperation.OperationType.UPDATE, currentRarity);
        scheduleIncrementalSync();
    }

    /** 服务端 Normal 模式处理（供网络包和单人游戏共用） */
    public static void handleNormalEditServer(ResourceLocation itemId, Item item) {
        if (currentRarity == 0) {
            RarityRegistry.unregister(item, false);
            addEditChangeOperation(itemId, ChangeOperation.OperationType.DELETE, null);
        } else {
            RarityRegistry.register(item, currentRarity, false);
            addEditChangeOperation(itemId, ChangeOperation.OperationType.UPDATE, currentRarity);
        }
        RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), currentRarity);
        scheduleIncrementalSync();
    }

    private static boolean handleFullMatchEdit(ResourceLocation itemId, ItemStack itemStack, Item item) {
        if (currentRarity == 0) return false; // FullMatch 模式不支持 rarity=0

        boolean isSingleplayer = Minecraft.getInstance().getSingleplayerServer() != null;

        if (!isSingleplayer) {
            EditModeRequestPacket packet = new EditModeRequestPacket(itemId, currentRarity, false,
                EditMode.FULLMATCH.ordinal(), autoReload, ignoreTags);
            EditModeRequestPacket.INSTANCE.sendToServer(packet);
        } else {
            handleFullMatchEditServer(itemId, itemStack, item);
            // FullMatch 模式触发 NBT 配置重载，使新规则立即生效
            org.yanbwe.raritycore.nbtmatching.NbtConfigLoader.loadAllConfigs();
        }
        return true;
    }

    /** 服务端 FullMatch 模式处理 */
    public static void handleFullMatchEditServer(ResourceLocation itemId, ItemStack itemStack, Item item) {
        if (itemStack == null || !itemStack.hasTag()) return;

        CompoundTag tag = itemStack.getTag();
        if (tag == null) return;

        // 解析忽略列表
        Set<String> ignoredSet = new HashSet<>();
        if (!ignoreTags.isEmpty()) {
            for (String part : ignoreTags.split("\\|")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) ignoredSet.add(trimmed);
            }
        }

        // 构建 NBT 匹配 JSON
        createFullMatchConfig(itemId, tag, ignoredSet);
        // 使用增量同步，避免每次编辑都发送全量稀有度映射
        addEditChangeOperation(itemId, ChangeOperation.OperationType.UPDATE, currentRarity);
        scheduleIncrementalSync();

        if (autoReload) {
            org.yanbwe.raritycore.nbtmatching.NbtConfigLoader.loadAllConfigs();
        }
    }

    /**
     * 创建 FullMatch NBT 匹配配置文件
     */
    private static void createFullMatchConfig(ResourceLocation itemId, CompoundTag tag, Set<String> ignoredSet) {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("item_id", itemId.toString());
        root.addProperty("rarity", currentRarity);

        com.google.gson.JsonArray conditions = new com.google.gson.JsonArray();

        for (String key : tag.getAllKeys()) {
            if (ignoredSet.contains(key)) continue;
            net.minecraft.nbt.Tag nbtTag = tag.get(key);
            conditions.add(buildCondition(key, nbtTag));
        }

        root.add("conditions", conditions);

        // 写入文件：edit_<itemId>_<N>.json
        String baseName = "edit_" + itemId.getNamespace() + "_" + itemId.getPath();
        java.nio.file.Path nbtDir = org.yanbwe.raritycore.config.ConfigManager.getConfigDirPath().resolve("nbt_matches");
        try {
            java.nio.file.Files.createDirectories(nbtDir);
            java.nio.file.Path file = findNextAvailableFile(nbtDir, baseName);
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                java.nio.file.Files.newOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
            RarityCore.LOGGER.info("Created FullMatch NBT config: {}", file);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to create FullMatch NBT config", e);
        }
    }

    private static com.google.gson.JsonObject buildCondition(String key, net.minecraft.nbt.Tag nbtTag) {
        com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
        cond.addProperty("path", key);

        int tagId = nbtTag.getId();
        // 字符串(8)、复合(10)、列表(9)在 stringContains=true 时用 contains 匹配
        boolean useContains = stringContains &&
            (tagId == net.minecraft.nbt.Tag.TAG_STRING
             || tagId == net.minecraft.nbt.Tag.TAG_COMPOUND
             || tagId == net.minecraft.nbt.Tag.TAG_LIST);

        if (useContains) {
            cond.addProperty("type", "contains");
            cond.addProperty("substring", nbtTag.getAsString());
        } else {
            cond.addProperty("type", "equals");
            cond.addProperty("value", nbtTag.getAsString());
        }

        return cond;
    }

    private static java.nio.file.Path findNextAvailableFile(java.nio.file.Path dir, String baseName) {
        for (int i = 1; i < 1000; i++) {
            java.nio.file.Path candidate = dir.resolve(baseName + "_" + i + ".json");
            if (!java.nio.file.Files.exists(candidate)) {
                return candidate;
            }
        }
        return dir.resolve(baseName + ".json");
    }

    @OnlyIn(Dist.CLIENT)
    private static void forceClientCacheUpdate(Item item, int rarity) {
        try {
            org.yanbwe.raritycore.cache.DualCacheManager.handleConfigReload();
            ItemStack stacking = new ItemStack(item);
            org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(stacking, rarity);
        } catch (Exception ignored) {}
    }

    /**
     * 添加编辑模式的变更操作到批量同步队列
     * 替代原先的每次全量同步，改为累积后通过 DelayedSyncManager 延迟批量发送
     * @param itemId 物品资源位置
     * @param type 操作类型（ADD/UPDATE/DELETE）
     * @param rarity 稀有度等级（DELETE 操作时可为 null）
     */
    private static void addEditChangeOperation(ResourceLocation itemId, ChangeOperation.OperationType type, Integer rarity) {
        SyncBatchManager.addOperation(new ChangeOperation(type, itemId, rarity), SyncBatchManager.SyncPriority.NORMAL);
    }

    /**
     * 调度增量同步
     * 利用 DelayedSyncManager 将短时间内的多次编辑合并为单个 IncrementalSyncPacket，
     * 避免每次编辑都向所有玩家发送全量稀有度映射
     */
    private static void scheduleIncrementalSync() {
        // 达到批量阈值时立即发送，否则延迟合并（默认1秒延迟）
        if (SyncBatchManager.getPendingOperationCount() >= 50) {
            DelayedSyncManager.flushPendingOperations();
        } else {
            DelayedSyncManager.scheduleDelayedSync();
        }
    }

    public static void reset() {
        editModeEnabled = false;
        currentMode = EditMode.NORMAL;
        currentRarity = 1;
        autoReload = false;
        ignoreTags = "";
        stringContains = true;
    }
}
