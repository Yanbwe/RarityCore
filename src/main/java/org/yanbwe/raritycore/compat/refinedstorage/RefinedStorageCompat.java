package org.yanbwe.raritycore.compat.refinedstorage;

import net.minecraftforge.fml.ModList;
import org.yanbwe.raritycore.RarityCore;

/**
 * 精致存储兼容性管理器
 * 通过Mixin Hook实现物品边框渲染兼容性
 */
public class RefinedStorageCompat {
    
    /**
     * 检查精致存储兼容性是否可用
     * @return 如果精致存储已加载返回true
     */
    public static boolean isAvailable() {
        return ModList.get().isLoaded("refinedstorage");
    }
    
    /**
     * 初始化精致存储兼容性适配器
     * 实际的兼容性逻辑通过Mixin实现
     */
    public static void initialize() {
        if (isAvailable()) {
            RarityCore.LOGGER.info("Refined Storage detected, Mixin-based compatibility will be applied");
            try {
                Class.forName("com.refinedmods.refinedstorage.screen.BaseScreen");
                // 成功加载类即可，无需额外日志
            } catch (ClassNotFoundException e) {
                RarityCore.LOGGER.debug("Refined Storage BaseScreen class not found");
            } catch (Exception e) {
                RarityCore.LOGGER.trace("Error checking BaseScreen class", e);
            }
        } else {
            RarityCore.LOGGER.debug("Refined Storage not found, skipping compatibility adapter");
        }
    }
}