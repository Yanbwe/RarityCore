package org.yanbwe.raritycore.compat;

import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter;
import org.yanbwe.raritycore.compat.refinedstorage.RefinedStorageCompat;
import org.yanbwe.raritycore.compat.ironsspells.IronSpellsAdapter;

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
        
        // 初始化神化模组适配器
        try {
            Class.forName("dev.shadowsoffire.apotheosis.adventure.loot.LootRarity");
            ApotheosisAdapter.init();
            RarityCore.LOGGER.info("Apotheosis compatibility adapter initialized");
        } catch (ClassNotFoundException e) {
            RarityCore.LOGGER.debug("Apotheosis not found, skipping compatibility adapter");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize Apotheosis compatibility adapter", e);
        }
        
        // 初始化精致存储适配器
        if (isModLoaded("refinedstorage")) {
            try {
                RefinedStorageCompat.initialize();
                RarityCore.LOGGER.info("Refined Storage compatibility adapter initialized");
            } catch (Exception e) {
                RarityCore.LOGGER.error("Failed to initialize Refined Storage compatibility adapter", e);
            }
        } else {
            RarityCore.LOGGER.debug("Refined Storage not found, skipping compatibility adapter");
        }
        
        // 初始化 Iron's Spells 适配器
        try {
            Class.forName("io.redspace.ironsspellbooks.IronsSpellbooks");
            IronSpellsAdapter.init();
            RarityCore.LOGGER.info("Iron's Spells compatibility adapter initialized");
        } catch (ClassNotFoundException e) {
            RarityCore.LOGGER.debug("Iron's Spells not found, skipping compatibility adapter");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize Iron's Spells compatibility adapter", e);
        }
        
        // TODO: 在此处添加其他模组的兼容性检测和初始化
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