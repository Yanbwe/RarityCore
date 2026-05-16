package org.yanbwe.raritycore.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;
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

    // FULLMATCH 模式专用参数（与 1.21.1 签名兼容）
    private static boolean autoReload = false;
    private static String ignoreComponents = "";
    private static boolean stringContains = true;

    // 忽略组件集合缓存：仅当 ignoreComponents 字符串变化时重新解析
    private static volatile java.util.Set<String> cachedIgnoredSet = java.util.Set.of();
    private static volatile String lastIgnoreComponents = "";

    // 编辑参数存储：key=参数名(如 rarity, autoReload, ignore, stringContains), value=参数值
    private static final Map<String, String> editParameters = new LinkedHashMap<>();

    // 可用的稀有度等级列表（不可变）
    private static final List<Integer> AVAILABLE_RARITIES = List.of(1, 2, 3, 4, 5, 6, 7);

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
     * 切换到下一个稀有度等级（在 AVAILABLE_RARITIES 中循环）。
     * 如果当前等级超出列表范围，重置为 1。
     */
    public static void nextRarity() {
        if (!editModeEnabled) return;

        int currentIndex = AVAILABLE_RARITIES.indexOf(currentRarity);
        if (currentIndex < 0) {
            currentRarity = 1;
            return;
        }
        int nextIndex = (currentIndex + 1) % AVAILABLE_RARITIES.size();
        currentRarity = AVAILABLE_RARITIES.get(nextIndex);
    }

    /**
     * 切换到上一个稀有度等级（在 AVAILABLE_RARITIES 中循环）。
     * 如果当前等级超出列表范围，重置为 1。
     */
    public static void previousRarity() {
        if (!editModeEnabled) return;

        int currentIndex = AVAILABLE_RARITIES.indexOf(currentRarity);
        if (currentIndex < 0) {
            currentRarity = 1;
            return;
        }
        int previousIndex = (currentIndex - 1 + AVAILABLE_RARITIES.size()) % AVAILABLE_RARITIES.size();
        currentRarity = AVAILABLE_RARITIES.get(previousIndex);
    }

    /**
     * 设置指定的稀有度等级（≥0，无上限）。
     * rarity=0 表示无稀有度（用于删除模式）。
     * @param rarity 稀有度等级（≥0）
     */
    public static void setRarity(int rarity) {
        if (rarity >= 0) {
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
     * 设置编辑参数（同时同步到专用字段以保持 1.21.1 兼容性）
     * @param key   参数名（如 rarity, autoReload, ignore, stringContains）
     * @param value 参数值
     */
    public static void setParameter(String key, String value) {
        editParameters.put(key, value);
        // 同步专用字段
        switch (key) {
            case "autoReload" -> autoReload = Boolean.parseBoolean(value);
            case "ignore" -> ignoreComponents = value;
            case "stringContains" -> stringContains = Boolean.parseBoolean(value);
        }
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

    // ============ FULLMATCH 参数便捷方法（1.21.1 API 兼容） ============

    public static boolean isAutoReload() {
        return autoReload;
    }

    public static void setAutoReload(boolean value) {
        autoReload = value;
        editParameters.put("autoReload", String.valueOf(value));
    }

    public static String getIgnoreComponents() {
        return ignoreComponents;
    }

    public static void setIgnoreComponents(String ignore) {
        ignoreComponents = ignore;
        editParameters.put("ignore", ignore);
    }

    public static boolean isStringContains() {
        return stringContains;
    }

    public static void setStringContains(boolean value) {
        stringContains = value;
        editParameters.put("stringContains", String.valueOf(value));
    }

    public static boolean isFullMatchMode() {
        return currentMode == EditMode.FULLMATCH;
    }

    /** getEditMode() 的别名，兼容 1.21.1 API */
    public static EditMode getCurrentMode() {
        return currentMode;
    }

    /** setEditMode(EditMode) 的别名，兼容 1.21.1 API */
    public static void setCurrentMode(EditMode mode) {
        setEditMode(mode);
    }

    public static String getModeName() {
        return currentMode == EditMode.FULLMATCH ? "fullmatch" : "normal";
    }

    public static void setModeByName(String modeName) {
        if ("fullmatch".equalsIgnoreCase(modeName)) {
            currentMode = EditMode.FULLMATCH;
        } else {
            currentMode = EditMode.NORMAL;
        }
    }

    /**
     * 将 ignoreComponents 字符串解析为忽略组件名集合（带缓存）。
     * <p>仅当 ignoreComponents 字符串变化时重新解析，避免在 FullMatch 配置生成
     * 的组件遍历循环中反复创建临时集合。</p>
     * @return 忽略的组件名集合（不可变）
     */
    public static java.util.Set<String> getIgnoredComponentsSet() {
        String current = ignoreComponents;
        if (current == null) current = "";
        if (current.equals(lastIgnoreComponents)) {
            return cachedIgnoredSet;
        }
        if (current.isEmpty()) {
            cachedIgnoredSet = java.util.Set.of();
        } else {
            java.util.Set<String> result = new java.util.HashSet<>();
            for (String part : current.split("\\|")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            cachedIgnoredSet = java.util.Set.copyOf(result);
        }
        lastIgnoreComponents = current;
        return cachedIgnoredSet;
    }

    public static boolean isComponentIgnored(String componentName) {
        if (componentName == null || componentName.isEmpty()) return false;
        java.util.Set<String> ignored = getIgnoredComponentsSet();
        if (ignored.isEmpty()) return false;

        // 短名（冒号后部分），如 "repair_cost"
        String shortName = componentName;
        int colonIdx = componentName.indexOf(':');
        if (colonIdx >= 0) {
            shortName = componentName.substring(colonIdx + 1);
        }

        for (String entry : ignored) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            // 去掉可能的 "components." 前缀（用户可能按配置 path 格式输入）
            if (e.startsWith("components.")) {
                e = e.substring("components.".length());
            }
            // 匹配完整名 "minecraft:repair_cost" 或短名 "repair_cost"
            if (componentName.equals(e) || shortName.equals(e)) {
                return true;
            }
        }
        return false;
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
        boolean isMultiplayer = !isSingleplayer();

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
     * FullMatch 模式处理器：遍历物品 Data Component 生成完整匹配配置 JSON，
     * 并发送到服务端写入文件。
     */
    private static boolean handleFullMatchMode(ItemStack itemStack, Identifier itemId, int rarity) {
        // 客户端生成完整匹配配置（遍历所有 Data Component 构建条件）
        JsonObject config = generateFullMatchConfig(itemStack, rarity);
        if (config == null) {
            RarityCore.LOGGER.warn("FullMatch: Failed to generate config for {}", itemId);
            return false;
        }

        String configJson = config.toString();
        boolean isMultiplayer = !isSingleplayer();

        if (isMultiplayer) {
            // 多人游戏：发送完整 JSON 到服务端
            EditModeRequestPayload payload = new EditModeRequestPayload(
                itemId, rarity, false, "FULLMATCH",
                configJson
            );
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
        } else {
            // 单人游戏：直接写入本地
            FullMatchConfigGenerator.generateOnServer(itemId, rarity, configJson);
        }

        return true;
    }

    /**
     * 生成 FullMatch 模式的 Item Data 匹配配置 JSON。
     * <p>遍历物品的所有 Data Component，根据类型生成条件：</p>
     * <ul>
     *   <li>文本类型 (String) — if stringContains → contains，else → equals</li>
     *   <li>复合/列表类型 — if stringContains → contains，else → equals</li>
     *   <li>数值/布尔类型 — 始终 equals</li>
     * </ul>
     * <p>ignoreComponents 中指定的组件名（如 "minecraft:damage"）会被跳过。</p>
     *
     * @param itemStack 目标物品堆
     * @param rarity    稀有度等级
     * @return 生成的配置 JSON 对象，失败返回 null
     */
    public static JsonObject generateFullMatchConfig(ItemStack itemStack, int rarity) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null) return null;

        net.minecraft.core.component.DataComponentMap components = itemStack.getComponents();
        if (components == null || components.isEmpty()) {
            return null;
        }

        boolean strContains = isStringContains();
        JsonArray conditions = new JsonArray();

        for (net.minecraft.core.component.TypedDataComponent<?> typed : components) {
            String componentName = typed.type().toString(); // e.g. "minecraft:damage"
            if (componentName == null || componentName.isEmpty()) continue;

            // 跳过忽略列表中的组件
            if (isComponentIgnored(componentName)) continue;

            Object value = typed.value();
            if (value == null) continue;

            String path = "components." + componentName;
            JsonObject condition = new JsonObject();
            condition.addProperty("path", path);
            condition.addProperty("description", componentName);

            if (value instanceof Number num) {
                condition.addProperty("type", "equals");
                condition.addProperty("value", num);
            } else if (value instanceof Boolean bool) {
                condition.addProperty("type", "equals");
                condition.addProperty("value", bool);
            } else if (value instanceof String str) {
                if (strContains) {
                    condition.addProperty("type", "contains");
                    condition.addProperty("substring", str);
                } else {
                    condition.addProperty("type", "equals");
                    condition.addProperty("value", str);
                }
            } else {
                // 复合类型、列表等
                String strVal = value.toString();
                if (strContains && !strVal.isEmpty()) {
                    condition.addProperty("type", "contains");
                    condition.addProperty("substring", strVal);
                } else {
                    condition.addProperty("type", "equals");
                    condition.addProperty("value", strVal);
                }
            }

            conditions.add(condition);
        }

        if (conditions.isEmpty()) {
            return null;
        }

        JsonObject config = new JsonObject();
        config.addProperty("item_id", itemId.toString());
        config.add("conditions", conditions);
        config.addProperty("rarity", rarity);
        config.addProperty("fuzzy_match", true);
        config.addProperty("priority", 0);
        config.addProperty("enabled", true);
        config.addProperty("description", "Auto-generated by FullMatch edit mode");

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
            org.yanbwe.raritycore.cache.RarityCacheCoordinator.updateIdCache(item, rarity);
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Cache update deferred for item (will sync via network): {}", e.getMessage());
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
        autoReload = false;
        ignoreComponents = "";
        stringContains = true;
        editParameters.clear();
    }
}
