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
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.ComponentBuilder;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

@Mod.EventBusSubscriber(modid = RarityCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RarityTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        // 检查是否启用工具提示插入
        if (!ConfigManager.isEnableTooltipInsert()) {
            return;
        }
        
        // 更新星星缓存
        ComponentBuilder.updateStarCache();
        
        ItemStack itemStack = event.getItemStack();
        Item item = itemStack.getItem();
        
        // 获取物品的稀有度（使用缓存优化）
        Integer rarity = RenderCacheManager.getCachedRarity(item);
        
        // 如果缓存未命中，则从注册表获取并缓存
        if (rarity == null) {
            rarity = RarityRegistry.getRarity(item);
            RenderCacheManager.cacheRarity(item, rarity);
        }
        
        // 如果启用了跳过未配置物品且物品没有配置稀有度，则不插入工具提示
        if (ConfigManager.isSkipUnconfiguredItems() && rarity == null) {
            return;
        }
        
        // 如果没有注册稀有度，默认为普通
        if (rarity == null) {
            rarity = RarityConstants.RARITY_COMMON;
        }

        // 标准化稀有度值，遵循模组的包容性原则
        rarity = RarityValidator.normalizeRarity(rarity);
        
        // 处理超出范围的稀有度值
        ChatFormatting color = RarityColorUtil.getRarityChatColor(rarity);
        MutableComponent prefixComponent;
        
        if (rarity > RarityConstants.RARITY_UNIQUE) {
            // 如果稀有度大于7，显示为 [x级稀有度-x(星星)]
            ChatFormatting uniqueColor = RarityColorUtil.getRarityChatColor(RarityConstants.RARITY_UNIQUE);
            MutableComponent rarityComponent = ComponentBuilder.buildSpecialRarityComponent(rarity, uniqueColor);
            
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
        
            // 构建文本（使用优化的组件构建器）
            MutableComponent starsComponent = ComponentBuilder.buildRarityComponent(rarity, color);
            MutableComponent rarityComponent = Component.empty().append(prefixComponent).append(starsComponent).withStyle(color);
            
            // 高效插入到工具提示
            ComponentBuilder.insertIntoTooltip(event.getToolTip(), rarityComponent);
        }
    }
}