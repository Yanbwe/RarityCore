package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.edit.EditModeManager;

/**
 * 编辑模式命令类
 * {@code /raritycore edit toggle <true|false>}
 * {@code /raritycore edit mode <normal|fullmatch>}
 * {@code /raritycore edit parameter <rarity|autoReload|ignore> <value>}
 */
public class EditCommands {

    @SuppressWarnings("null")
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("edit")
                // /raritycore edit toggle <true|false>
                .then(Commands.literal("toggle")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                            EditModeManager.setEditMode(enabled);
                            ctx.getSource().sendSuccess(() ->
                                Component.translatable("rarity.core.edit_toggle", enabled)
                                    .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED),
                                false);
                            return 1;
                        })
                    )
                )
                // /raritycore edit mode <normal|fullmatch>
                .then(Commands.literal("mode")
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("normal");
                            builder.suggest("fullmatch");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String modeStr = StringArgumentType.getString(ctx, "mode").toLowerCase();
                            if ("fullmatch".equals(modeStr)) {
                                EditModeManager.setMode(EditModeManager.EditMode.FULLMATCH);
                            } else {
                                EditModeManager.setMode(EditModeManager.EditMode.NORMAL);
                            }
                            ctx.getSource().sendSuccess(() ->
                                Component.translatable("rarity.core.edit_mode", modeStr)
                                    .withStyle(ChatFormatting.GREEN),
                                false);
                            return 1;
                        })
                    )
                )
                // /raritycore edit parameter <rarity|autoReload|ignore> <value>
                .then(Commands.literal("parameter")
                    .then(Commands.literal("rarity")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                            .executes(ctx -> {
                                int rarity = IntegerArgumentType.getInteger(ctx, "value");
                                EditModeManager.setRarity(rarity);
                                ctx.getSource().sendSuccess(() ->
                                    Component.translatable("rarity.core.edit_param_rarity", rarity)
                                        .withStyle(ChatFormatting.GREEN),
                                    false);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("autoReload")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> {
                                boolean value = BoolArgumentType.getBool(ctx, "value");
                                EditModeManager.setAutoReload(value);
                                ctx.getSource().sendSuccess(() ->
                                    Component.translatable("rarity.core.edit_param_autoReload", value)
                                        .withStyle(ChatFormatting.GREEN),
                                    false);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("stringContains")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> {
                                boolean value = BoolArgumentType.getBool(ctx, "value");
                                EditModeManager.setStringContains(value);
                                ctx.getSource().sendSuccess(() ->
                                    Component.translatable("rarity.core.edit_param_stringContains", value)
                                        .withStyle(ChatFormatting.GREEN),
                                    false);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("ignore")
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String value = StringArgumentType.getString(ctx, "value");
                                EditModeManager.setIgnoreTags(value);
                                ctx.getSource().sendSuccess(() ->
                                    Component.translatable("rarity.core.edit_param_ignore", value)
                                        .withStyle(ChatFormatting.GREEN),
                                    false);
                                return 1;
                            })
                        )
                    )
                )
            )
        );
    }
}
