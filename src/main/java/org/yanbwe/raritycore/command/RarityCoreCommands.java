package org.yanbwe.raritycore.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
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
import org.yanbwe.raritycore.data.RarityDataLoader;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class RarityCoreCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .then(Commands.literal("sethand")
                .then(Commands.argument("rarity", IntegerArgumentType.integer(RarityConstants.MIN_RARITY, RarityConstants.MAX_RARITY))
                    .executes(context -> setHandRarity(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "rarity")
                    ))
                )
            )
            .then(Commands.literal("setrarity")
                .then(Commands.argument("item", ResourceLocationArgument.id())
                    .then(Commands.argument("rarity", IntegerArgumentType.integer(RarityConstants.MIN_RARITY, RarityConstants.MAX_RARITY))
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
                .executes(context -> exportRarityData(context.getSource()))
            )
        );
        
        // 注册客户端配置重载命令
        dispatcher.register(Commands.literal("raritycore-client")
            .executes(context -> reloadClientConfig(context.getSource()))
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
                source.sendSuccess(() -> Component.literal("你手上没有物品！").withStyle(ChatFormatting.RED), false);
                return 0;
            }
            
            Item item = itemStack.getItem();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            
            if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                source.sendSuccess(() -> Component.literal("无法识别的物品！").withStyle(ChatFormatting.RED), false);
                return 0;
            }
            
            // 注册稀有度
            RarityRegistry.register(item, rarity);
            
            // 保存到配置文件
            saveRarityToConfig(itemId.toString(), rarity);
            
            source.sendSuccess(() -> Component.literal("已将手上物品 " + itemId + " 设置为稀有度 " + rarity).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (CommandSyntaxException e) {
            RarityCore.LOGGER.error("命令执行失败", e);
            source.sendSuccess(() -> Component.literal("命令执行失败！").withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
    
    /**
     * 设置指定物品的稀有度
     */
    private static int setItemRarity(CommandSourceStack source, ResourceLocation itemId, int rarity) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        
        if (item == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            source.sendSuccess(() -> Component.literal("未知物品ID: " + itemId).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        
        // 注册稀有度
        RarityRegistry.register(item, rarity);
        
        // 保存到配置文件
        saveRarityToConfig(itemId.toString(), rarity);
        
        source.sendSuccess(() -> Component.literal("已将物品 " + itemId + " 设置为稀有度 " + rarity).withStyle(ChatFormatting.GREEN), false);
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
        
        source.sendSuccess(() -> Component.literal("已重新加载稀有度数据").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 导出稀有度数据
     */
    private static int exportRarityData(CommandSourceStack source) {
        try {
            // 使用ConfigManager提供的路径
            Path configDir = ConfigManager.getConfigDirPath();
            Files.createDirectories(configDir);
            
            // 生成带时间戳的文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName = "export_" + timestamp + ".json";
            Path exportFile = configDir.resolve(fileName);
            
            // 导出当前注册的所有稀有度数据
            exportRarityDataToFile(exportFile);
            
            source.sendSuccess(() -> Component.literal("稀有度数据已导出到: " + exportFile.toString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            RarityCore.LOGGER.error("导出稀有度数据失败", e);
            source.sendSuccess(() -> Component.literal("导出稀有度数据失败: " + e.getMessage()).withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
    
    /**
     * 重新加载客户端配置
     */
    private static int reloadClientConfig(CommandSourceStack source) {
        ConfigManager.loadClientConfig();
        source.sendSuccess(() -> Component.literal("已重新加载客户端配置: 物品边框渲染=" + 
                ConfigManager.isEnableItemBorderRendering() + ", 边框样式=" + 
                (ConfigManager.getItemBorderStyle() == 0 ? "空心" : "实心")).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 保存稀有度到配置文件
     */
    private static void saveRarityToConfig(String itemId, int rarity) {
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
            RarityCore.LOGGER.error("保存稀有度配置失败", e);
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
}