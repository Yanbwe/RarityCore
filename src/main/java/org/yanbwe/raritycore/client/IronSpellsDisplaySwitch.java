package org.yanbwe.raritycore.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.cache.RarityCacheCoordinator;
import org.yanbwe.raritycore.compat.ironsspells.IronSpellsAdapter;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.registry.RarityRegistry;

/**
 * 铁魔法（Iron's Spellbooks）稀有度联动的<b>客户端显示开关</b>。
 *
 * <h2>为什么这个开关只作用于显示</h2>
 * <p>{@code client.json} 中的 {@code enableIronSpellsAdapter} 本质是客户端显示偏好：它写在
 * {@code config/raritycore/client.json} 里，而该文件在服务端与每个玩家的客户端上<b>各存一份且互不相同</b>。
 * 如果稀有度解析阶段（{@link org.yanbwe.raritycore.registry.RarityRegistry}）去读它，
 * 服务端就会用"服务器自己的 client.json"决定解析结果，导致同一件法术卷轴在服务端与客户端
 * 算出不同稀有度，且用户没有任何手段让两边对齐。</p>
 *
 * <p>因此解析链现在两端一致地把铁魔法物品解析为其法术等级对应的稀有度
 * （{@code RarityRegistry.checkIronSpellsRarity()} 不再读该开关），
 * 关闭联动只意味着<b>本地不显示</b>该稀有度：本类把派生值替换为"回退稀有度"
 * （ID 缓存/注册表里该物品类型本来的值，取不到则用
 * {@code defaults.noRarity.defaultRarity}），与"未配置稀有度"物品的表现保持一致。</p>
 *
 * <p>注意：{@code RarityCoreAPI.getRarity()} 等对外 API 返回的仍是解析链的真实结果（法术等级），
 * 不受本开关影响——这样其它模组/KubeJS 拿到的数据在任何一端都相同。</p>
 */
public final class IronSpellsDisplaySwitch {

    private IronSpellsDisplaySwitch() {}

    /**
     * 若铁魔法联动已在客户端被关闭，则把铁魔法派生稀有度替换为回退稀有度。
     *
     * @param itemStack 物品堆
     * @param rarity    解析链给出的稀有度（可能来自铁魔法）
     * @param itemFallback 关闭显示时使用的回退值；传 {@code null} 表示由本方法自行解析
     *                     （见 {@link #resolveFallbackRarity(ItemStack)}）
     * @return 应显示/使用的稀有度
     */
    public static int applyIfDisabled(ItemStack itemStack, int rarity, Integer itemFallback) {
        // 开关开启（默认）→ 完全不做额外工作，渲染热路径零开销
        if (ClientConfigManager.isEnableIronSpellsAdapter()) {
            return rarity;
        }
        if (!isIronSpellsRarity(itemStack)) {
            return rarity;
        }
        return itemFallback != null ? itemFallback : resolveFallbackRarity(itemStack);
    }

    /**
     * 该物品的稀有度是否由铁魔法适配器派生。
     *
     * <p>判定方式：{@link IronSpellsAdapter#getMappedRarity(ItemStack)} 能读出有效法术等级，
     * 就说明解析链的 iron spells 分支会命中该物品（解析链正是靠同一个方法取值），
     * 也就是渲染时看到的稀有度确实来自法术等级。没有 {@code irons_spellbooks:spell_container}
     * 组件、组件为空、或适配器未加载时都返回 false，调用方保持原稀有度不变。</p>
     */
    public static boolean isIronSpellsRarity(ItemStack itemStack) {
        return IronSpellsAdapter.getMappedRarity(itemStack) != null;
    }

    /**
     * 与"未配置稀有度"物品一致的回退链：
     * <ol>
     *   <li>ID 缓存（解析阶段写入的该物品类型原值）</li>
     *   <li>{@link RarityRegistry#ITEM_RARITY_MAP}（解析阶段的原值/服务端同步来的配置值）</li>
     *   <li>{@code defaults.noRarity.defaultRarity}</li>
     * </ol>
     *
     * <p>之所以要单独查一次 ITEM_RARITY_MAP：1.21.1 的 ID 缓存没有"从配置映射预加载"这一步
     * （1.20.1 的 {@code DualCacheManager.preloadIdCache()} 在 1.21.1 中不存在），
     * 且客户端收到 {@code RaritySyncPayload} 时是直接写 ITEM_RARITY_MAP、不写 ID 缓存，
     * 铁魔法法术卷轴又因带 spell_container 组件而只写组件缓存——只查 ID 缓存的话，
     * "优先沿用解析阶段原值"在真正需要它的客户端永远取不到值。</p>
     */
    private static int resolveFallbackRarity(ItemStack itemStack) {
        if (itemStack != null && !itemStack.isEmpty()) {
            Integer cached = RarityCacheCoordinator.getCachedRarity(itemStack.getItem());
            if (cached != null) {
                return cached;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
            if (itemId != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                Integer fromRegistry = RarityRegistry.ITEM_RARITY_MAP.get(itemId);
                if (fromRegistry != null) {
                    return fromRegistry;
                }
            }
        }
        return RarityStyleConfigManager.getInstance().getNoRarityDefaultRarity();
    }
}
