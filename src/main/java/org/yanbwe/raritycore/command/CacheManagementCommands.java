package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ConfigManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 缓存管理命令类
 * 处理渲染缓存系统的管理、统计和优化相关命令
 */
public class CacheManagementCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore-client")
            .then(Commands.literal("cache")
                .then(Commands.literal("stats")
                    .executes(context -> showCacheStats(context.getSource()))
                )
                .then(Commands.literal("clear")
                    .executes(context -> clearCache(context.getSource()))
                )
                .then(Commands.literal("health")
                    .executes(context -> showCacheHealth(context.getSource()))
                )
                .then(Commands.literal("smart-optimize")
                    .executes(context -> triggerSmartOptimization(context.getSource()))
                )
                .then(Commands.literal("system")
                    .then(Commands.literal("enable")
                        .executes(context -> enableCacheSystem(context.getSource()))
                    )
                    .then(Commands.literal("disable")
                        .executes(context -> disableCacheSystem(context.getSource()))
                    )
                    .then(Commands.literal("toggle")
                        .executes(context -> toggleCacheSystem(context.getSource()))
                    )
                    .then(Commands.literal("status")
                        .executes(context -> showCacheSystemStatus(context.getSource()))
                    )
                )
                .then(Commands.literal("counters")
                    .executes(context -> showCacheCounters(context.getSource()))
                )
            )
        );
    }
    
    /**
     * 显示缓存统计信息
     */
    private static int showCacheStats(CommandSourceStack source) {
        org.yanbwe.raritycore.client.RenderCacheManager.CacheStats stats = 
            org.yanbwe.raritycore.client.RenderCacheManager.getCacheStats();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_stats_title").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_hits", stats.getHits()).withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_misses", stats.getMisses()).withStyle(ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_hit_rate", stats.getHitRate()).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.rarity_cache_size", stats.getRarityCacheSize()).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.itemstack_cache_size", stats.getItemStackCacheSize()).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_clears", stats.getClears()).withStyle(ChatFormatting.GRAY), false);
        
        return 1;
    }
    
    /**
     * 清除渲染缓存
     */
    private static int clearCache(CommandSourceStack source) {
        org.yanbwe.raritycore.client.RenderCacheManager.clearAllCache();
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_cleared").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 显示缓存健康状态
     */
    private static int showCacheHealth(CommandSourceStack source) {
        try {
            org.yanbwe.raritycore.client.ImprovedRenderCacheManager.CacheHealthReport healthReport = 
                org.yanbwe.raritycore.client.ImprovedRenderCacheManager.performHealthCheck();
            
            source.sendSuccess(() -> Component.translatable("rarity.core.cache_health_title").withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.translatable("rarity.core.health_status", 
                healthReport.isHealthy() ? 
                    Component.translatable("rarity.core.health_healthy") : 
                    Component.translatable("rarity.core.health_problem"))
                .withStyle(healthReport.isHealthy() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            source.sendSuccess(() -> Component.translatable("rarity.core.total_entries", healthReport.getTotalEntries())
                .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.translatable("rarity.core.average_hit_rate", healthReport.getAverageHitRate())
                .withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.translatable("rarity.core.cleanup_count", healthReport.getCleanupCount())
                .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.translatable("rarity.core.time_since_last_cleanup", 
                healthReport.getTimeSinceLastCleanup() / 1000).withStyle(ChatFormatting.WHITE), false);
                
            // 内存统计
            org.yanbwe.raritycore.client.ImprovedRenderCacheManager.MemoryEstimationStats memStats = 
                healthReport.getMemoryStats();
            source.sendSuccess(() -> Component.translatable("rarity.core.average_entry_size", memStats.getAverageEntrySize())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to get cache health status", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.health_check_failed", e.getMessage())
                .withStyle(ChatFormatting.RED), false);
        }
        
        return 1;
    }
    
    /**
     * 触发智能缓存优化
     */
    private static int triggerSmartOptimization(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("rarity.core.smart_optimization_start").withStyle(ChatFormatting.YELLOW), false);
        
        CompletableFuture.runAsync(() -> {
            try {
                // 执行智能清理
                org.yanbwe.raritycore.client.ImprovedRenderCacheManager.smartCleanup();
                
                // 执行智能预加载
                org.yanbwe.raritycore.client.ImprovedRenderCacheManager.smartPreloadCache();
                
                // 获取统计信息
                org.yanbwe.raritycore.client.RenderCacheManager.CacheStats stats = 
                    org.yanbwe.raritycore.client.RenderCacheManager.getCacheStats();
                
                source.sendSuccess(() -> Component.translatable("rarity.core.smart_optimization_complete", 
                    stats.getHitRate(), stats.getRarityCacheSize(), stats.getItemStackCacheSize())
                    .withStyle(ChatFormatting.GREEN), false);
                    
            } catch (Exception e) {
                RarityCore.LOGGER.error("Smart optimization execution failed", e);
                source.sendSuccess(() -> Component.translatable("rarity.core.smart_optimization_failed", e.getMessage())
                    .withStyle(ChatFormatting.RED), false);
            }
        }, CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS));
        
        return 1;
    }
    
    /**
     * 启用缓存系统
     */
    private static int enableCacheSystem(CommandSourceStack source) {
        ConfigManager.setEnableCacheSystem(true);
        org.yanbwe.raritycore.client.ImprovedRenderCacheManager.setCacheSystemEnabled(true);
        
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_system_enabled").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 禁用缓存系统
     */
    private static int disableCacheSystem(CommandSourceStack source) {
        ConfigManager.setEnableCacheSystem(false);
        org.yanbwe.raritycore.client.ImprovedRenderCacheManager.setCacheSystemEnabled(false);
        
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_system_disabled").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
    
    /**
     * 切换缓存系统启用状态
     */
    private static int toggleCacheSystem(CommandSourceStack source) {
        boolean currentState = ConfigManager.isEnableCacheSystem();
        boolean newState = !currentState;
        
        ConfigManager.setEnableCacheSystem(newState);
        org.yanbwe.raritycore.client.ImprovedRenderCacheManager.setCacheSystemEnabled(newState);
        
        String status = newState ? Component.translatable("rarity.core.enabled").getString() : 
                                   Component.translatable("rarity.core.disabled").getString();
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_system_toggled", status)
            .withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        return 1;
    }
    
    /**
     * 显示缓存系统状态
     */
    private static int showCacheSystemStatus(CommandSourceStack source) {
        boolean isEnabled = ConfigManager.isEnableCacheSystem();
        boolean isManagerEnabled = org.yanbwe.raritycore.client.ImprovedRenderCacheManager.isCacheSystemEnabled();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_system_status_title").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_system_config_status", 
            isEnabled ? Component.translatable("rarity.core.enabled").withStyle(ChatFormatting.GREEN) : 
                       Component.translatable("rarity.core.disabled").withStyle(ChatFormatting.RED)), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_system_manager_status", 
            isManagerEnabled ? Component.translatable("rarity.core.enabled").withStyle(ChatFormatting.GREEN) : 
                              Component.translatable("rarity.core.disabled").withStyle(ChatFormatting.RED)), false);
        
        if (isEnabled && isManagerEnabled) {
            source.sendSuccess(() -> Component.translatable("rarity.core.cache_system_active_note").withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.translatable("rarity.core.cache_system_inactive_note").withStyle(ChatFormatting.GRAY), false);
        }
        
        return 1;
    }
    
    /**
     * 显示缓存计数器详细信息
     */
    private static int showCacheCounters(CommandSourceStack source) {
        org.yanbwe.raritycore.client.ImprovedRenderCacheManager.CacheStats stats = 
            org.yanbwe.raritycore.client.ImprovedRenderCacheManager.getCacheStats();
        
        boolean isCacheEnabled = org.yanbwe.raritycore.client.ImprovedRenderCacheManager.isCacheSystemEnabled();
        boolean isConfigEnabled = ConfigManager.isEnableCacheSystem();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_counters_title").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_system_enabled_status", 
            isCacheEnabled ? Component.translatable("rarity.core.enabled").withStyle(ChatFormatting.GREEN) : 
                            Component.translatable("rarity.core.disabled").withStyle(ChatFormatting.RED)), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_config_enabled_status", 
            isConfigEnabled ? Component.translatable("rarity.core.enabled").withStyle(ChatFormatting.GREEN) : 
                             Component.translatable("rarity.core.disabled").withStyle(ChatFormatting.RED)), false);
        
        long totalRequests = stats.getHits() + stats.getMisses();
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_total_requests", totalRequests)
            .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_hits_detail", stats.getHits())
            .withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_misses_detail", stats.getMisses())
            .withStyle(ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_hit_rate_detail", String.format("%.2f", stats.getHitRate()))
            .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_clears_detail", stats.getClears())
            .withStyle(ChatFormatting.YELLOW), false);
        
        if (!isCacheEnabled || !isConfigEnabled) {
            source.sendSuccess(() -> Component.translatable("rarity.core.cache_counters_disabled_note")
                .withStyle(ChatFormatting.GRAY), false);
        }
        
        return 1;
    }
}