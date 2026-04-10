package org.yanbwe.raritycore.client;

/*
  物品稀有度提示
  名字下面显示稀有度等级
 */

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.ComponentBuilder;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

@Mod.EventBusSubscriber(modid = RarityCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RarityTooltipHandler {

    @SubscribeEvent
    @SuppressWarnings("null")
    public static void onItemTooltip(ItemTooltipEvent event) {
        // 检查是否启用工具提示插入
        if (!ClientConfigManager.isEnableTooltipInsert()) {
            return;
        }
        
        
        ItemStack itemStack = event.getItemStack();
        Item item = itemStack.getItem();
        
        // 获取物品的稀有度(支持 NBT 匹配,使用物品堆缓存)
        Integer rarity = RenderCacheManager.getCachedRarity(itemStack);
                
        // 如果缓存未命中,则从注册表获取并缓存
        if (rarity == null) {
            rarity = RarityRegistry.getRarity(itemStack);
            if (rarity != null) {
                RenderCacheManager.cacheItemStackRarity(itemStack, rarity);
            }
        }
                
        // 如果仍然没有获取到稀有度,使用默认值
        if (rarity == null) {
            rarity = RarityConstants.RARITY_COMMON;
        }
        
        // 如果启用了跳过未配置物品且物品没有配置稀有度,则不插入工具提示
        // 注意:需要检查物品是否真的没有配置,而不是检查rarity是否为null
        if (ClientConfigManager.isSkipUnconfiguredItems() && !hasConfiguredRarity(item)) {
            return;
        }
        
        
        // 先检查是否为特殊稀有度(大于7),保存原始值用于显示
        boolean isSpecialRarity = rarity > RarityConstants.RARITY_UNIQUE;
        int displayRarity = rarity; // 保存用于显示的原始稀有度值
        
        // 标准化稀有度值用于颜色获取等内部处理
        rarity = RarityValidator.normalizeRarity(rarity);
        
        // 检查是否启用工具提示变色
        boolean enableColor = ClientConfigManager.isEnableTooltipColor();
        
        // 处理超出范围的稀有度值
        ChatFormatting color = enableColor ? RarityColorUtil.getRarityChatColor(rarity) : ChatFormatting.GRAY;
        MutableComponent prefixComponent;
        
        if (isSpecialRarity) {
            // 如果稀有度大于7,显示为 [x级稀有度-x(星星)]
            ChatFormatting uniqueColor = enableColor ? RarityColorUtil.getRarityChatColor(RarityConstants.RARITY_UNIQUE) : ChatFormatting.GRAY;
            MutableComponent rarityComponent = ComponentBuilder.buildSpecialRarityComponent(displayRarity, uniqueColor, enableColor);
            
            // 高效插入到工具提示
            ComponentBuilder.insertIntoTooltip(event.getToolTip(), rarityComponent);
            return;
        } else {
            
            // 设置前缀和颜色
            switch (rarity) {
                case RarityConstants.RARITY_COMMON:
                    prefixComponent = Component.translatable("rarity.core.common").withStyle(color);
                    break;
                case RarityConstants.RARITY_UNCOMMON:
                    prefixComponent = Component.translatable("rarity.core.uncommon").withStyle(color);
                    break;
                case RarityConstants.RARITY_RARE:
                    prefixComponent = Component.translatable("rarity.core.rare").withStyle(color);
                    break;
                case RarityConstants.RARITY_EPIC:
                    prefixComponent = Component.translatable("rarity.core.epic").withStyle(color);
                    break;
                case RarityConstants.RARITY_LEGENDARY:
                    prefixComponent = Component.translatable("rarity.core.legendary").withStyle(color);
                    break;
                case RarityConstants.RARITY_MYTHICAL:
                    prefixComponent = Component.translatable("rarity.core.mythical").withStyle(color);
                    break;
                case RarityConstants.RARITY_UNIQUE:
                    prefixComponent = Component.translatable("rarity.core.unique").withStyle(color);
                    break;
                default:
                    return;
            }
        
            // 构建文本(使用组件构建器)
            MutableComponent starsComponent = ComponentBuilder.buildRarityComponent(rarity, color, enableColor);
            MutableComponent rarityComponent = Component.empty().append(prefixComponent).append(starsComponent).withStyle(color);
            
            // 高效插入到工具提示
            ComponentBuilder.insertIntoTooltip(event.getToolTip(), rarityComponent);
        }
    }
    
    /**
     * 检查物品是否有配置的稀有度
     * @param item 要检查的物品
     * @return 如果物品有配置稀有度返回true,否则返回false
     */
    private static boolean hasConfiguredRarity(Item item) {
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
     * 处理skipUnconfiguredItems配置变更
     * 当配置改变时调用此方法来刷新工具提示处理状态
     */
    public static void handleSkipConfigChange() {
        // 使工具提示缓存失效
        RenderCacheManager.clearAllCache();
        // RarityTooltipHandler: skipUnconfiguredItems config change handled
    }
}