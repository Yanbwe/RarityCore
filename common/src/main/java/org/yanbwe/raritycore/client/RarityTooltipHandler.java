package org.yanbwe.raritycore.client;

/*
  物品稀有度提示
  名字下面显示稀有度等级
 */

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.event.RarityTooltipEvent;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.ComponentBuilder;
import org.yanbwe.raritycore.util.RarityConstants;

@EventBusSubscriber(modid = RarityCore.MODID, value = Dist.CLIENT)
public class RarityTooltipHandler {

    @SubscribeEvent
    @SuppressWarnings("null")
    public static void onItemTooltip(ItemTooltipEvent event) {
        RarityExclusionManager.setRenderingTooltipItem(true);
        try {
            // 检查是否启用工具提示插入
            if (!RarityStyleConfigManager.isTooltipEnabled()) {
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
                rarity = RarityConstants.MIN_RARITY;
            }

            // 如果启用了跳过未配置物品且物品没有配置稀有度,则不插入工具提示
            // 注意:需要检查物品是否真的没有配置,而不是检查rarity是否为null
            if (RarityStyleConfigManager.isNoRaritySkip() && !hasConfiguredRarity(item)) {
                return;
            }

            // Post RarityTooltipEvent to allow other mods to modify the tooltip list before insertion
            NeoForge.EVENT_BUS.post(new RarityTooltipEvent(itemStack, rarity, event.getToolTip()));

            // Per-level tooltip visibility comes from RarityStyleConfigManager
            if (!RarityStyleConfigManager.getTooltip(rarity).show()) {
                return;
            }

            // Build the unified tooltip line from RarityStyle.json settings and insert it
            MutableComponent rarityComponent = ComponentBuilder.buildRarityTooltipComponent(rarity);
            ComponentBuilder.insertIntoTooltip(event.getToolTip(), rarityComponent);
        } finally {
            RarityExclusionManager.setRenderingTooltipItem(false);
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

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return false;
        }

        if (RarityRegistry.ITEM_RARITY_MAP.containsKey(itemId)) {
            return true;
        }

        if (RarityRegistry.hasAutoRarity(itemId)) {
            return true;
        }

        return false;
    }

    /**
     * 处理skipUnconfiguredItems配置变更
     * 当配置改变时调用此方法来刷新工具提示处理状态
     */
    public static void handleSkipConfigChange() {
        // 使工具提示缓存失效
        RenderCacheManager.clearAllCache();
        // RarityTooltipHandler: skipUnconfiguredItems 配置变更已处理
    }
}