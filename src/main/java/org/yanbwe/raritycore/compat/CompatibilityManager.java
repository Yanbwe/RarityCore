package org.yanbwe.raritycore.compat;

import org.yanbwe.raritycore.RarityCore;

/**
 * 兼容性管理器
 * 负责检测和初始化各种模组的兼容性适配器
 */
public class CompatibilityManager {
    
    /**
     * 检查指定模组是否已加载
     * @param modId 模组ID
     * @return 如果模组已加载返回true
     */
    public static boolean isModLoaded(String modId) {
        return net.neoforged.fml.ModList.get().isLoaded(modId);
    }
    

    
    /**
     * 初始化所有兼容性适配器
     */
    public static void initializeCompatibilityAdapters() {
        RarityCore.LOGGER.info("Initializing compatibility adapters...");
        
        // 初始化精妙核心适配器（仅客户端，使用反射避免服务端加载客户端类导致崩溃）
        if (isModLoaded("sophisticatedcore")) {
            // 仅在客户端环境加载：SophisticatedCoreAdapter 引用了 net.minecraft.client.gui.GuiGraphicsExtractor，
            // 在专用服务器上直接引用会导致 NoClassDefFoundError
            if (net.neoforged.fml.loading.FMLEnvironment.getDist() != net.neoforged.api.distmarker.Dist.CLIENT) {
                RarityCore.LOGGER.debug("Skipping SophisticatedCore adapter initialization on dedicated server");
            } else {
                try {
                    // 使用反射调用避免编译器生成直接类引用，防止 JVM 在类验证阶段解析 GuiGraphics
                    Class<?> adapterClass = Class.forName("org.yanbwe.raritycore.client.SophisticatedCoreAdapter");
                    java.lang.reflect.Method initMethod = adapterClass.getMethod("init");
                    initMethod.invoke(null);
                    RarityCore.LOGGER.info("SophisticatedCore compatibility adapter initialized");
                } catch (ClassNotFoundException e) {
                    RarityCore.LOGGER.debug("SophisticatedCore adapter class not available, skipping");
                } catch (NoClassDefFoundError e) {
                    RarityCore.LOGGER.debug("SophisticatedCore adapter dependencies not available on server, skipping");
                } catch (Exception e) {
                    RarityCore.LOGGER.error("Failed to initialize SophisticatedCore compatibility adapter", e);
                }
            }
        } else {
            RarityCore.LOGGER.debug("SophisticatedCore not found, skipping compatibility adapter");
        }
        
        // 初始化精致存储适配器
        if (isModLoaded("refinedstorage")) {
            try {
                org.yanbwe.raritycore.compat.refinedstorage.RefinedStorageCompat.initialize();
                RarityCore.LOGGER.info("Refined Storage compatibility adapter initialized");
            } catch (Exception e) {
                RarityCore.LOGGER.error("Failed to initialize Refined Storage compatibility adapter", e);
            }
        } else {
            RarityCore.LOGGER.debug("Refined Storage not found, skipping compatibility adapter");
        }
    
        
        // 在此处添加其他模组的兼容性检测和初始化
        /*
        // 示例:JEI兼容性
        try {
            Class.forName("mezz.jei.api.IModPlugin");
            JEIIntegration.init();
            RarityCore.LOGGER.info("JEI compatibility adapter initialized");
        } catch (ClassNotFoundException e) {
            RarityCore.LOGGER.debug("JEI not found, skipping compatibility adapter");
        }
        */
        
        RarityCore.LOGGER.info("Compatibility adapter initialization completed");
    }
}