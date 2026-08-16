package org.yanbwe.raritycore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.edit.EditModeManager;
import org.yanbwe.raritycore.edit.EditModeManager.EditMode;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.util.RarityConstants;

import java.util.Set;

/**
 * 工具命令类
 * 处理编辑模式、性能监控、纹理边框等辅助功能命令。
 *
 * <p>编辑模式命令体系（Ver.13 重构）：
 * <ul>
 *   <li>/raritycore edit toggle &lt;true|false&gt;</li>
 *   <li>/raritycore edit mode &lt;normal|fullmatch&gt;</li>
 *   <li>/raritycore edit parameter &lt;rarity|autoReload|ignore|stringContains&gt; &lt;value&gt;</li>
 *   <li>/raritycore edit status</li>
 * </ul>
 */
public class UtilityCommands {

    /** 允许的编辑参数键集合 */
    private static final Set<String> ALLOWED_PARAM_KEYS = Set.of(
        "rarity", "autoReload", "ignore", "stringContains"
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raritycore")
            .requires(Commands.hasPermission(Commands.LEVEL_MODERATORS))
            // ============ edit 子命令 ============
            .then(Commands.literal("edit")
                // toggle <true|false>
                .then(Commands.literal("toggle")
                    .then(Commands.argument("state", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("true");
                            builder.suggest("false");
                            return builder.buildFuture();
                        })
                        .executes(context -> handleEditToggle(
                            context.getSource(),
                            StringArgumentType.getString(context, "state")
                        ))
                    )
                )
                // mode <normal|fullmatch>
                .then(Commands.literal("mode")
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("normal");
                            builder.suggest("fullmatch");
                            return builder.buildFuture();
                        })
                        .executes(context -> handleEditModeSet(
                            context.getSource(),
                            StringArgumentType.getString(context, "mode")
                        ))
                    )
                )
                // parameter <key> <value>
                .then(Commands.literal("parameter")
                    .then(Commands.argument("key", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (String key : ALLOWED_PARAM_KEYS) {
                                builder.suggest(key);
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(context -> handleEditParameter(
                                context.getSource(),
                                StringArgumentType.getString(context, "key"),
                                StringArgumentType.getString(context, "value")
                            ))
                        )
                    )
                )
                // status
                .then(Commands.literal("status")
                    .executes(context -> handleEditStatus(context.getSource()))
                )
            )
            // ============ perf 子命令（保持原有） ============
            .then(Commands.literal("perf")
                .then(Commands.literal("stats")
                    .executes(context -> showPerformanceStats(context.getSource()))
                )
                .then(Commands.literal("optimize")
                    .executes(context -> triggerManualOptimization(context.getSource()))
                )
            )
        );

        // 注册客户端命令（保持原有）
        dispatcher.register(Commands.literal("raritycore-client")
            .then(Commands.literal("texture")
                .then(Commands.literal("toggle")
                    .executes(context -> toggleTextureBorder(context.getSource()))
                )
            )
        );
    }

    // ============ edit 子命令处理 ============

    /**
     * 处理 /raritycore edit toggle &lt;true|false&gt;
     * 使用字符串参数，调用 {@link EditModeManager#setEditMode(boolean)} 显式设置编辑模式开关。
     *
     * @param source   命令源
     * @param stateArg 字符串参数，必须为 "true" 或 "false"
     * @return 1 成功，0 失败
     */
    private static int handleEditToggle(CommandSourceStack source, String stateArg) {
        if (!stateArg.equalsIgnoreCase("true") && !stateArg.equalsIgnoreCase("false")) {
            source.sendFailure(Component.literal("Invalid argument: must be 'true' or 'false'")
                .withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean state = Boolean.parseBoolean(stateArg);
        EditModeManager.setEditMode(state);

        if (state) {
            source.sendSuccess(() -> Component.literal("Edit mode enabled")
                .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("Edit mode disabled")
                .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    /**
     * 处理 /raritycore edit mode &lt;normal|fullmatch&gt;
     * 解析字符串参数为 {@link EditMode} 枚举，验证后调用 {@link EditModeManager#setEditMode(EditMode)}。
     *
     * @param source  命令源
     * @param modeArg 字符串参数，必须为 "normal" 或 "fullmatch"
     * @return 1 成功，0 失败
     */
    private static int handleEditModeSet(CommandSourceStack source, String modeArg) {
        EditMode mode;
        if ("normal".equalsIgnoreCase(modeArg)) {
            mode = EditMode.NORMAL;
        } else if ("fullmatch".equalsIgnoreCase(modeArg)) {
            mode = EditMode.FULLMATCH;
        } else {
            source.sendFailure(Component.literal("Invalid mode: must be 'normal' or 'fullmatch'")
                .withStyle(ChatFormatting.RED));
            return 0;
        }

        EditModeManager.setEditMode(mode);
        source.sendSuccess(() -> Component.literal("Edit mode set to: " + mode.name())
            .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * 处理 /raritycore edit parameter &lt;key&gt; &lt;value&gt;
     * 验证 key 在允许的集合内（rarity, autoReload, ignore, stringContains），
     * 通过后调用 {@link EditModeManager#setParameter(String, String)}。
     *
     * @param source 命令源
     * @param key    参数名
     * @param value  参数值
     * @return 1 成功，0 失败
     */
    private static int handleEditParameter(CommandSourceStack source, String key, String value) {
        if (!ALLOWED_PARAM_KEYS.contains(key)) {
            source.sendFailure(Component.literal(
                "Invalid parameter key: '" + key + "'. Allowed: rarity, autoReload, ignore, stringContains")
                .withStyle(ChatFormatting.RED));
            return 0;
        }

        EditModeManager.setParameter(key, value);
        source.sendSuccess(() -> Component.literal("Parameter '" + key + "' set to: " + value)
            .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * 处理 /raritycore edit status
     * 显示编辑模式的完整状态：开关状态、模式类型、当前稀有度、所有参数。
     *
     * @param source 命令源
     * @return 1 成功
     */
    private static int handleEditStatus(CommandSourceStack source) {
        boolean enabled = EditModeManager.isEditModeEnabled();
        EditMode mode = EditModeManager.getEditMode();
        int rarity = EditModeManager.getCurrentRarity();
        java.util.Map<String, String> params = EditModeManager.getAllParameters();

        source.sendSuccess(() -> Component.literal("=== Edit Mode Status ===")
            .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Enabled: " + enabled)
            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.literal("Mode: " + mode.name())
            .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("Current Rarity: " + rarity)
            .withStyle(ChatFormatting.YELLOW), false);

        if (params.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Parameters: (none)")
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("Parameters:")
                .withStyle(ChatFormatting.WHITE), false);
            for (java.util.Map.Entry<String, String> entry : params.entrySet()) {
                source.sendSuccess(() -> Component.literal("  " + entry.getKey() + " = " + entry.getValue())
                    .withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }

    // ============ perf 子命令（保持原有） ============

    /**
     * 显示性能统计信息
     */
    private static int showPerformanceStats(CommandSourceStack source) {
        // 获取各种性能指标
        org.yanbwe.raritycore.cache.RenderCacheManager.CacheStats cacheStats =
            org.yanbwe.raritycore.cache.RenderCacheManager.getCacheStats();

        int pendingChanges = SyncManager.getPendingChangeCount();
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
        org.yanbwe.raritycore.cache.RenderCacheManager.clearAllCache();

        // 重新加载配置
        org.yanbwe.raritycore.config.FinalRarityConfigFolderLoader.loadFinalRarityConfigFolder();
        org.yanbwe.raritycore.config.RarityConfigLoader.loadConfigRarityData();

        source.sendSuccess(() -> Component.translatable("rarity.core.optimization_completed").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // ============ 客户端命令（保持原有） ============

    /**
     * 切换纹理边框启用状态
     */
    private static int toggleTextureBorder(CommandSourceStack source) {
        try {
            boolean currentState = RarityStyleConfigManager.isBorderUseTexture(RarityConstants.MIN_RARITY);
            boolean newState = !currentState;
            RarityStyleConfigManager.setAllBorderUseTexture(newState);

            source.sendSuccess(() -> Component.translatable("rarity.core.texture_border_toggle_success",
                newState ? Component.translatable("rarity.core.enabled") : Component.translatable("rarity.core.disabled")).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to toggle texture border", e);
            source.sendSuccess(() -> Component.translatable("rarity.core.texture_border_toggle_error").withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }
}
