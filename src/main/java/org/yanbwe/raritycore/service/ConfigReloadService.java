package org.yanbwe.raritycore.service;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.config.FinalRarityConfigFolderLoader;
import org.yanbwe.raritycore.config.RarityConfigLoader;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.nbtmatching.NbtConfigLoader;
import org.yanbwe.raritycore.nbtmatching.SimpleNbtCache;
import org.yanbwe.raritycore.network.ChangeOperation;
import org.yanbwe.raritycore.network.NbtSyncManager;
import org.yanbwe.raritycore.network.SyncBatchManager;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.StarDisplayManager;

import java.util.List;

/**
 * 统一的配置重载服务
 * 提供游戏启动和手动reload指令共用的配置加载逻辑
 */
public class ConfigReloadService {
    
    /**
     * 执行完整的配置重载流程
     * @param source 命令源（可以为null，用于区分是命令调用还是启动加载）
     * @param isStartup 是否为游戏启动时调用
     */
    public static void reloadAllConfigs(CommandSourceStack source, boolean isStartup) {
        try {
            RarityCore.LOGGER.info("开始{}配置重载流程", isStartup ? "游戏启动时" : "手动");
            
            // 发送进度消息（仅在命令调用时）
            if (source != null) {
                sendProgressMessage(source, "开始重载所有配置...");
            }
            
            // 1. 加载服务端配置
            if (source != null) {
                sendProgressMessage(source, "加载服务端配置...");
            }
            ServerConfigManager.loadServerConfig();
            
            // 2. 加载NBT匹配配置（这是之前缺失的部分）
            if (source != null) {
                sendProgressMessage(source, "加载NBT匹配配置...");
            }
            NbtConfigLoader.loadAllConfigs();
            
            // 重新初始化NBT缓存
            SimpleNbtCache.reinitializeCache();
            
            // 同步NBT规则到所有客户端
            if (!isStartup) { // 启动时不需要同步，会在玩家登录时处理
                NbtSyncManager.syncNbtRulesToAllPlayers();
            }
            
            // 3. 加载FinalRarityConfig文件夹
            if (source != null) {
                sendProgressMessage(source, "加载FinalRarityConfig文件夹...");
            }
            FinalRarityConfigFolderLoader.loadFinalRarityConfigFolder();
            
            // 4. 加载FinalRarity.json文件
            if (source != null) {
                sendProgressMessage(source, "加载FinalRarity.json文件...");
            }
            RarityConfigLoader.loadConfigRarityData();
            
            // 5. 强制处理批处理队列中的操作（关键步骤）
            processPendingBatchOperations(source);
            
            // 6. 同步数据到所有客户端
            if (!isStartup) { // 启动时不需要同步，会在玩家登录时处理
                RarityRegistry.syncRarityToClientsWithRetry();
            }
            
            // 7. 处理客户端相关配置（仅在命令调用时）
            if (source != null) {
                handleClientSideConfigs();
                sendCompletionMessage(source);
            }
            
            RarityCore.LOGGER.info("配置重载流程完成");
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("配置重载过程中发生错误", e);
            if (source != null) {
                source.sendFailure(Component.literal("配置重载失败: " + e.getMessage())
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
                    sendProgressMessage(source, String.format("处理%d个批处理操作...", pendingOps.size()));
                }
                
                RarityCore.LOGGER.info("Processing {} pending batch operations during reload", pendingOps.size());
                
                // 应用所有待处理操作
                int appliedCount = 0;
                for (ChangeOperation op : pendingOps) {
                    net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(op.getItemId());
                    if (item != null && !op.getItemId().equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getDefaultKey())) {
                        switch (op.getType()) {
                            case ADD:
                            case UPDATE:
                                RarityRegistry.register(item, op.getRarity(), false);
                                appliedCount++;
                                RarityCore.LOGGER.debug("Applied batch ADD/UPDATE operation for item: {} -> rarity {}", 
                                    op.getItemId(), op.getRarity());
                                break;
                            case DELETE:
                                RarityRegistry.unregister(item, false);
                                appliedCount++;
                                RarityCore.LOGGER.debug("Applied batch DELETE operation for item: {}", op.getItemId());
                                break;
                        }
                    }
                }
                
                RarityCore.LOGGER.info("Successfully processed {} batch operations (applied: {})", 
                    pendingOps.size(), appliedCount);
                
                if (source != null) {
                    final int finalAppliedCount = appliedCount;
                    source.sendSuccess(() -> Component.literal(String.format("已处理%d个批处理操作", finalAppliedCount))
                        .withStyle(ChatFormatting.GREEN), false);
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error processing pending batch operations during reload", e);
            if (source != null) {
                source.sendFailure(Component.literal("处理批处理操作时出错: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            }
        }
    }
    
    /**
     * 处理客户端侧配置
     */
    private static void handleClientSideConfigs() {
        try {
            // 重新加载客户端配置
            ConfigManager.loadClientConfig();
            
            // 通知星星显示管理器重新加载配置
            StarDisplayManager.getInstance().reloadConfiguration();
            
            // 处理客户端配置变更对缓存的影响
            org.yanbwe.raritycore.client.ImprovedRenderCacheManager.handleClientConfigChange();
            
            // 特别处理skipUnconfiguredItems配置变更
            handleSkipUnconfiguredItemsChange();
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("处理客户端配置时出错", e);
        }
    }
    
    /**
     * 处理skipUnconfiguredItems配置变更
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
     * 发送进度消息
     */
    private static void sendProgressMessage(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.YELLOW), false);
    }
    
    /**
     * 发送完成消息
     */
    private static void sendCompletionMessage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("✅ 配置重载完成!")
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