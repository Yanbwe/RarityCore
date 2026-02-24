package org.yanbwe.raritycore.client;

import net.fabricmc.api.ClientModInitializer;

package org.yanbwe.raritycore.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.yanbwe.raritycore.Raritycore;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.util.SimpleCacheManager;

public class RaritycoreClient implements ClientModInitializer {
    
    // 纹理资源标识符
    private static final Identifier[] BORDER_TEXTURES = new Identifier[8];
    
    static {
        // 初始化边框纹理路径
        for (int i = 1; i <= 7; i++) {
            BORDER_TEXTURES[i] = new Identifier(Raritycore.MOD_ID, "textures/border/rarity_" + i + ".png");
        }
    }
    
    @Override
    public void onInitializeClient() {
        Raritycore.LOGGER.info("Initializing RarityCore Client...");
        
        // 预加载常用物品缓存
        SimpleCacheManager.preloadCommonItems();
        
        // 注册HUD渲染回调（用于测试显示）
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            // 这里可以添加一些调试信息显示
        });
        
        Raritycore.LOGGER.info("RarityCore Client initialized successfully!");
    }
    
    /**
     * 渲染物品稀有度边框
     * @param context 绘制上下文
     * @param itemStack 物品栈
     * @param x X坐标
     * @param y Y坐标
     */
    public static void renderItemRarityBorder(DrawContext context, ItemStack itemStack, int x, int y) {
        if (!ConfigManager.isEnableItemBorderRendering() || itemStack.isEmpty()) {
            return;
        }
        
        // 使用缓存获取稀有度
        int rarity = SimpleCacheManager.getCachedRarity(itemStack);
        if (rarity <= 1) { // 普通物品不显示边框
            return;
        }
        
        if (ConfigManager.isUseTextureBorder()) {
            // 纹理边框渲染
            renderTextureBorder(context, rarity, x, y);
        } else {
            // 颜色边框渲染
            int color = getRarityColor(rarity);
            renderColorBorder(context, color, x, y, ConfigManager.getItemBorderStyle() == 1);
        }
    }
    
    /**
     * 渲染纹理边框
     */
    private static void renderTextureBorder(DrawContext context, int rarity, int x, int y) {
        if (rarity < 1 || rarity > 7) {
            return;
        }
        
        Identifier texture = BORDER_TEXTURES[rarity];
        if (texture != null) {
            try {
                // 渲染16x16的纹理边框
                context.drawTexture(texture, x, y, 0, 0, 16, 16, 16, 16);
                Raritycore.LOGGER.debug("Rendered texture border for rarity {} at ({}, {})", rarity, x, y);
            } catch (Exception e) {
                Raritycore.LOGGER.warn("Failed to render texture border for rarity {}: {}", rarity, e.getMessage());
                // 回退到颜色边框
                int color = getRarityColor(rarity);
                renderColorBorder(context, color, x, y, false);
            }
        }
    }
    
    /**
     * 渲染颜色边框
     */
    private static void renderColorBorder(DrawContext context, int color, int x, int y, boolean solid) {
        if (solid) {
            // 实心边框 - 半透明填充
            int alphaColor = (color & 0x00FFFFFF) | 0x40000000; // 25%透明度
            context.fill(x, y, x + 16, y + 16, alphaColor);
        } else {
            // 空心边框 - 1像素宽度
            context.fill(x, y, x + 16, y + 1, color);           // 上边框
            context.fill(x, y + 15, x + 16, y + 16, color);     // 下边框
            context.fill(x, y, x + 1, y + 16, color);           // 左边框
            context.fill(x + 15, y, x + 16, y + 16, color);     // 右边框
        }
    }
    
    /**
     * 根据稀有度获取对应颜色（用于颜色边框模式和纹理加载失败时的回退）
     */
    private static int getRarityColor(int rarity) {
        return switch (rarity) {
            case 2 -> 0xFFAAAAAA; // 稀有 - 浅灰色
            case 3 -> 0xFF55FF55; // 罕见 - 绿色
            case 4 -> 0xFF5555FF; // 史诗 - 蓝色
            case 5 -> 0xFFFF55FF; // 传说 - 紫色
            case 6 -> 0xFFFFAA00; // 神话 - 橙色
            case 7 -> 0xFFFF5555; // 唯一 - 红色
            default -> 0xFFFFFFFF; // 普通 - 白色
        };
    }
    
    /**
     * 获取指定稀有度的纹理标识符
     * @param rarity 稀有度等级(1-7)
     * @return 纹理标识符，如果无效则返回null
     */
    public static Identifier getBorderTexture(int rarity) {
        if (rarity >= 1 && rarity <= 7) {
            return BORDER_TEXTURES[rarity];
        }
        return null;
    }
    
    /**
     * 检查纹理边框是否可用
     * @param rarity 稀有度等级
     * @return 如果纹理存在返回true
     */
    public static boolean isTextureBorderAvailable(int rarity) {
        Identifier texture = getBorderTexture(rarity);
        if (texture == null) return false;
        
        // 这里可以添加更复杂的纹理存在性检查
        return true;
    }
}
