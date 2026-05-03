package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * 配置管理命令类
 * 处理配置文件重载、版本管理等相关命令
 */
public class ConfigManagementCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("reload")
                .executes(context -> reloadRarityData(context.getSource()))
            )
        );
    }
    
    /**
     * 重新加载稀有度数据
     */
    private static int reloadRarityData(CommandSourceStack source) {
        // 使用统一的配置重载服务
        org.yanbwe.raritycore.service.ConfigReloadService.reloadFromCommand(source);
        return 1;
    }

}