package org.yanbwe.raritycore.compat.refinedstorage;

import net.neoforged.fml.ModList;
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
            // 兼容性通过 Mixin 实现，无需在服务端进行运行时类加载验证
        } else {
            RarityCore.LOGGER.debug("Refined Storage not found, skipping compatibility adapter");
        }
    }
}