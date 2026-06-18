package org.yanbwe.raritycore.service;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.calc.AutoRarityConfigManager;
import org.yanbwe.raritycore.client.ItemBorderRenderer;
import org.yanbwe.raritycore.client.RarityTooltipHandler;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.FinalRarityConfigFolderLoader;
import org.yanbwe.raritycore.config.RarityConfigLoader;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.config.TagRarityConfigLoader;
import org.yanbwe.raritycore.itemdatamatching.ItemDataConfigLoader;

import org.yanbwe.raritycore.network.ChangeOperation;
import org.yanbwe.raritycore.network.ItemDataSyncManager;
import org.yanbwe.raritycore.network.SyncBatchManager;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.CacheRefreshCoordinator;
import org.yanbwe.raritycore.util.StarDisplayManager;

import java.util.List;

/**
 * 统一的配置重载服务
 * 提供游戏启动和手动reload指令共用的配置加载逻辑
 */
public class ConfigReloadService {
    
    /**
     * 执行完整的配置重载流程
     * @param source 命令源(可以为null,用于区分是命令调用还是启动加载)
     * @param isStartup 是否为游戏启动时调用
     */
    public static void reloadAllConfigs(CommandSourceStack source, boolean isStartup) {
        try {
            RarityCore.LOGGER.info("Starting {} config reload process", isStartup ? "startup" : "manual");
            
            // 发送进度消息(仅在命令调用时)
            if (source != null) {
                sendProgressMessage(source, Component.translatable("rarity.core.reload_starting_all"));
            }
            
            // 1. 加载服务端配置(包含神化稀有度检测开关)
            if (source != null) {
                sendProgressMessage(source, Component.translatable("rarity.core.loading_server_config"));
            }
            ServerConfigManager.loadServerConfig();
                        
            // 2. 加载物品数据匹配配置(最高优先级)
            if (source != null) {
                sendProgressMessage(source, Component.translatable("rarity.core.loading_item_data_config"));
            }
            ItemDataConfigLoader.loadAllConfigs();
                        
            // 缓存刷新由下方的 handleCacheSystems() 统一处理，避免在此处重复调用
                        
            // 同步物品数据规则到所有客户端
            if (!isStartup) { // 启动时不需要同步,会在玩家登录时处理
                ItemDataSyncManager.syncItemDataRulesToAllPlayers();
            }
                        
            // 3. 加载 FinalRarityConfig文件夹(第三优先级)
            if (source != null) {
                sendProgressMessage(source, Component.translatable("rarity.core.loading_final_rarity_config_folder"));
            }
            FinalRarityConfigFolderLoader.loadFinalRarityConfigFolder();
                        
            // 4. 加载 FinalRarity.json 文件(第三优先级)
            if (source != null) {
                sendProgressMessage(source, Component.translatable("rarity.core.loading_final_rarity_file"));
            }
            RarityConfigLoader.loadConfigRarityData();
                        
            // 5. 加载自动计算的稀有度配置(第四优先级)
            if (source != null) {
                sendProgressMessage(source, Component.translatable("rarity.core.loading_auto_rarity_config"));
            }
            AutoRarityConfigManager.loadAutoRarityConfig();
            
            // 6. 强制处理批处理队列中的操作(关键步骤)
            processPendingBatchOperations(source);
            
            // 7. 同步数据到所有客户端
            if (!isStartup) { // 启动时不需要同步,会在玩家登录时处理
                SyncManager.syncRarityToClientsWithRetry(RarityRegistry.ITEM_RARITY_MAP,
                    RarityRegistry.getAutoRarityMap(),
                    TagRarityConfigLoader.getSyncedRules());
            }
            
            // 8. 加载客户端配置(包含自定义等级文本配置)
            if (source != null) {
                sendProgressMessage(source, Component.translatable("rarity.core.loading_client_config"));
            }
            handleClientSideConfigs();
            
            // 9. 处理双缓存系统重载
            handleCacheSystems();
            
            // 10. 发送完成消息(仅在命令调用时)
            if (source != null) {
                sendCompletionMessage(source);
            }
            
            RarityCore.LOGGER.info("Config reload process completed");
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error occurred during config reload process", e);
            if (source != null) {
                source.sendFailure(Component.translatable("rarity.core.config_reload_failed", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            }
        }
    }
    
    /**
     * 处理批处理队列中的待处理操作
     * 确保稀有度为0的删除操作能够正确应用
     */
    private static void processPendingBatchOperations(CommandSourceStack source) {
        try {
            // 获取并清空待处理的操作
            List<ChangeOperation> pendingOps = SyncBatchManager.getAndClearPendingOperations();
            
            if (!pendingOps.isEmpty()) {
                if (source != null) {
                    sendProgressMessage(source, Component.translatable("rarity.core.processing_batch_operations", pendingOps.size()));
                }
                
                RarityCore.LOGGER.info("Processing {} pending batch operations during reload", pendingOps.size());
                
                // 应用所有待处理操作
                int appliedCount = 0;
                for (ChangeOperation op : pendingOps) {
                    net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(op.getItemId());
                    if (item != null && !op.getItemId().equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                        switch (op.getType()) {
                            case ADD:
                            case UPDATE:
                                RarityRegistry.register(item, op.getRarity(), false);
                                appliedCount++;
                                break;
                            case DELETE:
                                RarityRegistry.unregister(item, false);
                                appliedCount++;
                                break;
                        }
                    }
                }
                
                RarityCore.LOGGER.info("Successfully processed {} batch operations (applied: {})", 
                    pendingOps.size(), appliedCount);
                
                if (source != null) {
                    final int finalAppliedCount = appliedCount;
                    source.sendSuccess(() -> Component.translatable("rarity.core.batch_operations_processed", finalAppliedCount)
                        .withStyle(ChatFormatting.GREEN), false);
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error processing pending batch operations during reload", e);
            if (source != null) {
                source.sendFailure(Component.translatable("rarity.core.batch_operation_error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            }
        }
    }
    
    /**
     * 处理双缓存系统
     */
    private static void handleCacheSystems() {
        try {
            // 协调并执行缓存刷新
            CacheRefreshCoordinator.coordinateRefresh();
            RarityCore.LOGGER.info("Dual cache system reloaded successfully");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error handling cache systems during reload", e);
        }
    }
    
    /**
     * 处理客户端侧配置
     */
    private static void handleClientSideConfigs() {
        try {
            // 重新加载客户端配置
            ClientConfigManager.loadClientConfig();
            
            // 通知星星显示管理器重新加载配置
            StarDisplayManager.getInstance().reloadConfiguration();
            
            // 特别处理skipUnconfiguredItems配置变更
            handleSkipUnconfiguredItemsChange();
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error processing client configuration", e);
        }
    }
    
    /**
     * 处理skipUnconfiguredItems配置变更
     */
    private static void handleSkipUnconfiguredItemsChange() {
        try {
            // 通知边框渲染器重新评估渲染逻辑
            ItemBorderRenderer.handleSkipConfigChange();
            
            // 通知工具提示处理器重新评估插入逻辑
            RarityTooltipHandler.handleSkipConfigChange();
            
            // 使相关缓存失效
            RenderCacheManager.clearAllCache();
            
            RarityCore.LOGGER.info("skipUnconfiguredItems config change processed, related systems refreshed");
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error processing skipUnconfiguredItems config change", e);
        }
    }
    
    /**
     * 发送进度消息
     */
    private static void sendProgressMessage(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message.copy().withStyle(ChatFormatting.YELLOW), false);
    }
    
    /**
     * 发送完成消息
     */
    private static void sendCompletionMessage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("rarity.core.config_reload_complete")
            .withStyle(ChatFormatting.GREEN), false);
    }
    
    /**
     * 游戏启动时调用的简化版本
     */
    public static void reloadOnStartup() {
        reloadAllConfigs(null, true);
    }
    
    /**
     * 命令调用时的完整版本
     */
    public static void reloadFromCommand(CommandSourceStack source) {
        reloadAllConfigs(source, false);
    }
}