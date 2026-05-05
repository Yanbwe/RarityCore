package org.yanbwe.raritycore.client;

/*
  物品稀有度提示
  名字下面显示稀有度等级
 */

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.RarityClientConfig;
import org.yanbwe.raritycore.event.RarityTooltipEvent;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.ComponentBuilder;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = RarityCore.MODID, value = Dist.CLIENT)
public class RarityTooltipHandler {

    @SubscribeEvent
    @SuppressWarnings("null")
    public static void onItemTooltip(ItemTooltipEvent event) {
        RarityExclusionManager.setRenderingTooltipItem(true);
        try {
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
        
        // 从 RarityClientConfig 获取该等级的客户端配置
        RarityClientConfig clientConfig = RarityClientConfig.getInstance();
        
        // RarityClientConfig 的 per-level tooltips 开关：如果该等级配置为不显示 tooltip，则跳过
        // 注：此开关仅在 RarityClientConfig 已加载时生效（非空配置）
        if (!clientConfig.isEmpty() && !clientConfig.isTooltipsEnabled(rarity)) {
            return;
        }
        
        // 从 RarityClientConfig 获取 RGB 颜色（含等级 >7 回退）
        // 使用 TextColor.fromRgb() + Style.EMPTY.withColor() 替代 ChatFormatting
        int rgbColor = clientConfig.getColor(rarity);
        MutableComponent prefixComponent;
        
        if (isSpecialRarity) {
            // 如果稀有度大于7,显示为 [x级稀有度] <星星>
            // 特殊稀有度颜色沿用等级 7（UNIQUE）的配置
            int uniqueRgbColor = clientConfig.getColor(RarityConstants.RARITY_UNIQUE);
            MutableComponent rarityComponent = ComponentBuilder.buildSpecialRarityComponent(displayRarity, uniqueRgbColor);
            
            // 触发 RarityTooltipEvent，允许监听器修改 tooltip 组件
            List<Component> tipComponents = new ArrayList<>();
            tipComponents.add(rarityComponent);
            RarityTooltipEvent tipEvent = new RarityTooltipEvent(itemStack, displayRarity, tipComponents, true);
            NeoForge.EVENT_BUS.post(tipEvent);
            
            // 插入事件中所有组件到工具提示（逆序插入以保持顺序，使用快照副本防止并发修改）
            List<Component> tooltip = event.getToolTip();
            List<Component> eventComponents = new ArrayList<>(tipEvent.getTooltipComponents());
            for (int i = eventComponents.size() - 1; i >= 0; i--) {
                tooltip.add(1, eventComponents.get(i));
            }
            return;
        } else {
            
            // 设置前缀
            switch (rarity) {
                case RarityConstants.RARITY_COMMON:
                    prefixComponent = Component.translatable("rarity.core.common");
                    break;
                case RarityConstants.RARITY_UNCOMMON:
                    prefixComponent = Component.translatable("rarity.core.uncommon");
                    break;
                case RarityConstants.RARITY_RARE:
                    prefixComponent = Component.translatable("rarity.core.rare");
                    break;
                case RarityConstants.RARITY_EPIC:
                    prefixComponent = Component.translatable("rarity.core.epic");
                    break;
                case RarityConstants.RARITY_LEGENDARY:
                    prefixComponent = Component.translatable("rarity.core.legendary");
                    break;
                case RarityConstants.RARITY_MYTHICAL:
                    prefixComponent = Component.translatable("rarity.core.mythical");
                    break;
                case RarityConstants.RARITY_UNIQUE:
                    prefixComponent = Component.translatable("rarity.core.unique");
                    break;
                default:
                    return;
            }
            
            // 应用前缀颜色：client.json 的 enableTooltipColor 为总闸
            // 关闭时 RarityClientConfig 颜色不生效；开启时使用 RarityClientConfig 定义的 RGB 颜色
            if (ClientConfigManager.isEnableTooltipColor()) {
                prefixComponent = prefixComponent.withStyle(Style.EMPTY.withColor(rgbColor));
            }
        
            // 构建星星组件（使用 RGB 颜色版本，内部同样检查总闸）
            MutableComponent starsComponent = ComponentBuilder.buildRarityComponent(rarity, rgbColor);
            MutableComponent rarityComponent = Component.empty().append(prefixComponent).append(starsComponent);
            
            // 触发 RarityTooltipEvent，允许监听器修改 tooltip 组件
            List<Component> tipComponents = new ArrayList<>();
            tipComponents.add(rarityComponent);
            RarityTooltipEvent tipEvent = new RarityTooltipEvent(itemStack, displayRarity, tipComponents, false);
            NeoForge.EVENT_BUS.post(tipEvent);
            
            // 插入事件中所有组件到工具提示（逆序插入以保持顺序，使用快照副本防止并发修改）
            List<Component> tooltip = event.getToolTip();
            List<Component> eventComponents = new ArrayList<>(tipEvent.getTooltipComponents());
            for (int i = eventComponents.size() - 1; i >= 0; i--) {
                tooltip.add(1, eventComponents.get(i));
            }
        }
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

        // 获取物品ID
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return false;
        }

        // 检查是否在手动配置中有稀有度
        if (RarityRegistry.ITEM_RARITY_MAP.containsKey(itemId)) {
            return true;
        }

        // 检查是否在自动计算稀有度中有配置
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
        // RarityTooltipHandler: skipUnconfiguredItems config change handled
    }
}