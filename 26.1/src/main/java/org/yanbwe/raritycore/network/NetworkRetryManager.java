package org.yanbwe.raritycore.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NetworkRetryManager {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_RETRY_DELAY_MS = 500;
    private static final double EXPONENTIAL_BACKOFF_MULTIPLIER = 2.0;

    /**
     * 专有调度线程池，避免使用 ForkJoinPool.commonPool() 与 Minecraft Tick 线程竞争。
     * 懒加载，支持 shutdown 后重建。
     */
    private static volatile ScheduledExecutorService retryScheduler;

    private static synchronized ScheduledExecutorService getRetryScheduler() {
        if (retryScheduler == null || retryScheduler.isShutdown()) {
            retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "RarityCore-Network-Retry");
                t.setDaemon(true);
                return t;
            });
        }
        return retryScheduler;
    }

    private static void scheduleRetry(Runnable task, long delayMs) {
        getRetryScheduler().schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    // ───── 广播级重试 ─────

    public static void sendIncrementalSyncWithRetry(IncrementalSyncPayload payload) {
        getRetryScheduler().execute(() -> {
            sendPayloadWithRetry(payload, 0);
        });
    }

    public static void sendFullSyncWithRetry(RaritySyncPayload payload) {
        getRetryScheduler().execute(() -> {
            sendPayloadWithRetry(payload, 0);
        });
    }

    /**
     * 向所有在线玩家广播 payload，带指数退避重试。
     * @param attempts 当前已尝试次数（0 表示首次，< MAX_RETRY_ATTEMPTS）
     */
    private static void sendPayloadWithRetry(CustomPacketPayload payload, int attempts) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }

            if (attempts > 0) {
                RarityCore.LOGGER.info("Payload sent successfully after {} retry attempts", attempts);
            }
        } catch (Exception e) {
            int nextAttempt = attempts + 1;
            if (nextAttempt < MAX_RETRY_ATTEMPTS) {
                long delay = (long) (BASE_RETRY_DELAY_MS * Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, attempts));
                RarityCore.LOGGER.warn("Payload sending failed (attempt {}/{}), retrying in {}ms: {}",
                    nextAttempt, MAX_RETRY_ATTEMPTS, delay, e.getMessage());

                scheduleRetry(() -> sendPayloadWithRetry(payload, nextAttempt), delay);
            } else {
                RarityCore.LOGGER.error("Failed to send payload after {} attempts. Last error: {}",
                    MAX_RETRY_ATTEMPTS, e.getMessage());
            }
        }
    }

    // ───── 单人重试 ─────

    public static void sendToPlayerWithRetry(ServerPlayer player, CustomPacketPayload payload) {
        getRetryScheduler().execute(() -> {
            sendToPlayerWithRetry(player, payload, 0);
        });
    }

    private static void sendToPlayerWithRetry(ServerPlayer player, CustomPacketPayload payload, int attempts) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
            if (attempts > 0) {
                RarityCore.LOGGER.info("Payload sent to player {} after {} retry attempts",
                    player.getName().getString(), attempts);
            }
        } catch (Exception e) {
            int nextAttempt = attempts + 1;
            if (nextAttempt < MAX_RETRY_ATTEMPTS) {
                long delay = (long) (BASE_RETRY_DELAY_MS * Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, attempts));
                RarityCore.LOGGER.warn("Payload sending to player {} failed (attempt {}/{}), retrying in {}ms: {}",
                    player.getName().getString(), nextAttempt, MAX_RETRY_ATTEMPTS, delay, e.getMessage());

                scheduleRetry(() -> sendToPlayerWithRetry(player, payload, nextAttempt), delay);
            } else {
                RarityCore.LOGGER.error("Failed to send payload to player {} after {} attempts. Last error: {}",
                    player.getName().getString(), MAX_RETRY_ATTEMPTS, e.getMessage());
            }
        }
    }

    /**
     * 关闭专用重试调度器。
     * 应在服务器停止时调用，下次使用时 getRetryScheduler() 会自动重建。
     */
    public static void shutdown() {
        if (retryScheduler != null && !retryScheduler.isShutdown()) {
            RarityCore.LOGGER.debug("Shutting down NetworkRetryManager executor");
            retryScheduler.shutdown();
            try {
                if (!retryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    RarityCore.LOGGER.warn("NetworkRetryManager executor did not terminate gracefully, forcing shutdown");
                    retryScheduler.shutdownNow();
                    if (!retryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        RarityCore.LOGGER.error("NetworkRetryManager executor could not be terminated");
                    }
                } else {
                    RarityCore.LOGGER.debug("NetworkRetryManager executor terminated gracefully");
                }
            } catch (InterruptedException e) {
                RarityCore.LOGGER.warn("Interrupted while waiting for NetworkRetryManager executor to terminate");
                retryScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        RarityCore.LOGGER.debug("NetworkRetryManager shutdown completed");
    }
}