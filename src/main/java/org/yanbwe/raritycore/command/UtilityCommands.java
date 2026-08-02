package org.yanbwe.raritycore.command;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.config.FinalRarityConfigFolderLoader;
import org.yanbwe.raritycore.config.RarityConfigLoader;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.edit.EditModeManager;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.util.JsonPerformanceOptimizer;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工具命令类
 * 处理编辑模式、性能监控、纹理边框等辅助功能命令
 */
public class UtilityCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("edit")
                .then(Commands.literal("enable")
                    .executes(context -> enableEditMode(context.getSource()))
                )
                .then(Commands.literal("disable")
                    .executes(context -> disableEditMode(context.getSource()))
                )
                .then(Commands.literal("toggle")
                    .executes(context -> toggleEditMode(context.getSource()))
                    .then(Commands.argument("state", BoolArgumentType.bool())
                        .executes(context -> setEditModeExplicit(
                            context.getSource(),
                            BoolArgumentType.getBool(context, "state")
                        ))
                    )
                )
                .then(Commands.literal("mode")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            java.util.stream.Stream.of("normal", "fullmatch"), builder))
                        .executes(context -> setEditModeType(
                            context.getSource(),
                            StringArgumentType.getString(context, "type")
                        ))
                    )
                )
                .then(Commands.literal("status")
                    .executes(context -> showEditModeStatus(context.getSource()))
                )
                .then(Commands.literal("parameter")
                    .then(Commands.literal("rarity")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                            .executes(context -> setParameterRarity(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "value")
                            ))
                        )
                    )
                    .then(Commands.literal("autoReload")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> setParameterAutoReload(
                                context.getSource(),
                                BoolArgumentType.getBool(context, "value")
                            ))
                        )
                    )
                    .then(Commands.literal("ignore")
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(context -> setParameterIgnore(
                                context.getSource(),
                                StringArgumentType.getString(context, "value")
                            ))
                        )
                    )
                    .then(Commands.literal("stringContains")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> setParameterStringContains(
                                context.getSource(),
                                BoolArgumentType.getBool(context, "value")
                            ))
                        )
                    )
                )
            )
            .then(Commands.literal("perf")
                .then(Commands.literal("stats")
                    .executes(context -> showPerformanceStats(context.getSource()))
                )
                .then(Commands.literal("optimize")
                    .executes(context -> triggerManualOptimization(context.getSource()))
                )
            )
        );
        
        // 注册客户端命令
        dispatcher.register(Commands.literal("raritycore-client")
            .then(Commands.literal("texture")
                .then(Commands.literal("toggle")
                    .executes(context -> toggleTextureBorder(context.getSource()))
                )
            )
        );
    }
    
    /**
     * 启用编辑模式
     */
    private static int enableEditMode(CommandSourceStack source) {
        EditModeManager.setEditMode(true);
        source.sendSuccess(() -> Component.translatable("rarity.core.edit_mode_enabled").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 禁用编辑模式
     */
    private static int disableEditMode(CommandSourceStack source) {
        EditModeManager.setEditMode(false);
        source.sendSuccess(() -> Component.translatable("rarity.core.edit_mode_disabled").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
    
    /**
     * 切换编辑模式
     */
    private static int toggleEditMode(CommandSourceStack source) {
        boolean newState = EditModeManager.toggleEditMode();
        if (newState) {
            source.sendSuccess(() -> Component.translatable("rarity.core.edit_mode_enabled").withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.translatable("rarity.core.edit_mode_disabled").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }
    
    /**
     * 显示编辑模式状态 (v13 增强: 显示模式类型和参数)
     */
    private static int showEditModeStatus(CommandSourceStack source) {
        boolean isEnabled = EditModeManager.isEditModeEnabled();
        int currentRarity = EditModeManager.getCurrentRarity();
        String modeName = EditModeManager.getModeName();

        if (isEnabled) {
            source.sendSuccess(() -> Component.literal("Edit mode: ENABLED")
                .withStyle(ChatFormatting.GREEN), false);
            source.sendSuccess(() -> Component.literal("  Mode: " + modeName)
                .withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal("  Rarity: " + currentRarity)
                .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal("  AutoReload: " + EditModeManager.isAutoReload())
                .withStyle(ChatFormatting.WHITE), false);
            source.sendSuccess(() -> Component.literal("  StringContains: " + EditModeManager.isStringContains())
                .withStyle(ChatFormatting.WHITE), false);
            if (!EditModeManager.getIgnoreComponents().isEmpty()) {
                source.sendSuccess(() -> Component.literal("  Ignore: " + EditModeManager.getIgnoreComponents())
                    .withStyle(ChatFormatting.WHITE), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("Edit mode: DISABLED")
                .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }
    
    /**
     * 显示性能统计信息
     */
    private static int showPerformanceStats(CommandSourceStack source) {
        // 获取各种性能指标
        RenderCacheManager.CacheStats cacheStats = 
            RenderCacheManager.getCacheStats();
        
        int pendingChanges = SyncManager.getPendingChangeCount();
        int registrySize = RarityRegistry.ITEM_RARITY_MAP.size();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.performance_stats_title").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.registry_size", registrySize).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.pending_changes", pendingChanges).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.cache_hit_rate", cacheStats.getHitRate()).withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.rarity_cache_size", cacheStats.getRarityCacheSize()).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.itemstack_cache_size", cacheStats.getItemStackCacheSize()).withStyle(ChatFormatting.WHITE), false);
        
        return 1;
    }
    
    /**
     * 触发性能优化
     */
    private static int triggerManualOptimization(CommandSourceStack source) {
        // 清理缓存
        RenderCacheManager.clearAllCache();
        
        // 重新加载配置
        FinalRarityConfigFolderLoader.loadFinalRarityConfigFolder();
        RarityConfigLoader.loadConfigRarityData();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.optimization_completed").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 切换纹理边框启用状态（V14：切换全局边框总开关）
     */
    private static int toggleTextureBorder(CommandSourceStack source) {
        RarityStyleConfigManager styleMgr = RarityStyleConfigManager.getInstance();
        boolean newState = !styleMgr.isBorderEnabled();
        styleMgr.setBorderEnabled(newState);
        
        source.sendSuccess(() -> Component.translatable("rarity.core.texture_border_toggle_success", 
            newState ? Component.translatable("rarity.core.enabled") : Component.translatable("rarity.core.disabled")).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // ──────────── v13 新增: 编辑模式命令执行器 ────────────

    /**
     * 显式设置编辑模式开关 (toggle <true|false>)
     */
    private static int setEditModeExplicit(CommandSourceStack source, boolean enabled) {
        EditModeManager.setEditMode(enabled);
        if (enabled) {
            source.sendSuccess(() -> Component.literal("Edit mode enabled").withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("Edit mode disabled").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    /**
     * 设置编辑模式类型 (mode <normal|fullmatch>)
     */
    private static int setEditModeType(CommandSourceStack source, String modeName) {
        String normalized = modeName.toLowerCase();
        if (!"normal".equals(normalized) && !"fullmatch".equals(normalized)) {
            source.sendSuccess(() -> Component.literal("Invalid mode: " + modeName + ". Use 'normal' or 'fullmatch'")
                .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        EditModeManager.setModeByName(normalized);
        source.sendSuccess(() -> Component.literal("Edit mode set to: " + EditModeManager.getModeName())
            .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * 设置 rarity 参数 (parameter rarity <value>)
     */
    private static int setParameterRarity(CommandSourceStack source, int value) {
        EditModeManager.setRarity(value);
        source.sendSuccess(() -> Component.literal("Rarity parameter set to: " + value)
            .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * 设置 autoReload 参数 (parameter autoReload <value>)
     */
    private static int setParameterAutoReload(CommandSourceStack source, boolean value) {
        EditModeManager.setAutoReload(value);
        source.sendSuccess(() -> Component.literal("AutoReload parameter set to: " + value)
            .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * 设置 ignore 参数 (parameter ignore <value>)
     */
    private static int setParameterIgnore(CommandSourceStack source, String value) {
        EditModeManager.setIgnoreComponents(value);
        source.sendSuccess(() -> Component.literal("Ignore components set to: " + value)
            .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * 设置 stringContains 参数 (parameter stringContains <value>)
     */
    private static int setParameterStringContains(CommandSourceStack source, boolean value) {
        EditModeManager.setStringContains(value);
        source.sendSuccess(() -> Component.literal("StringContains parameter set to: " + value)
            .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}