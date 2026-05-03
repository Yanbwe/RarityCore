package org.yanbwe.raritycore.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 网络重试管理器
 * 提供可靠的网络包发送机制，包含错误处理和重试功能
 */
public class NetworkRetryManager {
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_RETRY_DELAY_MS = 500;
    private static final double EXPONENTIAL_BACKOFF_MULTIPLIER = 2.0;
    
    // 用于延迟重试的调度器
    private static final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(
        Runtime.getRuntime().availableProcessors() / 2 + 1, r -> {
            Thread t = new Thread(r, "RarityCore-Network-Retry");
            t.setDaemon(true);
            return t;
        }
    );

    /**
     * 关闭重试调度器，释放线程资源
     * 在服务器停止时调用，确保 JVM 能干净退出
     */
    public static void shutdown() {
        retryScheduler.shutdown();
        try {
            if (!retryScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                retryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 调度延迟重试任务
     */
    private static void scheduleRetry(Runnable task, long delayMs) {
        retryScheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 带重试机制的增量同步包发送
     */
    public static void sendIncrementalSyncWithRetry(IncrementalSyncPacket packet) {
        CompletableFuture.runAsync(() -> {
            sendPacketWithRetry(
                IncrementalSyncPacket.INSTANCE,
                packet,
                MAX_RETRY_ATTEMPTS,
                BASE_RETRY_DELAY_MS
            );
        });
    }
    
    /**
     * 带重试机制的全量同步包发送
     */
    public static void sendFullSyncWithRetry(RaritySyncPacket packet) {
        CompletableFuture.runAsync(() -> {
            sendPacketWithRetry(
                RaritySyncPacket.INSTANCE,
                packet,
                MAX_RETRY_ATTEMPTS,
                BASE_RETRY_DELAY_MS
            );
        });
    }
    
    /**
     * 发送数据包到单个玩家
     */
    private static <T> boolean sendPacketToPlayer(Object channel, T packet, ServerPlayer player) {
        if (channel instanceof IncrementalSyncPacket && packet instanceof IncrementalSyncPacket) {
            IncrementalSyncPacket.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player), 
                (IncrementalSyncPacket) packet
            );
            return true;
        } else if (channel instanceof RaritySyncPacket && packet instanceof RaritySyncPacket) {
            RaritySyncPacket.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player), 
                (RaritySyncPacket) packet
            );
            return true;
        }
        return false;
    }
    
    /**
     * 通用的带重试包发送方法
     */
    private static <T> void sendPacketWithRetry(Object channelInstance, T packet, int maxRetries, long baseDelay) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < maxRetries) {
            try {
                // 发送包到所有在线玩家
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        sendPacketToPlayer(channelInstance, packet, player);
                    }
                }
                
                // 发送成功，记录日志并退出
                if (attempts > 0) {
                    RarityCore.LOGGER.info("Packet sent successfully after {} retry attempts", attempts);
                }
                return;
                
            } catch (Exception e) {
                lastException = e;
                attempts++;
                
                if (attempts < maxRetries) {
                    long delay = (long) (baseDelay * Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, attempts - 1));
                    RarityCore.LOGGER.warn("Packet sending failed (attempt {}/{}), retrying in {}ms: {}", 
                        attempts, maxRetries, delay, e.getMessage());
                    
                    // 使用ScheduledExecutorService进行延迟重试
                    scheduleRetry(() -> {
                        sendPacketWithRetry(channelInstance, packet, maxRetries, baseDelay);
                    }, delay);
                    return;
                }
            }
        }
        
        // 所有重试都失败了
        RarityCore.LOGGER.error("Failed to send packet after {} attempts. Last error: {}", 
            maxRetries, lastException != null ? lastException.getMessage() : "Unknown error");
    }
    
    /**
     * 发送包到特定玩家（带重试）
     */
    public static <T> void sendToPlayerWithRetry(Object channel, T packet, ServerPlayer player) {
        CompletableFuture.runAsync(() -> {
            sendToPlayerWithRetryInternal(channel, packet, player, MAX_RETRY_ATTEMPTS, BASE_RETRY_DELAY_MS);
        });
    }
    
    private static <T> void sendToPlayerWithRetryInternal(Object channel, T packet, ServerPlayer player, 
                                                         int maxRetries, long baseDelay) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < maxRetries) {
            try {
                sendPacketToPlayer(channel, packet, player);
                
                if (attempts > 0) {
                    RarityCore.LOGGER.info("Packet sent to player {} successfully after {} retry attempts", 
                        player.getName().getString(), attempts);
                }
                return;
                
            } catch (Exception e) {
                lastException = e;
                attempts++;
                
                if (attempts < maxRetries) {
                    long delay = (long) (baseDelay * Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, attempts - 1));
                    RarityCore.LOGGER.warn("Packet sending to player {} failed (attempt {}/{}), retrying in {}ms: {}", 
                        player.getName().getString(), attempts, maxRetries, delay, e.getMessage());
                    
                    // 使用ScheduledExecutorService进行延迟重试
                    scheduleRetry(() -> {
                        sendToPlayerWithRetryInternal(channel, packet, player, maxRetries, baseDelay);
                    }, delay);
                    return;
                }
            }
        }
        
        RarityCore.LOGGER.error("Failed to send packet to player {} after {} attempts. Last error: {}", 
            player.getName().getString(), maxRetries, lastException != null ? lastException.getMessage() : "Unknown error");
    }
}