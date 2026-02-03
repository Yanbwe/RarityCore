package org.yanbwe.raritycore.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void modifyHoverName(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        
        // 获取物品的稀有度（使用缓存优化）
        Item item = stack.getItem();
        Integer rarity = org.yanbwe.raritycore.client.RenderCacheManager.getCachedRarity(item);
        
        // 如果缓存未命中，则从注册表获取并缓存
        if (rarity == null) {
            rarity = RarityRegistry.getRarity(item);
            org.yanbwe.raritycore.client.RenderCacheManager.cacheRarity(item, rarity);
        }
        
        // 如果启用了跳过未配置物品且物品没有配置稀有度，则不修改名称颜色
        // 注意：需要检查物品是否真的没有配置，而不是默认的稀有度1
        if (org.yanbwe.raritycore.config.ServerConfigManager.isSkipUnconfiguredItems() && !hasConfiguredRarity(item)) {
            return;
        }
        
        // 如果没有注册稀有度，则不修改名称颜色
        if (rarity == null) {
            return;
        }

        // 标准化稀有度值，遵循模组的包容性原则
        rarity = RarityValidator.normalizeRarity(rarity);
        
        // 如果是普通稀有度（1），则使用白色，但不添加格式化代码（默认颜色）
        if (rarity == RarityConstants.RARITY_COMMON) {
            return;
        }
        
        // 获取对应颜色
        ChatFormatting color = RarityColorUtil.getRarityChatColor(rarity);
        Component originalName = cir.getReturnValue();
        
        // 设置带有颜色格式的名称并取消默认返回值
        cir.setReturnValue(originalName.copy().withStyle(color));
    }
    
    /**
     * 检查物品是否有配置的稀有度
     * @param item 要检查的物品
     * @return 如果物品有配置稀有度返回true，否则返回false
     */
    private boolean hasConfiguredRarity(Item item) {
        if (item == null) {
            return false;
        }
        
        // 获取物品ID
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            return false;
        }
        
        // 检查是否在注册表中有配置
        return RarityRegistry.ITEM_RARITY_MAP.containsKey(itemId);
    }
    
    /**
     * 根据稀有度等级获取对应颜色
     * @param rarity 稀有度等级 (2-7)
     * @return ChatFormatting颜色
     */
    private ChatFormatting getRarityColor(int rarity) {
        return RarityColorUtil.getRarityChatColor(rarity);
    }
}