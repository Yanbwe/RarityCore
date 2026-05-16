package org.yanbwe.raritycore.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.network.EditModeRequestPayload;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编辑模式管理器
 * 管理全局编辑模式状态、编辑模式类型、参数存储和当前选中的稀有度等级。
 *
 * <p>支持两种编辑模式：
 * <ul>
 *   <li><b>NORMAL</b> — 直接将物品稀有度写入 FinalRarity.json</li>
 *   <li><b>FULLMATCH</b> — 生成基于 Item Data 的完全匹配配置文件</li>
 * </ul>
 */
public class EditModeManager {

    /** 编辑模式枚举 */
    public enum EditMode {
        /** 正常模式：直接写入 FinalRarity.json */
        NORMAL,
        /** 完全匹配模式：生成 Item Data 匹配配置文件 */
        FULLMATCH
    }

    // 编辑模式开关
    private static boolean editModeEnabled = false;

    // 当前编辑模式类型，默认为 NORMAL
    private static EditMode currentMode = EditMode.NORMAL;

    // 当前选中的稀有度等级 (1-7)
    private static int currentRarity = 1;

    // 删除模式开关
    private static boolean deleteModeEnabled = false;

    // 编辑参数存储：key=参数名(如 rarity, autoReload, ignore, stringContains), value=参数值
    private static final Map<String, String> editParameters = new LinkedHashMap<>();

    // 可用的稀有度等级列表
    private static final List<Integer> AVAILABLE_RARITIES = new ArrayList<>();

    static {
        for (int i = 1; i <= 7; i++) {
            AVAILABLE_RARITIES.add(i);
        }
    }

    // ============ 编辑模式开关（保留原有方法） ============

    /**
     * 切换编辑模式开关
     * @return 新的编辑模式状态
     */
    public static boolean toggleEditMode() {
        editModeEnabled = !editModeEnabled;
        if (editModeEnabled) {
            currentRarity = 1;
        }
        return editModeEnabled;
    }

    /**
     * 设置编辑模式开关状态
     * @param enabled 是否启用编辑模式
     */
    public static void setEditMode(boolean enabled) {
        editModeEnabled = enabled;
        if (enabled) {
            currentRarity = 1;
        }
    }

    /**
     * 获取编辑模式开关状态
     * @return 是否处于编辑模式
     */
    public static boolean isEditModeEnabled() {
        return editModeEnabled;
    }

    // ============ 新增：编辑模式类型 ============

    /**
     * 设置编辑模式类型（重载：接受 EditMode 枚举）
     * @param mode 编辑模式类型，null 时回退为 NORMAL
     */
    public static void setEditMode(EditMode mode) {
        currentMode = mode != null ? mode : EditMode.NORMAL;
    }

    /**
     * 获取当前编辑模式类型
     * @return 当前 EditMode
     */
    public static EditMode getEditMode() {
        return currentMode;
    }

    // ============ 稀有度等级导航 ============

    /**
     * 切换到下一个稀有度等级
     */
    public static void nextRarity() {
        if (!editModeEnabled) return;

        int currentIndex = AVAILABLE_RARITIES.indexOf(currentRarity);
        int nextIndex = (currentIndex + 1) % AVAILABLE_RARITIES.size();
        currentRarity = AVAILABLE_RARITIES.get(nextIndex);
    }

    /**
     * 切换到上一个稀有度等级
     */
    public static void previousRarity() {
        if (!editModeEnabled) return;

        int currentIndex = AVAILABLE_RARITIES.indexOf(currentRarity);
        int previousIndex = (currentIndex - 1 + AVAILABLE_RARITIES.size()) % AVAILABLE_RARITIES.size();
        currentRarity = AVAILABLE_RARITIES.get(previousIndex);
    }

    /**
     * 设置指定的稀有度等级
     * @param rarity 稀有度等级 (1-7)
     */
    public static void setRarity(int rarity) {
        if (rarity >= 1 && rarity <= 7) {
            currentRarity = rarity;
        }
    }

    /**
     * 设置当前稀有度等级（setRarity 别名，兼容新命名约定）
     * @param rarity 稀有度等级 (1-7)
     */
    public static void setCurrentRarity(int rarity) {
        setRarity(rarity);
    }

    /**
     * 获取当前选中的稀有度等级
     * @return 当前稀有度等级
     */
    public static int getCurrentRarity() {
        return currentRarity;
    }

    // ============ 删除模式 ============

    /**
     * 切换删除模式
     * @return 新的删除模式状态
     */
    public static boolean toggleDeleteMode() {
        if (!editModeEnabled) return false;
        deleteModeEnabled = !deleteModeEnabled;
        return deleteModeEnabled;
    }

    /**
     * 设置删除模式状态
     * @param enabled 是否启用删除模式
     */
    public static void setDeleteMode(boolean enabled) {
        if (editModeEnabled) {
            deleteModeEnabled = enabled;
        }
    }

    /**
     * 获取删除模式状态
     * @return 是否处于删除模式
     */
    public static boolean isDeleteModeEnabled() {
        return deleteModeEnabled && editModeEnabled;
    }

    // ============ 参数存储 ============

    /**
     * 设置编辑参数
     * @param key   参数名（如 rarity, autoReload, ignore, stringContains）
     * @param value 参数值
     */
    public static void setParameter(String key, String value) {
        editParameters.put(key, value);
    }

    /**
     * 获取编辑参数
     * @param key 参数名
     * @return 参数值，不存在时返回 null
     */
    public static String getParameter(String key) {
        return editParameters.get(key);
    }

    /**
     * 获取所有参数的只读副本
     * @return 不可修改的参数映射
     */
    public static Map<String, String> getAllParameters() {
        return Collections.unmodifiableMap(editParameters);
    }

    // ============ 辅助方法 ============

    /**
     * 获取可用稀有度等级列表
     * @return 稀有度等级列表副本
     */
    public static List<Integer> getAvailableRarities() {
        return new ArrayList<>(AVAILABLE_RARITIES);
    }

    /**
     * 判断当前是否为单人游戏（集成服务器模式）
     * <p>注意：使用 getSingleplayerServer() 而非 getConnection()，
     * 因为在单人游戏中打开世界时 getConnection() 也可能非 null。</p>
     * @return 是否为单人游戏
     */
    public static boolean isSingleplayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getSingleplayerServer() != null;
    }

    // ============ 核心：修改物品稀有度 ============

    /**
     * 在编辑模式下修改物品稀有度
     * <p>根据当前 EditMode 分派到不同的处理器：
     * <ul>
     *   <li>NORMAL — 写入 FinalRarity.json</li>
     *   <li>FULLMATCH — 生成 Item Data 匹配配置（stub）</li>
     * </ul>
     * @param itemStack 要修改的物品堆
     * @return 是否成功修改
     */
    @SuppressWarnings("null")
    public static boolean modifyItemRarity(ItemStack itemStack) {
        if (!editModeEnabled || itemStack.isEmpty()) {
            return false;
        }

        Item item = itemStack.getItem();
        if (item == null) {
            return false;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return false;
        }

        int targetRarity = deleteModeEnabled ? 0 : currentRarity;

        // 根据编辑模式分派处理
        if (currentMode == EditMode.FULLMATCH) {
            return handleFullMatchMode(itemStack, itemId, targetRarity);
        } else {
            return handleNormalMode(item, itemId, targetRarity);
        }
    }

    /**
     * Normal 模式处理器：将物品稀有度写入 FinalRarity.json
     * <p>rarity=0 表示取消注册（删除稀有度分配）。
     * 多人游戏时通过网络包发送到服务端处理。</p>
     */
    private static boolean handleNormalMode(Item item, Identifier itemId, int rarity) {
        Minecraft mc = Minecraft.getInstance();
        boolean isMultiplayer = mc.getConnection() != null;

        if (isMultiplayer) {
            // 多人游戏：发送请求包到服务端
            EditModeRequestPayload payload = new EditModeRequestPayload(
                itemId,
                rarity,
                rarity == 0
            );
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
        } else {
            // 单人游戏：本地处理并保存配置
            if (rarity == 0) {
                RarityRegistry.unregister(item, false);
                RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), 0);
            } else {
                RarityRegistry.register(item, rarity, false);
                RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), rarity);
            }
            RarityRegistry.syncRarityToClientsWithRetry();
        }

        forceClientCacheUpdate(item, rarity);
        return true;
    }

    /**
     * FullMatch 模式处理器：生成 Item Data 匹配配置对象
     * <p>当前为 stub 实现：仅构建内存中的配置 JSON 对象，
     * 文件写入将在 subtask 19 中通过 ItemDataConfigLoader 完成。</p>
     */
    private static boolean handleFullMatchMode(ItemStack itemStack, Identifier itemId, int rarity) {
        JsonObject config = generateFullMatchConfig(itemStack, rarity);
        // 暂不写文件（subtask 19 实现）：ItemDataConfigLoader.saveFullMatchConfig(config, itemId);
        forceClientCacheUpdate(itemStack.getItem(), rarity);
        return config != null;
    }

    /**
     * 生成 FullMatch 模式的 Item Data 匹配配置 JSON
     * <p>根据当前存储的编辑参数构建 JSON 配置对象，格式符合
     * {@link org.yanbwe.raritycore.itemdatamatching.SimpleConfigValidator} 的要求：</p>
     * <pre>
     * {
     *   "item_id": "namespace:path",
     *   "rarity": 5,
     *   "conditions": [
     *     { "path": "...", "type": "contains", "substring": "..." },
     *     ...
     *   ]
     * }
     * </pre>
     *
     * <p>参数映射：
     * <ul>
     *   <li><b>ignore</b> — | 分隔的多值，每个值生成一个 equals 条件</li>
     *   <li><b>stringContains</b> — 单个值，生成一个 contains 条件，值写入 substring 字段</li>
     * </ul>
     *
     * @param itemStack 目标物品堆（用于获取完整的 Component 上下文）
     * @param rarity    稀有度等级
     * @return 生成的配置 JSON 对象，参数缺失时 conditions 可能为空数组
     */
    public static JsonObject generateFullMatchConfig(ItemStack itemStack, int rarity) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null) return null;

        JsonObject config = new JsonObject();
        config.addProperty("item_id", itemId.toString());
        config.addProperty("rarity", rarity);

        // 构建条件列表
        JsonArray conditions = new JsonArray();

        // ignore 参数：| 分隔的多值，每个值生成一个 equals 条件
        String ignore = editParameters.get("ignore");
        if (ignore != null && !ignore.isEmpty()) {
            for (String part : ignore.split("\\|")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    JsonObject cond = new JsonObject();
                    cond.addProperty("path", "custom_data.ignore");
                    cond.addProperty("type", "equals");
                    cond.addProperty("value", trimmed);
                    conditions.add(cond);
                }
            }
        }

        // stringContains 参数：生成 contains 条件，值字段为 substring
        String stringContains = editParameters.get("stringContains");
        if (stringContains != null && !stringContains.isEmpty()) {
            JsonObject cond = new JsonObject();
            cond.addProperty("path", "custom_data.contains");
            cond.addProperty("type", "contains");
            cond.addProperty("substring", stringContains);
            conditions.add(cond);
        }

        config.add("conditions", conditions);
        return config;
    }

    // ============ 客户端缓存 ============

    /**
     * 强制更新客户端本地缓存，在网络同步之前立即刷新显示效果
     * @param item   要更新的物品
     * @param rarity 新的稀有度等级
     */
    private static void forceClientCacheUpdate(Item item, int rarity) {
        try {
            org.yanbwe.raritycore.cache.DualCacheManager.handleConfigReload();

            ItemStack itemStack = new ItemStack(item);
            org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
        } catch (Exception e) {
            // 静默失败，等待网络同步后自动更新
        }
    }

    // ============ 重置 ============

    /**
     * 重置编辑模式所有状态为默认值
     */
    public static void reset() {
        editModeEnabled = false;
        currentRarity = 1;
        deleteModeEnabled = false;
        currentMode = EditMode.NORMAL;
        editParameters.clear();
    }
}
