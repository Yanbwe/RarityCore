package org.yanbwe.raritycore.client;

/*
  物品稀有度提示
  名字下面显示稀有度等级
 */

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter;
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
        // 使用包含神化NBT检查的增强版配置检测
        if (ClientConfigManager.isSkipUnconfiguredItems() && !RarityRegistry.hasConfiguredRarity(item, itemStack)) {
            return;
        }
        
        // 安全兜底:直接检查神化稀有度,确保工具提示正确反映神化稀有度
        // 当物品有神化NBT数据但标准流程因缓存/NBT大小/解析等原因未能获取神化稀有度时,
        // 此兜底确保工具提示使用最高优先级的稀有度
        rarity = applyApotheosisRarityFallback(itemStack, item, rarity);
        
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
     * 直接检查神化稀有度作为兜底,确保工具提示反映实际神化稀有度
     * 注意:直接调用神化适配器自身(而非通过RarityRegistry),绕过配置中的 checkApotheosisRarity 开关,
     * 以免用户因配置关闭导致工具提示回退到ITEMMAP配置值而无法反映实际神化稀有度
     */
    private static int applyApotheosisRarityFallback(ItemStack itemStack, Item item, int currentRarity) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return currentRarity;
        }
        
        // 直接调用神化适配器,绕过RarityRegistry中的配置检查,确保真实反映神化稀有度
        Integer apothRarity = ApotheosisAdapter.getMappedRarity(itemStack);
        if (apothRarity != null && apothRarity != currentRarity) {
            RarityCore.LOGGER.debug("Apotheosis rarity fallback activated for {}: registry={}, apotheosis={}",
                ForgeRegistries.ITEMS.getKey(item), currentRarity, apothRarity);
            return apothRarity;
        }
        return currentRarity;
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