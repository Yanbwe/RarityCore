package org.yanbwe.raritycore.compat.sophisticatedcore;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.neoforged.fml.ModList;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.client.ItemBorderRenderer;

/**
 * 精妙核心兼容性适配器
 * 通过精妙核心提供的装饰渲染器接口实现物品边框渲染
 */
public class SophisticatedCoreAdapter {
    
    /**
     * 初始化精妙核心兼容性适配器
     */
    public static void init() {
        // 检查精妙核心是否加载
        if (!ModList.get().isLoaded("sophisticatedcore")) {
            RarityCore.LOGGER.debug("SophisticatedCore mod not detected, skipping initialization");
            return;
        }
        
        // 检查客户端配置项是否启用精妙核心适配器
        if (!org.yanbwe.raritycore.config.ClientConfigManager.isEnableSophisticatedCoreAdapter()) {
            RarityCore.LOGGER.debug("SophisticatedCore adapter is disabled in client config, skipping initialization");
            return;
        }
        
        try {
            // 设置装饰渲染器
            setSlotDecorationRenderer();
            RarityCore.LOGGER.info("SophisticatedCore decoration renderer registered successfully");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to register SophisticatedCore decoration renderer", e);
        }
    }
    
    /**
     * 设置精妙核心的槽位装饰渲染器
     * 利用其提供的ISlotDecorationRenderer接口
     */
    private static void setSlotDecorationRenderer() {
        try {
            // 使用反射设置装饰渲染器，避免编译时依赖
            Class<?> storageScreenBaseClass = Class.forName("net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase");
            Class<?> slotDecorationRendererClass = Class.forName("net.p3pp3rf1y.sophisticatedcore.client.gui.ISlotDecorationRenderer");
            
            // 创建装饰渲染器实例
            Object decorationRenderer = java.lang.reflect.Proxy.newProxyInstance(
                SophisticatedCoreAdapter.class.getClassLoader(),
                new Class[]{slotDecorationRendererClass},
                (proxy, method, args) -> {
                    if ("renderDecoration".equals(method.getName()) && args.length == 2) {
                        GuiGraphics guiGraphics = (GuiGraphics) args[0];
                        Slot slot = (Slot) args[1];
                        // 调用我们的边框渲染逻辑
                        ItemBorderRenderer.renderRarityBorder(guiGraphics, slot.getItem(), slot.x, slot.y);
                    }
                    return null;
                }
            );
            
            // 调用静态方法设置渲染器
            java.lang.reflect.Method setRendererMethod = storageScreenBaseClass.getMethod("setSlotDecorationRenderer", slotDecorationRendererClass);
            setRendererMethod.invoke(null, decorationRenderer);
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to set SophisticatedCore slot decoration renderer via reflection", e);
            throw new RuntimeException("Could not initialize SophisticatedCore compatibility", e);
        }
    }
}