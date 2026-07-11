package org.yanbwe.raritycore.client;

/*
  物品稀有度提示
  名字下面显示稀有度等级
 */

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.common.MinecraftForge;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.event.RarityTooltipEvent;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.ComponentBuilder;
import org.yanbwe.raritycore.util.RarityValidator;
import org.yanbwe.raritycore.util.StringResolver;

@Mod.EventBusSubscriber(modid = RarityCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RarityTooltipHandler {

    @SubscribeEvent
    @SuppressWarnings("null")
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!ClientConfigManager.isEnableTooltipInsert()) {
            return;
        }

        // 检测到 ColorTooltips 模组时跳过本模组工具提示插入，避免重复展示
        if (org.yanbwe.raritycore.compat.CompatibilityManager.isColorTooltipsLoaded()) {
            return;
        }

        ItemStack itemStack = event.getItemStack();
        Item item = itemStack.getItem();

        Integer rarity = RenderCacheManager.getCachedRarity(itemStack);
        if (rarity == null) {
            rarity = RarityRegistry.getRarity(itemStack);
            if (rarity != null) {
                RenderCacheManager.cacheItemStackRarity(itemStack, rarity);
            }
        }
        if (rarity == null) {
            rarity = RarityStyleConfigManager.getDefaultsNoRarityDefaultRarity();
        }

        if (ClientConfigManager.isSkipUnconfiguredItems() && !RarityRegistry.hasConfiguredRarity(item, itemStack)) {
            return;
        }

        rarity = applyApotheosisRarityFallback(itemStack, item, rarity);

        RarityTooltipEvent tooltipEvent = new RarityTooltipEvent(itemStack, rarity,
            new java.util.ArrayList<>());
        MinecraftForge.EVENT_BUS.post(tooltipEvent);
        java.util.List<Component> extraTooltips = tooltipEvent.getTooltipList();

        // 全局主开关 + 该等级 show 开关
        if (!RarityStyleConfigManager.isLevelTooltipEnabled(rarity)) {
            return;
        }

        boolean tooltipColorOn = ClientConfigManager.isEnableTooltipColor();
        int rgbColor = RarityStyleConfigManager.getColor(RarityValidator.normalizeRarity(rarity));
        Style colorStyle = tooltipColorOn ? Style.EMPTY.withColor(TextColor.fromRgb(rgbColor)) : Style.EMPTY;

        MutableComponent rarityComponent = buildStandardTooltipComponent(RarityValidator.normalizeRarity(rarity), colorStyle, tooltipColorOn);

        ComponentBuilder.insertIntoTooltip(event.getToolTip(), rarityComponent);
        if (!extraTooltips.isEmpty()) {
            event.getToolTip().addAll(extraTooltips);
        }
    }

    /**
     * 构建标准（1~7 级）工具提示组件，按 content 分段着色
     */
    private static MutableComponent buildStandardTooltipComponent(int rarity, Style colorStyle, boolean colorOn) {
        RarityStyleConfigManager.TooltipConfig tc = RarityStyleConfigManager.getTooltip(rarity);
        boolean levelColored = colorOn && tc.level.colored && RarityStyleConfigManager.isTooltipColorEnabled();
        boolean starColored = colorOn && tc.star.colored && RarityStyleConfigManager.isTooltipColorEnabled();
        boolean totalColored = colorOn && tc.colored && RarityStyleConfigManager.isTooltipColorEnabled();
        Component levelName = buildLevelComponent(rarity);
        Component starComp = Component.literal(ComponentBuilder.getStars(rarity));
        return buildSegmentedComponent(rarity, levelName, starComp, colorStyle, levelColored, starColored, totalColored);
    }

    /**
     * 构建 level 段组件（翻译键解析 + fallback）
     */
    private static Component buildLevelComponent(int rarity) {
        String keyTemplate = RarityStyleConfigManager.getLevelTranslationKey(rarity);
        String key = keyTemplate.replace("{level}", String.valueOf(rarity));
        Component resolved;
        if (StringResolver.isTranslationKey(key)) {
            String realKey = StringResolver.extractKey(key);
            if (StringResolver.isKeyMissing(realKey)) {
                // fallback（声明式占位符，不注入 %d 参数）
                String fb = RarityStyleConfigManager.getLevelFallbackKey(rarity).replace("{level}", String.valueOf(rarity));
                if (StringResolver.isTranslationKey(fb)) {
                    resolved = Component.translatable(StringResolver.extractKey(fb));
                } else {
                    resolved = Component.literal(StringResolver.resolveEmbeddedKeys(fb));
                }
            } else {
                resolved = Component.translatable(realKey);
            }
        } else {
            resolved = Component.literal(StringResolver.resolveEmbeddedKeys(key));
        }
        return resolved;
    }

    /**
     * 按 content 模板将 level / star 分段插入，并对各段独立着色
     * 当 totalColored 开启时，对整个工具提示行着色（level/star 段不再单独着色，避免重复上色）
     * 支持 content 中重复出现占位符（每次出现均被替换）
     */
    private static MutableComponent buildSegmentedComponent(int rarity, Component levelName, Component starComp,
                                                             Style colorStyle, boolean levelColored, boolean starColored,
                                                             boolean totalColored) {
        String content = RarityStyleConfigManager.getTooltipContent(rarity);
        MutableComponent result = Component.empty();
        final String levelToken = "@{level}";
        final String starToken = "@{star}";
        int idxLevel = content.indexOf(levelToken);
        int idxStar = content.indexOf(starToken);
        if (idxLevel < 0 && idxStar < 0) {
            // 无占位符，按字面量整体处理
            return result.append(Component.literal(StringResolver.resolveEmbeddedKeys(content)));
        }
        int cursor = 0;
        while (cursor < content.length()) {
            int nextLevel = content.indexOf(levelToken, cursor);
            int nextStar = content.indexOf(starToken, cursor);
            int next = content.length();
            boolean isLevel = false;
            boolean isStar = false;
            if (nextLevel >= 0) {
                next = nextLevel;
                isLevel = true;
            }
            if (nextStar >= 0 && nextStar < next) {
                next = nextStar;
                isLevel = false;
                isStar = true;
            }
            if (next == cursor) {
                // 命中占位符
                if (isLevel) {
                    result.append(totalColored ? levelName : (levelColored ? levelName.copy().withStyle(colorStyle) : levelName));
                    cursor += levelToken.length();
                } else {
                    result.append(totalColored ? starComp : (starColored ? starComp.copy().withStyle(colorStyle) : starComp));
                    cursor += starToken.length();
                }
                continue;
            }
            // 静态文本段，直到下一个占位符
            result.append(Component.literal(StringResolver.resolveEmbeddedKeys(content.substring(cursor, next))));
            cursor = next;
        }
        // 总染色：对整个工具提示行上色（含静态分隔符）
        return totalColored ? result.withStyle(colorStyle) : result;
    }

    private static int applyApotheosisRarityFallback(ItemStack itemStack, Item item, int currentRarity) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return currentRarity;
        }
        Integer apothRarity = ApotheosisAdapter.getMappedRarity(itemStack);
        if (apothRarity != null && apothRarity != currentRarity) {
            RarityCore.LOGGER.debug("Apotheosis rarity fallback activated for {}: registry={}, apotheosis={}",
                ForgeRegistries.ITEMS.getKey(item), currentRarity, apothRarity);
            return apothRarity;
        }
        return currentRarity;
    }

    public static void handleSkipConfigChange() {
        RenderCacheManager.clearAllCache();
    }
}
