package org.yanbwe.raritycore.compat.sophisticatedcore;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.neoforged.fml.ModList;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.client.ItemBorderRenderer;

/**
 * 精妙核心兼容性实现类
 * 提供更直接的兼容性方法调用
 */
public class SophisticatedCoreCompat {
    
    private static boolean isInitialized = false;
    
    /**
     * 检查精妙核心是否可用
     */
    public static boolean isSophisticatedCoreAvailable() {
        return ModList.get().isLoaded("sophisticatedcore");
    }
    
    /**
     * 初始化精妙核心兼容性（直接方式）
     * 需要在精妙核心存在的情况下调用
     */
    public static void initializeDirect() {
        if (isInitialized) {
            return;
        }
        
        if (!isSophisticatedCoreAvailable()) {
            RarityCore.LOGGER.warn("Attempting to initialize SophisticatedCore compatibility when mod is not loaded");
            return;
        }
        
        try {
            // 使用反射调用精妙核心的API设置装饰渲染器
            Class<?> storageScreenBaseClass = Class.forName("net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase");
            Class<?> slotDecorationRendererClass = Class.forName("net.p3pp3rf1y.sophisticatedcore.client.gui.ISlotDecorationRenderer");
            
            // 创建装饰渲染器实例
            Object decorationRenderer = java.lang.reflect.Proxy.newProxyInstance(
                SophisticatedCoreCompat.class.getClassLoader(),
                new Class[]{slotDecorationRendererClass},
                (proxy, method, args) -> {
                    if ("renderDecoration".equals(method.getName()) && args.length == 2) {
                        GuiGraphicsExtractor guiGraphics = (GuiGraphicsExtractor) args[0];
                        Slot slot = (Slot) args[1];
                        ItemBorderRenderer.renderRarityBorder(guiGraphics, slot.getItem(), slot.x, slot.y);
                    }
                    return null;
                }
            );
            
            // 调用静态方法设置渲染器
            java.lang.reflect.Method setRendererMethod = storageScreenBaseClass.getMethod("setSlotDecorationRenderer", slotDecorationRendererClass);
            setRendererMethod.invoke(null, decorationRenderer);
            
            isInitialized = true;
            RarityCore.LOGGER.info("SophisticatedCore compatibility initialized via reflection");
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize SophisticatedCore compatibility via reflection", e);
        }
    }
    
    /**
     * 渲染槽位边框（供外部调用）
     */
    public static void renderSlotBorder(GuiGraphicsExtractor guiGraphics, Slot slot) {
        if (isInitialized && slot != null && !slot.getItem().isEmpty()) {
            ItemBorderRenderer.renderRarityBorder(guiGraphics, slot.getItem(), slot.x, slot.y);
        }
    }
    
    /**
     * 重置初始化状态（主要用于测试）
     */
    public static void reset() {
        isInitialized = false;
    }
}