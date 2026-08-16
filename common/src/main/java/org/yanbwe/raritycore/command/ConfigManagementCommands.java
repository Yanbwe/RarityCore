package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.ConfigValidator;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.config.ServerConfigManager;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置管理命令类
 * 处理配置文件重载、版本管理等相关命令
 */
public class ConfigManagementCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .then(Commands.literal("reload")
                .executes(context -> reloadRarityData(context.getSource()))
            )

            .then(Commands.literal("config")
                .then(Commands.literal("version")
                    .executes(context -> showConfigVersionInfo(context.getSource()))
                )
                .then(Commands.literal("upgrade")
                    .executes(context -> forceConfigUpgrade(context.getSource()))
                )
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
        // 使用统一的配置重载服务
        org.yanbwe.raritycore.service.ConfigReloadService.reloadFromCommand(source);
        return 1;
    }
    
    /**
     * 重新加载客户端配置
     */
    private static int reloadClientConfig(CommandSourceStack source) {
        ClientConfigManager.loadClientConfig();

        // 热重载 V14 样式配置（RarityStyle.json）
        RarityStyleConfigManager.reload();
        
        // 通知星星显示管理器重新加载配置
        org.yanbwe.raritycore.util.StarDisplayManager.getInstance().reloadConfiguration();
        
        // 处理客户端配置变更对缓存的影响
        org.yanbwe.raritycore.cache.DualCacheManager.handleConfigReload();
        
        // 特别处理skipUnconfiguredItems配置变更 - 通知相关渲染系统
        handleSkipUnconfiguredItemsChange();
        
        // 简单的重载完成提示
        source.sendSuccess(() -> Component.translatable("rarity.core.client_config_reloaded").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 处理skipUnconfiguredItems配置变更
     * 当此配置改变时,需要通知相关渲染系统刷新状态
     */
    private static void handleSkipUnconfiguredItemsChange() {
        try {
            // 通知边框渲染器重新评估渲染逻辑
            org.yanbwe.raritycore.client.ItemBorderRenderer.handleSkipConfigChange();
            
            // 通知工具提示处理器重新评估插入逻辑
            org.yanbwe.raritycore.client.RarityTooltipHandler.handleSkipConfigChange();
            
            // 使相关缓存失效
            org.yanbwe.raritycore.cache.RenderCacheManager.clearAllCache();
            
            RarityCore.LOGGER.info("skipUnconfiguredItems config change handled, related systems refreshed");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling skipUnconfiguredItems config change", e);
        }
    }
    
    /**
     * 显示配置版本信息
     */
    private static int showConfigVersionInfo(CommandSourceStack source) {
        try {
            source.sendSuccess(() -> Component.translatable("rarity.core.config_version_info_title").withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.translatable("rarity.core.config_version_deprecated").withStyle(ChatFormatting.YELLOW), false);
            
            // 显示客户端配置状态
            Path clientConfigPath = ClientConfigManager.getClientConfigPath();
            if (Files.exists(clientConfigPath)) {
                source.sendSuccess(() -> Component.translatable("rarity.core.client_config_exists").withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendSuccess(() -> Component.translatable("rarity.core.client_config_not_found").withStyle(ChatFormatting.RED), false);
            }
            
            // 显示服务端配置状态
            Path serverConfigPath = ServerConfigManager.getServerConfigPath();
            if (Files.exists(serverConfigPath)) {
                source.sendSuccess(() -> Component.translatable("rarity.core.server_config_exists").withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendSuccess(() -> Component.translatable("rarity.core.server_config_not_found").withStyle(ChatFormatting.RED), false);
            }
            
            return 1;
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to show config version info", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.config_version_info_failed", e.getMessage())
                .withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
    
    /**
     * 强制配置验证
     */
    private static int forceConfigUpgrade(CommandSourceStack source) {
        try {
            source.sendSuccess(() -> Component.translatable("rarity.core.forcing_config_validation").withStyle(ChatFormatting.YELLOW), false);
            
            // 强制验证客户端配置
            com.google.gson.JsonObject defaultClientConfig = ConfigValidator.createDefaultClientConfig();
            ConfigValidator.validateConfig(ClientConfigManager.getClientConfigPath(), defaultClientConfig, "client (forced)");
            
            // 强制验证服务端配置
            com.google.gson.JsonObject defaultServerConfig = ConfigValidator.createDefaultServerConfig();
            ConfigValidator.validateConfig(ServerConfigManager.getServerConfigPath(), defaultServerConfig, "server (forced)");
            
            // 强制升级后重载样式配置
            RarityStyleConfigManager.reload();

            source.sendSuccess(() -> Component.translatable("rarity.core.config_validation_completed")
                .withStyle(ChatFormatting.GREEN), false);
            
            return 1;
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to force config validation", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.config_validation_failed", e.getMessage())
                .withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
    

}
