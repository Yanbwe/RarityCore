package org.yanbwe.raritycore.client;

/*
  物品稀有度提示
  名字下面显示稀有度等级
 */

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.compat.colortooltips.ColorTooltipsCompat;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.event.RarityTooltipEvent;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.ComponentBuilder;
import org.yanbwe.raritycore.util.RarityConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = RarityCore.MODID, value = Dist.CLIENT)
public class RarityTooltipHandler {

    // ═══════════════════════════════════════════════════════════════
    // 缓存：避免每帧 (60fps) 重新创建相同的 Component 对象
    // ═══════════════════════════════════════════════════════════════

    /** 翻译前缀组件缓存：rarityLevel (1-7) → MutableComponent */
    private static final MutableComponent[] PREFIX_CACHE = new MutableComponent[8];

    /** 星星组件缓存（普通稀有度）：rarityLevel → 带颜色星星的完整 tooltip 组件 */
    private static final ConcurrentHashMap<Integer, MutableComponent> rarityTooltipCache = new ConcurrentHashMap<>();

    /** 前缀组件已初始化标记 */
    private static volatile boolean prefixCacheInitialized = false;

    /**
     * 初始化翻译前缀缓存（懒加载，首次调用时填充）
     */
    private static MutableComponent getCachedPrefix(int rarity) {
        if (!prefixCacheInitialized) {
            synchronized (PREFIX_CACHE) {
                if (!prefixCacheInitialized) {
                    PREFIX_CACHE[1] =
                        Component.translatable("rarity.core.1");
                    PREFIX_CACHE[2] =
                        Component.translatable("rarity.core.2");
                    PREFIX_CACHE[3] =
                        Component.translatable("rarity.core.3");
                    PREFIX_CACHE[4] =
                        Component.translatable("rarity.core.4");
                    PREFIX_CACHE[5] =
                        Component.translatable("rarity.core.5");
                    PREFIX_CACHE[6] =
                        Component.translatable("rarity.core.6");
                    PREFIX_CACHE[7] =
                        Component.translatable("rarity.core.7");
                    prefixCacheInitialized = true;
                }
            }
        }
        if (rarity >= 1 && rarity <= 7) {
            return PREFIX_CACHE[rarity];
        }
        return null;
    }

    /**
     * 使所有 tooltip 缓存失效（配置重载时调用）
     */
    public static void invalidateCaches() {
        synchronized (PREFIX_CACHE) {
            prefixCacheInitialized = false;
        }
        rarityTooltipCache.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // 主事件处理器
    // ═══════════════════════════════════════════════════════════════

    @SubscribeEvent
    @SuppressWarnings("null")
    public static void onItemTooltip(ItemTooltipEvent event) {
        RarityExclusionManager.setRenderingTooltipItem(true);
        try {
            // 检查是否启用工具提示插入
            if (!ClientConfigManager.isEnableTooltipInsert()) {
                return;
            }

            // 检测到 colortooltips 模组时，由其接管工具提示渲染，本模组跳过插入
            if (ColorTooltipsCompat.isLoaded()) {
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
        if (ClientConfigManager.isSkipUnconfiguredItems() && !RarityRegistry.hasConfiguredRarity(item)) {
            return;
        }

        RarityStyleConfigManager styleMgr = RarityStyleConfigManager.getInstance();

        // 从 RarityStyleConfigManager 获取该等级的 tooltip 配置
        if (!styleMgr.resolveTooltip(rarity).show) {
            return;
        }

        // 从 RarityStyleConfigManager 获取 RGB 颜色（含等级 >7 回退）
        int rgbColor = styleMgr.resolveColor(rarity);

        // 所有等级统一走标准路径
        // 从缓存获取 tooltip 组件，避免每帧重新构建 translatable + stars
        MutableComponent rarityComponent = getOrCreateRarityTooltipComponent(rarity, rgbColor);

        // 发送 RarityTooltipEvent
        List<Component> tipComponents = new ArrayList<>();
        tipComponents.add(rarityComponent);
        RarityTooltipEvent tipEvent = new RarityTooltipEvent(itemStack, rarity, tipComponents);
        NeoForge.EVENT_BUS.post(tipEvent);

        List<Component> tooltip = event.getToolTip();
        List<Component> eventComponents = new ArrayList<>(tipEvent.getTooltipComponents());
        for (int i = eventComponents.size() - 1; i >= 0; i--) {
            tooltip.add(1, eventComponents.get(i));
        }
        } finally {
            RarityExclusionManager.setRenderingTooltipItem(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 缓存的组件构建方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 获取或创建标准稀有度 tooltip 组件（带缓存）。
     * 缓存 key：rarity * 0x1000000 + (rgbColor & 0xFFFFFF)
     * 注意：由于 RarityStyleConfigManager 可能被重新加载改变颜色，
     * 缓存会在 invalidateCaches() 时清空。
     */
    private static MutableComponent getOrCreateRarityTooltipComponent(int rarity, int rgbColor) {
        int cacheKey = (rarity << 24) | (rgbColor & 0x00FFFFFF);
        return rarityTooltipCache.computeIfAbsent(cacheKey, k -> {
            MutableComponent prefixComponent = getCachedPrefix(rarity);
            if (prefixComponent == null) {
                // 等级超过7时，使用 RarityStyleConfigManager 获取等级名称组件
                prefixComponent = (MutableComponent) RarityStyleConfigManager.getInstance().resolveLevelNameComponent(rarity);
            }

            // 应用前缀颜色
            if (RarityStyleConfigManager.getInstance().isTooltipColorEnabled()) {
                prefixComponent = prefixComponent.copy().withStyle(Style.EMPTY.withColor(rgbColor));
            }

            // 构建星星组件（使用 RGB 颜色版本）
            MutableComponent starsComponent = ComponentBuilder.buildRarityComponent(rarity, rgbColor);
            return Component.empty().append(prefixComponent).append(starsComponent);
        });
    }

    /**
     * 处理skipUnconfiguredItems配置变更
     * 当配置改变时调用此方法来刷新工具提示处理状态
     */
    public static void handleSkipConfigChange() {
        // 使工具提示缓存失效
        RenderCacheManager.clearAllCache();
        // 使本类中的翻译前缀和 tooltip 缓存失效
        invalidateCaches();
        // RarityTooltipHandler: skipUnconfiguredItems 配置变更已处理
    }
}