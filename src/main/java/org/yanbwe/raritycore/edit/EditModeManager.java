package org.yanbwe.raritycore.edit;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.command.RarityCoreCommands;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑模式管理器
 * 管理全局编辑模式状态和当前选中的稀有度等级
 */
public class EditModeManager {
    
    // 编辑模式开关
    private static boolean editModeEnabled = false;
    
    // 当前选中的稀有度等级 (1-7)
    private static int currentRarity = 1;
    
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
     * 获取可用稀有度等级列表
     * @return 稀有度等级列表
     */
    public static List<Integer> getAvailableRarities() {
        return new ArrayList<>(AVAILABLE_RARITIES);
    }
    
    /**
     * 在编辑模式下修改物品稀有度
     * @param itemStack 要修改的物品堆
     * @return 是否成功修改
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean modifyItemRarity(ItemStack itemStack) {
        if (!editModeEnabled || itemStack.isEmpty()) {
            return false;
        }
        
        Item item = itemStack.getItem();
        if (item == null) {
            return false;
        }
        
        // 获取物品ID
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            return false;
        }
        
        // 注册稀有度（不自动同步，因为后面会手动同步）
        RarityRegistry.register(item, currentRarity, false);
        
        // 保存到配置文件
        RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), currentRarity);
        
        // 使用重试机制手动同步到所有客户端
        RarityRegistry.syncRarityToClientsWithRetry();
        
        return true;
    }
    
    /**
     * 重置编辑模式状态
     */
    public static void reset() {
        editModeEnabled = false;
        currentRarity = 1;
    }
}