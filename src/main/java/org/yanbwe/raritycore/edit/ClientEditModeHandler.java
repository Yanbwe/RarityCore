package org.yanbwe.raritycore.edit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.yanbwe.raritycore.cache.DualCacheManager;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.network.EditModeRequestPayload;
import org.yanbwe.raritycore.registry.RarityRegistry;

/**
 * 客户端编辑模式处理器
 * 包含仅限客户端的方法，在专用服务端上不会加载此类
 * 与 EditModeManager 协作: EditModeManager 管理共享状态, 本类处理客户端的物品修改逻辑
 */
@OnlyIn(Dist.CLIENT)
public class ClientEditModeHandler {

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

        boolean deleteModeEnabled = EditModeManager.isDeleteModeEnabled();
        int currentRarity = EditModeManager.getCurrentRarity();

        // 检查是否在多人游戏中
        Minecraft mc = Minecraft.getInstance();
        boolean isMultiplayer = mc.getConnection() != null;

        if (isMultiplayer) {
            // 多人游戏: 发送请求包到服务端, 由服务端保存配置并同步
            EditModeRequestPayload payload = new EditModeRequestPayload(
                itemId,
                deleteModeEnabled ? 0 : currentRarity,
                deleteModeEnabled
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

        // 立即刷新本地缓存, 确保显示效果立即生效
        forceClientCacheUpdate(item, deleteModeEnabled ? 0 : currentRarity);

        return true;
    }

    /**
     * 强制更新客户端本地缓存
     * 在网络同步之前立即刷新显示效果
     * @param item 要更新的物品
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
