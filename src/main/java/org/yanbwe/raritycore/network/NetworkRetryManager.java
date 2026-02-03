package org.yanbwe.raritycore.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;

import java.util.concurrent.CompletableFuture;

/**
 * 网络重试管理器
 * 提供可靠的网络包发送机制，包含错误处理和重试功能
 */
public class NetworkRetryManager {
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_RETRY_DELAY_MS = 500;
    private static final double EXPONENTIAL_BACKOFF_MULTIPLIER = 2.0;
    
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
     * 通用的带重试包发送方法
     */
    private static <T> void sendPacketWithRetry(Object channel, T packet, int maxRetries, long baseDelay) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < maxRetries) {
            try {
                // 发送包到所有在线玩家
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        // 这里需要根据具体通道类型进行转换
                        if (channel instanceof IncrementalSyncPacket) {
                            IncrementalSyncPacket.INSTANCE.send(
                                PacketDistributor.PLAYER.with(() -> player), 
                                (IncrementalSyncPacket) packet
                            );
                        } else if (channel instanceof RaritySyncPacket) {
                            RaritySyncPacket.INSTANCE.send(
                                PacketDistributor.PLAYER.with(() -> player), 
                                (RaritySyncPacket) packet
                            );
                        }
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
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        RarityCore.LOGGER.error("Retry thread interrupted", ie);
                        break;
                    }
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
                if (channel instanceof IncrementalSyncPacket) {
                    IncrementalSyncPacket.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player), 
                        (IncrementalSyncPacket) packet
                    );
                } else if (channel instanceof RaritySyncPacket) {
                    RaritySyncPacket.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player), 
                        (RaritySyncPacket) packet
                    );
                }
                
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
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        RarityCore.LOGGER.error("Failed to send packet to player {} after {} attempts. Last error: {}", 
            player.getName().getString(), maxRetries, lastException != null ? lastException.getMessage() : "Unknown error");
    }
}