package org.yanbwe.raritycore.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
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
    
    // 单线程调度器即可满足延迟重试需求，避免多线程资源浪费
    private static final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
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
     * 捕获 RejectedExecutionException 防止调度器已关闭时静默失败
     */
    private static void scheduleRetry(Runnable task, long delayMs) {
        try {
            retryScheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            RarityCore.LOGGER.debug("Retry scheduler is shutting down, retry task rejected");
        }
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
     * 通用的带重试包发送方法（入口）
     * 内部委托给 sendWithRetryAttempt，避免递归调用时重置重试计数器
     */
    private static <T> void sendPacketWithRetry(Object channelInstance, T packet, int maxRetries, long baseDelay) {
        sendWithRetryAttempt(channelInstance, packet, maxRetries, baseDelay, 0);
    }
    
    /**
     * 带重试计数的实际发送方法
     * @param attempt 当前尝试次数（0-based），递归调用时递增以避免无限重试
     */
    private static <T> void sendWithRetryAttempt(Object channelInstance, T packet, int maxRetries, long baseDelay, int attempt) {
        try {
            // 发送包到所有在线玩家
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    sendPacketToPlayer(channelInstance, packet, player);
                }
            }
            
            // 发送成功，记录日志并退出
            if (attempt > 0) {
                RarityCore.LOGGER.info("Packet sent successfully after {} retry attempts", attempt);
            }
            
        } catch (Exception e) {
            if (attempt + 1 < maxRetries) {
                long delay = (long) (baseDelay * Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, attempt));
                RarityCore.LOGGER.warn("Packet sending failed (attempt {}/{}), retrying in {}ms: {}", 
                    attempt + 1, maxRetries, delay, e.getMessage());
                
                // 使用ScheduledExecutorService进行延迟重试，attempt + 1 确保最终会停止
                scheduleRetry(() -> {
                    sendWithRetryAttempt(channelInstance, packet, maxRetries, baseDelay, attempt + 1);
                }, delay);
            } else {
                // 所有重试都失败了
                RarityCore.LOGGER.error("Failed to send packet after {} attempts. Last error: {}", 
                    maxRetries, e.getMessage());
            }
        }
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
        sendToPlayerWithRetryAttempt(channel, packet, player, maxRetries, baseDelay, 0);
    }
    
    /**
     * 带重试计数的向单玩家发送方法
     * @param attempt 当前尝试次数（0-based），递归调用时递增以避免无限重试
     */
    private static <T> void sendToPlayerWithRetryAttempt(Object channel, T packet, ServerPlayer player,
                                                        int maxRetries, long baseDelay, int attempt) {
        try {
            sendPacketToPlayer(channel, packet, player);
            
            if (attempt > 0) {
                RarityCore.LOGGER.info("Packet sent to player {} successfully after {} retry attempts", 
                    player.getName().getString(), attempt);
            }
            
        } catch (Exception e) {
            if (attempt + 1 < maxRetries) {
                long delay = (long) (baseDelay * Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, attempt));
                RarityCore.LOGGER.warn("Packet sending to player {} failed (attempt {}/{}), retrying in {}ms: {}", 
                    player.getName().getString(), attempt + 1, maxRetries, delay, e.getMessage());
                
                // 使用ScheduledExecutorService进行延迟重试，attempt + 1 确保最终会停止
                scheduleRetry(() -> {
                    sendToPlayerWithRetryAttempt(channel, packet, player, maxRetries, baseDelay, attempt + 1);
                }, delay);
            } else {
                RarityCore.LOGGER.error("Failed to send packet to player {} after {} attempts. Last error: {}", 
                    player.getName().getString(), maxRetries, e.getMessage());
            }
        }
    }
}