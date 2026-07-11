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

    /** 注册物品稀有度，同步到客户端 */
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

    /** 获取 ItemStack 的稀有度 */
    public static int getRarity(@NotNull ItemStack itemStack) {
        return RarityRegistry.getRarity(itemStack);
    }

    /** 获取 Item 的稀有度 */
    public static int getRarity(@NotNull Item item) {
        return RarityRegistry.getRarity(item);
    }

    /** 获取标准化稀有度（小于 1 的值会钳制为 1） */
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

    /** 验证稀有度值是否有效（小于 1 视为无效） */
    public static boolean isValidRarity(int rarity) {
        return RarityValidator.isValidRarity(rarity);
    }

    /** 标准化稀有度值（小于 1 的值会钳制为 1） */
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
        return RarityStyleConfigManager.isItemNameColorEnabled(MIN_RARITY);
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
        java.util.Map<ResourceLocation, org.yanbwe.raritycore.event.RarityRegistryChangedEvent.RarityChange> changes =
                new java.util.HashMap<>();
        for (java.util.Map.Entry<Item, Integer> e : entries.entrySet()) {
            ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(e.getKey());
            Integer oldR = id != null ? RarityRegistry.getRarity(e.getKey()) : null;
            RarityRegistry.register(e.getKey(), e.getValue(), false);
            changes.put(id, new org.yanbwe.raritycore.event.RarityRegistryChangedEvent.RarityChange(oldR, e.getValue()));
        }
        RarityRegistry.syncIncrementalChangesToClients();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new org.yanbwe.raritycore.event.RarityRegistryChangedEvent(changes));
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

    /** 返回所有被解析为指定稀有度等级集合中任一等级的物品 */
    public static java.util.List<net.minecraft.world.item.Item> getItemsByRarities(java.util.Set<Integer> rarities) {
        return RarityRegistry.getItemsByRarities(rarities);
    }

    /** 返回所有被解析为指定稀有度等级集合中任一等级的物品 ID */
    public static java.util.List<ResourceLocation> getItemIdsByRarities(java.util.Set<Integer> rarities) {
        java.util.List<ResourceLocation> ids = new java.util.ArrayList<>();
        for (net.minecraft.world.item.Item item : RarityRegistry.getItemsByRarities(rarities)) {
            ids.add(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item));
        }
        return ids;
    }

    /** 返回被解析为指定稀有度等级的物品数量 */
    public static int getRarityCount(int rarity) {
        return RarityRegistry.getRarityCount(rarity);
    }

    /** 返回当前全部已解析稀有度等级的快照（显式配置与自动计算合并） */
    public static java.util.Map<ResourceLocation, Integer> getAllRarityEntries() {
        return RarityRegistry.getAllRarityEntries();
    }

    // ══════════════════════════════════════════════════════
    // 配置重载
    // ══════════════════════════════════════════════════════

    /** 触发完整配置重载（命令源为空，视为程序化触发） */
    public static void reloadConfigs() {
        org.yanbwe.raritycore.service.ConfigReloadService.reloadAllConfigs(null, false);
    }

    // ══════════════════════════════════════════════════════
    // 视觉表现批量写入与诊断
    // ══════════════════════════════════════════════════════

    /** 开始批量写入，期间 setter 不逐条写盘与发布事件 */
    public static void beginStyleBatch() {
        org.yanbwe.raritycore.config.RarityStyleConfigManager.beginStyleBatch();
    }

    /** 结束批量写入，统一写盘并发布一次聚合事件 */
    public static void endStyleBatch() {
        org.yanbwe.raritycore.config.RarityStyleConfigManager.endStyleBatch();
    }

    /** 以结构化补丁整体写入某等级视觉表现配置（null 字段保留现有值） */
    public static void setStyle(org.yanbwe.raritycore.config.RarityStyleConfigManager.StylePatch patch) {
        org.yanbwe.raritycore.config.RarityStyleConfigManager.setStyle(patch);
    }

    /**
     * 校验并标准化稀有度等级
     * 等级小于 MIN_RARITY 时返回 1，其余等级保持原值
     */
    public static int validateRarity(int rarity) {
        if (rarity < MIN_RARITY) {
            return RarityValidator.normalizeRarity(rarity);
        }
        return rarity;
    }

    /** 返回某等级生效视觉表现的不可变快照（border/tooltip/star 合并结果） */
    public static org.yanbwe.raritycore.config.RarityStyleConfigManager.StyleSnapshot getStyleSnapshot(int rarity) {
        return org.yanbwe.raritycore.config.RarityStyleConfigManager.getStyleSnapshot(rarity);
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

    public static final int MIN_RARITY = RarityConstants.MIN_RARITY;
    public static final int MAX_RARITY = RarityConstants.MAX_RARITY;
    public static final int DEFAULT_RGB_COLOR = RarityColorUtil.DEFAULT_RGB_COLOR;

    /** 正式 API 版本号（仅供联动模组做特性探测，与模组版本解耦） */
    public static final int API_VERSION = 1400;

    /**
     * 模组是否可用（类与基础依赖已加载）
     */
    public static boolean isAvailable() {
        return true;
    }

    /**
     * 获取模组的版本号（来自 mods.toml 的 version 字段）
     * @return 版本字符串，获取失败时返回 unknown
     */
    public static String getModVersion() {
        try {
            return net.minecraftforge.fml.ModList.get()
                    .getModContainerById(org.yanbwe.raritycore.RarityCore.MODID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 获取当前配置版本号（配置重载时递增，用于客户端同步校验）
     */
    public static int getConfigVersion() {
        return org.yanbwe.raritycore.network.SyncManager.getConfigVersion();
    }
}
