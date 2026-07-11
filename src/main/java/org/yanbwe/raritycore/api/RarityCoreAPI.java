package org.yanbwe.raritycore.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.config.TagRarityConfigManager;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.event.RarityQueryEvent;
import org.yanbwe.raritycore.event.RarityTooltipEvent;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

import java.util.List;
import java.util.Map;

/**
 * RarityCore 正式公共 API
 * 供其他模组直接调用的稳定接口
 * 
 * <h2>快速使用</h2>
 * <pre>{@code
 * // 注册稀有度
 * RarityCoreAPI.registerRarity(myItem, 5);
 * 
 * // 查询稀有度
 * int rarity = RarityCoreAPI.getRarity(itemStack);
 * 
 * // 获取颜色
 * int rgb = RarityCoreAPI.getRarityColor(rarity);
 * 
 * // 监听事件
 * MinecraftForge.EVENT_BUS.addListener(RarityCoreAPI::onRarityChanged);
 * }</pre>
 */
public final class RarityCoreAPI {

    private RarityCoreAPI() {}

    // ══════════════════════════════════════════════════════
    // 稀有度注册与查询
    // ══════════════════════════════════════════════════════

    /** 注册物品稀有度 (1-7)，同步到客户端 */
    public static void registerRarity(@NotNull Item item, int rarity) {
        RarityRegistry.register(item, rarity, true);
    }

    /** 注册物品稀有度，可选是否同步 */
    public static void registerRarity(@NotNull Item item, int rarity, boolean sync) {
        RarityRegistry.register(item, rarity, sync);
    }

    /** 删除物品稀有度注册 */
    public static void unregisterRarity(@NotNull Item item) {
        RarityRegistry.unregister(item, true);
    }

    /** 获取 ItemStack 的稀有度 (1-7) */
    public static int getRarity(@NotNull ItemStack itemStack) {
        return RarityRegistry.getRarity(itemStack);
    }

    /** 获取 Item 的稀有度 (1-7) */
    public static int getRarity(@NotNull Item item) {
        return RarityRegistry.getRarity(item);
    }

    /** 获取标准化稀有度（非法值会被规范化到 1-7 范围） */
    public static int getNormalizedRarity(@NotNull ItemStack itemStack) {
        return RarityRegistry.getNormalizedRarity(itemStack);
    }

    /** 获取标准化稀有度 */
    public static int getNormalizedRarity(@NotNull Item item) {
        return RarityRegistry.getNormalizedRarity(item);
    }

    /** 获取本地化稀有度工具提示 */
    public static String getLocalizedTooltip(@NotNull ItemStack itemStack) {
        return RarityRegistry.getLocalizedRarityTooltip(itemStack);
    }

    /** 获取本地化稀有度工具提示 */
    public static String getLocalizedTooltip(@NotNull Item item) {
        return RarityRegistry.getLocalizedRarityTooltip(item);
    }

    /** 检查物品是否有已配置的稀有度 */
    public static boolean hasConfiguredRarity(@NotNull Item item, @Nullable ItemStack itemStack) {
        return RarityRegistry.hasConfiguredRarity(item, itemStack);
    }

    /** 获取所有已注册的稀有度映射（只读） */
    public static Map<ResourceLocation, Integer> getRegistryMap() {
        return RarityRegistry.getItemRarityMap();
    }

    // ══════════════════════════════════════════════════════
    // 颜色
    // ══════════════════════════════════════════════════════

    /** 获取稀有度等级对应的 RGB 颜色 (0xRRGGBB) */
    public static int getRarityRgbColor(int rarity) {
        return RarityColorUtil.getRarityRgbColor(rarity);
    }

    /** 通过 RarityStyle 获取逐级配置的颜色 */
    public static int getRarityColor(int rarity) {
        return RarityStyleConfigManager.getColor(rarity);
    }

    /** 通过 RarityStyle 获取逐级配置的纹理路径 */
    public static String getRarityTexture(int rarity) {
        return RarityStyleConfigManager.getBorderTexture(rarity);
    }

    /** 解析十六进制颜色字符串 "#RRGGBB" 为 RGB int */
    public static int parseColor(String hex) {
        return RarityColorUtil.parseRgbColor(hex);
    }

    /** RGB int 格式化为 #RRGGBB 字符串 */
    public static String formatColor(int rgb) {
        return RarityColorUtil.formatRgbColor(rgb);
    }

    // ══════════════════════════════════════════════════════
    // 验证
    // ══════════════════════════════════════════════════════

    /** 验证稀有度值是否在有效范围内 (1-7) */
    public static boolean isValidRarity(int rarity) {
        return RarityValidator.isValidRarity(rarity);
    }

    /** 标准化稀有度值（非法值会被规范化到 1-7 范围） */
    public static int normalizeRarity(int rarity) {
        return RarityValidator.normalizeRarity(rarity);
    }

    // ══════════════════════════════════════════════════════
    // Tag 稀有度
    // ══════════════════════════════════════════════════════

    /** 获取物品匹配的 Tag 规则中的最高稀有度 (0 表示无匹配) */
    public static int getTagRarity(Item item) {
        return TagRarityConfigManager.getHighestTagRarity(item);
    }

    /** 获取已加载的 Tag 规则数量 */
    public static int getTagRuleCount() {
        return TagRarityConfigManager.getRuleCount();
    }

    // ══════════════════════════════════════════════════════
    // 配置
    // ══════════════════════════════════════════════════════

    /** 主开关：是否渲染物品边框 */
    public static boolean isBorderRenderingEnabled() {
        return RarityStyleConfigManager.isBorderEnabled();
    }

    /** 主开关：是否插入工具提示 */
    public static boolean isTooltipInsertEnabled() {
        return RarityStyleConfigManager.isTooltipEnabled();
    }

    /** 主开关：是否变色物品名称 */
    public static boolean isNameColorEnabled() {
        return RarityStyleConfigManager.isItemNameColorEnabled(RARITY_COMMON);
    }

    /** 逐级开关：该等级是否渲染边框 */
    public static boolean isLevelRendererEnabled(int rarity) {
        return RarityStyleConfigManager.isLevelRendererEnabled(rarity);
    }

    /** 逐级开关：该等级是否显示工具提示 */
    public static boolean isLevelTooltipEnabled(int rarity) {
        return RarityStyleConfigManager.isLevelTooltipEnabled(rarity);
    }

    /** 逐级开关：该等级是否变色名称 */
    public static boolean isLevelNameColorEnabled(int rarity) {
        return RarityStyleConfigManager.isLevelNameColorEnabled(rarity);
    }

    /** 服务端：是否启用 NBT 稀有度控制 */
    public static boolean isNbtRarityControlEnabled() {
        return ServerConfigManager.isEnableNbtRarityControl();
    }

    // ══════════════════════════════════════════════════════
    // 批量注册
    // ══════════════════════════════════════════════════════

    /** 批量注册物品稀有度映射（不逐条同步，注册结束后统一同步一次） */
    public static void registerRarities(@NotNull java.util.Map<Item, Integer> entries) {
        for (java.util.Map.Entry<Item, Integer> e : entries.entrySet()) {
            RarityRegistry.register(e.getKey(), e.getValue(), false);
        }
        RarityRegistry.syncIncrementalChangesToClients();
    }

    // ══════════════════════════════════════════════════════
    // 稀有度物品查询
    // ══════════════════════════════════════════════════════

    /** 返回所有被解析为指定稀有度等级的物品 */
    public static java.util.List<net.minecraft.world.item.Item> getItemsByRarity(int rarity) {
        return RarityRegistry.getItemsByRarity(rarity);
    }

    /** 返回所有被解析为指定稀有度等级的物品 ID */
    public static java.util.List<ResourceLocation> getItemIdsByRarity(int rarity) {
        return RarityRegistry.getItemIdsByRarity(rarity);
    }

    /** 返回当前出现过的稀有度等级集合 */
    public static java.util.Set<Integer> getConfiguredRarities() {
        return RarityRegistry.getConfiguredRarities();
    }

    // ══════════════════════════════════════════════════════
    // 配置重载
    // ══════════════════════════════════════════════════════

    /** 触发完整配置重载（命令源为空，视为程序化触发） */
    public static void reloadConfigs() {
        org.yanbwe.raritycore.service.ConfigReloadService.reloadAllConfigs(null, false);
    }

    // ══════════════════════════════════════════════════════
    // V14 视觉表现查询与写入
    // ══════════════════════════════════════════════════════

    /** 主开关：工具提示是否染色 */
    public static boolean isTooltipColorEnabled() {
        return RarityStyleConfigManager.isTooltipColorEnabled();
    }

    /** 主开关：是否渲染物品边框 */
    public static boolean isBorderEnabled() {
        return RarityStyleConfigManager.isBorderEnabled();
    }

    /** 主开关：是否插入工具提示 */
    public static boolean isTooltipEnabled() {
        return RarityStyleConfigManager.isTooltipEnabled();
    }

    /** 无稀有度物品是否跳过渲染 */
    public static boolean isNoRaritySkip() {
        return RarityStyleConfigManager.getDefaultsNoRaritySkip();
    }

    /** 无稀有度物品兜底等级 */
    public static int getNoRarityDefaultRarity() {
        return RarityStyleConfigManager.getDefaultsNoRarityDefaultRarity();
    }

    /** 逐级工具提示内容 */
    public static String getTooltipContent(int rarity) {
        return RarityStyleConfigManager.getTooltipContent(rarity);
    }

    /** 逐级 level 段翻译键 */
    public static String getLevelTranslationKey(int rarity) {
        return RarityStyleConfigManager.getLevelTranslationKey(rarity);
    }

    /** 逐级 level 段回退键 */
    public static String getLevelFallbackKey(int rarity) {
        return RarityStyleConfigManager.getLevelFallbackKey(rarity);
    }

    /** 逐级星星配置 */
    public static RarityStyleConfigManager.StarSegmentConfig getStarConfig(int rarity) {
        return RarityStyleConfigManager.getStarConfig(rarity);
    }

    /** 大于 MAX_RARITY 的特殊稀有度文本 */
    public static String getSpecialRarityText(int rarity) {
        return RarityStyleConfigManager.getSpecialRarityText(rarity);
    }

    /** 逐级边框是否使用纹理 */
    public static boolean isBorderUseTexture(int rarity) {
        return RarityStyleConfigManager.isBorderUseTexture(rarity);
    }

    /** 逐级边框样式（1=实心，0=空心） */
    public static int getBorderStyle(int rarity) {
        return RarityStyleConfigManager.getBorderStyle(rarity);
    }

    /** 边框回退纹理 */
    public static String getBorderFallback() {
        return RarityStyleConfigManager.getBorderFallback();
    }

    // ── 写入 ──

    /** 设置主开关：是否渲染物品边框 */
    public static void setBorderEnabled(boolean enable) {
        RarityStyleConfigManager.setBorderEnabled(enable);
    }

    /** 设置主开关：是否插入工具提示 */
    public static void setTooltipEnabled(boolean enable) {
        RarityStyleConfigManager.setTooltipEnabled(enable);
    }

    /** 设置主开关：工具提示是否染色 */
    public static void setTooltipColorEnabled(boolean enable) {
        RarityStyleConfigManager.setTooltipColorEnabled(enable);
    }

    /** 设置无稀有度物品跳过渲染 */
    public static void setNoRaritySkip(boolean skip) {
        RarityStyleConfigManager.setNoRaritySkip(skip);
    }

    /** 设置无稀有度物品兜底等级 */
    public static void setNoRarityDefaultRarity(int rarity) {
        RarityStyleConfigManager.setNoRarityDefaultRarity(rarity);
    }

    /** 设置逐级边框是否使用纹理 */
    public static void setBorderUseTexture(int rarity, boolean useTexture) {
        RarityStyleConfigManager.setBorderUseTexture(rarity, useTexture);
    }

    /** 设置逐级边框样式 */
    public static void setBorderStyle(int rarity, int style) {
        RarityStyleConfigManager.setBorderStyle(rarity, style);
    }

    /** 设置逐级工具提示内容 */
    public static void setTooltipContent(int rarity, String content) {
        RarityStyleConfigManager.setTooltipContent(rarity, content);
    }

    /** 设置逐级星星显示模式 */
    public static void setStarMode(int rarity, String mode) {
        RarityStyleConfigManager.setStarMode(rarity, mode);
    }

    /** 设置逐级星星重复字符 */
    public static void setStarRepeatChar(int rarity, String repeatChar) {
        RarityStyleConfigManager.setStarRepeatChar(rarity, repeatChar);
    }

    /** 设置大于 MAX_RARITY 的特殊稀有度文本 */
    public static void setSpecialRarityText(int rarity, String text) {
        RarityStyleConfigManager.setSpecialRarityText(rarity, text);
    }

    // ══════════════════════════════════════════════════════
    // 网络同步
    // ══════════════════════════════════════════════════════

    /** 同步稀有度数据到所有客户端 */
    public static void syncToClients() {
        RarityRegistry.syncRarityToClients();
    }

    // ══════════════════════════════════════════════════════
    // 常量
    // ══════════════════════════════════════════════════════

    public static final int RARITY_COMMON = RarityConstants.RARITY_COMMON;
    public static final int RARITY_UNCOMMON = RarityConstants.RARITY_UNCOMMON;
    public static final int RARITY_RARE = RarityConstants.RARITY_RARE;
    public static final int RARITY_EPIC = RarityConstants.RARITY_EPIC;
    public static final int RARITY_LEGENDARY = RarityConstants.RARITY_LEGENDARY;
    public static final int RARITY_MYTHICAL = RarityConstants.RARITY_MYTHICAL;
    public static final int RARITY_UNIQUE = RarityConstants.RARITY_UNIQUE;
    public static final int MIN_RARITY = RarityConstants.MIN_RARITY;
    public static final int MAX_RARITY = RarityConstants.MAX_RARITY;
    public static final int DEFAULT_RGB_COLOR = RarityColorUtil.DEFAULT_RGB_COLOR;
}
