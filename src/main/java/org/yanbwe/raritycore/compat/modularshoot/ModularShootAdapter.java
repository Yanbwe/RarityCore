package org.yanbwe.raritycore.compat.modularshoot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * ModularShoot 兼容适配器 — 组件稀有度控制的"半开"范围判定。
 *
 * <p>组件稀有度控制（raritycore.Level）是 per-stack 的，且只被 ModularRarity
 * 写入 ModularShoot 提供的枪械/插件物品上。若对所有物品都执行组件读取，
 * 每个无关物品都要做 {@code CUSTOM_DATA.copyTag()} 全量 NBT 复制，热路径开销大。
 * 因此仅在 ModularShoot 的枪械物品（modularshoot:gun）与插件物品
 * （modularshoot:plugin）上执行组件读取，其余物品 O(1) 短路跳过。</p>
 *
 * <p>不检测绑定表（body-snatch）物品：绑定物品的稀有度由
 * {@code RarityCoreAPI.registerRarity}（ITEM_RARITY_MAP 查询链第 5 位）提供，
 * 不依赖组件读取。</p>
 *
 * <p>无需 ModList 探测：modularshoot 未加载时无人占用这两个 itemId，
 * 判定自然不命中，行为与之前一致。</p>
 */
public final class ModularShootAdapter {

    /** ModularShoot 枪械物品 id */
    private static final ResourceLocation GUN_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath("modularshoot", "gun");

    /** ModularShoot 插件物品 id */
    private static final ResourceLocation PLUGIN_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath("modularshoot", "plugin");

    /**
     * 判断物品堆是否处于组件稀有度读取范围内（半开判定）。
     * 仅 ModularShoot 提供的枪械/插件物品参与组件读取，其余物品返回 false。
     *
     * @param stack 物品堆（null/empty 由调用方先行处理，此处不额外检查）
     * @return 在范围内返回 true
     */
    public static boolean isRelevant(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return GUN_ITEM_ID.equals(itemId) || PLUGIN_ITEM_ID.equals(itemId);
    }

    private ModularShootAdapter() {
    }
}
