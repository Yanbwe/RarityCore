package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.yanbwe.raritycore.Raritycore;
import org.yanbwe.raritycore.client.RarityClientData;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.registry.RarityRegistry;

public class RarityCoreCommands {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("raritycore")
            .requires(source -> source.hasPermissionLevel(2))
            
            // 查询物品稀有度
            .then(CommandManager.literal("get")
                .then(CommandManager.argument("item", StringArgumentType.string())
                    .executes(context -> {
                        String itemIdString = StringArgumentType.getString(context, "item");
                        try {
                            Identifier itemId = new Identifier(itemIdString);
                            int rarity = RarityClientData.getRarity(itemId);
                            context.getSource().sendFeedback(() -> 
                                Text.literal("物品 " + itemId + " 的稀有度为: " + rarity), false);
                        } catch (Exception e) {
                            context.getSource().sendError(Text.literal("无效的物品ID: " + itemIdString));
                        }
                        return 1;
                    })
                )
            )
            
            // 设置物品稀有度
            .then(CommandManager.literal("set")
                .then(CommandManager.argument("item", StringArgumentType.string())
                    .then(CommandManager.argument("rarity", IntegerArgumentType.integer(1, 7))
                        .executes(context -> {
                            String itemIdString = StringArgumentType.getString(context, "item");
                            int rarity = IntegerArgumentType.getInteger(context, "rarity");
                            
                            try {
                                Identifier itemId = new Identifier(itemIdString);
                                RarityRegistry.registerDatapackRarity(itemId, rarity);
                                context.getSource().sendFeedback(() -> 
                                    Text.literal("已设置物品 " + itemId + " 的稀有度为: " + rarity), true);
                                
                                // 通知所有玩家更新
                                context.getSource().getServer().getPlayerManager().getPlayerList().forEach(player -> 
                                    RarityRegistry.syncRarityToClient(player)
                                );
                                
                            } catch (Exception e) {
                                context.getSource().sendError(Text.literal("设置稀有度失败: " + e.getMessage()));
                            }
                            return 1;
                        })
                    )
                )
            )
            
            // 重载配置
            .then(CommandManager.literal("reload")
                .executes(context -> {
                    ConfigManager.initializeConfigs();
                    context.getSource().sendFeedback(() -> 
                        Text.literal("稀有度配置已重载"), true);
                    return 1;
                })
            )
            
            // 查看客户端缓存状态
            .then(CommandManager.literal("cache")
                .executes(context -> {
                    int cacheSize = RarityClientData.getCachedEntriesCount();
                    context.getSource().sendFeedback(() -> 
                        Text.literal("客户端缓存条目数: " + cacheSize), false);
                    return 1;
                })
            )
            
            // 配置管理命令
            .then(CommandManager.literal("config")
                .then(CommandManager.literal("border")
                    .then(CommandManager.argument("enable", IntegerArgumentType.integer(0, 1))
                        .executes(context -> {
                            int enable = IntegerArgumentType.getInteger(context, "enable");
                            ConfigManager.setEnableItemBorderRendering(enable == 1);
                            context.getSource().sendFeedback(() -> 
                                Text.literal("边框渲染已" + (enable == 1 ? "启用" : "禁用")), true);
                            return 1;
                        })
                    )
                )
                .then(CommandManager.literal("style")
                    .then(CommandManager.argument("style", IntegerArgumentType.integer(0, 1))
                        .executes(context -> {
                            int style = IntegerArgumentType.getInteger(context, "style");
                            ConfigManager.setItemBorderStyle(style);
                            context.getSource().sendFeedback(() -> 
                                Text.literal("边框样式已设置为: " + (style == 0 ? "空心" : "实心")), true);
                            return 1;
                        })
                    )
                )
                .then(CommandManager.literal("texture")
                    .then(CommandManager.argument("enable", IntegerArgumentType.integer(0, 1))
                        .executes(context -> {
                            int enable = IntegerArgumentType.getInteger(context, "enable");
                            ConfigManager.setUseTextureBorder(enable == 1);
                            context.getSource().sendFeedback(() -> 
                                Text.literal("纹理边框已" + (enable == 1 ? "启用" : "禁用")), true);
                            return 1;
                        })
                    )
                )
            )
            
            // 设置手上物品稀有度
            .then(CommandManager.literal("sethand")
                .then(CommandManager.argument("rarity", IntegerArgumentType.integer(1, 7))
                    .executes(context -> {
                        int rarity = IntegerArgumentType.getInteger(context, "rarity");
                        
                        // 获取执行命令的玩家手上的物品
                        ItemStack heldItem = context.getSource().getPlayer().getMainHandStack();
                        
                        if (heldItem.isEmpty()) {
                            context.getSource().sendError(Text.literal("你手上没有物品！"));
                            return 0;
                        }
                        
                        Item item = heldItem.getItem();
                        Identifier itemId = Registries.ITEM.getId(item);
                        
                        if (itemId == null || itemId.equals(Registries.ITEM.getDefaultId())) {
                            context.getSource().sendError(Text.literal("无法识别的物品！"));
                            return 0;
                        }
                        
                        try {
                            RarityRegistry.registerDatapackRarity(itemId, rarity);
                            context.getSource().sendFeedback(() -> 
                                Text.literal("已将手上物品 " + itemId + " 设置为稀有度 " + rarity), true);
                            
                            // 通知所有玩家更新
                            context.getSource().getServer().getPlayerManager().getPlayerList().forEach(player -> 
                                RarityRegistry.syncRarityToClient(player)
                            );
                            
                            return 1;
                        } catch (Exception e) {
                            context.getSource().sendError(Text.literal("设置稀有度失败: " + e.getMessage()));
                            return 0;
                        }
                    })
                )
            )
        );
        
        Raritycore.LOGGER.info("Registered RarityCore commands");
    }
}