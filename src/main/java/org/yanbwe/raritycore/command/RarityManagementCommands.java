package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 稀有度管理命令类
 * 处理物品稀有度的设置、删除等相关命令
 */
public class RarityManagementCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .requires(source -> source.hasPermission(2))
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
            .then(Commands.literal("recalculate-auto")
                .executes(context -> recalculateAutoRarity(context.getSource()))
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
            
            // 注册稀有度(不自动同步,因为后面会手动同步)
            RarityRegistry.register(item, rarity, false);
            
            // 保存到配置文件
            saveRarityToConfig(itemId.toString(), rarity);
            
            // 手动同步到所有客户端
            RarityRegistry.syncRarityToClients();
            
            source.sendSuccess(() -> Component.translatable("rarity.core.item_set_rarity", itemId, rarity).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
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
        
        // 注册稀有度(不自动同步,因为后面会手动同步)
        RarityRegistry.register(item, rarity, false);
        
        // 保存到配置文件
        saveRarityToConfig(itemId.toString(), rarity);
        
        // 手动同步到所有客户端
        RarityRegistry.syncRarityToClients();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.item_set_rarity_by_id", itemId, rarity).withStyle(ChatFormatting.GREEN), false);
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
            
            // 删除稀有度(不自动同步,因为后面会手动同步)
            RarityRegistry.unregister(item, false);
            
            // 保存到配置文件(稀有度为0表示删除)
            saveRarityToConfig(itemId.toString(), 0);
            
            // 手动同步到所有客户端
            RarityRegistry.syncRarityToClients();
            
            source.sendSuccess(() -> Component.translatable("rarity.core.item_remove_rarity", itemId).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
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
        
        // 删除稀有度(不自动同步,因为后面会手动同步)
        RarityRegistry.unregister(item, false);
        
        // 保存到配置文件(稀有度为0表示删除)
        saveRarityToConfig(itemId.toString(), 0);
        
        // 手动同步到所有客户端
        RarityRegistry.syncRarityToClients();
        
        source.sendSuccess(() -> Component.translatable("rarity.core.item_remove_rarity_by_id", itemId).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
    
    /**
     * 保存稀有度到配置文件
     */
    public static void saveRarityToConfig(String itemId, int rarity) {
        try {
            Path configDir = ConfigManager.getConfigDirPath();
            Files.createDirectories(configDir);
            
            Path configFile = ConfigManager.getFinalRarityConfigPath();
            
            // 读取现有配置
            com.google.gson.JsonObject jsonObject;
            if (Files.exists(configFile)) {
                String content = Files.readString(configFile);
                if (!content.trim().isEmpty()) {
                    try {
                        // 使用 JsonParser 解析现有配置
                        jsonObject = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                    } catch (Exception e) {
                        // 如果解析失败,创建新的空对象
                        RarityCore.LOGGER.warn("Failed to parse config file, will recreate", e);
                        jsonObject = new com.google.gson.JsonObject();
                    }
                } else {
                    jsonObject = new com.google.gson.JsonObject();
                }
            } else {
                jsonObject = new com.google.gson.JsonObject();
            }
            
            // 更新配置 - 添加或修改指定的物品 ID 和稀有度
            jsonObject.addProperty(itemId, rarity);
            
            // 写入配置文件
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(configFile.toFile())) {
                gson.toJson(jsonObject, writer);
            }
        } catch (IOException e) {
            RarityCore.LOGGER.error("Failed to save rarity config", e);
        }
    }
    
    /**
     * 重新计算自动稀有度
     */
    private static int recalculateAutoRarity(CommandSourceStack source) {
        try {
            // 强制重新计算
            org.yanbwe.raritycore.calc.AutoRarityCalculator.forceRecalculate();
            
            source.sendSuccess(() -> Component.literal("已启动自动稀有度重新计算,请稍候..."), true);
            return 1;
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to recalculate auto rarity", e);
            source.sendSuccess(() -> Component.literal("重新计算失败:" + e.getMessage()).withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
}