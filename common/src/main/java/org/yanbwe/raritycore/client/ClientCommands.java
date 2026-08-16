package org.yanbwe.raritycore.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import org.yanbwe.raritycore.cache.CacheConfig;
import org.yanbwe.raritycore.cache.ComponentCacheManager;
import org.yanbwe.raritycore.cache.DualCacheManager;
import org.yanbwe.raritycore.cache.IdCacheManager;
import org.yanbwe.raritycore.cache.RarityCacheCoordinator;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.event.RarityConfigReloadEvent;
import org.yanbwe.raritycore.util.StarDisplayManager;

/**
 * 客户端命令管理器
 * 负责注册所有客户端相关的子命令
 * 供没有OP权限的普通玩家使用
 */
public class ClientCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore-client")
            .then(Commands.literal("cache")
                // 显示缓存统计信息（两级缓存明细）
                .then(Commands.literal("stats")
                    .executes(context -> {
                        RenderCacheManager.CacheStats stats = RenderCacheManager.getCacheStats();
                        RarityCacheCoordinator.CombinedCacheStatistics combined =
                            RarityCacheCoordinator.getStatistics();
                        boolean isCacheEnabled = org.yanbwe.raritycore.config.ClientConfigManager.isEnableCacheSystem();
                        CacheConfig config = DualCacheManager.getConfig();
                        
                        // ── 系统状态 ──
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_stats_header")
                            .withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_enabled_status",
                            isCacheEnabled ? "§a✓ 启用" : "§c✗ 禁用")
                            .withStyle(ChatFormatting.WHITE), false);
                        
                        // ── 整体统计 ──
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_overall_section")
                            .withStyle(ChatFormatting.YELLOW), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_total_entries",
                            combined.getTotalSize())
                            .withStyle(ChatFormatting.WHITE), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_overall_hit_rate",
                            String.format("%.1f%%", combined.getOverallHitRate()))
                            .withStyle(combined.getOverallHitRate() > 50 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
                        
                        // ── ID缓存层 ──
                        IdCacheManager.IdCacheStatistics idStats = IdCacheManager.getStatistics();
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_id_section")
                            .withStyle(ChatFormatting.AQUA), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_tier_size",
                            idStats.getCacheSize())
                            .withStyle(ChatFormatting.WHITE), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_tier_hits",
                            idStats.getHits())
                            .withStyle(ChatFormatting.WHITE), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_tier_misses",
                            idStats.getMisses())
                            .withStyle(ChatFormatting.WHITE), false);
                        if (idStats.getHits() + idStats.getMisses() > 0) {
                            context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_tier_hit_rate",
                                String.format("%.1f%%", idStats.getHitRate()))
                                .withStyle(idStats.getHitRate() > 50 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
                        }
                        
                        // ── 组件缓存层 ──
                        ComponentCacheManager.ComponentCacheStatistics compStats = ComponentCacheManager.getStatistics();
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_component_section")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_tier_size",
                            compStats.getCacheSize())
                            .withStyle(ChatFormatting.WHITE), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_tier_hits",
                            compStats.getHits())
                            .withStyle(ChatFormatting.WHITE), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_tier_misses",
                            compStats.getMisses())
                            .withStyle(ChatFormatting.WHITE), false);
                        if (compStats.getHits() + compStats.getMisses() > 0) {
                            context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_tier_hit_rate",
                                String.format("%.1f%%", compStats.getHitRate()))
                                .withStyle(compStats.getHitRate() > 50 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
                        }
                        
                        // ── 配置信息 ──
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_config_header")
                            .withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD), false);
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
                        RenderCacheManager.clearAllCache();
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_cleared")
                            .withStyle(ChatFormatting.GREEN), false);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("reload")
                .executes(context -> {
                    // 重载 client.json
                    ClientConfigManager.loadClientConfig();
                    // 重载 V14 样式配置（RarityStyle.json）
                    RarityStyleConfigManager.reload();
                    // 重载星星显示
                    StarDisplayManager.getInstance().reloadConfiguration();
                    // 处理 skipUnconfiguredItems 变更
                    ItemBorderRenderer.handleSkipConfigChange();
                    RarityTooltipHandler.handleSkipConfigChange();
                    // 刷新缓存
                    RenderCacheManager.clearAllCache();
                    // 发布客户端配置重载事件
                    NeoForge.EVENT_BUS.post(new RarityConfigReloadEvent.Client());
                    context.getSource().sendSuccess(() -> Component.translatable("rarity.core.client_config_reloaded")
                        .withStyle(ChatFormatting.GREEN), false);
                    return 1;
                })
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
