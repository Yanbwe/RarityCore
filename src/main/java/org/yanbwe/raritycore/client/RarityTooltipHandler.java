package org.yanbwe.raritycore.client;

/**
 * 物品稀有度提示
 * 名字下面显示稀有度等级
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
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;

@Mod.EventBusSubscriber(modid = RarityCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RarityTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        Item item = itemStack.getItem();
        
        // 获取物品的稀有度，未注册的物品默认为普通
        Integer rarity = RarityRegistry.getRarity(item);
        if (rarity == null) {
            rarity = RarityConstants.RARITY_COMMON;
        }

        if (rarity < RarityConstants.MIN_RARITY || rarity > RarityConstants.MAX_RARITY) {
            return;
        }
        
        // 设置前缀和颜色
        MutableComponent prefixComponent;
        ChatFormatting color = RarityColorUtil.getRarityChatColor(rarity);
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
        
        // 创建星星，星号数量等于稀有度值
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < rarity; i++) {
            stars.append("\u2B50");
        }
        
        // 构建文本
        MutableComponent starsComponent = Component.literal(" " + stars.toString()).withStyle(color);
        MutableComponent rarityComponent = Component.empty().append(prefixComponent).append(starsComponent);
        
        // 在工具提示列表的第二行插入稀有度提示
        if (event.getToolTip().size() >= 1) {
            event.getToolTip().add(1, rarityComponent);
        } else {
            event.getToolTip().add(rarityComponent);
        }
    }
}