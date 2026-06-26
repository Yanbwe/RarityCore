package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.itemdatamatching.ItemDataRarityMatcher;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据导出命令类
 * 处理稀有度数据导出相关命令
 */
public class ExportManagementCommands {
    
    @SuppressWarnings("null")
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .requires(Commands.hasPermission(Commands.LEVEL_MODERATORS))
            .then(Commands.literal("export")
                .then(Commands.literal("all")
                    .executes(context -> exportAllRarityData(context.getSource()))
                )
                .then(Commands.literal("all-mod")
                    .executes(context -> exportAllModRarityData(context.getSource()))
                )
                .then(Commands.literal("mod")
                    .then(Commands.argument("modid", IdentifierArgument.id())
                        .executes(context -> exportModRarityData(context.getSource(), IdentifierArgument.getId(context, "modid")))
                    )
                )
                .then(Commands.literal("rarity")
                    .then(Commands.argument("rarity", IntegerArgumentType.integer(1, 7))
                        .executes(context -> exportRarityFiltered(context.getSource(), IntegerArgumentType.getInteger(context, "rarity")))
                    )
                )
            )
            .then(Commands.literal("details")
                .executes(context -> showDetails(context.getSource()))
                .then(Commands.literal("id")
                    .executes(context -> showDetails(context.getSource()))
                )
                .then(Commands.literal("itemdata")
                    .executes(context -> showItemDataDetails(context.getSource()))
                )
            )
        );
    }
    
    /**
     * 显示稀有度配置详情
     */
    @SuppressWarnings("null")
    private static int showDetails(CommandSourceStack source) {
        // 统计每个模组的物品数量
        Map<String, Integer> modItemCount = new HashMap<>();
        int totalItems = 0;
        
        for (Map.Entry<Identifier, Integer> entry : RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
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
     * 显示物品数据匹配配置详情
     */
    @SuppressWarnings("null")
    private static int showItemDataDetails(CommandSourceStack source) {
        try {
            // 获取物品数据规则统计信息
            Map<Identifier, Integer> ruleStats = ItemDataRarityMatcher.getRuleStatistics();
            int totalRules = ItemDataRarityMatcher.getRuleCount();
            int itemsWithItemDataConfig = ruleStats.size();
            
            // 发送基本统计信息
            source.sendSuccess(() -> Component.translatable("rarity.core.item_data_items_count", itemsWithItemDataConfig).withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.translatable("rarity.core.item_data_rules_count", totalRules).withStyle(ChatFormatting.AQUA), false);
            
            // 发送各物品的配置数量
            if (!ruleStats.isEmpty()) {
                source.sendSuccess(() -> Component.translatable("rarity.core.item_data_configs_detail").withStyle(ChatFormatting.YELLOW), false);
                for (Map.Entry<Identifier, Integer> entry : ruleStats.entrySet()) {
                    source.sendSuccess(() -> Component.literal(entry.getKey().toString() + ":" + entry.getValue()).withStyle(ChatFormatting.WHITE), false);
                }
            }
            
            return 1;
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error getting item data config details", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.command_failed").withStyle(ChatFormatting.RED), false);
            return 0;
        }
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
            String mcVersion = "1.21.11"; // 从 gradle.properties 获取的 Minecraft 版本
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
    private static int exportModRarityData(CommandSourceStack source, Identifier modId) {
        try {
            // 使用ConfigManager提供的路径
            Path configDir = ConfigManager.getConfigDirPath();
            Files.createDirectories(configDir);
            
            // 生成带 Minecraft 版本和时间戳的文件名
            String mcVersion = "1.21.11";
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
            String mcVersion = "1.21.11";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path exportDir = configDir.resolve("export_all-mod_" + mcVersion + "_" + timestamp);
            Files.createDirectories(exportDir);
            
            // 按模组分组稀有度数据
            Map<String, Map<String, Integer>> modBasedData = new HashMap<>();
            
            for (Map.Entry<Identifier, Integer> entry : RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
                String modId = entry.getKey().getNamespace();
                String itemId = entry.getKey().toString();
                Integer rarity = entry.getValue();
                
                modBasedData.computeIfAbsent(modId, k -> new HashMap<>()).put(itemId, rarity);
            }
            
            // 为每个模组创建单独的文件(包含时间戳)
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
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
     * 导出稀有度数据到文件
     */
    private static void exportRarityDataToFile(Path exportFile) throws IOException {
        // 使用LinkedHashMap保持顺序
        java.util.LinkedHashMap<String, Integer> exportData = new java.util.LinkedHashMap<>();
        
        // 从注册表获取所有已注册的稀有度数据
        for (Map.Entry<Identifier, Integer> entry : RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
            exportData.put(entry.getKey().toString(), entry.getValue());
        }
        
        // 写入导出文件
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
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
        for (Map.Entry<Identifier, Integer> entry : RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
            if (entry.getKey().getNamespace().equals(modNamespace)) {
                exportData.put(entry.getKey().toString(), entry.getValue());
            }
        }
        
        // 写入导出文件
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(exportFile.toFile())) {
            gson.toJson(exportData, writer);
        }
    }

    /**
     * 导出指定稀有度等级的所有物品数据
     * 从 ITEM_RARITY_MAP 中过滤指定稀有度的条目,输出与 FinalRarity.json 一致的格式
     */
    private static int exportRarityFiltered(CommandSourceStack source, int rarity) {
        try {
            // 使用ConfigManager提供的路径
            Path configDir = ConfigManager.getConfigDirPath();
            Files.createDirectories(configDir);

            // 生成带时间戳的文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "export_rarity_" + rarity + "_" + timestamp + ".json";
            Path exportFile = configDir.resolve(fileName);

            // 过滤指定稀有度的物品
            java.util.LinkedHashMap<String, Integer> exportData = new java.util.LinkedHashMap<>();
            for (Map.Entry<Identifier, Integer> entry : RarityRegistry.ITEM_RARITY_MAP.entrySet()) {
                if (entry.getValue() == rarity) {
                    exportData.put(entry.getKey().toString(), entry.getValue());
                }
            }

            // 写入导出文件
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(exportFile.toFile())) {
                gson.toJson(exportData, writer);
            }

            int itemCount = exportData.size();
            if (itemCount > 0) {
                source.sendSuccess(() -> Component.translatable("rarity.core.export_rarity_success",
                    rarity, itemCount, exportFile.toString()).withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendSuccess(() -> Component.translatable("rarity.core.export_rarity_empty",
                    rarity).withStyle(ChatFormatting.YELLOW), false);
            }
            return 1;
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to export rarity filtered data", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.export_failed",
                e.getMessage()).withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
}