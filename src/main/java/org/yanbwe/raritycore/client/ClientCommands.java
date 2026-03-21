package org.yanbwe.raritycore.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.cache.CacheConfig;
import org.yanbwe.raritycore.cache.DualCacheManager;
import org.yanbwe.raritycore.cache.RenderCacheManager;

/**
 * 客户端命令管理器
 * 负责注册所有客户端相关的子命令
 * 供没有OP权限的普通玩家使用
 */
public class ClientCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore-client")
            .then(Commands.literal("cache")
                // 显示缓存统计信息
                .then(Commands.literal("stats")
                    .executes(context -> {
                        RenderCacheManager.CacheStats stats = RenderCacheManager.getCacheStats();
                        boolean isCacheEnabled = org.yanbwe.raritycore.config.ClientConfigManager.isEnableCacheSystem();
                        CacheConfig config = DualCacheManager.getConfig();
                        
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_stats_header")
                            .withStyle(ChatFormatting.GOLD), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_enabled_status", isCacheEnabled ? "启用" : "禁用")
                            .withStyle(isCacheEnabled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_hit_rate_display", stats.getHitRate())
                            .withStyle(ChatFormatting.AQUA), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.id_cache_size", stats.getRarityCacheSize())
                            .withStyle(ChatFormatting.YELLOW), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.nbt_cache_size", stats.getItemStackCacheSize())
                            .withStyle(ChatFormatting.YELLOW), false);
                        
                        // 添加缓存配置信息
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.cache_config_header")
                            .withStyle(ChatFormatting.GOLD), false);
                        context.getSource().sendSuccess(() -> Component.translatable("rarity.core.nbt_cache_capacity", config.getActualMaxNbtCacheSize())
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