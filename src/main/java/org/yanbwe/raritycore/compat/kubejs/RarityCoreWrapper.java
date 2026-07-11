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

    // ---- V14 视觉表现查询 ----

    @Info("检查工具提示是否染色")
    static boolean isTooltipColorEnabled() {
        return RarityCoreAPI.isTooltipColorEnabled();
    }

    @Info("检查是否渲染物品边框")
    static boolean isBorderEnabled() {
        return RarityCoreAPI.isBorderEnabled();
    }

    @Info("检查是否插入工具提示")
    static boolean isTooltipInsertEnabled() {
        return RarityCoreAPI.isTooltipEnabled();
    }

    @Info("检查无稀有度物品是否跳过渲染")
    static boolean isNoRaritySkip() {
        return RarityCoreAPI.isNoRaritySkip();
    }

    @Info("获取无稀有度物品兜底等级")
    static int getNoRarityDefaultRarity() {
        return RarityCoreAPI.getNoRarityDefaultRarity();
    }

    @Info("获取该等级工具提示内容")
    static String getTooltipContent(int rarity) {
        return RarityCoreAPI.getTooltipContent(rarity);
    }

    @Info("获取该等级 level 段翻译键")
    static String getLevelTranslationKey(int rarity) {
        return RarityCoreAPI.getLevelTranslationKey(rarity);
    }

    @Info("获取该等级 level 段回退键")
    static String getLevelFallbackKey(int rarity) {
        return RarityCoreAPI.getLevelFallbackKey(rarity);
    }

    @Info("获取该等级大于 MAX_RARITY 的特殊稀有度文本")
    static String getSpecialRarityText(int rarity) {
        return RarityCoreAPI.getSpecialRarityText(rarity);
    }

    @Info("检查该等级边框是否使用纹理")
    static boolean isBorderUseTexture(int rarity) {
        return RarityCoreAPI.isBorderUseTexture(rarity);
    }

    @Info("获取该等级边框样式 (1=实心, 0=空心)")
    static int getBorderStyle(int rarity) {
        return RarityCoreAPI.getBorderStyle(rarity);
    }

    @Info("获取边框回退纹理")
    static String getBorderFallback() {
        return RarityCoreAPI.getBorderFallback();
    }

    // ---- V14 视觉表现写入 ----

    @Info("设置是否渲染物品边框")
    static void setBorderEnabled(boolean enable) {
        RarityCoreAPI.setBorderEnabled(enable);
    }

    @Info("设置是否插入工具提示")
    static void setTooltipEnabled(boolean enable) {
        RarityCoreAPI.setTooltipEnabled(enable);
    }

    @Info("设置工具提示是否染色")
    static void setTooltipColorEnabled(boolean enable) {
        RarityCoreAPI.setTooltipColorEnabled(enable);
    }

    @Info("设置无稀有度物品跳过渲染")
    static void setNoRaritySkip(boolean skip) {
        RarityCoreAPI.setNoRaritySkip(skip);
    }

    @Info("设置无稀有度物品兜底等级")
    static void setNoRarityDefaultRarity(int rarity) {
        RarityCoreAPI.setNoRarityDefaultRarity(rarity);
    }

    @Info("设置该等级边框是否使用纹理")
    static void setBorderUseTexture(int rarity, boolean useTexture) {
        RarityCoreAPI.setBorderUseTexture(rarity, useTexture);
    }

    @Info("设置该等级边框样式")
    static void setBorderStyle(int rarity, int style) {
        RarityCoreAPI.setBorderStyle(rarity, style);
    }

    @Info("设置该等级工具提示内容")
    static void setTooltipContent(int rarity, String content) {
        RarityCoreAPI.setTooltipContent(rarity, content);
    }

    @Info("设置该等级星星显示模式")
    static void setStarMode(int rarity, String mode) {
        RarityCoreAPI.setStarMode(rarity, mode);
    }

    @Info("设置该等级星星重复字符")
    static void setStarRepeatChar(int rarity, String repeatChar) {
        RarityCoreAPI.setStarRepeatChar(rarity, repeatChar);
    }

    @Info("设置大于 MAX_RARITY 的特殊稀有度文本")
    static void setSpecialRarityText(int rarity, String text) {
        RarityCoreAPI.setSpecialRarityText(rarity, text);
    }

    // ---- 配置与查询 ----

    @Info("触发完整配置重载")
    static void reloadConfigs() {
        RarityCoreAPI.reloadConfigs();
    }

    @Info("返回该稀有度等级的全部物品 ID")
    static java.util.List<String> getItemsByRarity(int rarity) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (ResourceLocation id : RarityCoreAPI.getItemIdsByRarity(rarity)) {
            ids.add(id.toString());
        }
        return ids;
    }

    @Info("返回当前出现过的稀有度等级集合")
    static java.util.List<Integer> getConfiguredRarities() {
        return new java.util.ArrayList<>(RarityCoreAPI.getConfiguredRarities());
    }

    @Info("稀有度常量")
    static int COMMON() { return RarityCoreAPI.RARITY_COMMON; }
    static int UNCOMMON() { return RarityCoreAPI.RARITY_UNCOMMON; }
    static int RARE() { return RarityCoreAPI.RARITY_RARE; }
    static int EPIC() { return RarityCoreAPI.RARITY_EPIC; }
    static int LEGENDARY() { return RarityCoreAPI.RARITY_LEGENDARY; }
    static int MYTHICAL() { return RarityCoreAPI.RARITY_MYTHICAL; }
    static int UNIQUE() { return RarityCoreAPI.RARITY_UNIQUE; }
}
