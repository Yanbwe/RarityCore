package org.yanbwe.raritycore.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.yanbwe.raritycore.config.RarityClientConfig;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.config.TagRarityLoader;
import org.yanbwe.raritycore.registry.ComponentRarityResolver;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityValidator;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * RarityCore 正式公共 API
 * <p>
 * 为其他模组和外部调用方提供统一的静态方法入口。
 * 所有方法均委托至 {@link RarityRegistry} 及其他底层类，
 * 确保行为一致且不受内部重构影响。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * int rarity = RarityCoreAPI.getRarity(myItemStack);
 * if (rarity >= RarityCoreAPI.RARITY_EPIC) {
 *     // 处理史诗及以上稀有度物品
 * }
 * }</pre>
 *
 * @see RarityRegistry
 * @since 1.13
 */
public final class RarityCoreAPI {

    private RarityCoreAPI() {
        throw new UnsupportedOperationException("RarityCoreAPI is a utility class and cannot be instantiated");
    }

    // ============================================================
    // 稀有度等级常量
    // ============================================================

    /** 普通 (Common) */
    public static final int RARITY_COMMON = 1;
    /** 稀有 (Uncommon) */
    public static final int RARITY_UNCOMMON = 2;
    /** 罕见 (Rare) */
    public static final int RARITY_RARE = 3;
    /** 史诗 (Epic) */
    public static final int RARITY_EPIC = 4;
    /** 传说 (Legendary) */
    public static final int RARITY_LEGENDARY = 5;
    /** 神话 (Mythical) */
    public static final int RARITY_MYTHICAL = 6;
    /** 唯一 (Unique) */
    public static final int RARITY_UNIQUE = 7;

    /** 最小稀有度等级 */
    public static final int MIN_RARITY = RARITY_COMMON;
    /** 最大稀有度等级 */
    public static final int MAX_RARITY = RARITY_UNIQUE;

    /** 默认 RGB 颜色值 (灰色) */
    public static final int DEFAULT_RGB_COLOR = 0xCCCCCC;

    /**
     * 7 级稀有度的默认 RGB 颜色映射。
     * 键为稀有度等级 (1-7)，值为 {@code #RRGGBB} 格式的十六进制颜色字符串。
     */
    public static final Map<Integer, String> DEFAULT_RGB_COLORS = Map.of(
            RARITY_COMMON, "#A0A0A0",
            RARITY_UNCOMMON, "#00AA00",
            RARITY_RARE, "#00AAAA",
            RARITY_EPIC, "#C870FF",
            RARITY_LEGENDARY, "#FFAA00",
            RARITY_MYTHICAL, "#FF5555",
            RARITY_UNIQUE, "#AA0000"
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
     * @return 稀有度等级 (1-7)，默认返回 {@value #RARITY_COMMON}
     */
    public static int getRarity(@Nullable Item item) {
        return RarityRegistry.getRarity(item);
    }

    /**
     * 获取物品栈的稀有度等级，支持物品数据匹配和缓存。
     * 遵循完整的优先级链。
     *
     * @param itemStack 要查询的物品栈，可为 null（返回默认稀有度）
     * @return 稀有度等级 (1-7)，默认返回 {@value #RARITY_COMMON}
     */
    public static int getRarity(@Nullable ItemStack itemStack) {
        return RarityRegistry.getRarity(itemStack);
    }

    /**
     * 获取物品的标准化稀有度等级。
     * 小于 1 的值视为 1，大于 7 的值视为 7。
     *
     * @param item 要查询的物品，可为 null
     * @return 标准化后的稀有度等级 (1-7)
     */
    public static int getNormalizedRarity(@Nullable Item item) {
        return RarityRegistry.getNormalizedRarity(item);
    }

    /**
     * 获取物品栈的标准化稀有度等级。
     * 小于 1 的值视为 1，大于 7 的值视为 7。
     *
     * @param itemStack 要查询的物品栈，可为 null
     * @return 标准化后的稀有度等级 (1-7)
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
     * @param rarity 稀有度等级 (1-7)
     */
    public static void registerRarity(@NotNull Item item, int rarity) {
        RarityRegistry.register(item, rarity, true);
    }

    /**
     * 注册物品的稀有度等级，可选是否同步到客户端。
     *
     * @param item   要注册的物品
     * @param rarity 稀有度等级 (1-7)
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

    /** @deprecated 使用 {@link #registerRarity(Item, int)} */
    @Deprecated
    public static void register(@Nullable Item item, int rarity) {
        RarityRegistry.register(item, rarity);
    }

    /** @deprecated 使用 {@link #unregisterRarity(Item)} */
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
     */
    @NotNull
    public static Map<Identifier, Integer> getItemRarityMap() {
        return RarityRegistry.getItemRarityMap();
    }

    /**
     * 获取当前所有自动稀有度映射。
     *
     * @return 物品 ID → 稀有度等级 的实时映射视图
     */
    @NotNull
    public static Map<Identifier, Integer> getAutoRarityMap() {
        return RarityRegistry.getAutoRarityMap();
    }

    // ============================================================
    // 验证
    // ============================================================

    /**
     * 验证稀有度值是否在有效范围内 (1-7)。
     *
     * @param rarity 要验证的稀有度值
     * @return 如果在 1-7 范围内返回 true
     */
    public static boolean isValidRarity(int rarity) {
        return RarityValidator.isValidRarity(rarity);
    }

    /**
     * 标准化稀有度值：小于 1 视为 1，大于 7 视为 7。
     *
     * @param rarity 原始稀有度值
     * @return 标准化后的稀有度值 (1-7)
     */
    public static int normalizeRarity(int rarity) {
        return RarityValidator.normalizeRarity(rarity);
    }

    // ============================================================
    // 颜色与纹理
    // ============================================================

    /**
     * 获取指定稀有度等级的 RGB 颜色值。
     * 优先使用 RarityClientConfig 中配置的自定义颜色。
     *
     * @param level 稀有度等级
     * @return RGB 颜色值 (0xRRGGBB)
     */
    public static int getColor(int level) {
        String hex = RarityClientConfig.getColorHex(level);
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return RarityColorUtil.getRarityRgbColor(level);
        }
    }

    /**
     * 获取指定稀有度等级的纹理边框路径。
     *
     * @param level 稀有度等级
     * @return 纹理资源路径
     */
    public static String getTexture(int level) {
        return RarityClientConfig.getLevelConfig(level).texture();
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
    // Tag 稀有度
    // ============================================================

    /**
     * 获取物品匹配的 Tag 规则最高稀有度。
     * 遍历 TagRarity.json 中定义的规则，返回第一个匹配 Tag 的稀有度等级。
     *
     * @param item 要查询的物品
     * @return 稀有度等级 (1-7)，无匹配时返回 0
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
    // 逐级表现开关 (RarityClientConfig)
    // ============================================================

    /**
     * 检查指定稀有度等级是否启用边框渲染。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     */
    public static boolean isLevelRendererEnabled(int level) {
        return RarityClientConfig.isRendererEnabled(level);
    }

    /**
     * 检查指定稀有度等级是否启用工具提示显示。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     */
    public static boolean isLevelTooltipEnabled(int level) {
        return RarityClientConfig.isTooltipsEnabled(level);
    }

    /**
     * 检查指定稀有度等级是否启用物品名称颜色修改。
     *
     * @param level 稀有度等级
     * @return 启用返回 true
     */
    public static boolean isLevelNameColorEnabled(int level) {
        return RarityClientConfig.isNameColorEnabled(level);
    }

    // ============================================================
    // 组件稀有度控制
    // ============================================================

    /**
     * 检查是否开启了 DataComponent 稀有度控制功能。
     *
     * @return 启用返回 true
     */
    public static boolean isComponentRarityControlEnabled() {
        return ServerConfigManager.isEnableComponentRarityControl();
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
}
