package org.yanbwe.raritycore.compat.kubejs;

import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.api.RarityCoreAPI;
import org.yanbwe.raritycore.registry.RarityRegistry;

/**
 * RarityCore KubeJS 绑定包装器
 * 通过静态方法暴露 API 给 KubeJS 脚本
 * 
 * <h2>JS 用法</h2>
 * <pre>{@code
 * // 注册稀有度
 * RarityCore.register("minecraft:diamond_sword", 5)
 * 
 * // 查询稀有度
 * let rarity = RarityCore.getRarity("minecraft:diamond_sword")
 * 
 * // 获取颜色
 * let color = RarityCore.getColor(5)
 * }</pre>
 */
@Info("RarityCore API bindings for KubeJS")
public interface RarityCoreWrapper {

    // ---- 注册 ----

    @Info("注册物品稀有度 (1-7)")
    static void register(String itemId, int rarity) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        if (item != null) {
            RarityCoreAPI.registerRarity(item, rarity);
        }
    }

    @Info("删除物品稀有度注册")
    static void unregister(String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        if (item != null) {
            RarityCoreAPI.unregisterRarity(item);
        }
    }

    // ---- 查询 ----

    @Info("通过物品 ID 获取稀有度等级")
    static int getRarity(String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        return item != null ? RarityCoreAPI.getRarity(item) : 1;
    }

    @Info("通过物品 ID 获取标准化稀有度 (1-7)")
    static int getNormalizedRarity(String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        return item != null ? RarityCoreAPI.getNormalizedRarity(item) : 1;
    }

    @Info("检查物品是否有已配置的稀有度")
    static boolean hasRarity(String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        return item != null && RarityCoreAPI.hasConfiguredRarity(item, null);
    }

    // ---- 颜色 ----

    @Info("获取稀有度等级对应的 RGB 颜色 (0xRRGGBB)")
    static int getColor(int rarity) {
        return RarityCoreAPI.getRarityColor(rarity);
    }

    @Info("获取稀有度等级默认 RGB 颜色")
    static int getDefaultColor(int rarity) {
        return RarityCoreAPI.getRarityRgbColor(rarity);
    }

    @Info("解析十六进制颜色字符串为 RGB int")
    static int parseColor(String hex) {
        return RarityCoreAPI.parseColor(hex);
    }

    // ---- 纹理 ----

    @Info("获取稀有度等级的纹理路径")
    static String getTexture(int rarity) {
        return RarityCoreAPI.getRarityTexture(rarity);
    }

    // ---- 验证 ----

    @Info("验证稀有度值是否在有效范围内 (1-7)")
    static boolean isValidRarity(int rarity) {
        return RarityCoreAPI.isValidRarity(rarity);
    }

    @Info("标准化稀有度值 (<1→1, >7→7)")
    static int normalizeRarity(int rarity) {
        return RarityCoreAPI.normalizeRarity(rarity);
    }

    // ---- 配置 ----

    @Info("检查该等级是否启用边框渲染")
    static boolean isRendererEnabled(int rarity) {
        return RarityCoreAPI.isLevelRendererEnabled(rarity);
    }

    @Info("检查该等级是否启用工具提示")
    static boolean isTooltipEnabled(int rarity) {
        return RarityCoreAPI.isLevelTooltipEnabled(rarity);
    }

    // ---- 常量 ----

    @Info("稀有度常量")
    static int COMMON() { return RarityCoreAPI.RARITY_COMMON; }
    static int UNCOMMON() { return RarityCoreAPI.RARITY_UNCOMMON; }
    static int RARE() { return RarityCoreAPI.RARITY_RARE; }
    static int EPIC() { return RarityCoreAPI.RARITY_EPIC; }
    static int LEGENDARY() { return RarityCoreAPI.RARITY_LEGENDARY; }
    static int MYTHICAL() { return RarityCoreAPI.RARITY_MYTHICAL; }
    static int UNIQUE() { return RarityCoreAPI.RARITY_UNIQUE; }
}
