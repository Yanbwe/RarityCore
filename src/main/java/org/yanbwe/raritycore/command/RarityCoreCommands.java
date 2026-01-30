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
import org.yanbwe.raritycore.config.RarityConfigLoader;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.edit.EditModeManager;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class RarityCoreCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .then(Commands.literal("sethand")
                .then(Commands.argument("rarity", IntegerArgumentType.integer())
                    .executes(context -> setHandRarity(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "rarity")
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
        );
        
        // 注册客户端配置重载命令
        dispatcher.register(Commands.literal("raritycore-client")
            .executes(context -> reloadClientConfig(context.getSource()))
        );
        
        // 注册切换纹理边框命令
        dispatcher.register(Commands.literal("raritycore-texture")
            .then(Commands.literal("toggle")
                .executes(context -> toggleTextureBorder(context.getSource()))
            )
        );
        
        // 注册编辑模式命令
        dispatcher.register(Commands.literal("raritycore-edit")
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
        // 重新加载资源包数据
        // 注意：在实际游戏中，这通常需要通过资源重载器来完成
        // 这里我们只重新加载配置文件
        RarityConfigLoader.loadConfigRarityData();
        
        // 同步更新后的数据到所有客户端
        RarityRegistry.syncRarityToClients();
        
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
            
            // 为每个模组创建单独的文件
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
}