package org.yanbwe.raritycore.compat.ftblibrary;

import net.minecraftforge.fml.ModList;
import org.yanbwe.raritycore.RarityCore;

/**
 * FTB Library 兼容性管理器
 * 通过Mixin Hook实现物品边框渲染兼容性（覆盖 FTB Quests 等 FTB 系列界面）
 */
public class FtbLibraryCompat {

    /**
     * 检查FTB Library兼容性是否可用
     * @return 如果FTB Library已加载返回true
     */
    public static boolean isAvailable() {
        return ModList.get().isLoaded("ftblibrary");
    }

    /**
     * 初始化FTB Library兼容性适配器
     * 实际的兼容性逻辑通过Mixin实现
     */
    public static void initialize() {
        if (isAvailable()) {
            RarityCore.LOGGER.info("FTB Library detected, Mixin-based compatibility will be applied");
        } else {
            RarityCore.LOGGER.debug("FTB Library not found, skipping compatibility adapter");
        }
    }
}
