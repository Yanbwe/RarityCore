package org.yanbwe.raritycore.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.RarityColorUtil;
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
        // 检查是否启用了边框渲染
        if (!ConfigManager.isEnableItemBorderRendering()) {
            return;
        }
        
        if (itemStack.isEmpty()) {
            return;
        }

        // 获取物品的稀有度，未注册的物品默认为普通
        Item item = itemStack.getItem();
        Integer rarity = RarityRegistry.getRarity(item);
        if (rarity == null) {
            rarity = RarityConstants.RARITY_COMMON; // 默认为普通
        }

        // 限制稀有度在1-7范围内
        if (rarity < RarityConstants.RARITY_COMMON) {
            rarity = RarityConstants.RARITY_COMMON;
        } else if (rarity > RarityConstants.RARITY_UNIQUE) {
            rarity = RarityConstants.RARITY_UNIQUE;
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
    private static void renderColorBorder(GuiGraphics guiGraphics, int rarity, int x, int y) {
        // 根据稀有度获取对应颜色
        int borderColor = RarityColorUtil.getRarityArgbColor(rarity);
        
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