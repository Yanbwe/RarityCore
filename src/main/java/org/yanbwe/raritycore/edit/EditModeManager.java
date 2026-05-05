package org.yanbwe.raritycore.edit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 编辑模式管理器
 * 管理全局编辑模式状态和当前选中的稀有度等级
 * 仅包含共享/服务端状态 — 客户端专用逻辑已移至 ClientEditModeHandler
 *
 * v13 新增: 编辑模式枚举(NORMAL/FULLMATCH)和参数存储(autoReload/ignoreComponents/stringContains)
 */
public class EditModeManager {

    /**
     * 编辑模式枚举
     * NORMAL - 普通模式: 将物品+稀有度写入 FinalRarity.json
     * FULLMATCH - 完全匹配模式: 基于物品 Data Component 的完全匹配规则
     */
    public enum EditMode {
        NORMAL,
        FULLMATCH
    }

    // 编辑模式开关
    private static boolean editModeEnabled = false;

    // 当前编辑模式类型 (v13 新增)
    private static EditMode currentMode = EditMode.NORMAL;

    // 当前选中的稀有度等级 (≥0, v13 放宽范围)
    private static int currentRarity = 1;

    // 删除模式开关
    private static boolean deleteModeEnabled = false;

    // FULLMATCH 模式参数 (v13 新增)
    private static boolean autoReload = false;
    private static String ignoreComponents = "";
    private static boolean stringContains = true;

    // 可用的稀有度等级列表 (用于 cycleRarity 循环)
    private static final List<Integer> AVAILABLE_RARITIES = new ArrayList<>();

    static {
        // 初始化可用稀有度等级 (1-7)
        for (int i = 1; i <= 7; i++) {
            AVAILABLE_RARITIES.add(i);
        }
    }
    
    /**
     * 切换编辑模式
     * @return 新的编辑模式状态
     */
    public static boolean toggleEditMode() {
        editModeEnabled = !editModeEnabled;
        if (editModeEnabled) {
            currentRarity = 1; // 重置为默认稀有度
        }
        return editModeEnabled;
    }
    
    /**
     * 设置编辑模式状态
     * @param enabled 是否启用编辑模式
     */
    public static void setEditMode(boolean enabled) {
        editModeEnabled = enabled;
        if (enabled) {
            currentRarity = 1; // 重置为默认稀有度
        }
    }
    
    /**
     * 获取编辑模式状态
     * @return 是否处于编辑模式
     */
    public static boolean isEditModeEnabled() {
        return editModeEnabled;
    }
    
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
     * v13: 放宽为 ≥0, rarity=0 表示无稀有度
     * @param rarity 稀有度等级 (≥0)
     */
    public static void setRarity(int rarity) {
        if (rarity >= 0) {
            currentRarity = rarity;
        }
    }
    
    /**
     * 获取当前选中的稀有度等级
     * @return 当前稀有度等级
     */
    public static int getCurrentRarity() {
        return currentRarity;
    }
    
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
    
    /**
     * 获取可用稀有度等级列表
     * @return 稀有度等级列表
     */
    public static List<Integer> getAvailableRarities() {
        return new ArrayList<>(AVAILABLE_RARITIES);
    }
    
    // ──────────── v13 新增: 编辑模式类型管理 ────────────

    /**
     * 获取当前编辑模式类型
     * @return 当前模式 (NORMAL 或 FULLMATCH)
     */
    public static EditMode getCurrentMode() {
        return currentMode;
    }

    /**
     * 设置编辑模式类型
     * @param mode 编辑模式枚举值
     */
    public static void setCurrentMode(EditMode mode) {
        currentMode = mode;
    }

    /**
     * 通过名称字符串设置编辑模式
     * @param modeName "normal" 或 "fullmatch" (大小写不敏感)
     */
    public static void setModeByName(String modeName) {
        if ("fullmatch".equalsIgnoreCase(modeName)) {
            currentMode = EditMode.FULLMATCH;
        } else {
            currentMode = EditMode.NORMAL;
        }
    }

    /**
     * 获取当前模式名称字符串
     * @return "normal" 或 "fullmatch"
     */
    public static String getModeName() {
        return currentMode == EditMode.FULLMATCH ? "fullmatch" : "normal";
    }

    // ──────────── v13 新增: FULLMATCH 模式参数管理 ────────────

    /**
     * 获取 autoReload 参数 (生成配置后是否自动重载)
     * @return true 表示自动重载
     */
    public static boolean isAutoReload() {
        return autoReload;
    }

    /**
     * 设置 autoReload 参数
     * @param value true 表示自动重载
     */
    public static void setAutoReload(boolean value) {
        autoReload = value;
    }

    /**
     * 获取忽略的组件列表字符串 (以 | 分隔)
     * @return 忽略组件字符串
     */
    public static String getIgnoreComponents() {
        return ignoreComponents;
    }

    /**
     * 设置忽略的组件列表
     * @param ignore 以 | 分隔的组件名列表
     */
    public static void setIgnoreComponents(String ignore) {
        ignoreComponents = ignore;
    }

    /**
     * 获取 stringContains 参数 (是否使用包含匹配)
     * @return true 表示使用包含匹配
     */
    public static boolean isStringContains() {
        return stringContains;
    }

    /**
     * 设置 stringContains 参数
     * @param value true 表示使用包含匹配
     */
    public static void setStringContains(boolean value) {
        stringContains = value;
    }

    /**
     * 重置编辑模式状态 (包括 v13 新增参数)
     */
    public static void reset() {
        editModeEnabled = false;
        currentRarity = 1;
        deleteModeEnabled = false;
        currentMode = EditMode.NORMAL;
        autoReload = false;
        ignoreComponents = "";
        stringContains = true;
    }

    // ──────────── v13 新增: FullMatch 模式工具方法 ────────────

    /**
     * 将 ignoreComponents 字符串解析为忽略组件名集合
     * 格式: "aaa|bbb|ccc" 或带转义的 "aaa\\|bbb\\|ccc"
     * @return 忽略的组件名集合 (不可变)
     */
    public static Set<String> getIgnoredComponentsSet() {
        if (ignoreComponents == null || ignoreComponents.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (String part : ignoreComponents.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return Set.copyOf(result);
    }

    /**
     * 检查指定组件名是否在忽略列表中
     * @param componentName 组件全名 (如 "minecraft:damage")
     * @return true 表示应忽略
     */
    public static boolean isComponentIgnored(String componentName) {
        return getIgnoredComponentsSet().contains(componentName);
    }

    /**
     * 判断当前是否为 FullMatch 模式
     * @return true 表示 FullMatch 模式
     */
    public static boolean isFullMatchMode() {
        return currentMode == EditMode.FULLMATCH;
    }
}