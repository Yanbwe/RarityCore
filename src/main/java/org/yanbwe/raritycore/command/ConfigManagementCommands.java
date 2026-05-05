package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.DualCacheManager;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.client.ItemBorderRenderer;
import org.yanbwe.raritycore.client.RarityTooltipHandler;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.service.ConfigReloadService;
import org.yanbwe.raritycore.util.StarDisplayManager;

/**
 * 配置管理命令类
 * 处理配置文件重载等相关命令
 */
public class ConfigManagementCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .then(Commands.literal("reload")
                .executes(context -> reloadRarityData(context.getSource()))
            )
        );
        
        // 注册客户端命令
        dispatcher.register(Commands.literal("raritycore-client")
            .then(Commands.literal("reload")
                .executes(context -> reloadClientConfig(context.getSource()))
            )
        );
    }
    
    /**
     * 重新加载稀有度数据
     */
    private static int reloadRarityData(CommandSourceStack source) {
        ConfigReloadService.reloadFromCommand(source);
        return 1;
    }
    
    /**
     * 重新加载客户端配置
     */
    private static int reloadClientConfig(CommandSourceStack source) {
        ClientConfigManager.loadClientConfig();
        
        StarDisplayManager.getInstance().reloadConfiguration();
        
        DualCacheManager.handleConfigReload();
        
        handleSkipUnconfiguredItemsChange();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.client_config_reloaded").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 处理skipUnconfiguredItems配置变更
     */
    private static void handleSkipUnconfiguredItemsChange() {
        try {
            ItemBorderRenderer.handleSkipConfigChange();
            RarityTooltipHandler.handleSkipConfigChange();
            RenderCacheManager.clearAllCache();
            RarityCore.LOGGER.info("skipUnconfiguredItems config change handled, related systems refreshed");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling skipUnconfiguredItems config change", e);
        }
    }
    
}
