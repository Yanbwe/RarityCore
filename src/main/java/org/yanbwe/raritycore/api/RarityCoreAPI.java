package org.yanbwe.raritycore.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yanbwe.raritycore.config.RarityClientConfigManager;
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

    /** 通过 RarityClientConfig 获取逐级配置的颜色 */
    public static int getRarityColor(int rarity) {
        return RarityClientConfigManager.getRarityColor(rarity);
    }

    /** 通过 RarityClientConfig 获取逐级配置的纹理路径 */
    public static String getRarityTexture(int rarity) {
        return RarityClientConfigManager.getRarityTexture(rarity);
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

    /** 客户端主开关：是否渲染物品边框 */
    public static boolean isBorderRenderingEnabled() {
        return org.yanbwe.raritycore.config.ClientConfigManager.isEnableItemBorderRendering();
    }

    /** 客户端主开关：是否插入工具提示 */
    public static boolean isTooltipInsertEnabled() {
        return org.yanbwe.raritycore.config.ClientConfigManager.isEnableTooltipInsert();
    }

    /** 客户端主开关：是否变色物品名称 */
    public static boolean isNameColorEnabled() {
        return org.yanbwe.raritycore.config.ClientConfigManager.isEnableItemNameColor();
    }

    /** 逐级开关：该等级是否渲染边框 */
    public static boolean isLevelRendererEnabled(int rarity) {
        return RarityClientConfigManager.isRendererEnabled(rarity);
    }

    /** 逐级开关：该等级是否显示工具提示 */
    public static boolean isLevelTooltipEnabled(int rarity) {
        return RarityClientConfigManager.isTooltipsEnabled(rarity);
    }

    /** 逐级开关：该等级是否变色名称 */
    public static boolean isLevelNameColorEnabled(int rarity) {
        return RarityClientConfigManager.isNameColorEnabled(rarity);
    }

    /** 服务端：是否启用 NBT 稀有度控制 */
    public static boolean isNbtRarityControlEnabled() {
        return ServerConfigManager.isEnableNbtRarityControl();
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
