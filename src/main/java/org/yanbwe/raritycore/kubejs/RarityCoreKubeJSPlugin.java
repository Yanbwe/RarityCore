package org.yanbwe.raritycore.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import org.yanbwe.raritycore.api.RarityCoreAPI;

/**
 * RarityCore KubeJS 集成插件
 * <p>
 * 通过 {@code kubejs.plugins.txt} 被 KubeJS 自动发现和加载。
 * 在 {@link #registerBindings(BindingRegistry)} 中将 {@link RarityCoreAPI}
 * 的所有 public static 方法和常量注册为 {@code raritycore} 绑定。
 *
 * <h3>脚本使用示例</h3>
 * <pre>{@code
 * // 查询物品稀有度
 * var rarity = raritycore.getRarity(itemStack);
 * var normalized = raritycore.getNormalizedRarity(itemStack);
 *
 * // 使用稀有度常量
 * if (rarity === raritycore.RARITY_LEGENDARY) {
 *     console.log("This is a legendary item!");
 * }
 *
 * // 验证稀有度值
 * if (raritycore.isValidRarity(5)) { ... }
 * }</pre>
 *
 * @see RarityCoreAPI 正式公共 API
 */
public class RarityCoreKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerBindings(BindingRegistry bindings) {
        // 将 RarityCoreAPI 的 public static 方法和常量直接暴露为 raritycore 绑定
        // KubeJS/Rhino 会自动包装静态成员：raritycore.getRarity(), raritycore.RARITY_COMMON 等
        bindings.add("raritycore", RarityCoreAPI.class);
    }
}
