package org.yanbwe.raritycore.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * 缓存管理命令（已废弃）
 * 所有缓存功能现已迁移至ClientCommands类
 * 此类仅作兼容性保留
 */
public class CacheManagementCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 缓存命令已迁移至ClientCommands类
        // 此处留空以保持API兼容性
    }
}