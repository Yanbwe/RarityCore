package org.yanbwe.raritycore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.RarityClientConfig;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityConstants;

public class ItemBorderRenderer {
    
    /**
     * 根据物品稀有度渲染边框
     * @param guiGraphics GUI图形上下文
     * @param itemStack 物品栈
     * @param x X坐标
     * @param y Y坐标
     */
    public static void renderRarityBorder(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y) {
        // 检查是否启用了边框渲染 (client.json 总闸)
        if (!ClientConfigManager.isEnableItemBorderRendering()) {
            return;
        }
        
        if (itemStack.isEmpty()) {
            return;
        }

        Integer rarity;
        
        // 检查缓存系统是否启用
        boolean isCacheEnabled = ClientConfigManager.isEnableCacheSystem();
        
        if (isCacheEnabled) {
            rarity = RenderCacheManager.getCachedRarity(itemStack);
            if (rarity == null) {
                rarity = RarityRegistry.getRarity(itemStack);
                if (rarity != null) {
                    RenderCacheManager.cacheItemStackRarity(itemStack, rarity);
                }
            }
        } else {
            rarity = RarityRegistry.getRarity(itemStack);
        }
        
        if (rarity == null) {
            rarity = RarityConstants.RARITY_COMMON;
        }
        
        Item item = itemStack.getItem();
        if (ClientConfigManager.isSkipUnconfiguredItems() && !RarityRegistry.hasConfiguredRarity(item)) {
            return;
        }
        
        // 直接使用原始稀有度值查询 RarityClientConfig，其 getConfig() 内部处理：
        // level < 1 → 回退1 | level > 7 未配置 → 回退7 | level 已配置 → 直接命中
        // 从 RarityClientConfig 获取该等级的客户端配置
        RarityClientConfig clientConfig = RarityClientConfig.getInstance();
        
        // 检查 RarityClientConfig 的 per-level renderer 开关
        if (!clientConfig.isEmpty() && !clientConfig.isRendererEnabled(rarity)) {
            return;
        }
        
        // 根据配置选择渲染方式
        if (ClientConfigManager.isUseTextureBorder()) {
            // 从 RarityClientConfig 获取纹理路径，回退到默认路径
            String texturePath = clientConfig.getTexture(rarity);
            renderTextureBorder(guiGraphics, texturePath, x, y, rarity);
        } else {
            // 使用 RarityClientConfig 的 RGB 颜色渲染边框
            int rgbColor = clientConfig.getColor(rarity);
            renderColorBorder(guiGraphics, rgbColor, x, y);
        }
    }
    
    /**
     * 使用纹理渲染边框
     * @param guiGraphics GUI图形上下文
     * @param texturePath 纹理资源路径
     * @param x X坐标
     * @param y Y坐标
     * @param fallbackRarity 纹理加载失败时回退使用的稀有度等级
     */
    private static void renderTextureBorder(GuiGraphics guiGraphics, String texturePath, int x, int y, int fallbackRarity) {
        ResourceLocation textureLocation = null;
        
        try {
            textureLocation = ResourceLocation.parse(texturePath);
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to parse texture path '{}', falling back to color border: {}", texturePath, e.getMessage());
            RarityClientConfig clientConfig = RarityClientConfig.getInstance();
            renderColorBorder(guiGraphics, clientConfig.getColor(fallbackRarity), x, y);
            return;
        }
        
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            guiGraphics.blit(textureLocation, x, y, 0, 0, 16, 16, 16, 16);
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to load texture '{}', falling back to color border: {}", texturePath, e.getMessage());
            RarityClientConfig clientConfig = RarityClientConfig.getInstance();
            renderColorBorder(guiGraphics, clientConfig.getColor(fallbackRarity), x, y);
        }
    }
    
    /**
     * 使用 RGB 颜色渲染边框
     * @param guiGraphics GUI 图形上下文
     * @param rgbColor    RGB 颜色值 (0xRRGGBB)
     * @param x           X 坐标
     * @param y           Y 坐标
     */
    private static void renderColorBorder(GuiGraphics guiGraphics, int rgbColor, int x, int y) {
        // 添加完全不透明 alpha 通道
        int argbColor = 0xFF000000 | (rgbColor & 0x00FFFFFF);
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        if (ClientConfigManager.getItemBorderStyle() == 1) {
            // 实心边框 - 50%半透明
            int alphaMask = 0x80000000;
            int translucentColor = (argbColor & 0x00FFFFFF) | alphaMask;
            guiGraphics.fill(x, y, x + 16, y + 16, translucentColor);
        } else {
            // 空心边框
            guiGraphics.fill(x, y, x + 16, y + 1, argbColor);
            guiGraphics.fill(x, y + 15, x + 16, y + 16, argbColor);
            guiGraphics.fill(x, y, x + 1, y + 16, argbColor);
            guiGraphics.fill(x + 15, y, x + 16, y + 16, argbColor);
        }
    }
    
    /**
     * 处理skipUnconfiguredItems配置变更
     * 当配置改变时调用此方法来刷新渲染状态
     */
    public static void handleSkipConfigChange() {
        // 使边框渲染缓存失效
        RenderCacheManager.clearAllCache();
        // ItemBorderRenderer: skipUnconfiguredItems 配置变更已处理
    }
    

}