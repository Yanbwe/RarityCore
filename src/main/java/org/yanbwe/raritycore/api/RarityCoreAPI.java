package org.yanbwe.raritycore.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.yanbwe.raritycore.cache.RarityCacheCoordinator;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.config.TagRarityConfigLoader;
import org.yanbwe.raritycore.registry.ComponentRarityReader;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RarityCore 正式公共 API (V14)
 * 为其他模组和脚本提供统一的静态方法入口，支持无上限稀有度等级。
 *
 * <h2>快速使用</h2>
 * <pre>{@code
 * // 注册稀有度
 * RarityCoreAPI.registerRarity(myItem, 5);
 *
 * // 查询稀有度
 * int rarity = RarityCoreAPI.getRarity(itemStack);
 *
 * // 获取标准化稀有度
 * int normalized = RarityCoreAPI.getNormalizedRarity(itemStack);
 *
 * // 验证稀有度值
 * if (RarityCoreAPI.isValidRarity(rarity)) { ... }
 * }</pre>
 *
 * @see RarityRegistry 内部注册和查询实现
 * @see RarityValidator 稀有度值验证工具
 * @see RarityStyleConfigManager V14 样式配置管理
 */
public final class RarityCoreAPI {

    private RarityCoreAPI() {}

    // ══════════════════════════════════════════════════════
    // 常量
    // ══════════════════════════════════════════════════════

    public static final int MIN_RARITY = RarityConstants.MIN_RARITY;
    public static final int MAX_RARITY = RarityConstants.MAX_RARITY;
    /** 默认 RGB 颜色（1.21.1 现值） */
    public static final int DEFAULT_RGB_COLOR = RarityColorUtil.DEFAULT_RGB_COLOR;
    /** 正式 API 版本号（供联动模组做特性探测，与模组版本解耦） */
    public static final int API_VERSION = 1400;

    // ══════════════════════════════════════════════════════
    // 稀有度注册与查询
    // ══════════════════════════════════════════════════════

    /**
     * 注册物品的稀有度等级，并同步到客户端。
     *
     * @param item   要注册的物品
     * @param rarity 稀有度等级 (1+，无上限)
     */
    public static void registerRarity(@NotNull Item item, int rarity) {
        RarityRegistry.register(item, rarity, true);
    }

    /**
     * 获取 ItemStack 的稀有度等级。
     * 优先级：物品数据匹配 &gt; 神化模组 &gt; 本模组配置 &gt; 自动计算 &gt; 原版映射
     *
     * @param itemStack 要查询的物品栈
     * @return 稀有度等级 (1+，无上限)，默认返回 1
     */
    public static int getRarity(@NotNull ItemStack itemStack) {
        return RarityRegistry.getRarity(itemStack);
    }

    /**
     * 获取 Item 的稀有度等级。
     * 优先级：物品数据匹配 &gt; 神化模组 &gt; 本模组配置 &gt; 自动计算 &gt; 原版映射
     *
     * @param item 要查询的物品
     * @return 稀有度等级 (1+，无上限)，默认返回 1
     */
    public static int getRarity(@NotNull Item item) {
        return RarityRegistry.getRarity(item);
    }

    /**
     * 获取 ItemStack 的标准化稀有度等级。
     * 非法值（小于 1）会被规范化到有效范围内。
     *
     * @param itemStack 要查询的物品栈
     * @return 标准化后的稀有度等级 (≥1)
     */
    public static int getNormalizedRarity(@NotNull ItemStack itemStack) {
        return RarityRegistry.getNormalizedRarity(itemStack);
    }

    /**
     * 获取 Item 的标准化稀有度等级。
     * 非法值（小于 1）会被规范化到有效范围内。
     *
     * @param item 要查询的物品
     * @return 标准化后的稀有度等级 (≥1)
     */
    public static int getNormalizedRarity(@NotNull Item item) {
        return RarityRegistry.getNormalizedRarity(item);
    }

    /**
     * 删除物品的稀有度注册，并同步到客户端。
     *
     * @param item 要删除注册的物品
     */
    public static void unregisterRarity(@NotNull Item item) {
        RarityRegistry.unregister(item, true);
    }

    /**
     * 获取物品栈的本地化稀有度工具提示字符串。
     * 支持根据 ItemStack 的 NBT/组件数据获取更准确的稀有度。
     *
     * @param itemStack 要获取工具提示的物品栈
     * @return 本地化的稀有度工具提示
     */
    public static String getLocalizedTooltip(@NotNull ItemStack itemStack) {
        return RarityRegistry.getLocalizedRarityTooltip(itemStack);
    }

    /**
     * 获取物品的本地化稀有度工具提示字符串（使用默认 ItemStack）。
     * 简便方法，不需要手动创建 ItemStack。
     *
     * @param item 要获取工具提示的物品
     * @return 本地化的稀有度工具提示
     */
    public static String getLocalizedTooltip(@NotNull Item item) {
        return RarityRegistry.getLocalizedRarityTooltip(new ItemStack(item));
    }

    /**
     * 获取所有已注册的稀有度映射（只读视图）。
     *
     * @return 物品 ID 到稀有度等级的映射
     */
    public static Map<ResourceLocation, Integer> getRegistryMap() {
        return RarityRegistry.getItemRarityMap();
    }

    // ══════════════════════════════════════════════════════
    // 验证
    // ══════════════════════════════════════════════════════

    /**
     * 验证稀有度值是否不低于 MIN_RARITY。
     *
     * @param rarity 要验证的稀有度值
     * @return 如果有效返回 true
     */
    public static boolean isValidRarity(int rarity) {
        return RarityValidator.isValidRarity(rarity);
    }

    /**
     * 标准化稀有度值，遵循模组的包容性原则：
     * 小于 MIN_RARITY 的值视为 MIN_RARITY。
     *
     * @param rarity 原始稀有度值
     * @return 标准化后的稀有度值
     */
    public static int normalizeRarity(int rarity) {
        return RarityValidator.normalizeRarity(rarity);
    }

    // ══════════════════════════════════════════════════════
    // 网络同步
    // ══════════════════════════════════════════════════════

    /**
     * 同步稀有度数据到所有客户端。
     */
    public static void syncToClients() {
        RarityRegistry.syncRarityToClients();
    }

    /**
     * 同步稀有度数据到所有客户端（带重试机制）。
     */
    public static void syncToClientsWithRetry() {
        RarityRegistry.syncRarityToClientsWithRetry();
    }

    // ══════════════════════════════════════════════════════
    // 健康检查
    // ══════════════════════════════════════════════════════

    /**
     * 获取当前待同步的变更操作数量。
     *
     * @return 待处理的变更操作数
     */
    public static int getPendingChangeCount() {
        return RarityRegistry.getPendingChangeCount();
    }

    /**
     * 清理映射中引用无效物品 ID 的条目。
     *
     * @return 被移除的无效条目数量
     */
    public static int pruneInvalidEntries() {
        return RarityRegistry.pruneInvalidEntries();
    }

    // ══════════════════════════════════════════════════════
    // 注册（三参数版本）
    // ══════════════════════════════════════════════════════

    /**
     * 注册物品的稀有度等级，可选是否同步到客户端。
     *
     * @param item   要注册的物品
     * @param rarity 稀有度等级 (1+，无上限)
     * @param sync   是否同步到客户端
     */
    public static void registerRarity(@NotNull Item item, int rarity, boolean sync) {
        RarityRegistry.register(item, rarity, sync);
    }

    /**
     * 批量注册物品稀有度映射（不逐条同步，注册结束后统一同步一次）。
     * 注册完成后发布 {@link org.yanbwe.raritycore.event.RarityRegistryChangedEvent}。
     *
     * @param entries 物品到稀有度等级的映射
     */
    public static void registerRarities(@NotNull java.util.Map<Item, Integer> entries) {
        java.util.Map<ResourceLocation, Integer> changed = new java.util.HashMap<>();
        for (java.util.Map.Entry<Item, Integer> e : entries.entrySet()) {
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey());
            RarityRegistry.register(e.getKey(), e.getValue(), false);
            if (id != null) {
                changed.put(id, e.getValue());
            }
        }
        RarityRegistry.syncIncrementalChangesToClients();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new org.yanbwe.raritycore.event.RarityRegistryChangedEvent(changed, java.util.Collections.emptySet()));
    }

    // ══════════════════════════════════════════════════════
    // 颜色与纹理
    // ══════════════════════════════════════════════════════

    /**
     * 获取指定稀有度等级的 RGB 颜色值。
     * 通过 RarityStyleConfigManager 解析配置的颜色。
     *
     * @param level 稀有度等级
     * @return RGB 颜色值 (0xRRGGBB)
     */
    public static int getRarityColor(int level) {
        return RarityStyleConfigManager.getInstance().resolveColor(level);
    }

    /**
     * @deprecated 使用 {@link #getRarityColor(int)}
     */
    @Deprecated
    public static int getColor(int level) {
        return getRarityColor(level);
    }

    /**
     * 获取指定稀有度等级的纹理边框路径。
     *
     * @param level 稀有度等级
     * @return 纹理资源路径
     */
    public static String getRarityTexture(int level) {
        return RarityStyleConfigManager.getInstance().getBorderTexture(level);
    }

    /**
     * @deprecated 使用 {@link #getRarityTexture(int)}
     */
    @Deprecated
    public static String getTexture(int level) {
        return getRarityTexture(level);
    }

    /**
     * 解析 "#RRGGBB" 格式的十六进制颜色字符串为 RGB int 值。
     *
     * @param hex 十六进制颜色字符串（如 "#FFAA00"）
     * @return RGB 颜色值，解析失败返回默认灰色
     */
    public static int parseColor(String hex) {
        return RarityColorUtil.parseRgbColor(hex);
    }

    /**
     * 将 RGB int 颜色值格式化为 "#RRGGBB" 十六进制字符串。
     *
     * @param rgb RGB 颜色值
     * @return 十六进制颜色字符串
     */
    public static String formatColor(int rgb) {
        return RarityColorUtil.formatRgbColor(rgb);
    }

    // ══════════════════════════════════════════════════════
    // Tag 稀有度
    // ══════════════════════════════════════════════════════

    /**
     * 获取物品匹配的 Tag 规则最高稀有度。
     * 遍历 TagRarity.json 中定义的规则，返回第一个匹配 Tag 的稀有度等级。
     *
     * @param item 要查询的物品
     * @return 稀有度等级 (1+，无上限)，无匹配时返回 0
     */
    public static int getTagRarity(@NotNull Item item) {
        Integer result = RarityRegistry.getTagRarity(item);
        return result != null ? result : 0;
    }

    /**
     * 获取已加载的 Tag 稀有度规则数量。
     *
     * @return 规则数量
     */
    public static int getTagRuleCount() {
        return TagRarityConfigLoader.getLoadedRuleCount();
    }

    // ══════════════════════════════════════════════════════
    // 样式查询
    // ══════════════════════════════════════════════════════

    /** 逐级工具提示内容 */
    public static String getTooltipContent(int rarity) {
        return RarityStyleConfigManager.getInstance().resolveTooltip(rarity).content;
    }

    /** 逐级 level 段翻译键 */
    public static String getLevelTranslationKey(int rarity) {
        return RarityStyleConfigManager.getInstance().resolveTooltip(rarity).level.translationKey;
    }

    /** 逐级 level 段回退键 */
    public static String getLevelFallbackKey(int rarity) {
        return RarityStyleConfigManager.getInstance().resolveTooltip(rarity).level.fallback;
    }

    /** 逐级星星配置 */
    public static RarityStyleConfigManager.StarSegmentConfig getStarConfig(int rarity) {
        return RarityStyleConfigManager.getInstance().getStarConfig(rarity);
    }

    /** 逐级边框是否使用纹理 */
    public static boolean isBorderUseTexture(int rarity) {
        return RarityStyleConfigManager.getInstance().resolveBorder(rarity).useTexture;
    }

    /** 逐级边框样式（1=实心，0=空心） */
    public static int getBorderStyle(int rarity) {
        return RarityStyleConfigManager.getInstance().resolveBorder(rarity).style;
    }

    /** 边框回退纹理 */
    public static String getBorderFallback() {
        return RarityStyleConfigManager.getInstance().getBorderFallback();
    }

    // ══════════════════════════════════════════════════════
    // 样式写入
    // ══════════════════════════════════════════════════════

    /** 设置主开关：是否渲染物品边框 */
    public static void setBorderEnabled(boolean enable) {
        RarityStyleConfigManager.getInstance().setBorderEnabled(enable);
    }

    /** 设置主开关：是否插入工具提示 */
    public static void setTooltipEnabled(boolean enable) {
        RarityStyleConfigManager.getInstance().setTooltipEnabled(enable);
    }

    /** 设置主开关：工具提示是否染色 */
    public static void setTooltipColorEnabled(boolean enable) {
        RarityStyleConfigManager.getInstance().setTooltipColorEnabled(enable);
    }

    /** 设置无稀有度物品跳过渲染 */
    public static void setNoRaritySkip(boolean skip) {
        RarityStyleConfigManager.getInstance().setNoRaritySkip(skip);
    }

    /** 设置无稀有度物品兜底等级 */
    public static void setNoRarityDefaultRarity(int rarity) {
        RarityStyleConfigManager.getInstance().setNoRarityDefaultRarity(rarity);
    }

    /** 设置逐级边框是否使用纹理 */
    public static void setBorderUseTexture(int rarity, boolean useTexture) {
        RarityStyleConfigManager.getInstance().setBorderUseTexture(rarity, useTexture);
    }

    /** 设置逐级边框样式 */
    public static void setBorderStyle(int rarity, int style) {
        RarityStyleConfigManager.getInstance().setBorderStyle(rarity, style);
    }

    /** 设置逐级工具提示内容 */
    public static void setTooltipContent(int rarity, String content) {
        RarityStyleConfigManager.getInstance().setTooltipContent(rarity, content);
    }

    /** 设置逐级星星显示模式 */
    public static void setStarMode(int rarity, String mode) {
        RarityStyleConfigManager.getInstance().setTooltipStarMode(rarity, mode);
    }

    /** 设置逐级星星重复字符 */
    public static void setStarRepeatChar(int rarity, String repeatChar) {
        RarityStyleConfigManager.getInstance().setTooltipStarRepeatChar(rarity, repeatChar);
    }

    // ══════════════════════════════════════════════════════
    // 样式批量写入
    // ══════════════════════════════════════════════════════

    /** 开始批量写入，期间 setter 不逐条写盘与失效缓存 */
    public static void beginStyleBatch() {
        RarityStyleConfigManager.getInstance().beginStyleBatch();
    }

    /** 结束批量写入，统一写盘并失效缓存 */
    public static void endStyleBatch() {
        RarityStyleConfigManager.getInstance().endStyleBatch();
    }

    /** 以结构化补丁整体写入某等级视觉表现配置（null 字段保留现有值） */
    public static void setStyle(RarityStyleConfigManager.StylePatch patch) {
        RarityStyleConfigManager.getInstance().setStyle(patch);
    }

    /** 返回某等级生效视觉表现的不可变快照（border/tooltip/star 合并结果） */
    public static RarityStyleConfigManager.StyleSnapshot getStyleSnapshot(int rarity) {
        return RarityStyleConfigManager.getInstance().getStyleSnapshot(rarity);
    }

    // ══════════════════════════════════════════════════════
    // 遍历查询
    // ══════════════════════════════════════════════════════

    /**
     * 返回所有被解析为指定稀有度等级的物品（遍历物品注册表，覆盖配置/自动/原版/联动来源）。
     *
     * @param rarity 稀有度等级
     * @return 匹配物品的只读列表
     */
    public static List<Item> getItemsByRarity(int rarity) {
        return RarityRegistry.getItemsByRarity(rarity);
    }

    /**
     * 返回所有被解析为指定稀有度等级的物品 ID。
     *
     * @param rarity 稀有度等级
     * @return 匹配物品 ID 的只读列表
     */
    public static List<ResourceLocation> getItemIdsByRarity(int rarity) {
        return RarityRegistry.getItemIdsByRarity(rarity);
    }

    /**
     * 返回当前出现过的稀有度等级集合（手动配置 ∪ 自动计算 ∪ 无稀有度兜底等级）。
     *
     * @return 出现过的等级集合（只读）
     */
    public static Set<Integer> getConfiguredRarities() {
        return RarityRegistry.getConfiguredRarities();
    }

    /**
     * 返回所有被解析为指定稀有度等级集合中任一等级的物品。
     *
     * @param rarities 稀有度等级集合
     * @return 匹配物品的只读列表
     */
    public static List<Item> getItemsByRarities(Set<Integer> rarities) {
        return RarityRegistry.getItemsByRarities(rarities);
    }

    /**
     * 返回所有被解析为指定稀有度等级集合中任一等级的物品 ID（由物品列表组装）。
     *
     * @param rarities 稀有度等级集合
     * @return 匹配物品 ID 的列表
     */
    public static List<ResourceLocation> getItemIdsByRarities(Set<Integer> rarities) {
        List<ResourceLocation> ids = new java.util.ArrayList<>();
        for (Item item : RarityRegistry.getItemsByRarities(rarities)) {
            ids.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
        }
        return ids;
    }

    /**
     * 返回被解析为指定稀有度等级的物品数量。
     *
     * @param rarity 稀有度等级
     * @return 匹配物品数量
     */
    public static int getRarityCount(int rarity) {
        return RarityRegistry.getRarityCount(rarity);
    }

    /**
     * 返回当前全部已解析稀有度等级的快照（显式配置与自动计算合并，手动覆盖自动）。
     *
     * @return 合并后的只读映射
     */
    public static Map<ResourceLocation, Integer> getAllRarityEntries() {
        return RarityRegistry.getAllRarityEntries();
    }

    // ══════════════════════════════════════════════════════
    // 逐级表现开关
    // ══════════════════════════════════════════════════════

    /**
     * 检查指定稀有度等级是否启用边框渲染。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     */
    public static boolean isLevelRendererEnabled(int level) {
        return RarityStyleConfigManager.getInstance().isLevelRendererEnabled(level);
    }

    /**
     * 检查指定稀有度等级是否启用工具提示显示。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     */
    public static boolean isLevelTooltipEnabled(int level) {
        return RarityStyleConfigManager.getInstance().isLevelTooltipEnabled(level);
    }

    /**
     * 检查指定稀有度等级是否启用物品名称颜色修改。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     */
    public static boolean isLevelNameColorEnabled(int level) {
        return RarityStyleConfigManager.getInstance().isLevelNameColorEnabled(level);
    }

    // ══════════════════════════════════════════════════════
    // V14 全局开关 (RarityStyleConfigManager)
    // ══════════════════════════════════════════════════════

    /** 全局边框渲染开关。 */
    public static boolean isBorderEnabled() {
        return RarityStyleConfigManager.getInstance().isBorderEnabled();
    }

    /** 全局工具提示开关。 */
    public static boolean isTooltipEnabled() {
        return RarityStyleConfigManager.getInstance().isTooltipEnabled();
    }

    /** 全局工具提示染色开关。 */
    public static boolean isTooltipColorEnabled() {
        return RarityStyleConfigManager.getInstance().isTooltipColorEnabled();
    }

    /**
     * 逐级边框开关。
     *
     * @deprecated 使用 {@link #isLevelRendererEnabled(int)}
     */
    @Deprecated
    public static boolean isBorderEnabled(int level) {
        return RarityStyleConfigManager.getInstance().isLevelRendererEnabled(level);
    }

    /**
     * 逐级工具提示开关。
     *
     * @deprecated 使用 {@link #isLevelTooltipEnabled(int)}
     */
    @Deprecated
    public static boolean isTooltipEnabled(int level) {
        return RarityStyleConfigManager.getInstance().isLevelTooltipEnabled(level);
    }

    /** V14 别名，同 isBorderEnabled()。 */
    @Deprecated
    public static boolean isBorderRenderingEnabled() {
        return isBorderEnabled();
    }

    /** V14 别名，同 isTooltipEnabled()。 */
    @Deprecated
    public static boolean isTooltipInsertEnabled() {
        return isTooltipEnabled();
    }

    /** 无稀有度物品是否跳过渲染。 */
    public static boolean isNoRaritySkip() {
        return RarityStyleConfigManager.getInstance().isNoRaritySkip();
    }

    /** 无稀有度物品兜底等级。 */
    public static int getNoRarityDefaultRarity() {
        return RarityStyleConfigManager.getInstance().getNoRarityDefaultRarity();
    }

    /** 主开关：是否变色物品名称（查询 MIN_RARITY 等级）。 */
    public static boolean isNameColorEnabled() {
        return RarityStyleConfigManager.getInstance().resolveItemNameColor(RarityConstants.MIN_RARITY);
    }

    /**
     * 逐级名称颜色查询。
     *
     * @deprecated 使用 {@link #isLevelNameColorEnabled(int)}
     */
    @Deprecated
    public static boolean isNameColorEnabled(int level) {
        return RarityStyleConfigManager.getInstance().isLevelNameColorEnabled(level);
    }

    // ══════════════════════════════════════════════════════
    // 组件稀有度控制 (DataComponent CUSTOM_DATA)
    // ══════════════════════════════════════════════════════

    /**
     * 检查是否开启了 NBT/DataComponent 稀有度控制功能。
     * 实际控制的是 DataComponent（CUSTOM_DATA）稀有度读取。
     *
     * @return 启用返回 true
     */
    public static boolean isNbtRarityControlEnabled() {
        return ServerConfigManager.isEnableComponentRarityControl();
    }

    /**
     * @deprecated 使用 {@link #isNbtRarityControlEnabled()}
     */
    @Deprecated
    public static boolean isComponentRarityControlEnabled() {
        return isNbtRarityControlEnabled();
    }

    /**
     * 从物品的 CUSTOM_DATA 组件中读取 raritycore 稀有度等级。
     * 读取路径：DataComponents.CUSTOM_DATA → "raritycore" → "Level"
     *
     * @param itemStack 要读取的物品栈
     * @return 稀有度等级，Level 无效或不存在时返回 0
     */
    public static int getComponentRarity(@NotNull ItemStack itemStack) {
        Integer level = ComponentRarityReader.readLevel(itemStack);
        return level != null ? level : 0;
    }

    /**
     * 使指定物品堆的稀有度缓存立即失效（ID 缓存 + 组件缓存）。
     *
     * <p>供外部模组在写入/移除组件稀有度后调用（如 ModularRarity 装/拆插件后），
     * 避免类型级 ID 缓存快速路径返回旧值导致视觉延迟（最长约 30-60 分钟）。</p>
     *
     * <p>注意：必须同时失效两类缓存——{@link RarityCacheCoordinator#invalidate(ItemStack)}
     * 只清组件缓存，类型级 ID 缓存需按物品 id 失效（{@link RarityCacheCoordinator#invalidate(ResourceLocation)}，
     * 内部已判空）。</p>
     *
     * @param itemStack 组件刚发生变更的物品堆
     */
    public static void invalidateItem(@NotNull ItemStack itemStack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        RarityCacheCoordinator.invalidate(itemId); // 清 ID 缓存（内部已判空）
        RarityCacheCoordinator.invalidate(itemStack); // 清组件缓存
    }

    // ══════════════════════════════════════════════════════
    // 增量同步
    // ══════════════════════════════════════════════════════

    /**
     * 同步增量稀有度变更到所有客户端。
     * 仅发送自上次同步以来发生变化的条目。
     */
    public static void syncIncrementalChangesToClients() {
        RarityRegistry.syncIncrementalChangesToClients();
    }

    // ══════════════════════════════════════════════════════
    // 配置检查
    // ══════════════════════════════════════════════════════

    /**
     * 检查物品是否有已配置的稀有度。
     * 查找范围：用户手动配置 (ITEM_RARITY_MAP) 和自动计算配置 (AUTO_RARITY_MAP)。
     *
     * @param item 要检查的物品
     * @return 有配置返回 true
     */
    public static boolean hasConfiguredRarity(@NotNull Item item) {
        return RarityRegistry.hasConfiguredRarity(item);
    }

    /**
     * 检查物品及其物品栈是否有已配置的稀有度。
     * 同时检查配置映射和 DataComponent 组件稀有度。
     *
     * @param item      要检查的物品
     * @param itemStack 对应的物品栈（用于检查组件稀有度）
     * @return 有配置返回 true
     */
    public static boolean hasConfiguredRarity(@NotNull Item item, @NotNull ItemStack itemStack) {
        return RarityRegistry.hasConfiguredRarity(item)
                || ComponentRarityReader.hasComponentRarity(itemStack);
    }

    // ══════════════════════════════════════════════════════
    // 版本探测
    // ══════════════════════════════════════════════════════

    /**
     * 模组是否可用（类与基础依赖已加载）
     */
    public static boolean isAvailable() {
        return true;
    }

    /**
     * 获取模组的版本号（来自 neoforge.mods.toml 的 version 字段）
     * @return 版本字符串，获取失败时返回 unknown
     */
    public static String getModVersion() {
        try {
            return net.neoforged.fml.ModList.get()
                    .getModContainerById(org.yanbwe.raritycore.RarityCore.MODID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 获取当前配置版本号（配置重载时递增，供客户端同步校验）
     */
    public static int getConfigVersion() {
        return org.yanbwe.raritycore.network.SyncManager.getConfigVersion();
    }

    // ══════════════════════════════════════════════════════
    // 简单委托
    // ══════════════════════════════════════════════════════

    /** 校验并标准化稀有度等级（与 normalizeRarity 等价） */
    public static int validateRarity(int rarity) {
        return normalizeRarity(rarity);
    }

    /** 触发完整配置重载（命令源为空，视为程序化触发） */
    public static void reloadConfigs() {
        org.yanbwe.raritycore.service.ConfigReloadService.reloadAllConfigs(null, false);
    }

    /** 获取稀有度等级对应的内置 RGB 颜色 (0xRRGGBB) */
    public static int getRarityRgbColor(int rarity) {
        return RarityColorUtil.getRarityRgbColor(rarity);
    }
}
