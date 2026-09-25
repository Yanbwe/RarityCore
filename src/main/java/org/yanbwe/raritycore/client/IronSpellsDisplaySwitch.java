package org.yanbwe.raritycore.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;

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
 * <p>因此解析链现在两端一致地把铁魔法物品解析为其法术等级对应的稀有度，
 * 关闭联动只意味着<b>本地不显示</b>该稀有度：本类把派生值替换为"回退稀有度"
 * （缓存/注册表值，取不到则用 {@code defaults.noRarity.defaultRarity}），
 * 与"未配置稀有度"物品的表现保持一致。</p>
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
     *                     （NBT 缓存 → ID 缓存 → 无稀有度默认等级）
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
     * <p>{@code irons_spellbooks:spell_container} 是法术卷轴/法术书用于保存法术等级（决定稀有度）的键，
     * 与 {@link org.yanbwe.raritycore.cache.DualCacheManager} 中绕过 ID 缓存的判定条件保持一致。</p>
     */
    public static boolean isIronSpellsRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return false;
        }
        CompoundTag tag = itemStack.getTag();
        return tag != null && tag.contains("irons_spellbooks:spell_container");
    }

    /** 与"未配置稀有度"物品一致的回退链：按物品查 ID 缓存 → 无稀有度默认等级 */
    private static int resolveFallbackRarity(ItemStack itemStack) {
        Integer cached = RenderCacheManager.getCachedRarity(itemStack.getItem());
        if (cached != null) {
            return cached;
        }
        return RarityStyleConfigManager.getDefaultsNoRarityDefaultRarity();
    }
}
