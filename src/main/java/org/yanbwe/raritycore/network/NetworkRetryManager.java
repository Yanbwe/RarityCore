package org.yanbwe.raritycore.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NetworkRetryManager {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_RETRY_DELAY_MS = 500;
    private static final double EXPONENTIAL_BACKOFF_MULTIPLIER = 2.0;

    private static final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(
        Runtime.getRuntime().availableProcessors() / 2 + 1, r -> {
            Thread t = new Thread(r, "RarityCore-Network-Retry");
            t.setDaemon(true);
            return t;
        }
    );

    private static void scheduleRetry(Runnable task, long delayMs) {
        retryScheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    public static void sendIncrementalSyncWithRetry(IncrementalSyncPayload payload) {
        CompletableFuture.runAsync(() -> {
            sendPayloadWithRetry(payload, MAX_RETRY_ATTEMPTS, BASE_RETRY_DELAY_MS);
        });
    }

    public static void sendFullSyncWithRetry(RaritySyncPayload payload) {
        CompletableFuture.runAsync(() -> {
            sendPayloadWithRetry(payload, MAX_RETRY_ATTEMPTS, BASE_RETRY_DELAY_MS);
        });
    }

    /**
     * 向所有玩家发送载荷，并为每个玩家独立追踪重试状态。
     * 避免原逻辑中"任一玩家失败则全部重发"的重复包问题。
     */
    private static void sendPayloadWithRetry(CustomPacketPayload payload, int maxRetries, long baseDelay) {
        if (maxRetries <= 0) {
            RarityCore.LOGGER.error("Payload retry exhausted before start: {}", payload);
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            RarityCore.LOGGER.warn("Cannot retry payload: no server instance");
            return;
        }

        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        for (ServerPlayer player : players) {
            sendToPlayerWithRetryInternal(player, payload, maxRetries, baseDelay);
        }
    }

    public static void sendToPlayerWithRetry(ServerPlayer player, CustomPacketPayload payload) {
        CompletableFuture.runAsync(() -> {
            sendToPlayerWithRetryInternal(player, payload, MAX_RETRY_ATTEMPTS, BASE_RETRY_DELAY_MS);
        });
    }

    private static void sendToPlayerWithRetryInternal(ServerPlayer player, CustomPacketPayload payload,
                                                       int maxRetries, long baseDelay) {
        if (maxRetries <= 0) {
            RarityCore.LOGGER.error("Player payload retry exhausted before start: {}", payload);
            return;
        }

        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            try {
                PacketDistributor.sendToPlayer(player, payload);
                return;

            } catch (Exception e) {
                lastException = e;
                attempts++;

                if (attempts < maxRetries) {
                    long delay = (long) (baseDelay * Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, attempts - 1));
                    RarityCore.LOGGER.warn("Payload sending to player {} failed (attempt {}/{}), retrying in {}ms: {}",
                        player.getName().getString(), attempts, maxRetries, delay, e.getMessage());

                    final int remainingRetries = maxRetries - attempts;
                    scheduleRetry(() -> {
                        sendToPlayerWithRetryInternal(player, payload, remainingRetries, baseDelay);
                    }, delay);
                    return;
                }
            }
        }

        RarityCore.LOGGER.error("Failed to send payload to player {} after {} attempts. Last error: {}",
            player.getName().getString(), maxRetries, lastException != null ? lastException.getMessage() : "Unknown error");
    }

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