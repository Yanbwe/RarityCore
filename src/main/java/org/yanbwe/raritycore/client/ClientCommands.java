package org.yanbwe.raritycore.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.cache.CacheConfig;
import org.yanbwe.raritycore.cache.DualCacheManager;
import org.yanbwe.raritycore.cache.RarityCacheCoordinator;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.util.StarDisplayManager;

/**
 * 客户端命令管理器
 * 负责注册所有客户端相关的子命令
 * 供没有OP权限的普通玩家使用
 */
public class ClientCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore-client")
            // 重载全部客户端配置（客户端专用，不在服务端 reload 中触发）
            .then(Commands.literal("reload")
                .executes(context -> {
                    // 1. 重载 client.json
                    ClientConfigManager.loadClientConfig();
                    // 2. 重载星星显示策略
                    StarDisplayManager.getInstance().reloadConfiguration();
                    // 3. 重载 RarityStyle.json
                    RarityStyleConfigManager.getInstance().reload();
                    // 4. 处理 skipUnconfiguredItems 配置变更
                    ItemBorderRenderer.handleSkipConfigChange();
                    RarityTooltipHandler.handleSkipConfigChange();
                    // 5. 刷新渲染缓存
                    RenderCacheManager.clearAllCache();
                    int levelCount = RarityStyleConfigManager.getInstance().getConfiguredLevels().size();
                    context.getSource().sendSuccess(() -> Component.translatable(
                        "rarity.core.rarity_client_config_reloaded", levelCount)
                        .withStyle(ChatFormatting.GREEN), false);
                    return 1;
                })
            )
            .then(Commands.literal("cache")
                // 显示缓存统计信息
                .then(Commands.literal("stats")
                    .executes(context -> {
                        boolean isCacheEnabled = ClientConfigManager.isEnableCacheSystem();
                        CacheConfig config = DualCacheManager.getConfig();
                        RarityCacheCoordinator.CombinedCacheStatistics stats = RarityCacheCoordinator.getStatistics();
                        
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_stats_header")
                            .withStyle(ChatFormatting.GOLD), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_system_enabled_status", isCacheEnabled ? "启用" : "禁用")
                            .withStyle(isCacheEnabled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
                        
                        // 显示ID缓存统计
                        context.getSource().sendSuccess(() -> Component.literal("ID缓存: " + stats.getIdCacheSize() + " 条目 (命中率: " + String.format("%.1f%%", stats.getIdCacheHitRate()) + ")")
                            .withStyle(ChatFormatting.AQUA), false);
                        
                        // 显示组件缓存统计
                        context.getSource().sendSuccess(() -> Component.literal("组件缓存: " + stats.getComponentCacheSize() + " 条目 (命中率: " + String.format("%.1f%%", stats.getComponentCacheHitRate()) + ")")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                        
                        // 显示总体统计
                        context.getSource().sendSuccess(() -> Component.literal("总体命中率: " + String.format("%.1f%%", stats.getOverallHitRate()))
                            .withStyle(ChatFormatting.YELLOW), false);
                        context.getSource().sendSuccess(() -> Component.literal("总缓存大小: " + stats.getTotalSize() + " 条目")
                            .withStyle(ChatFormatting.YELLOW), false);

                        // 添加缓存配置信息
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_config_header")
                            .withStyle(ChatFormatting.GOLD), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_capacity", config.getActualMaxCacheSize())
                            .withStyle(ChatFormatting.WHITE), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.dynamic_multiplier", config.getDynamicCapacityMultiplier())
                            .withStyle(ChatFormatting.WHITE), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.capacity_range", config.getDynamicCapacityMin(), config.getDynamicCapacityMax())
                            .withStyle(ChatFormatting.WHITE), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.current_item_count", getCurrentItemCount())
                            .withStyle(ChatFormatting.WHITE), false);
                        
                        return 1;
                    })
                )
                
                // 清空缓存
                .then(Commands.literal("clear")
                    .executes(context -> {
                        DualCacheManager.handleConfigReload();
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_cleared")
                            .withStyle(ChatFormatting.GREEN), false);
                        return 1;
                    })
                )
            )
        );
    }
    
    /**
     * 获取当前物品总数
     */
    private static int getCurrentItemCount() {
        try {
            return (int) BuiltInRegistries.ITEM.stream().count();
        } catch (Exception e) {
            return 0;
        }
    }
}
