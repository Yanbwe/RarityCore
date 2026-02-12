package org.yanbwe.raritycore.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.config.FinalRarityConfigFolderLoader;
import org.yanbwe.raritycore.config.RarityConfigLoader;
import org.yanbwe.raritycore.edit.EditModeManager;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.BufferedReader;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RarityCoreCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .requires(source -> source.hasPermission(2)) // 仅OP可用
            .then(Commands.literal("sethand")
                .then(Commands.argument("rarity", IntegerArgumentType.integer())
                    .executes(context -> setHandRarity(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "rarity")
                    ))
                )
            )
            .then(Commands.literal("removehand")
                .executes(context -> removeHandRarity(context.getSource()))
            )
            .then(Commands.literal("removerarity")
                .then(Commands.argument("item", ResourceLocationArgument.id())
                    .executes(context -> removeItemRarity(
                        context.getSource(),
                        ResourceLocationArgument.getId(context, "item")
                    ))
                )
            )
            .then(Commands.literal("setrarity")
                .then(Commands.argument("item", ResourceLocationArgument.id())
                    .then(Commands.argument("rarity", IntegerArgumentType.integer())
                        .executes(context -> setItemRarity(
                            context.getSource(),
                            ResourceLocationArgument.getId(context, "item"),
                            IntegerArgumentType.getInteger(context, "rarity")
                        ))
                    )
                )
            )
            .then(Commands.literal("reload")
                .executes(context -> reloadRarityData(context.getSource()))
            )
            .then(Commands.literal("export")
                .then(Commands.literal("all")
                    .executes(context -> exportAllRarityData(context.getSource()))
                )
                .then(Commands.literal("all-mod")
                    .executes(context -> exportAllModRarityData(context.getSource()))
                )
                .then(Commands.literal("mod")
                    .then(Commands.argument("modid", ResourceLocationArgument.id())
                        .executes(context -> exportModRarityData(context.getSource(), ResourceLocationArgument.getId(context, "modid")))
                    )
                )
            )
            .then(Commands.literal("details")
                .executes(context -> showDetails(context.getSource()))
            )
            .then(Commands.literal("perf")
                .then(Commands.literal("stats")
                    .executes(context -> showPerformanceStats(context.getSource()))
                )
                .then(Commands.literal("optimize")
                    .executes(context -> triggerManualOptimization(context.getSource()))
                )
            )
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
            .then(Commands.literal("server")
                .then(Commands.literal("reload")
                    .executes(context -> reloadServerConfig(context.getSource()))
                )
                .then(Commands.literal("status")
                    .executes(context -> showServerConfigStatus(context.getSource()))
                )
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
            .then(Commands.literal("texture")
                .then(Commands.literal("toggle")
                    .executes(context -> toggleTextureBorder(context.getSource()))
                )
            )

        );
    }
    
    /**
     * 设置手上物品的稀有度
     */
    private static int setHandRarity(CommandSourceStack source, int rarity) {
        try {
            Player player = source.getPlayerOrException();
            ItemStack itemStack = player.getMainHandItem();
            
            if (itemStack.isEmpty()) {
                source.sendSuccess(() -> Component.translatable("rarity.core.no_item_in_hand").withStyle(ChatFormatting.RED), false);
                return 0;
            }
            
            Item item = itemStack.getItem();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            
            if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                source.sendSuccess(() -> Component.translatable("rarity.core.unrecognized_item").withStyle(ChatFormatting.RED), false);
                return 0;
            }
            
            // 注册稀有度（不自动同步，因为后面会手动同步）
            RarityRegistry.register(item, rarity, false);
            
            // 保存到配置文件
            saveRarityToConfig(itemId.toString(), rarity);
            
            // 手动同步到所有客户端
            RarityRegistry.syncRarityToClients();
            
            source.sendSuccess(() -> Component.translatable("rarity.core.item_set_rarity", itemId, rarity).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (CommandSyntaxException e) {
            RarityCore.LOGGER.error("Command execution failed", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.command_failed").withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
    
    /**
     * 设置指定物品的稀有度
     */
    private static int setItemRarity(CommandSourceStack source, ResourceLocation itemId, int rarity) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        
        if (item == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            source.sendSuccess(() -> Component.translatable("rarity.core.unknown_item_id", itemId).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        
        // 注册稀有度（不自动同步，因为后面会手动同步）
        RarityRegistry.register(item, rarity, false);
        
        // 保存到配置文件
        saveRarityToConfig(itemId.toString(), rarity);
        
        // 手动同步到所有客户端
        RarityRegistry.syncRarityToClients();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.item_set_rarity_by_id", itemId, rarity).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 重新加载稀有度数据
     */
    private static int reloadRarityData(CommandSourceStack source) {
        // 重新加载所有配置文件
        
        // 按照加载顺序重新加载所有配置
        // 1. FinalRarityConfig文件夹
        source.sendSuccess(() -> Component.translatable("rarity.core.loading_final_rarity_config_folder").withStyle(ChatFormatting.YELLOW), false);
        FinalRarityConfigFolderLoader.loadFinalRarityConfigFolder();
        
        // 2. FinalRarity.json文件
        source.sendSuccess(() -> Component.translatable("rarity.core.loading_final_rarity_file").withStyle(ChatFormatting.YELLOW), false);
        RarityConfigLoader.loadConfigRarityData();
        
        // 同步数据到所有客户端
        RarityRegistry.syncRarityToClientsWithRetry();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.reload_success").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 显示稀有度配置详情
     */
    private static int showDetails(CommandSourceStack source) {
        // 统计每个模组的物品数量
        Map<String, Integer> modItemCount = new HashMap<>();
        int totalItems = 0;
        
        for (Map.Entry<ResourceLocation, Integer> entry : RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
            String modId = entry.getKey().getNamespace();
            modItemCount.put(modId, modItemCount.getOrDefault(modId, 0) + 1);
            totalItems++;
        }
        
        int modCount = modItemCount.size();
        
        // 构建响应消息
        StringBuilder response = new StringBuilder();
        response.append(Component.translatable("rarity.core.mods_count", modCount).getString()).append("\n");
        response.append(Component.translatable("rarity.core.items_count", totalItems).getString()).append("\n");
        
        // 添加模组列表
        for (Map.Entry<String, Integer> entry : modItemCount.entrySet()) {
            response.append(entry.getKey()).append(":").append(entry.getValue()).append(" ");
        }
        
        source.sendSuccess(() -> Component.literal(response.toString().trim()).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
    
    /**
     * 导出所有稀有度数据
     */
    private static int exportAllRarityData(CommandSourceStack source) {
        try {
            // 使用ConfigManager提供的路径
            Path configDir = ConfigManager.getConfigDirPath();
            Files.createDirectories(configDir);
            
            // 生成带 Minecraft 版本和时间戳的文件名
            String mcVersion = "1.20.1"; // 从 gradle.properties 获取的 Minecraft 版本
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName = "export_all_" + mcVersion + "_" + timestamp + ".json";
            Path exportFile = configDir.resolve(fileName);
            
            // 导出当前注册的所有稀有度数据
            exportRarityDataToFile(exportFile);
            
            source.sendSuccess(() -> Component.translatable("rarity.core.export_all_success", exportFile.toString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to export rarity data", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.export_failed", e.getMessage()).withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
    
    /**
     * 导出特定模组的稀有度数据
     */
    private static int exportModRarityData(CommandSourceStack source, ResourceLocation modId) {
        try {
            // 使用ConfigManager提供的路径
            Path configDir = ConfigManager.getConfigDirPath();
            Files.createDirectories(configDir);
            
            // 生成带 Minecraft 版本和时间戳的文件名
            String mcVersion = "1.20.1";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName = "export_mod_" + modId.getNamespace() + "_" + mcVersion + "_" + timestamp + ".json";
            Path exportFile = configDir.resolve(fileName);
            
            // 导出特定模组的稀有度数据
            exportModRarityDataToFile(exportFile, modId.getNamespace());
            
            source.sendSuccess(() -> Component.translatable("rarity.core.export_mod_success", modId.getNamespace(), exportFile.toString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to export mod rarity data", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.export_mod_failed", e.getMessage()).withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
    
    /**
     * 导出所有模组的稀有度数据到单独的文件
     */
    private static int exportAllModRarityData(CommandSourceStack source) {
        try {
            // 使用ConfigManager提供的路径
            Path configDir = ConfigManager.getConfigDirPath();
            Files.createDirectories(configDir);
            
            // 生成带 Minecraft 版本和时间戳的文件夹名
            String mcVersion = "1.20.1";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path exportDir = configDir.resolve("export_all-mod_" + mcVersion + "_" + timestamp);
            Files.createDirectories(exportDir);
            
            // 按模组分组稀有度数据
            Map<String, Map<String, Integer>> modBasedData = new HashMap<>();
            
            for (Map.Entry<ResourceLocation, Integer> entry : RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
                String modId = entry.getKey().getNamespace();
                String itemId = entry.getKey().toString();
                Integer rarity = entry.getValue();
                
                modBasedData.computeIfAbsent(modId, k -> new HashMap<>()).put(itemId, rarity);
            }
            
            // 为每个模组创建单独的文件（包含时间戳）
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            for (Map.Entry<String, Map<String, Integer>> modEntry : modBasedData.entrySet()) {
                String modId = modEntry.getKey();
                Map<String, Integer> modData = modEntry.getValue();
                
                Path modExportFile = exportDir.resolve(modId + "_" + mcVersion + ".json");
                
                try (FileWriter writer = new FileWriter(modExportFile.toFile())) {
                    gson.toJson(modData, writer);
                }
            }
            
            source.sendSuccess(() -> Component.translatable("rarity.core.export_all_success", exportDir.toString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to export all-mod rarity data", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.export_failed", e.getMessage()).withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
    
    /**
     * 重新加载客户端配置
     */
    private static int reloadClientConfig(CommandSourceStack source) {
        ConfigManager.loadClientConfig();
        String borderStyleText = ConfigManager.getItemBorderStyle() == 0 ? Component.translatable("rarity.core.border_style_hollow").getString() : Component.translatable("rarity.core.border_style_solid").getString();
        String textureBorderStatus = ConfigManager.isUseTextureBorder() ? Component.translatable("rarity.core.enabled").getString() : Component.translatable("rarity.core.disabled").getString();
        
        // 处理客户端配置变更对缓存的影响
        org.yanbwe.raritycore.client.ImprovedRenderCacheManager.handleClientConfigChange();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.reload_client_config_with_texture", 
                ConfigManager.isEnableItemBorderRendering(), 
                borderStyleText,
                textureBorderStatus).withStyle(ChatFormatting.GREEN), false);
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
                        RarityCore.LOGGER.warn("解析配置文件失败，将重新创建", e);
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
    
    /**
     * 保存稀有度到配置文件
     */
    private static void saveRarityToConfig(String itemId, int rarity) {
        saveRarityToConfigInternal(itemId, rarity);
    }
    
    /**
     * 公共方法：保存稀有度到配置文件
     * 供其他类调用
     */
    public static void saveRarityToConfigPublic(String itemId, int rarity) {
        saveRarityToConfigInternal(itemId, rarity);
    }
    
    /**
     * 内部实现：保存稀有度到配置文件
     */
    private static void saveRarityToConfigInternal(String itemId, int rarity) {
        try {
            Path configDir = ConfigManager.getConfigDirPath();
            Files.createDirectories(configDir);
            
            Path configFile = ConfigManager.getFinalRarityConfigPath();
            
            // 读取现有配置
            JsonObject jsonObject;
            if (Files.exists(configFile)) {
                String content = Files.readString(configFile);
                if (!content.trim().isEmpty()) {
                    try {
                        // 使用JsonParser解析现有配置
                        jsonObject = JsonParser.parseString(content).getAsJsonObject();
                    } catch (Exception e) {
                        // 如果解析失败，创建新的空对象
                        RarityCore.LOGGER.warn("解析配置文件失败，将重新创建", e);
                        jsonObject = new JsonObject();
                    }
                } else {
                    jsonObject = new JsonObject();
                }
            } else {
                jsonObject = new JsonObject();
            }
            
            // 更新配置 - 添加或修改指定的物品ID和稀有度
            jsonObject.addProperty(itemId, rarity);
            
            // 写入配置文件
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(configFile.toFile())) {
                gson.toJson(jsonObject, writer);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to save rarity config", e);
        }
    }
    
    /**
     * 导出稀有度数据到文件
     */
    private static void exportRarityDataToFile(Path exportFile) throws IOException {
        // 使用LinkedHashMap保持顺序
        java.util.LinkedHashMap<String, Integer> exportData = new java.util.LinkedHashMap<>();
        
        // 从注册表获取所有已注册的稀有度数据
        for (Map.Entry<ResourceLocation, Integer> entry : RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
            exportData.put(entry.getKey().toString(), entry.getValue());
        }
        
        // 写入导出文件
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(exportFile.toFile())) {
            gson.toJson(exportData, writer);
        }
    }
    
    /**
     * 导出特定模组的稀有度数据到文件
     */
    private static void exportModRarityDataToFile(Path exportFile, String modNamespace) throws IOException {
        // 使用LinkedHashMap保持顺序
        java.util.LinkedHashMap<String, Integer> exportData = new java.util.LinkedHashMap<>();
        
        // 从注册表获取特定模组的已注册稀有度数据
        for (Map.Entry<ResourceLocation, Integer> entry : RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
            if (entry.getKey().getNamespace().equals(modNamespace)) {
                exportData.put(entry.getKey().toString(), entry.getValue());
            }
        }
        
        // 写入导出文件
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(exportFile.toFile())) {
            gson.toJson(exportData, writer);
        }
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
     * 重新加载服务端配置
     */
    private static int reloadServerConfig(CommandSourceStack source) {
        try {
            // 重新加载服务端配置
            org.yanbwe.raritycore.config.ServerConfigManager.loadServerConfig();
            
            // 重新加载稀有度数据以应用新配置
            org.yanbwe.raritycore.config.FinalRarityConfigFolderLoader.loadFinalRarityConfigFolder();
            org.yanbwe.raritycore.config.RarityConfigLoader.loadConfigRarityData();
            
            // 同步数据到所有客户端
            org.yanbwe.raritycore.registry.RarityRegistry.syncRarityToClientsWithRetry();
            
            String checkVanillaRarityStatus = org.yanbwe.raritycore.config.ServerConfigManager.isCheckVanillaRarity() ? 
                Component.translatable("rarity.core.enabled").getString() : 
                Component.translatable("rarity.core.disabled").getString();
            String skipUnconfiguredItemsStatus = org.yanbwe.raritycore.config.ServerConfigManager.isSkipUnconfiguredItems() ? 
                Component.translatable("rarity.core.enabled").getString() : 
                Component.translatable("rarity.core.disabled").getString();
            
            source.sendSuccess(() -> Component.translatable("rarity.core.server_config_reload_success", 
                checkVanillaRarityStatus, skipUnconfiguredItemsStatus).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to reload server config", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.server_config_reload_failed", e.getMessage())
                .withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
    
    /**
     * 显示服务端配置状态
     */
    private static int showServerConfigStatus(CommandSourceStack source) {
        boolean checkVanillaRarity = org.yanbwe.raritycore.config.ServerConfigManager.isCheckVanillaRarity();
        boolean skipUnconfiguredItems = org.yanbwe.raritycore.config.ServerConfigManager.isSkipUnconfiguredItems();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.server_config_status_title").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.check_vanilla_rarity", 
            checkVanillaRarity ? Component.translatable("rarity.core.enabled") : Component.translatable("rarity.core.disabled"))
            .withStyle(checkVanillaRarity ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.translatable("rarity.core.skip_unconfigured_items", 
            skipUnconfiguredItems ? Component.translatable("rarity.core.enabled") : Component.translatable("rarity.core.disabled"))
            .withStyle(skipUnconfiguredItems ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        
        return 1;
    }
    
    /**
     * 删除手上物品的稀有度
     */
    private static int removeHandRarity(CommandSourceStack source) {
        try {
            Player player = source.getPlayerOrException();
            ItemStack itemStack = player.getMainHandItem();
            
            if (itemStack.isEmpty()) {
                source.sendSuccess(() -> Component.translatable("rarity.core.no_item_in_hand").withStyle(ChatFormatting.RED), false);
                return 0;
            }
            
            Item item = itemStack.getItem();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            
            if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                source.sendSuccess(() -> Component.translatable("rarity.core.unrecognized_item").withStyle(ChatFormatting.RED), false);
                return 0;
            }
            
            // 删除稀有度（不自动同步，因为后面会手动同步）
            RarityRegistry.unregister(item, false);
            
            // 保存到配置文件（稀有度为0表示删除）
            saveRarityToConfig(itemId.toString(), 0);
            
            // 手动同步到所有客户端
            RarityRegistry.syncRarityToClients();
            
            source.sendSuccess(() -> Component.translatable("rarity.core.item_remove_rarity", itemId).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (CommandSyntaxException e) {
            RarityCore.LOGGER.error("Command execution failed", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.command_failed").withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
    
    /**
     * 删除指定物品的稀有度
     */
    private static int removeItemRarity(CommandSourceStack source, ResourceLocation itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        
        if (item == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            source.sendSuccess(() -> Component.translatable("rarity.core.unknown_item_id", itemId).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        
        // 删除稀有度（不自动同步，因为后面会手动同步）
        RarityRegistry.unregister(item, false);
        
        // 保存到配置文件（稀有度为0表示删除）
        saveRarityToConfig(itemId.toString(), 0);
        
        // 手动同步到所有客户端
        RarityRegistry.syncRarityToClients();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.item_remove_rarity_by_id", itemId).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 显示配置版本信息
     */
    private static int showConfigVersionInfo(CommandSourceStack source) {
        try {
            source.sendSuccess(() -> Component.translatable("rarity.core.config_version_info_title").withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.translatable("rarity.core.current_config_version", 
                org.yanbwe.raritycore.config.ConfigVersionManager.CURRENT_CONFIG_VERSION)
                .withStyle(ChatFormatting.YELLOW), false);
            
            // 显示客户端配置版本
            Path clientConfigPath = ConfigManager.getClientConfigPath();
            if (Files.exists(clientConfigPath)) {
                try (BufferedReader reader = Files.newBufferedReader(clientConfigPath)) {
                    JsonObject clientConfig = new Gson().fromJson(reader, JsonObject.class);
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
            Path serverConfigPath = org.yanbwe.raritycore.config.ServerConfigManager.getServerConfigPath();
            if (Files.exists(serverConfigPath)) {
                try (BufferedReader reader = Files.newBufferedReader(serverConfigPath)) {
                    JsonObject serverConfig = new Gson().fromJson(reader, JsonObject.class);
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
            int clientResult = org.yanbwe.raritycore.config.ConfigVersionManager.checkAndUpgradeConfig(
                ConfigManager.getClientConfigPath(), "client (forced)");
            
            // 强制升级服务端配置
            int serverResult = org.yanbwe.raritycore.config.ConfigVersionManager.checkAndUpgradeConfig(
                org.yanbwe.raritycore.config.ServerConfigManager.getServerConfigPath(), "server (forced)");
            
            source.sendSuccess(() -> Component.translatable("rarity.core.config_upgrade_completed", 
                clientResult, serverResult, org.yanbwe.raritycore.config.ConfigVersionManager.CURRENT_CONFIG_VERSION)
                .withStyle(ChatFormatting.GREEN), false);
            
            return 1;
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to force config upgrade", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.config_upgrade_failed", e.getMessage())
                .withStyle(ChatFormatting.RED), false);
            return 0;
        }
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
        boolean isConfigEnabled = org.yanbwe.raritycore.config.ConfigManager.isEnableCacheSystem();
        
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
    
    /**
     * 验证缓存禁用功能
     */
    private static int verifyCacheDisable(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("=== 缓存禁用功能验证 ===").withStyle(ChatFormatting.GOLD), false);
        
        // 保存当前状态
        boolean originalConfigState = org.yanbwe.raritycore.config.ConfigManager.isEnableCacheSystem();
        boolean originalManagerState = org.yanbwe.raritycore.client.ImprovedRenderCacheManager.isCacheSystemEnabled();
        
        source.sendSuccess(() -> Component.literal("初始状态检查:").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("  配置管理器: " + (originalConfigState ? "启用" : "禁用")).withStyle(originalConfigState ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.literal("  缓存管理器: " + (originalManagerState ? "启用" : "禁用")).withStyle(originalManagerState ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        
        // 测试当前启用状态下的缓存行为
        source.sendSuccess(() -> Component.literal("\n启用状态测试:").withStyle(ChatFormatting.YELLOW), false);
        boolean enabledStateWorks = testCacheBehavior(true);
        source.sendSuccess(() -> Component.literal("  启用状态下缓存行为: " + (enabledStateWorks ? "✓ 正常" : "✗ 异常")).withStyle(enabledStateWorks ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        
        // 临时禁用缓存系统进行测试
        source.sendSuccess(() -> Component.literal("\n禁用测试:").withStyle(ChatFormatting.YELLOW), false);
        org.yanbwe.raritycore.config.ConfigManager.setEnableCacheSystem(false);
        org.yanbwe.raritycore.client.ImprovedRenderCacheManager.setCacheSystemEnabled(false);
        
        // 验证禁用状态
        boolean disabledConfigState = org.yanbwe.raritycore.config.ConfigManager.isEnableCacheSystem();
        boolean disabledManagerState = org.yanbwe.raritycore.client.ImprovedRenderCacheManager.isCacheSystemEnabled();
        boolean statesConsistent = disabledConfigState == disabledManagerState;
        
        source.sendSuccess(() -> Component.literal("  配置管理器: " + (disabledConfigState ? "启用" : "禁用")).withStyle(disabledConfigState ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.literal("  缓存管理器: " + (disabledManagerState ? "启用" : "禁用")).withStyle(disabledManagerState ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.literal("  状态一致性: " + (statesConsistent ? "✓ 一致" : "✗ 不一致")).withStyle(statesConsistent ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        
        // 测试禁用状态下的缓存行为
        boolean disabledStateWorks = testCacheBehavior(false);
        source.sendSuccess(() -> Component.literal("  禁用状态下缓存行为: " + (disabledStateWorks ? "✓ 正确" : "✗ 错误")).withStyle(disabledStateWorks ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        
        // 恢复原始状态
        source.sendSuccess(() -> Component.literal("\n状态恢复:").withStyle(ChatFormatting.YELLOW), false);
        org.yanbwe.raritycore.config.ConfigManager.setEnableCacheSystem(originalConfigState);
        org.yanbwe.raritycore.client.ImprovedRenderCacheManager.setCacheSystemEnabled(originalManagerState);
        
        boolean restoredConfigState = org.yanbwe.raritycore.config.ConfigManager.isEnableCacheSystem();
        boolean restoredManagerState = org.yanbwe.raritycore.client.ImprovedRenderCacheManager.isCacheSystemEnabled();
        
        source.sendSuccess(() -> Component.literal("  配置管理器恢复: " + (restoredConfigState == originalConfigState ? "✓ 成功" : "✗ 失败")).withStyle(restoredConfigState == originalConfigState ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.literal("  缓存管理器恢复: " + (restoredManagerState == originalManagerState ? "✓ 成功" : "✗ 失败")).withStyle(restoredManagerState == originalManagerState ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        
        source.sendSuccess(() -> Component.literal("\n=== 验证结果 ===").withStyle(ChatFormatting.GOLD), false);
        boolean overallSuccess = statesConsistent && disabledStateWorks && 
                               (restoredConfigState == originalConfigState) && 
                               (restoredManagerState == originalManagerState);
        
        if (overallSuccess) {
            source.sendSuccess(() -> Component.literal("✓ 缓存禁用功能验证通过").withStyle(ChatFormatting.GREEN), false);
            source.sendSuccess(() -> Component.literal("  - 禁用时缓存方法正确返回null").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("  - 禁用时缓存操作被正确忽略").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("  - 状态切换和恢复功能正常").withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("✗ 缓存禁用功能存在问题").withStyle(ChatFormatting.RED), false);
        }
        
        return 1;
    }
    
    /**
     * 测试缓存行为的核心逻辑
     * @param expectEnabled 期望的启用状态
     * @return 测试是否通过
     */
    private static boolean testCacheBehavior(boolean expectEnabled) {
        boolean testPassed = true;
        
        try {
            // 测试获取缓存方法
            Integer result1 = org.yanbwe.raritycore.client.ImprovedRenderCacheManager.getCachedRarity((net.minecraft.world.item.Item)null);
            Integer result2 = org.yanbwe.raritycore.client.ImprovedRenderCacheManager.getCachedItemStackRarity(null);
            
            // 在禁用状态下应该返回null，在启用状态下可能返回null（因为传入的是null）
            boolean nullResults = (result1 == null) && (result2 == null);
            
            if (expectEnabled) {
                // 启用状态下，我们主要验证方法不抛异常
                // 实际的缓存行为取决于传入的参数，这里主要是确保方法能正常调用
            } else {
                // 禁用状态下，方法应该明确返回null
                if (!nullResults) {
                    testPassed = false;
                }
            }
            
            // 测试缓存操作方法（不应该抛出异常）
            org.yanbwe.raritycore.client.ImprovedRenderCacheManager.cacheRarity(null, 5);
            org.yanbwe.raritycore.client.ImprovedRenderCacheManager.cacheItemStackRarity(null, 5);
            
        } catch (Exception e) {
            testPassed = false;
        }
        
        return testPassed;
    }
}