package org.yanbwe.raritycore.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.util.SimpleCacheManager;

public class ItemNameColorUtil {
    
    /**
     * 获取物品名称应该使用的颜色
     * @param stack 物品栈
     * @return 颜色值（ARGB格式）
     */
    public static int getItemNameColor(ItemStack stack) {
        if (!ConfigManager.isEnableItemNameColor() || stack.isEmpty()) {
            return 0xFFFFFFFF; // 默认白色
        }
        
        int rarity = SimpleCacheManager.getCachedRarity(stack);
        if (rarity <= 1) {
            return 0xFFFFFFFF; // 普通物品使用白色
        }
        
        return RaritycoreClient.getRarityColor(rarity);
    }
    
    /**
     * 渲染带颜色的物品名称
     * @param context 绘制上下文
     * @param textRenderer 文字渲染器
     * @param stack 物品栈
     * @param text 文本内容
     * @param x X坐标
     * @param y Y坐标
     * @param shadow 是否显示阴影
     * @return 渲染结果
     */
    public static int renderColoredText(DrawContext context, TextRenderer textRenderer, ItemStack stack, String text, int x, int y, boolean shadow) {
        int color = getItemNameColor(stack);
        return context.drawText(textRenderer, text, x, y, color, shadow);
    }
    
    /**
     * 检查是否应该应用稀有度颜色
     * @param stack 物品栈
     * @return 是否应该应用颜色
     */
    public static boolean shouldApplyRarityColor(ItemStack stack) {
        return ConfigManager.isEnableItemNameColor() && 
               !stack.isEmpty() && 
               SimpleCacheManager.getCachedRarity(stack) > 1;
    }
}