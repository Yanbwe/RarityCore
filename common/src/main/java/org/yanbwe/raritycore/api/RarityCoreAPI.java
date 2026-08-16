package org.yanbwe.raritycore.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RarityCacheCoordinator;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.config.TagRarityLoader;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.registry.ComponentRarityResolver;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RarityCore 正式公共 API (V14)
 * <p>
 * 为其他模组和外部调用方提供统一的静态方法入口。
 * 所有方法均委托至 {@link RarityRegistry}、{@link RarityStyleConfigManager} 及其他底层类，
 * 确保行为一致且不受内部重构影响。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * int rarity = RarityCoreAPI.getRarity(myItemStack);
 * if (RarityCoreAPI.isValidRarity(rarity)) {
 *     // 处理该稀有度物品
 * }
 * }</pre>
 *
 * @see RarityRegistry
 * @see RarityStyleConfigManager
 * @since 1.13
 */
public final class RarityCoreAPI {

    private RarityCoreAPI() {
        throw new UnsupportedOperationException("RarityCoreAPI is a utility class and cannot be instantiated");
    }

    // ============================================================
    // 稀有度等级常量
    // ============================================================

    /** 最小稀有度等级（V14 无上限，仅保证下限） */
    public static final int MIN_RARITY = 1;
    /** 内置预设档位数量（默认 7），非稀有度上限 */
    public static final int MAX_RARITY = 7;

    /** V14 API 版本号（供联动模组做特性探测，与模组版本解耦） */
    public static final int API_VERSION = 1400;

    /** 默认 RGB 颜色值 (灰色) */
    public static final int DEFAULT_RGB_COLOR = 0xCCCCCC;

    /**
     * 内置预设档位的默认 RGB 颜色映射。
     * 键为稀有度等级 (内置档位 1-7)，值为 {@code #RRGGBB} 格式的十六进制颜色字符串。
     * V14 不限制上限，8+ 等级颜色由 {@link RarityStyleConfigManager} 继承/回退规则处理。
     */
    public static final Map<Integer, String> DEFAULT_RGB_COLORS = Map.of(
            1, "#CCCCCC",
            2, "#55FF55",
            3, "#00AAAA",
            4, "#C870FF",
            5, "#FFAA00",
            6, "#FF5555",
            7, "#FF3333"
    );

    // ============================================================
    // 核心查询方法
    // ============================================================

    /**
     * 获取物品的稀有度等级。
     * 当 ItemStack 无法创建时（如数据加载早期阶段），
     * 回退至仅通过物品 ID 查询。
     *
     * @param item 要查询的物品，可为 null（返回默认稀有度）
     * @return 稀有度等级 (1+，V14 不限制上限)，默认返回 {@value #MIN_RARITY}
     */
    public static int getRarity(@Nullable Item item) {
        return RarityRegistry.getRarity(item);
    }

    /**
     * 获取物品栈的稀有度等级，支持物品数据匹配和缓存。
     * 遵循完整的优先级链。
     *
     * @param itemStack 要查询的物品栈，可为 null（返回默认稀有度）
     * @return 稀有度等级 (1+，V14 不限制上限)，默认返回 {@value #MIN_RARITY}
     */
    public static int getRarity(@Nullable ItemStack itemStack) {
        return RarityRegistry.getRarity(itemStack);
    }

    /**
     * 获取物品的标准化稀有度等级。
     * 小于 1 的值视为 1；V14 不限制上限，不再将大于 7 的值截断。
     *
     * @param item 要查询的物品，可为 null
     * @return 标准化后的稀有度等级 (≥1，无上限)
     */
    public static int getNormalizedRarity(@Nullable Item item) {
        return RarityRegistry.getNormalizedRarity(item);
    }

    /**
     * 获取物品栈的标准化稀有度等级。
     * 小于 1 的值视为 1；V14 不限制上限，不再将大于 7 的值截断。
     *
     * @param itemStack 要查询的物品栈，可为 null
     * @return 标准化后的稀有度等级 (≥1，无上限)
     */
    public static int getNormalizedRarity(@Nullable ItemStack itemStack) {
        return RarityRegistry.getNormalizedRarity(itemStack);
    }

    // ============================================================
    // 注册 / 注销方法
    // ============================================================

    /**
     * 注册物品的稀有度等级，并同步到客户端。
     *
     * @param item   要注册的物品
     * @param rarity 稀有度等级 (1+，V14 不限制上限)
     */
    public static void registerRarity(@NotNull Item item, int rarity) {
        RarityRegistry.register(item, rarity, true);
    }

    /**
     * 注册物品的稀有度等级，可选是否同步到客户端。
     *
     * @param item   要注册的物品
     * @param rarity 稀有度等级 (1+，V14 不限制上限)
     * @param sync   是否同步到客户端
     */
    public static void registerRarity(@NotNull Item item, int rarity, boolean sync) {
        RarityRegistry.register(item, rarity, sync);
    }

    /**
     * 删除物品的稀有度注册，并同步到客户端。
     *
     * @param item 要删除注册的物品
     */
    public static void unregisterRarity(@NotNull Item item) {
        RarityRegistry.unregister(item, true);
    }

    /** @deprecated 保留兼容，推荐使用 1.21.1 对齐方法 {@link #registerRarity(Item, int)} */
    @Deprecated
    public static void register(@Nullable Item item, int rarity) {
        RarityRegistry.register(item, rarity);
    }

    /** @deprecated 保留兼容，推荐使用 1.21.1 对齐方法 {@link #unregisterRarity(Item)} */
    @Deprecated
    public static void unregister(@Nullable Item item) {
        RarityRegistry.unregister(item, true);
    }

    // ============================================================
    // 工具提示
    // ============================================================

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
        return getLocalizedTooltip(new ItemStack(item));
    }

    // ============================================================
    // 数据访问器
    // ============================================================

    /**
     * 获取当前所有已注册的物品稀有度映射（只读视图）。
     * 包含通过 FinalRarity.json、数据包及运行时注册添加的条目。
     *
     * @return 物品 ID → 稀有度等级 的实时映射视图
     */
    @NotNull
    public static Map<Identifier, Integer> getRegistryMap() {
        return RarityRegistry.getItemRarityMap();
    }

    /**
     * 获取当前所有物品稀有度映射（同 {@link #getRegistryMap()}）。
     *
     * @deprecated 保留兼容，推荐使用 1.21.1 对齐方法 {@link #getRegistryMap()}
     */
    @Deprecated
    @NotNull
    public static Map<Identifier, Integer> getItemRarityMap() {
        return RarityRegistry.getItemRarityMap();
    }

    /**
     * 获取当前所有自动稀有度映射。
     *
     * @return 物品 ID → 稀有度等级 的实时映射视图
     * @deprecated 保留兼容，推荐使用 1.21.1 对齐方法 {@link #getAllRarityEntries()}
     */
    @Deprecated
    @NotNull
    public static Map<Identifier, Integer> getAutoRarityMap() {
        return RarityRegistry.getAutoRarityMap();
    }

    // ============================================================
    // 验证
    // ============================================================

    /**
     * 验证稀有度值是否有效（≥ {@link #MIN_RARITY}，V14 不限制上限）。
     *
     * @param rarity 要验证的稀有度值
     * @return 有效返回 true
     */
    public static boolean isValidRarity(int rarity) {
        return RarityValidator.isValidRarity(rarity);
    }

    /**
     * 标准化稀有度值：小于 1 视为 1；V14 不限制上限，不再截断大于 7 的值。
     *
     * @param rarity 原始稀有度值
     * @return 标准化后的稀有度值 (≥1，无上限)
     */
    public static int normalizeRarity(int rarity) {
        return RarityValidator.normalizeRarity(rarity);
    }

    // ============================================================
    // 颜色与纹理
    // ============================================================

    /**
     * 获取指定稀有度等级的 RGB 颜色值。
     * 委托 {@link RarityStyleConfigManager#getColor(int)}，支持 V14 继承与回退。
     *
     * @param level 稀有度等级
     * @return RGB 颜色值 (0xRRGGBB)
     */
    public static int getRarityColor(int level) {
        return getColor(level);
    }

    /**
     * 获取指定稀有度等级的 RGB 颜色值。
     * 委托 {@link RarityStyleConfigManager#getColor(int)}，支持 V14 继承与回退。
     *
     * @param level 稀有度等级
     * @return RGB 颜色值 (0xRRGGBB)
     * @deprecated 使用 {@link #getRarityColor(int)}
     */
    @Deprecated
    public static int getColor(int level) {
        return RarityStyleConfigManager.getColor(level);
    }

    /**
     * 获取指定稀有度等级的纹理边框路径。
     * 委托 {@link RarityStyleConfigManager#getBorderTexture(int)}，8+ 等级按继承/回退规则处理。
     *
     * @param level 稀有度等级
     * @return 纹理资源路径
     */
    public static String getRarityTexture(int level) {
        return getTexture(level);
    }

    /**
     * 获取指定稀有度等级的纹理边框路径。
     * 委托 {@link RarityStyleConfigManager#getBorderTexture(int)}，8+ 等级按继承/回退规则处理。
     *
     * @param level 稀有度等级
     * @return 纹理资源路径
     * @deprecated 使用 {@link #getRarityTexture(int)}
     */
    @Deprecated
    public static String getTexture(int level) {
        return RarityStyleConfigManager.getBorderTexture(level);
    }

    /**
     * 解析 "#RRGGBB" 格式的十六进制颜色字符串为 RGB int 值。
     *
     * @param hex 十六进制颜色字符串（如 "#FFAA00"）
     * @return RGB 颜色值，解析失败返回默认白色
     */
    public static int parseColor(String hex) {
        return RarityColorUtil.parseHexColor(hex);
    }

    /**
     * 将 RGB int 颜色值格式化为 "#RRGGBB" 十六进制字符串。
     *
     * @param rgb RGB 颜色值
     * @return 十六进制颜色字符串
     */
    public static String formatColor(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    // ============================================================
    // V14 全局开关
    // ============================================================

    /**
     * 全局边框渲染总开关。
     *
     * @return 启用返回 true
     */
    public static boolean isBorderEnabled() {
        return RarityStyleConfigManager.isBorderEnabled();
    }

    /**
     * 全局工具提示插入总开关。
     *
     * @return 启用返回 true
     */
    public static boolean isTooltipEnabled() {
        return RarityStyleConfigManager.isTooltipEnabled();
    }

    /**
     * 全局工具提示着色总开关。
     *
     * @return 启用返回 true
     */
    public static boolean isTooltipColorEnabled() {
        return RarityStyleConfigManager.isTooltipColorEnabled();
    }

    /** V14 别名，同 {@link #isBorderEnabled()}。 */
    @Deprecated
    public static boolean isBorderRenderingEnabled() {
        return isBorderEnabled();
    }

    /** V14 别名，同 {@link #isTooltipEnabled()}。 */
    @Deprecated
    public static boolean isTooltipInsertEnabled() {
        return isTooltipEnabled();
    }

    /**
     * 主开关：是否变色物品名称（查询 {@link RarityConstants#MIN_RARITY} 等级）。
     *
     * @return 启用返回 true
     */
    public static boolean isNameColorEnabled() {
        return RarityStyleConfigManager.isNameColorEnabled(RarityConstants.MIN_RARITY);
    }

    /**
     * 无稀有度物品是否跳过渲染。
     *
     * @return 跳过返回 true
     */
    public static boolean isNoRaritySkip() {
        return RarityStyleConfigManager.isNoRaritySkip();
    }

    /**
     * 无稀有度物品的兜底稀有度等级。
     *
     * @return 兜底等级
     */
    public static int getNoRarityDefaultRarity() {
        return RarityStyleConfigManager.getNoRarityDefaultRarity();
    }

    /**
     * 设置无稀有度物品是否跳过渲染。
     *
     * @param skip 是否跳过
     */
    public static void setNoRaritySkip(boolean skip) {
        RarityStyleConfigManager.setNoRaritySkip(skip);
    }

    /**
     * 设置无稀有度物品的兜底稀有度等级。
     *
     * @param rarity 兜底等级
     */
    public static void setNoRarityDefaultRarity(int rarity) {
        RarityStyleConfigManager.setNoRarityDefaultRarity(rarity);
    }

    // ============================================================
    // 逐级表现开关 (RarityStyleConfigManager)
    // ============================================================

    /**
     * 检查指定稀有度等级是否启用边框渲染。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     * @deprecated 使用 {@link #isLevelRendererEnabled(int)}
     */
    @Deprecated
    public static boolean isBorderEnabled(int level) {
        return isLevelRendererEnabled(level);
    }

    /**
     * 检查指定稀有度等级是否启用工具提示显示。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     * @deprecated 使用 {@link #isLevelTooltipEnabled(int)}
     */
    @Deprecated
    public static boolean isTooltipEnabled(int level) {
        return isLevelTooltipEnabled(level);
    }

    /**
     * 检查指定稀有度等级是否启用物品名称颜色修改。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     * @deprecated 使用 {@link #isLevelNameColorEnabled(int)}
     */
    @Deprecated
    public static boolean isNameColorEnabled(int level) {
        return isLevelNameColorEnabled(level);
    }

    /**
     * 检查指定稀有度等级是否启用边框渲染。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     */
    public static boolean isLevelRendererEnabled(int level) {
        return RarityStyleConfigManager.getBorder(level).show();
    }

    /**
     * 检查指定稀有度等级是否启用工具提示显示。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     */
    public static boolean isLevelTooltipEnabled(int level) {
        return RarityStyleConfigManager.getTooltip(level).show();
    }

    /**
     * 检查指定稀有度等级是否启用物品名称颜色修改。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     */
    public static boolean isLevelNameColorEnabled(int level) {
        return RarityStyleConfigManager.isNameColorEnabled(level);
    }

    // ============================================================
    // 逐级查询与诊断
    // ============================================================

    /**
     * 获取指定稀有度等级的工具提示内容模板。
     *
     * @param level 稀有度等级
     * @return 工具提示内容模板
     */
    public static String getTooltipContent(int level) {
        return RarityStyleConfigManager.getTooltipContent(level);
    }

    /**
     * 获取指定稀有度等级的等级翻译键模板。
     *
     * @param level 稀有度等级
     * @return 翻译键模板
     */
    public static String getLevelTranslationKey(int level) {
        return RarityStyleConfigManager.getLevelTranslationKey(level);
    }

    /**
     * 获取指定稀有度等级的等级回退键模板。
     *
     * @param level 稀有度等级
     * @return 回退键模板
     */
    public static String getLevelFallbackKey(int level) {
        return RarityStyleConfigManager.getLevelFallbackKey(level);
    }

    /**
     * 获取指定稀有度等级的星星配置。
     *
     * @param level 稀有度等级
     * @return 星星配置
     */
    public static RarityStyleConfigManager.StarSegmentConfig getStarConfig(int level) {
        return RarityStyleConfigManager.getStarConfig(level);
    }

    /**
     * 检查指定稀有度等级的边框是否使用纹理。
     *
     * @param level 稀有度等级
     * @return 使用纹理返回 true
     */
    public static boolean isBorderUseTexture(int level) {
        return RarityStyleConfigManager.isBorderUseTexture(level);
    }

    /**
     * 获取指定稀有度等级的边框样式（1=实心，0=空心）。
     *
     * @param level 稀有度等级
     * @return 边框样式
     */
    public static int getBorderStyle(int level) {
        return RarityStyleConfigManager.getBorderStyle(level);
    }

    /**
     * 获取默认边框回退纹理。
     *
     * @return 回退纹理配置
     */
    public static String getBorderFallback() {
        return RarityStyleConfigManager.getBorderFallback();
    }

    // ============================================================
    // V14 样式写入
    // ============================================================

    /**
     * 设置全局边框渲染总开关。
     *
     * @param enable 是否启用
     */
    public static void setBorderEnabled(boolean enable) {
        RarityStyleConfigManager.setBorderEnabled(enable);
    }

    /**
     * 设置全局工具提示插入总开关。
     *
     * @param enable 是否启用
     */
    public static void setTooltipEnabled(boolean enable) {
        RarityStyleConfigManager.setTooltipEnabled(enable);
    }

    /**
     * 设置全局工具提示着色总开关。
     *
     * @param enable 是否启用
     */
    public static void setTooltipColorEnabled(boolean enable) {
        RarityStyleConfigManager.setTooltipColorEnabled(enable);
    }

    /**
     * 设置指定稀有度等级的边框是否使用纹理。
     *
     * @param level     稀有度等级
     * @param useTexture 是否使用纹理
     */
    public static void setBorderUseTexture(int level, boolean useTexture) {
        RarityStyleConfigManager.setBorderUseTexture(level, useTexture);
    }

    /**
     * 设置指定稀有度等级的边框样式（1=实心，0=空心）。
     *
     * @param level 稀有度等级
     * @param style 边框样式
     */
    public static void setBorderStyle(int level, int style) {
        RarityStyleConfigManager.setBorderStyle(level, style);
    }

    /**
     * 设置指定稀有度等级的工具提示内容模板。
     *
     * @param level   稀有度等级
     * @param content 工具提示内容模板
     */
    public static void setTooltipContent(int level, String content) {
        RarityStyleConfigManager.setTooltipContent(level, content);
    }

    /**
     * 设置指定稀有度等级的星星显示模式。
     *
     * @param level 稀有度等级
     * @param mode  星星模式
     */
    public static void setStarMode(int level, String mode) {
        RarityStyleConfigManager.setStarMode(level, mode);
    }

    /**
     * 设置指定稀有度等级的星星重复字符。
     *
     * @param level      稀有度等级
     * @param repeatChar 重复字符
     */
    public static void setStarRepeatChar(int level, String repeatChar) {
        RarityStyleConfigManager.setStarRepeatChar(level, repeatChar);
    }

    /**
     * 开始批量样式写入；期间 setter 不逐条写盘、不发布事件。
     */
    public static void beginStyleBatch() {
        RarityStyleConfigManager.beginStyleBatch();
    }

    /**
     * 结束批量样式写入；归零时统一保存并发布一次聚合事件。
     */
    public static void endStyleBatch() {
        RarityStyleConfigManager.endStyleBatch();
    }

    /**
     * 以单个补丁整体写入某稀有度等级的视觉表现配置（canonical，与 1.21.1 对齐）。
     * 稀有度由 {@code patch.rarity} 指定；补丁内 null 字段表示保留现有值。
     *
     * @param patch 样式补丁
     */
    public static void setStyle(RarityStyleConfigManager.StylePatch patch) {
        RarityStyleConfigManager.setStyle(patch);
    }

    /**
     * 以单个补丁整体写入指定稀有度等级的视觉表现配置。
     * 补丁内 null 字段表示保留现有值。
     *
     * @param level 稀有度等级
     * @param patch 样式补丁
     * @deprecated 使用 {@link #setStyle(RarityStyleConfigManager.StylePatch)}，
     *             在 {@link RarityStyleConfigManager.StylePatch} 中指定 {@code rarity}。
     */
    @Deprecated
    public static void setStyle(int level, RarityStyleConfigManager.StylePatch patch) {
        RarityStyleConfigManager.setStyle(level, patch);
    }

    /**
     * 遍历当前已配置等级，批量设置边框是否使用纹理。
     *
     * @param useTexture 是否使用纹理
     * @deprecated 保留兼容，推荐使用 1.21.1 对齐方法 {@link #setBorderUseTexture(int, boolean)}
     */
    @Deprecated
    public static void setAllBorderUseTexture(boolean useTexture) {
        RarityStyleConfigManager.setAllBorderUseTexture(useTexture);
    }

    // ============================================================
    // 校验 / 快照 / 可用性 / 版本
    // ============================================================

    /**
     * 校验并标准化稀有度等级（与 {@link #normalizeRarity(int)} 等价）。
     *
     * @param rarity 稀有度值
     * @return 标准化后的稀有度等级 (≥1，无上限)
     */
    public static int validateRarity(int rarity) {
        return normalizeRarity(rarity);
    }

    /**
     * 获取指定稀有度等级的完整生效视觉表现快照。
     *
     * @param level 稀有度等级
     * @return 样式快照
     */
    public static RarityStyleConfigManager.StyleSnapshot getStyleSnapshot(int level) {
        return RarityStyleConfigManager.getStyleSnapshot(level);
    }

    /**
     * RarityStyle.json 配置是否已成功初始化。
     *
     * @return 可用返回 true
     */
    public static boolean isAvailable() {
        return RarityStyleConfigManager.isAvailable();
    }

    /**
     * 获取模组的版本号（来自 neoforge.mods.toml 的 version 字段）。
     *
     * @return 版本字符串，获取失败时返回 unknown
     */
    public static String getModVersion() {
        try {
            return ModList.get()
                    .getModContainerById(RarityCore.MODID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 获取当前 RarityStyle.json 的配置版本号。
     *
     * @return 配置版本号
     */
    public static int getConfigVersion() {
        return SyncManager.getConfigVersion();
    }

    // ============================================================
    // 集合查询与批量注册
    // ============================================================

    /**
     * 返回所有被解析为指定稀有度等级的物品。
     *
     * @param rarity 稀有度等级
     * @return 匹配物品的只读列表
     */
    public static List<Item> getItemsByRarity(int rarity) {
        return new ArrayList<>(RarityRegistry.getItemsByRarity(rarity));
    }

    /**
     * 返回所有被解析为指定稀有度等级的物品 ID。
     *
     * @param rarity 稀有度等级
     * @return 匹配物品 ID 的只读列表
     */
    public static List<Identifier> getItemIdsByRarity(int rarity) {
        return new ArrayList<>(RarityRegistry.getItemIdsByRarity(rarity));
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
     * 批量注册物品稀有度映射（不逐条同步，注册结束后统一同步一次）。
     *
     * @param entries 物品到稀有度等级的映射
     */
    public static void registerRarities(Map<Item, Integer> entries) {
        RarityRegistry.registerRarities(entries);
    }

    /**
     * 返回所有被解析为指定稀有度等级集合中任一等级的物品。
     *
     * @param rarities 稀有度等级集合
     * @return 匹配物品的只读列表
     */
    public static List<Item> getItemsByRarities(Set<Integer> rarities) {
        return new ArrayList<>(RarityRegistry.getItemsByRarities(rarities));
    }

    /**
     * 返回所有被解析为指定稀有度等级集合中任一等级的物品 ID。
     *
     * @param rarities 稀有度等级集合
     * @return 匹配物品 ID 的只读列表
     */
    public static List<Identifier> getItemIdsByRarities(Set<Integer> rarities) {
        return new ArrayList<>(RarityRegistry.getItemIdsByRarities(rarities));
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
     * 返回当前全部已注册稀有度条目快照（显式配置与自动计算合并，手动覆盖自动）。
     *
     * @return 合并后的只读映射
     */
    public static Map<Identifier, Integer> getAllRarityEntries() {
        return RarityRegistry.getAllRarityEntries();
    }

    // ============================================================
    // Tag 稀有度
    // ============================================================

    /**
     * 获取物品匹配的 Tag 规则最高稀有度。
     * 遍历 TagRarity.json 中定义的规则，返回第一个匹配 Tag 的稀有度等级。
     *
     * @param item 要查询的物品
     * @return 稀有度等级 (1+，V14 不限制上限)，无匹配时返回 0
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
        return TagRarityLoader.getTagRules().size();
    }

    // ============================================================
    // 组件稀有度控制
    // ============================================================

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
     * 检查是否开启了 DataComponent 稀有度控制功能。
     *
     * @return 启用返回 true
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
        ComponentRarityResolver.ComponentRarityData data =
                ComponentRarityResolver.resolveComponentRarity(itemStack);
        return data != null ? data.level() : 0;
    }

    /**
     * 使指定物品堆的稀有度缓存立即失效（ID 缓存 + 组件缓存）。
     *
     * <p>供外部模组在写入/移除组件稀有度后调用（如 ModularRarity 装/拆插件后），
     * 避免类型级 ID 缓存快速路径返回旧值导致视觉延迟（最长约 30-60 分钟）。</p>
     *
     * <p>注意：必须同时失效两类缓存——{@link RarityCacheCoordinator#invalidate(ItemStack)}
     * 只清组件缓存，类型级 ID 缓存需按物品 id 失效（{@link RarityCacheCoordinator#invalidate(Identifier)}，
     * 内部已判空）。</p>
     *
     * @param itemStack 组件刚发生变更的物品堆
     */
    public static void invalidateItem(@NotNull ItemStack itemStack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        RarityCacheCoordinator.invalidate(itemId); // 清 ID 缓存（内部已判空）
        RarityCacheCoordinator.invalidate(itemStack); // 清组件缓存
    }

    // ============================================================
    // 网络同步
    // ============================================================

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

    /**
     * 同步增量稀有度变更到所有客户端。
     */
    public static void syncIncrementalChangesToClients() {
        RarityRegistry.syncIncrementalChangesToClients();
    }

    /**
     * 获取当前待同步的变更操作数量。
     *
     * @return 待处理的变更操作数
     */
    public static int getPendingChangeCount() {
        return RarityRegistry.getPendingChangeCount();
    }

    // ============================================================
    // 健康检查
    // ============================================================

    /**
     * 清理映射中引用无效物品 ID 的条目。
     *
     * @return 被移除的无效条目数量
     */
    public static int pruneInvalidEntries() {
        return RarityRegistry.pruneInvalidEntries();
    }

    // ============================================================
    // 配置检查
    // ============================================================

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
                || ComponentRarityResolver.resolveComponentRarity(itemStack) != null;
    }

    // ============================================================
    // 简单委托
    // ============================================================

    /** 触发完整配置重载（命令源为空，视为程序化触发）。 */
    public static void reloadConfigs() {
        org.yanbwe.raritycore.service.ConfigReloadService.reloadAllConfigs(null, false);
    }

    /** 获取稀有度等级对应的内置 RGB 颜色 (0xRRGGBB)。 */
    public static int getRarityRgbColor(int rarity) {
        return RarityColorUtil.getRarityRgbColor(rarity);
    }
}
