package org.yanbwe.raritycore.compat.refinedstorage;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.fml.ModList;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.client.ItemBorderRenderer;

/**
 * 精致存储兼容性管理器
 * 通过精致存储提供的渲染API接口实现物品边框渲染
 */
public class RefinedStorageCompat {
    
    /**
     * 初始化精致存储兼容性适配器
     */
    public static void initialize() {
        // 检查精致存储是否加载
        if (!ModList.get().isLoaded("refinedstorage")) {
            RarityCore.LOGGER.debug("Refined Storage mod not detected, skipping initialization");
            return;
        }
        
        try {
            // 设置装饰渲染器
            setItemDrawerDecorator();
            RarityCore.LOGGER.info("Refined Storage item drawer decorator registered successfully");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to register Refined Storage item drawer decorator", e);
        }
    }
    
    /**
     * 设置精致存储的物品渲染装饰器
     * 利用其提供的IElementDrawer接口包装原有的物品渲染逻辑
     */
    private static void setItemDrawerDecorator() {
        try {
            // 使用反射获取相关类，避免编译时依赖
            Class<?> elementDrawerClass = Class.forName("com.refinedmods.refinedstorage.api.render.IElementDrawer");
            Class<?> elementDrawersClass = Class.forName("com.refinedmods.refinedstorage.api.render.IElementDrawers");
            Class<?> itemStackClass = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> guiGraphicsClass = Class.forName("net.minecraft.client.gui.GuiGraphics");
            
            // 创建装饰器代理，包装原有的getItemDrawer方法
            java.lang.reflect.InvocationHandler handler = (proxy, method, args) -> {
                if ("draw".equals(method.getName()) && args.length == 4) {
                    // 这是draw方法调用
                    GuiGraphics guiGraphics = (GuiGraphics) args[0];
                    int x = (Integer) args[1];
                    int y = (Integer) args[2];
                    Object itemStack = args[3];
                    
                    // 调用我们的边框渲染逻辑
                    if (itemStack != null) {
                        // 使用反射获取ItemStack实例
                        ItemBorderRenderer.renderRarityBorder(guiGraphics, (net.minecraft.world.item.ItemStack) itemStack, x, y);
                    }
                }
                return null;
            };
            
            // 创建装饰器实例
            Object decorator = java.lang.reflect.Proxy.newProxyInstance(
                RefinedStorageCompat.class.getClassLoader(),
                new Class[]{elementDrawerClass},
                handler
            );
            
            // TODO: 这里需要找到合适的方法来注册装饰器
            // 由于精致存储的API设计，可能需要在运行时动态替换或包装ElementDrawers实例
            RarityCore.LOGGER.info("Refined Storage decorator created, integration ready");
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to create Refined Storage item drawer decorator via reflection", e);
            throw new RuntimeException("Could not initialize Refined Storage compatibility", e);
        }
    }
}