package org.yanbwe.raritycore.calc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.service.ConfigReloadService;

/**
 * 延迟重载调度器 — 自动计算完成后安排两次配置重载
 * 使用 Minecraft 服务器的 tickable 机制实现延迟执行
 */
public class ReloadScheduler {

    /**
     * 安排自动重载(10 秒后执行第一次,1 秒后执行第二次)
     */
    static void scheduleAutoReload() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        // 使用服务器 tick 调度器实现延迟执行
        final int[] delayTicks = {200}; // 10 秒 = 200 tick
        final boolean[] executed = {false}; // 标记是否已执行

        server.addTickable(new Runnable() {
            @Override
            public void run() {
                if (executed[0]) {
                    return;
                }

                if (delayTicks[0] > 0) {
                    delayTicks[0]--;
                    return;
                }

                executed[0] = true;

                try {
                    // 第一次重载
                    ConfigReloadService.reloadFromCommand(null);
                    RarityCore.LOGGER.info("First auto reload completed");

                    // 短暂延迟后执行第二次重载(确保所有配置完全应用)
                    final int[] innerDelay = {20}; // 1 秒 = 20 tick
                    final boolean[] innerExecuted = {false};

                    server.addTickable(new Runnable() {
                        @Override
                        public void run() {
                            if (innerExecuted[0]) {
                                return;
                            }

                            if (innerDelay[0] > 0) {
                                innerDelay[0]--;
                                return;
                            }

                            innerExecuted[0] = true;

                            try {
                                // 第二次重载
                                ConfigReloadService.reloadFromCommand(null);
                                RarityCore.LOGGER.info("Second auto reload completed");

                                // 发送完成提示
                                AutoRarityCalculator.sendToAllPlayers(
                                    Component.translatable("rarity.core.auto_reload_complete_message")
                                        .withStyle(ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD));

                            } catch (Exception e) {
                                RarityCore.LOGGER.error("Error during second auto reload", e);
                            }
                        }
                    });

                } catch (Exception e) {
                    RarityCore.LOGGER.error("Error during first auto reload", e);
                }
            }
        });
    }
}
