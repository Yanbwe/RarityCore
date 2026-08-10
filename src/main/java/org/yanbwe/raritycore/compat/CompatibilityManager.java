package org.yanbwe.raritycore.compat;

import org.yanbwe.raritycore.RarityCore;

/**
 * 兼容性管理器
 * 负责检测和初始化各种模组的兼容性适配器
 */
public class CompatibilityManager {
    
    // ColorTooltips模组检测状态
    private static boolean isColorTooltipsLoaded = false;
    
    /**
     * 检查指定模组是否已加载
     * @param modId 模组ID
     * @return 如果模组已加载返回true
     */
    public static boolean isModLoaded(String modId) {
        return net.minecraftforge.fml.ModList.get().isLoaded(modId);
    }
    
    /**
     * 检查ColorTooltips模组是否已加载
     * 实时检测模组加载状态，支持不同大小写形式
     * @return 如果ColorTooltips模组已加载返回true
     */
    public static boolean isColorTooltipsLoaded() {
        return isModLoaded("colortooltips") || isModLoaded("ColorTooltips");
    }
    

    
    /**
     * 初始化所有兼容性适配器
     */
    public static void initializeCompatibilityAdapters() {
        RarityCore.LOGGER.info("Initializing compatibility adapters...");
        
        // 检测ColorTooltips模组
        isColorTooltipsLoaded = isColorTooltipsLoaded();
        if (isColorTooltipsLoaded) {
            RarityCore.LOGGER.info("ColorTooltips mod detected (ID: colortooltips or ColorTooltips), tooltip insertion will be disabled");
        }
        
        // 初始化精妙核心适配器（仅客户端，使用反射避免服务端加载客户端类导致崩溃）
        if (isModLoaded("sophisticatedcore")) {
            // 仅在客户端环境加载：SophisticatedCoreAdapter 引用了 net.minecraft.client.gui.GuiGraphics，
            // 在专用服务器上直接引用会导致 NoClassDefFoundError
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist != net.minecraftforge.api.distmarker.Dist.CLIENT) {
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
        
        // 初始化神化模组适配器
        try {
            Class.forName("dev.shadowsoffire.apotheosis.adventure.loot.LootRarity");
            org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter.init();
            RarityCore.LOGGER.info("Apotheosis compatibility adapter initialized");
        } catch (ClassNotFoundException e) {
            RarityCore.LOGGER.debug("Apotheosis not found, skipping compatibility adapter");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize Apotheosis compatibility adapter", e);
        }
        
        // 初始化 Iron's Spellbooks 适配器（可在 client.json 中通过 enableIronSpellsAdapter 禁用）
        if (org.yanbwe.raritycore.config.ClientConfigManager.isEnableIronSpellsAdapter()) {
            try {
                Class.forName("io.redspace.ironsspellbooks.IronsSpellbooks");
                org.yanbwe.raritycore.compat.ironsspellbooks.IronSpellbooksAdapter.init();
                RarityCore.LOGGER.info("Iron's Spellbooks compatibility adapter initialized");
            } catch (ClassNotFoundException e) {
                RarityCore.LOGGER.debug("Iron's Spellbooks not found, skipping compatibility adapter");
            } catch (Exception e) {
                RarityCore.LOGGER.error("Failed to initialize Iron's Spellbooks compatibility adapter", e);
            }
        } else {
            RarityCore.LOGGER.info("Iron's Spellbooks compatibility adapter disabled by client config");
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
        
        // 初始化 TacZ 适配器
        try {
            org.yanbwe.raritycore.compat.tacz.TacZAdapter.init();
            RarityCore.LOGGER.info("TacZ compatibility adapter check completed");
        } catch (Exception e) {
            RarityCore.LOGGER.debug("TacZ check failed, skipping compatibility adapter");
        }
        
        // TODO: 在此处添加其他模组的兼容性检测和初始化
        /*
        // 示例：JEI兼容性
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