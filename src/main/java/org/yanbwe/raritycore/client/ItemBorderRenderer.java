package org.yanbwe.raritycore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.RarityCore;
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
        if (!ConfigManager.isEnableItemBorderRendering()) {
            return;
        }
        
        if (itemStack.isEmpty()) {
            return;
        }

        // 获取物品的稀有度
        Item item = itemStack.getItem();
        Integer rarity = RarityRegistry.getRarity(item);
        
        // 如果启用了跳过未配置物品且物品没有配置稀有度，则不渲染
        if (ConfigManager.isSkipUnconfiguredItems() && rarity == null) {
            return;
        }
        
        // 遵循模组包容性原则：小于1视为1，大于7视为7
        rarity = RarityValidator.normalizeRarity(rarity);
        
        // 检查是否启用物品背景渲染
        if (ConfigManager.isEnableItemBackgroundRendering()) {
            // 渲染物品背景
            renderItemBackground(guiGraphics, rarity, x, y);
        }
        
        // 检查是否使用纹理边框
        if (ConfigManager.isUseTextureBorder()) {
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
        // 构造纹理路径，例如: raritycore:textures/border/rarity_1.png
        String textureName = "rarity_" + rarity;
        ResourceLocation textureLocation = new ResourceLocation(RarityConstants.BORDER_TEXTURE_PATH + textureName + RarityConstants.TEXTURE_SUFFIX);
        
        // 尝试绘制纹理边框
        try {
            // 启用混合模式以确保纹理透明度正确显示
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            
            // 使用 blit 方法，指定完整的纹理坐标和裁剪尺寸
            // 参数顺序：ResourceLocation texture, int x, int y, float z, 
            //           int uOffset, int vOffset, int uWidth, int vHeight, 
            //           int textureWidth, int textureHeight
            guiGraphics.blit(textureLocation, x, y, 0, 0, 16, 16, 16, 16);
        } catch (Exception e) {
            // 如果纹理加载失败，回退到颜色边框
            RarityCore.LOGGER.warn("Failed to load texture for rarity {}, falling back to color border: {}", rarity, e.getMessage());
            renderColorBorder(guiGraphics, rarity, x, y);
        }
    }
    
    /**
     * 使用颜色渲染边框
     * @param guiGraphics GUI图形上下文
     * @param rarity 稀有度等级
     * @param x X坐标
     * @param y Y坐标
     */
    private static void renderItemBackground(GuiGraphics guiGraphics, int rarity, int x, int y) {
        // 根据稀有度获取对应颜色
        int backgroundColor = RarityColorUtil.getRarityArgbColor(rarity);
        
        // 设置背景颜色为半透明
        int alphaMask;
        if (ConfigManager.isUseTextureBorder()) {
            // 当启用纹理边框时，降低物品背景的透明度（如设为25%），避免与纹理叠加导致透明度异常
            alphaMask = 0x40000000;  // 25%透明度的alpha值
        } else {
            alphaMask = 0x60000000;  // 37.5%透明度的alpha值
        }
        int translucentBackgroundColor = (backgroundColor & 0x00FFFFFF) | alphaMask;  // 保留RGB值，设置alpha
        
        // 启用混合模式以确保透明度正确显示
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // 绘制16x16区域的半透明背景
        guiGraphics.fill(x, y, x + 16, y + 16, translucentBackgroundColor);
    }
    
    private static void renderColorBorder(GuiGraphics guiGraphics, int rarity, int x, int y) {
        // 根据稀有度获取对应颜色
        int borderColor = RarityColorUtil.getRarityArgbColor(rarity);
        
        // 启用混合模式以确保透明度正确显示
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        if (ConfigManager.getItemBorderStyle() == 1) {
            // 实心边框 - 50%半透明，16x16大小
            // 通过将alpha值设置为0x80（128/255 ≈ 50%透明度）实现半透明
            int alphaMask = 0x80000000;  // 50%透明度的alpha值
            int translucentColor = (borderColor & 0x00FFFFFF) | alphaMask;  // 保留RGB值，设置alpha为50%
            
            // 绘制16x16区域的半透明背景
            guiGraphics.fill(x, y, x + 16, y + 16, translucentColor);
        } else {
            // 空心边框 - 16x16 像素的物品槽，边框宽度为1像素
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
     * 根据稀有度等级获取对应颜色
     * @param rarity 稀有度等级 (1-7)
     * @return ARGB颜色值
     */
    private static int getRarityColor(int rarity) {
        return RarityColorUtil.getRarityArgbColor(rarity);
    }
}