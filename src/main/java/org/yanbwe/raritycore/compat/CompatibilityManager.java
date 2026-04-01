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
        
        // 初始化精妙核心适配器
        try {
            Class.forName("net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase");
            org.yanbwe.raritycore.compat.sophisticatedcore.SophisticatedCoreAdapter.init();
            RarityCore.LOGGER.info("SophisticatedCore compatibility adapter initialized");
        } catch (ClassNotFoundException e) {
            RarityCore.LOGGER.debug("SophisticatedCore not found, skipping compatibility adapter");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize SophisticatedCore compatibility adapter", e);
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
        
        // 初始化精致存储适配器
        try {
            Class.forName("com.refinedmods.refinedstorage.screen.BaseScreen");
            org.yanbwe.raritycore.compat.refinedstorage.RefinedStorageCompat.initialize();
            RarityCore.LOGGER.info("Refined Storage compatibility adapter initialized");
        } catch (ClassNotFoundException e) {
            RarityCore.LOGGER.debug("Refined Storage not found, skipping compatibility adapter");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize Refined Storage compatibility adapter", e);
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