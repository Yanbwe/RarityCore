package org.yanbwe.raritycore.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑模式管理器
 * 管理全局编辑模式状态和当前选中的稀有度等级
 * 仅包含共享/服务端状态 — 客户端专用逻辑已移至 ClientEditModeHandler
 */
public class EditModeManager {
    
    // 编辑模式开关
    private static boolean editModeEnabled = false;
    
    // 当前选中的稀有度等级 (1-7)
    private static int currentRarity = 1;
    
    // 删除模式开关
    private static boolean deleteModeEnabled = false;
    
    // 可用的稀有度等级列表
    private static final List<Integer> AVAILABLE_RARITIES = new ArrayList<>();
    
    static {
        // 初始化可用稀有度等级
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
     * @param rarity 稀有度等级 (1-7)
     */
    public static void setRarity(int rarity) {
        if (rarity >= 1 && rarity <= 7) {
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
    
    /**
     * 重置编辑模式状态
     */
    public static void reset() {
        editModeEnabled = false;
        currentRarity = 1;
        deleteModeEnabled = false;
    }
}