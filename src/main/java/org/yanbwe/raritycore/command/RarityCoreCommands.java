package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.yanbwe.raritycore.client.ClientCommands;

/**
 * 主命令类 - 整合所有子命令模块
 * 负责注册和协调各个功能模块的命令
 */
public class RarityCoreCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 注册服务端命令(需要OP权限)
        RarityManagementCommands.register(dispatcher);
        ConfigManagementCommands.register(dispatcher);
        ExportManagementCommands.register(dispatcher);
        UtilityCommands.register(dispatcher);
        
        // 注册客户端命令(无需OP权限)
        ClientCommands.register(dispatcher);
    }
    
    /**
     * 公共方法:保存稀有度到配置文件
     * 供其他类调用的转发方法
     * @param itemId 物品ID字符串
     * @param rarity 稀有度等级
     */
    public static void saveRarityToConfigPublic(String itemId, int rarity) {
        RarityManagementCommands.saveRarityToConfig(itemId, rarity);
    }
}