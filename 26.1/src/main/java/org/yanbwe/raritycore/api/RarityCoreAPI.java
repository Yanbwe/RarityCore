package org.yanbwe.raritycore.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.yanbwe.raritycore.registry.RarityRegistry;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * RarityCore 公共 API
 * <p>
 * 为其他模组和外部调用方提供统一的静态方法入口。
 * 所有方法均委托至 {@link RarityRegistry} 的核心实现，
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
 * <h3>稀有度等级说明</h3>
 * <table>
 *   <tr><th>等级</th><th>常量</th><th>名称</th></tr>
 *   <tr><td>1</td><td>{@link #RARITY_COMMON}</td><td>普通</td></tr>
 *   <tr><td>2</td><td>{@link #RARITY_UNCOMMON}</td><td>稀有</td></tr>
 *   <tr><td>3</td><td>{@link #RARITY_RARE}</td><td>罕见</td></tr>
 *   <tr><td>4</td><td>{@link #RARITY_EPIC}</td><td>史诗</td></tr>
 *   <tr><td>5</td><td>{@link #RARITY_LEGENDARY}</td><td>传说</td></tr>
 *   <tr><td>6</td><td>{@link #RARITY_MYTHICAL}</td><td>神话</td></tr>
 *   <tr><td>7</td><td>{@link #RARITY_UNIQUE}</td><td>唯一</td></tr>
 * </table>
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

    // ============================================================
    // 默认 RGB 颜色映射
    // ============================================================

    /**
     * 7 级稀有度的默认 RGB 颜色映射。
     * <p>
     * 键为稀有度等级 (1-7)，值为 {@code #RRGGBB} 格式的十六进制颜色字符串。
     * 颜色值从现有 ARGB 颜色系统迁移而来，与 {@link org.yanbwe.raritycore.util.RarityColorUtil} 保持一致。
     * </p>
     *
     * @since 1.13
     */
    public static final Map<Integer, String> DEFAULT_RGB_COLOR = Map.of(
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
     * <p>
     * 委托至 {@link RarityRegistry#getRarity(Item)}。
     * 当 ItemStack 无法创建时（如数据加载早期阶段），
     * 回退至仅通过物品 ID 查询，跳过需要完整 ItemStack 的路径（如物品数据匹配）。
     * </p>
     *
     * @param item 要查询的物品，可为 null（返回默认稀有度）
     * @return 稀有度等级 (1-7)，默认返回 {@value #RARITY_COMMON}
     * @since 1.13
     */
    public static int getRarity(@Nullable Item item) {
        return RarityRegistry.getRarity(item);
    }

    /**
     * 获取物品栈的稀有度等级，支持物品数据匹配和缓存。
     * <p>
     * 委托至 {@link RarityRegistry#getRarity(ItemStack)}。
     * 遵循完整的优先级链：物品数据匹配 → ID 映射 → 自动映射 → 原版稀有度 → 默认值。
     * </p>
     *
     * @param itemStack 要查询的物品栈，可为 null（返回默认稀有度）
     * @return 稀有度等级 (1-7)，默认返回 {@value #RARITY_COMMON}
     * @since 1.13
     */
    public static int getRarity(@Nullable ItemStack itemStack) {
        return RarityRegistry.getRarity(itemStack);
    }

    /**
     * 获取物品的标准化稀有度等级。
     * <p>
     * 委托至 {@link RarityRegistry#getNormalizedRarity(Item)}，
     * 通过 {@link org.yanbwe.raritycore.util.RarityValidator#normalizeRarity} 将原始值钳制至 [1, 7] 范围。
     * 小于 1 的值视为 1，大于 7 的值视为 7。
     * </p>
     *
     * @param item 要查询的物品，可为 null
     * @return 标准化后的稀有度等级 (1-7)
     * @since 1.13
     */
    public static int getNormalizedRarity(@Nullable Item item) {
        return RarityRegistry.getNormalizedRarity(item);
    }

    /**
     * 获取物品栈的标准化稀有度等级。
     * <p>
     * 委托至 {@link RarityRegistry#getNormalizedRarity(ItemStack)}，
     * 通过 {@link org.yanbwe.raritycore.util.RarityValidator#normalizeRarity} 将原始值钳制至 [1, 7] 范围。
     * 小于 1 的值视为 1，大于 7 的值视为 7。
     * </p>
     *
     * @param itemStack 要查询的物品栈，可为 null
     * @return 标准化后的稀有度等级 (1-7)
     * @since 1.13
     */
    public static int getNormalizedRarity(@Nullable ItemStack itemStack) {
        return RarityRegistry.getNormalizedRarity(itemStack);
    }

    // ============================================================
    // 注册 / 注销方法
    // ============================================================

    /**
     * 注册物品的稀有度等级。
     * <p>
     * 委托至 {@link RarityRegistry#register(Item, int)}，
     * 默认触发客户端同步（syncToClients=true）。
     * 注册完成后会触发 {@link org.yanbwe.raritycore.event.RarityChangeEvent} 事件。
     * </p>
     *
     * @param item   要注册的物品，不可为 null
     * @param rarity 稀有度等级 (1-7)
     * @since 1.13
     */
    public static void register(@Nullable Item item, int rarity) {
        RarityRegistry.register(item, rarity);
    }

    /**
     * 注销物品的稀有度注册。
     * <p>
     * 委托至 {@link RarityRegistry#unregister(Item, boolean)}，
     * 默认触发客户端同步（syncToClients=true）。
     * 注销完成后会触发 {@link org.yanbwe.raritycore.event.RarityChangeEvent} 事件（变更类型为 REMOVE）。
     * </p>
     *
     * @param item 要注销的物品，不可为 null
     * @since 1.13
     */
    public static void unregister(@Nullable Item item) {
        RarityRegistry.unregister(item, true);
    }

    // ============================================================
    // 数据访问器
    // ============================================================

    /**
     * 获取当前所有已注册的物品稀有度映射 (ITEM_RARITY_MAP)。
     * <p>
     * 委托至 {@link RarityRegistry#getItemRarityMap()}。
     * 返回的是实时视图，修改会直接影响稀有度系统。
     * 包含通过 FinalRarity.json、数据包及运行时注册添加的条目。
     * </p>
     *
     * @return 物品 ID → 稀有度等级 的实时映射视图
     * @since 1.13
     */
    @NotNull
    public static Map<Identifier, Integer> getItemRarityMap() {
        return RarityRegistry.getItemRarityMap();
    }

    /**
     * 获取当前所有自动稀有度映射 (AUTO_RARITY_MAP)。
     * <p>
     * 委托至 {@link RarityRegistry#getAutoRarityMap()}。
     * 返回的是实时视图，修改会直接影响稀有度系统。
     * 包含通过自动稀有度系统（如基于物品属性的自动分配）添加的条目。
     * </p>
     *
     * @return 物品 ID → 稀有度等级 的实时映射视图
     * @since 1.13
     */
    @NotNull
    public static Map<Identifier, Integer> getAutoRarityMap() {
        return RarityRegistry.getAutoRarityMap();
    }
}
