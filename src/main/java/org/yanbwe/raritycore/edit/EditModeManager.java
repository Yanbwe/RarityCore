package org.yanbwe.raritycore.edit;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.registries.BuiltInRegistries;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.network.EditModeRequestPayload;
import org.yanbwe.raritycore.network.NetworkConstants;
import org.yanbwe.raritycore.registry.RarityRegistry;

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
     * 在编辑模式下修改物品稀有度
     * @param itemStack 要修改的物品堆
     * @return 是否成功修改
     */
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("null")
    public static boolean modifyItemRarity(ItemStack itemStack) {
        if (!editModeEnabled || itemStack.isEmpty()) {
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
            
        // 检查是否在多人游戏中
        Minecraft mc = Minecraft.getInstance();
        boolean isMultiplayer = mc.getConnection() != null;
        
        if (isMultiplayer) {
            // 多人游戏:发送请求包到服务端,由服务端保存配置并同步
            EditModeRequestPayload payload = new EditModeRequestPayload(
                itemId, 
                deleteModeEnabled ? 0 : currentRarity, 
                deleteModeEnabled
            );
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        } else {
            // 单人游戏:本地处理并保存配置
            if (deleteModeEnabled) {
                RarityRegistry.unregister(item, false);
                RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), 0);
            } else {
                RarityRegistry.register(item, currentRarity, false);
                RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), currentRarity);
            }
            RarityRegistry.syncRarityToClientsWithRetry();
        }
                    
        // 立即刷新本地缓存,确保显示效果立即生效
        forceClientCacheUpdate(item, deleteModeEnabled ? 0 : currentRarity);
            
        return true;
    }
        
    /**
     * 强制更新客户端本地缓存
     * 在网络同步之前立即刷新显示效果
     * @param item 要更新的物品
     * @param rarity 新的稀有度等级
     */
    @OnlyIn(Dist.CLIENT)
    private static void forceClientCacheUpdate(Item item, int rarity) {
        try {
            // 清空旧缓存
            org.yanbwe.raritycore.cache.DualCacheManager.handleConfigReload();
                
            // 立即重新缓存新稀有度
            ItemStack itemStack = new ItemStack(item);
            org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
        } catch (Exception e) {
            // 静默失败,等待网络同步后自动更新
        }
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