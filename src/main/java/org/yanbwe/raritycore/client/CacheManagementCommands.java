package org.yanbwe.raritycore.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.DualCacheManager;
import org.yanbwe.raritycore.client.RenderCacheManager;

public class CacheManagementCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .then(Commands.literal("cache")
                // 不需要权限要求，供所有玩家使用
                
                // 显示缓存统计信息
                .then(Commands.literal("stats")
                    .executes(context -> {
                        RenderCacheManager.CacheStats stats = RenderCacheManager.getCacheStats();
                        boolean isCacheEnabled = org.yanbwe.raritycore.config.ConfigManager.isEnableCacheSystem();
                        
                        context.getSource().sendSuccess(() -> Component.literal("=== 缓存统计信息 ===")
                            .withStyle(ChatFormatting.GOLD), false);
                        context.getSource().sendSuccess(() -> Component.literal("缓存启用状态: " + (isCacheEnabled ? "启用" : "禁用"))
                            .withStyle(isCacheEnabled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
                        context.getSource().sendSuccess(() -> Component.literal("命中率: " + String.format("%.2f%%", stats.getHitRate()))
                            .withStyle(ChatFormatting.AQUA), false);
                        context.getSource().sendSuccess(() -> Component.literal("ID缓存大小: " + stats.getRarityCacheSize())
                            .withStyle(ChatFormatting.YELLOW), false);
                        context.getSource().sendSuccess(() -> Component.literal("NBT缓存大小: " + stats.getItemStackCacheSize())
                            .withStyle(ChatFormatting.YELLOW), false);
                        
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
}