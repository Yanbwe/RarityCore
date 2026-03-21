package org.yanbwe.raritycore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

public class ItemBorderRenderer {
    
    /**
     * 根据物品稀有度渲染边框
     * @param guiGraphics GUI图形上下文
     * @param itemStack 物品栈
     * @param x X坐标
     * @param y Y坐标
     */
    public static void renderRarityBorder(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y) {
        // 检查是否启用了边框渲染
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
            // 获取物品栈的稀有度(使用缓存)
            rarity = org.yanbwe.raritycore.cache.RenderCacheManager.getCachedRarity(itemStack);
            // 如果缓存没有命中,则从注册表获取并缓存
            if (rarity == null) {
                rarity = RarityRegistry.getRarity(itemStack);
                if (rarity != null) {
                    org.yanbwe.raritycore.cache.RenderCacheManager.cacheItemStackRarity(itemStack, rarity);
                }
            }
        } else {
            // 缓存系统禁用时直接获取稀有度
            rarity = RarityRegistry.getRarity(itemStack);
        }
        
        // 如果仍然没有获取到稀有度,使用默认值
        if (rarity == null) {
            rarity = RarityConstants.RARITY_COMMON;
        }
        
        // 如果启用了跳过未配置物品且物品没有配置稀有度,则不渲染
        // 注意:需要检查物品是否真的没有配置,而不是默认的稀有度1
        Item item = itemStack.getItem();
        if (ClientConfigManager.isSkipUnconfiguredItems() && !hasConfiguredRarity(item)) {
            return;
        }
        
        // 遵循模组包容性原则:小于1视为1,大于7视为7
        rarity = RarityValidator.normalizeRarity(rarity);
        
        // 根据配置选择渲染方式
        if (ClientConfigManager.isUseTextureBorder()) {
            // 使用纹理渲染边框
            renderTextureBorder(guiGraphics, rarity, x, y);
        } else {
            // 使用颜色渲染边框
            renderColorBorder(guiGraphics, rarity, x, y);
        }
    }
    
    /**
     * 使用纹理渲染边框
     * @param guiGraphics GUI图形上下文
     * @param rarity 稀有度等级
     * @param x X坐标
     * @param y Y坐标
     */
    private static void renderTextureBorder(GuiGraphics guiGraphics, int rarity, int x, int y) {
        // 构造纹理路径,例如: raritycore:textures/border/rarity_1.png
        String textureName = "rarity_" + rarity;
        ResourceLocation textureLocation = null;
        
        try {
            // 安全解析纹理路径
            textureLocation = ResourceLocation.parse(RarityConstants.BORDER_TEXTURE_PATH + textureName + RarityConstants.TEXTURE_SUFFIX);
        } catch (Exception e) {
            // 路径解析失败,回退到颜色边框
            RarityCore.LOGGER.warn("Failed to parse texture path for rarity {}, falling back to color border: {}", rarity, e.getMessage());
            renderColorBorder(guiGraphics, rarity, x, y);
            return;
        }
        
        // 尝试绘制纹理边框
        try {
            // 启用混合模式以确保纹理透明度正确显示
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            
            // 使用 blit 方法,指定完整的纹理坐标和裁剪尺寸
            // 参数顺序:ResourceLocation texture, int x, int y, float z, 
            //           int uOffset, int vOffset, int uWidth, int vHeight, 
            //           int textureWidth, int textureHeight
            guiGraphics.blit(textureLocation, x, y, 0, 0, 16, 16, 16, 16);
        } catch (Exception e) {
            // 如果纹理加载失败,回退到颜色边框
            RarityCore.LOGGER.warn("Failed to load texture for rarity {}, falling back to color border: {}", rarity, e.getMessage());
            renderColorBorder(guiGraphics, rarity, x, y);
        }
    }
    
    private static void renderColorBorder(GuiGraphics guiGraphics, int rarity, int x, int y) {
        // 根据稀有度获取对应颜色
        int borderColor = RarityColorUtil.getRarityArgbColor(rarity);
        
        // 启用混合模式以确保透明度正确显示
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        if (ClientConfigManager.getItemBorderStyle() == 1) {
            // 实心边框 - 50%半透明,16x16大小
            // 通过将alpha值设置为0x80(128/255 ≈ 50%透明度)实现半透明
            int alphaMask = 0x80000000;  // 50%透明度的alpha值
            int translucentColor = (borderColor & 0x00FFFFFF) | alphaMask;  // 保留RGB值,设置alpha为50%
            
            // 绘制16x16区域的半透明背景
            guiGraphics.fill(x, y, x + 16, y + 16, translucentColor);
        } else {
            // 空心边框 - 16x16 像素的物品槽,边框宽度为1像素
            // 上边框
            guiGraphics.fill(x, y, x + 16, y + 1, borderColor);
            // 下边框
            guiGraphics.fill(x, y + 15, x + 16, y + 16, borderColor);
            // 左边框
            guiGraphics.fill(x, y, x + 1, y + 16, borderColor);
            // 右边框
            guiGraphics.fill(x + 15, y, x + 16, y + 16, borderColor);
        }
    }
    
    /**
     * 检查物品是否有配置的稀有度
     * @param item 要检查的物品
     * @return 如果物品有配置稀有度返回true,否则返回false
     */
    private static boolean hasConfiguredRarity(Item item) {
        if (item == null) {
            return false;
        }
        
        // 获取物品ID
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            return false;
        }
        
        // 检查是否在注册表中有配置
        return RarityRegistry.ITEM_RARITY_MAP.containsKey(itemId);
    }
    
    /**
     * 处理skipUnconfiguredItems配置变更
     * 当配置改变时调用此方法来刷新渲染状态
     */
    public static void handleSkipConfigChange() {
        // 使边框渲染缓存失效
        RenderCacheManager.clearAllCache();
        // ItemBorderRenderer: skipUnconfiguredItems config change handled
    }
    

}