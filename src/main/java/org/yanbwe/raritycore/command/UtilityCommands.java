package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.edit.EditModeManager;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
                )
                .then(Commands.literal("status")
                    .executes(context -> showEditModeStatus(context.getSource()))
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
     * 显示编辑模式状态
     */
    private static int showEditModeStatus(CommandSourceStack source) {
        boolean isEnabled = EditModeManager.isEditModeEnabled();
        int currentRarity = EditModeManager.getCurrentRarity();
        
        if (isEnabled) {
            source.sendSuccess(() -> Component.translatable("rarity.core.edit_mode_status_enabled", currentRarity)
                .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.translatable("rarity.core.edit_mode_status_disabled")
                .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }
    
    /**
     * 显示性能统计信息
     */
    private static int showPerformanceStats(CommandSourceStack source) {
        // 获取各种性能指标
        org.yanbwe.raritycore.client.RenderCacheManager.CacheStats cacheStats = 
            org.yanbwe.raritycore.client.RenderCacheManager.getCacheStats();
        
        int pendingChanges = org.yanbwe.raritycore.registry.RarityRegistry.getPendingChangeCount();
        int registrySize = org.yanbwe.raritycore.registry.RarityRegistry.ITEM_RARITY_MAP.size();
        
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
        org.yanbwe.raritycore.client.RenderCacheManager.clearAllCache();
        
        // 重新加载配置
        org.yanbwe.raritycore.config.FinalRarityConfigFolderLoader.loadFinalRarityConfigFolder();
        org.yanbwe.raritycore.config.RarityConfigLoader.loadConfigRarityData();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.optimization_completed").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 切换纹理边框启用状态
     */
    private static int toggleTextureBorder(CommandSourceStack source) {
        boolean currentState = ConfigManager.isUseTextureBorder();
        boolean newState = !currentState;
        ConfigManager.setUseTextureBorder(newState);
        
        // 尝试保存到配置文件
        try {
            Path configDir = ConfigManager.getConfigDirPath();
            Files.createDirectories(configDir);
            
            Path configFile = ConfigManager.getClientConfigPath();
            
            // 读取现有配置
            JsonObject jsonObject;
            if (Files.exists(configFile)) {
                String content = Files.readString(configFile);
                if (!content.trim().isEmpty()) {
                    try {
                        jsonObject = JsonParser.parseString(content).getAsJsonObject();
                    } catch (Exception e) {
                        RarityCore.LOGGER.warn("Failed to parse config file, will recreate", e);
                        jsonObject = new JsonObject();
                    }
                } else {
                    jsonObject = new JsonObject();
                }
            } else {
                jsonObject = new JsonObject();
            }
            
            // 更新配置
            jsonObject.addProperty("useTextureBorder", newState);
            
            // 写入配置文件
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(configFile.toFile())) {
                gson.toJson(jsonObject, writer);
            }
            
            source.sendSuccess(() -> Component.translatable("rarity.core.texture_border_toggle_success", 
                newState ? Component.translatable("rarity.core.enabled") : Component.translatable("rarity.core.disabled")).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to save client config", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.texture_border_toggle_error").withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
}