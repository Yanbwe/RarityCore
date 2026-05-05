package org.yanbwe.raritycore.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

import java.util.Map;

/**
 * RarityCore 正式公共 API
 * 为其他模组和脚本提供统一的静态方法入口
 *
 * <h2>快速使用</h2>
 * <pre>{@code
 * // 注册稀有度
 * RarityCoreAPI.registerRarity(myItem, RarityCoreAPI.RARITY_LEGENDARY);
 *
 * // 查询稀有度
 * int rarity = RarityCoreAPI.getRarity(itemStack);
 *
 * // 获取标准化稀有度（自动钳制到 1-7）
 * int normalized = RarityCoreAPI.getNormalizedRarity(itemStack);
 *
 * // 验证稀有度值
 * if (RarityCoreAPI.isValidRarity(rarity)) { ... }
 * }</pre>
 *
 * @see RarityRegistry 内部注册和查询实现
 * @see RarityConstants 稀有度等级常量定义
 * @see RarityValidator 稀有度值验证工具
 */
public final class RarityCoreAPI {

    private RarityCoreAPI() {}

    // ══════════════════════════════════════════════════════
    // 常量
    // ══════════════════════════════════════════════════════

    /** 普通 (Common) */
    public static final int RARITY_COMMON = RarityConstants.RARITY_COMMON;
    /** 稀有 (Uncommon) */
    public static final int RARITY_UNCOMMON = RarityConstants.RARITY_UNCOMMON;
    /** 罕见 (Rare) */
    public static final int RARITY_RARE = RarityConstants.RARITY_RARE;
    /** 史诗 (Epic) */
    public static final int RARITY_EPIC = RarityConstants.RARITY_EPIC;
    /** 传说 (Legendary) */
    public static final int RARITY_LEGENDARY = RarityConstants.RARITY_LEGENDARY;
    /** 神话 (Mythical) */
    public static final int RARITY_MYTHICAL = RarityConstants.RARITY_MYTHICAL;
    /** 唯一 (Unique) */
    public static final int RARITY_UNIQUE = RarityConstants.RARITY_UNIQUE;

    /** 最小稀有度等级 */
    public static final int MIN_RARITY = RarityConstants.MIN_RARITY;
    /** 最大稀有度等级 */
    public static final int MAX_RARITY = RarityConstants.MAX_RARITY;

    /** 默认 RGB 颜色值 (白色) */
    public static final int DEFAULT_RGB_COLOR = 0xCCCCCC;

    // ══════════════════════════════════════════════════════
    // 稀有度注册与查询
    // ══════════════════════════════════════════════════════

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
     * 获取 ItemStack 的稀有度等级。
     * 优先级：物品数据匹配 &gt; 神化模组 &gt; 本模组配置 &gt; 自动计算 &gt; 原版映射
     *
     * @param itemStack 要查询的物品栈
     * @return 稀有度等级 (1-7)，默认返回 1
     */
    public static int getRarity(@NotNull ItemStack itemStack) {
        return RarityRegistry.getRarity(itemStack);
    }

    /**
     * 获取 Item 的稀有度等级。
     * 优先级：物品数据匹配 &gt; 神化模组 &gt; 本模组配置 &gt; 自动计算 &gt; 原版映射
     *
     * @param item 要查询的物品
     * @return 稀有度等级 (1-7)，默认返回 1
     */
    public static int getRarity(@NotNull Item item) {
        return RarityRegistry.getRarity(item);
    }

    /**
     * 获取 ItemStack 的标准化稀有度等级。
     * 非法值（小于 1 或大于 7）会被规范化到有效范围内。
     *
     * @param itemStack 要查询的物品栈
     * @return 标准化后的稀有度等级 (1-7)
     */
    public static int getNormalizedRarity(@NotNull ItemStack itemStack) {
        return RarityRegistry.getNormalizedRarity(itemStack);
    }

    /**
     * 获取 Item 的标准化稀有度等级。
     * 非法值（小于 1 或大于 7）会被规范化到有效范围内。
     *
     * @param item 要查询的物品
     * @return 标准化后的稀有度等级 (1-7)
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
     * 获取物品的本地化稀有度工具提示字符串。
     *
     * @param item 要获取工具提示的物品
     * @return 本地化的稀有度工具提示
     */
    public static String getLocalizedTooltip(@NotNull Item item) {
        return RarityRegistry.getLocalizedRarityTooltip(item);
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
     * 验证稀有度值是否在有效范围内 (1-7)。
     *
     * @param rarity 要验证的稀有度值
     * @return 如果在 1-7 范围内返回 true
     */
    public static boolean isValidRarity(int rarity) {
        return RarityValidator.isValidRarity(rarity);
    }

    /**
     * 标准化稀有度值，遵循模组的包容性原则：
     * 小于 1 的值视为 1，大于 7 的值视为 7。
     *
     * @param rarity 原始稀有度值
     * @return 标准化后的稀有度值 (1-7)
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
}
