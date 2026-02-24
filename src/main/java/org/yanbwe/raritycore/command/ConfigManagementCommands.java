package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.config.ConfigVersionManager;
import org.yanbwe.raritycore.config.FinalRarityConfigFolderLoader;
import org.yanbwe.raritycore.config.RarityConfigLoader;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.nbtmatching.NbtConfigLoader;
import org.yanbwe.raritycore.nbtmatching.SimpleNbtCache;
import org.yanbwe.raritycore.network.NbtSyncManager;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;

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
        // 重新加载所有配置文件
        
        // 1. 重新加载NBT匹配配置
        source.sendSuccess(() -> Component.translatable("rarity.core.loading_nbt_config").withStyle(ChatFormatting.YELLOW), false);
        NbtConfigLoader.loadAllConfigs();
        
        // 重新初始化NBT缓存
        SimpleNbtCache.reinitializeCache();
        
        // 同步NBT规则到所有客户端
        NbtSyncManager.syncNbtRulesToAllPlayers();
        
        // 2. FinalRarityConfig文件夹
        source.sendSuccess(() -> Component.translatable("rarity.core.loading_final_rarity_config_folder").withStyle(ChatFormatting.YELLOW), false);
        FinalRarityConfigFolderLoader.loadFinalRarityConfigFolder();
        
        // 3. FinalRarity.json文件
        source.sendSuccess(() -> Component.translatable("rarity.core.loading_final_rarity_file").withStyle(ChatFormatting.YELLOW), false);
        RarityConfigLoader.loadConfigRarityData();
        
        // 同步数据到所有客户端
        RarityRegistry.syncRarityToClientsWithRetry();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.reload_success").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 重新加载客户端配置
     */
    private static int reloadClientConfig(CommandSourceStack source) {
        ConfigManager.loadClientConfig();
        
        // 通知星星显示管理器重新加载配置
        org.yanbwe.raritycore.util.StarDisplayManager.getInstance().reloadConfiguration();
        
        // 处理客户端配置变更对缓存的影响
        org.yanbwe.raritycore.client.ImprovedRenderCacheManager.handleClientConfigChange();
        
        // 特别处理skipUnconfiguredItems配置变更 - 通知相关渲染系统
        handleSkipUnconfiguredItemsChange();
        
        // 简单的重载完成提示
        source.sendSuccess(() -> Component.translatable("rarity.core.client_config_reloaded").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 处理skipUnconfiguredItems配置变更
     * 当此配置改变时，需要通知相关渲染系统刷新状态
     */
    private static void handleSkipUnconfiguredItemsChange() {
        try {
            // 通知边框渲染器重新评估渲染逻辑
            org.yanbwe.raritycore.client.ItemBorderRenderer.handleSkipConfigChange();
            
            // 通知工具提示处理器重新评估插入逻辑
            org.yanbwe.raritycore.client.RarityTooltipHandler.handleSkipConfigChange();
            
            // 使相关缓存失效
            org.yanbwe.raritycore.client.RenderCacheManager.clearAllCache();
            
            RarityCore.LOGGER.info("skipUnconfiguredItems配置变更已处理，相关系统已刷新");
        } catch (Exception e) {
            RarityCore.LOGGER.error("处理skipUnconfiguredItems配置变更时出错", e);
        }
    }
    
    /**
     * 显示配置版本信息
     */
    private static int showConfigVersionInfo(CommandSourceStack source) {
        try {
            source.sendSuccess(() -> Component.translatable("rarity.core.config_version_info_title").withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.translatable("rarity.core.current_config_version", 
                ConfigVersionManager.CURRENT_CONFIG_VERSION)
                .withStyle(ChatFormatting.YELLOW), false);
            
            // 显示客户端配置版本
            Path clientConfigPath = ConfigManager.getClientConfigPath();
            if (Files.exists(clientConfigPath)) {
                try (BufferedReader reader = Files.newBufferedReader(clientConfigPath)) {
                    com.google.gson.JsonObject clientConfig = new com.google.gson.Gson().fromJson(reader, com.google.gson.JsonObject.class);
                    final int clientVersion;
                    final String clientModVersion;
                    if (clientConfig != null) {
                        clientVersion = clientConfig.has("config_version") ? clientConfig.get("config_version").getAsInt() : 0;
                        clientModVersion = clientConfig.has("mod_version") ? clientConfig.get("mod_version").getAsString() : "unknown";
                    } else {
                        clientVersion = 0;
                        clientModVersion = "unknown";
                    }
                    source.sendSuccess(() -> Component.translatable("rarity.core.client_config_version", 
                        clientVersion, clientModVersion).withStyle(ChatFormatting.GREEN), false);
                }
            } else {
                source.sendSuccess(() -> Component.translatable("rarity.core.client_config_not_found").withStyle(ChatFormatting.RED), false);
            }
            
            // 显示服务端配置版本
            Path serverConfigPath = ServerConfigManager.getServerConfigPath();
            if (Files.exists(serverConfigPath)) {
                try (BufferedReader reader = Files.newBufferedReader(serverConfigPath)) {
                    com.google.gson.JsonObject serverConfig = new com.google.gson.Gson().fromJson(reader, com.google.gson.JsonObject.class);
                    final int serverVersion;
                    final String serverModVersion;
                    if (serverConfig != null) {
                        serverVersion = serverConfig.has("config_version") ? serverConfig.get("config_version").getAsInt() : 0;
                        serverModVersion = serverConfig.has("mod_version") ? serverConfig.get("mod_version").getAsString() : "unknown";
                    } else {
                        serverVersion = 0;
                        serverModVersion = "unknown";
                    }
                    source.sendSuccess(() -> Component.translatable("rarity.core.server_config_version", 
                        serverVersion, serverModVersion).withStyle(ChatFormatting.GREEN), false);
                }
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
     * 强制配置升级
     */
    private static int forceConfigUpgrade(CommandSourceStack source) {
        try {
            source.sendSuccess(() -> Component.translatable("rarity.core.forcing_config_upgrade").withStyle(ChatFormatting.YELLOW), false);
            
            // 强制升级客户端配置
            int clientResult = ConfigVersionManager.checkAndUpgradeConfig(
                ConfigManager.getClientConfigPath(), "client (forced)");
            
            // 强制升级服务端配置
            int serverResult = ConfigVersionManager.checkAndUpgradeConfig(
                ServerConfigManager.getServerConfigPath(), "server (forced)");
            
            source.sendSuccess(() -> Component.translatable("rarity.core.config_upgrade_completed", 
                clientResult, serverResult, ConfigVersionManager.CURRENT_CONFIG_VERSION)
                .withStyle(ChatFormatting.GREEN), false);
            
            return 1;
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to force config upgrade", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.config_upgrade_failed", e.getMessage())
                .withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
}