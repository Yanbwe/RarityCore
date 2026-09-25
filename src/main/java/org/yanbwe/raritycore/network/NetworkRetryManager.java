package org.yanbwe.raritycore.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
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
     *
     * <p>历史缺陷：本方法曾用 {@code channel instanceof IncrementalSyncPacket} 之类的判断来
     * 分派发送，但形参实为 {@code IncrementalSyncPacket.INSTANCE} / {@code RaritySyncPacket.INSTANCE}，
     * 其声明类型是 {@link SimpleChannel}——与包类没有任何继承关系，两个 instanceof 恒为假，
     * 于是本方法恒返回 false（发送从未发生）。而调用方忽略返回值、成功日志又只在 attempt>0 时
     * 打印，因此失败完全静默：{@code /raritycore reload} 之后的全量同步从未真正下发给客户端。
     * 现在直接用形参 channel 发送，不再做无意义的类型判断。</p>
     *
     * @param channel 已注册的网络通道（如 {@link IncrementalSyncPacket#INSTANCE}）
     * @param packet  要发送的数据包
     * @param player  目标玩家
     */
    private static <T> boolean sendPacketToPlayer(SimpleChannel channel, T packet, ServerPlayer player) {
        if (channel == null || packet == null || player == null) {
            return false;
        }
        try {
            channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
            return true;
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to send {} to player {}", packet.getClass().getSimpleName(),
                player.getName().getString(), e);
            return false;
        }
    }
    
    /**
     * 通用的带重试包发送方法（入口）
     * 内部委托给 sendWithRetryAttempt，避免递归调用时重置重试计数器
     */
    private static <T> void sendPacketWithRetry(SimpleChannel channel, T packet, int maxRetries, long baseDelay) {
        sendWithRetryAttempt(channel, packet, maxRetries, baseDelay, 0);
    }
    
    /**
     * 带重试计数的实际发送方法
     *
     * <p>判定成功的依据是 sendPacketToPlayer 的返回值，而不是"没有抛异常"——
     * 历史缺陷正是忽略了返回值，导致一次都没发出去也照样记录成功日志。</p>
     *
     * @param attempt 当前尝试次数（0-based），递归调用时递增以避免无限重试
     */
    private static <T> void sendWithRetryAttempt(SimpleChannel channel, T packet, int maxRetries, long baseDelay, int attempt) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            // 服务端已停止，重试没有意义
            return;
        }

        java.util.List<ServerPlayer> failed = new java.util.ArrayList<>();
        java.util.List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            if (!sendPacketToPlayer(channel, packet, player)) {
                failed.add(player);
            }
        }

        if (failed.isEmpty()) {
            // 全部成功（无在线玩家时也视为成功）
            if (attempt > 0) {
                RarityCore.LOGGER.info("Packet sent successfully after {} retry attempts", attempt);
            }
            return;
        }

        // 部分玩家失败：只对失败者重试，避免给已成功的玩家重复发包
        if (attempt + 1 < maxRetries) {
            long delay = (long) (baseDelay * Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, attempt));
            RarityCore.LOGGER.warn("Packet send failed for {}/{} player(s) (attempt {}/{}), retrying in {}ms",
                failed.size(), players.size(), attempt + 1, maxRetries, delay);
            scheduleRetry(() -> {
                for (ServerPlayer player : failed) {
                    sendToPlayerWithRetryAttempt(channel, packet, player, maxRetries, baseDelay, attempt + 1);
                }
            }, delay);
        } else {
            RarityCore.LOGGER.error("Failed to send {} to {} player(s) after {} attempts",
                packet.getClass().getSimpleName(), failed.size(), maxRetries);
        }
    }
    
    /**
     * 发送包到特定玩家（带重试）
     */
    public static <T> void sendToPlayerWithRetry(SimpleChannel channel, T packet, ServerPlayer player) {
        CompletableFuture.runAsync(() -> {
            sendToPlayerWithRetryInternal(channel, packet, player, MAX_RETRY_ATTEMPTS, BASE_RETRY_DELAY_MS);
        });
    }
    
    private static <T> void sendToPlayerWithRetryInternal(SimpleChannel channel, T packet, ServerPlayer player,
                                                         int maxRetries, long baseDelay) {
        sendToPlayerWithRetryAttempt(channel, packet, player, maxRetries, baseDelay, 0);
    }
    
    /**
     * 带重试计数的向单玩家发送方法
     *
     * <p>与 {@link #sendWithRetryAttempt} 同理：以 sendPacketToPlayer 的返回值判定成功，
     * 失败时按指数退避重试，不再依赖"是否抛异常"。</p>
     *
     * @param attempt 当前尝试次数（0-based），递归调用时递增以避免无限重试
     */
    private static <T> void sendToPlayerWithRetryAttempt(SimpleChannel channel, T packet, ServerPlayer player,
                                                        int maxRetries, long baseDelay, int attempt) {
        if (sendPacketToPlayer(channel, packet, player)) {
            if (attempt > 0) {
                RarityCore.LOGGER.info("Packet sent to player {} successfully after {} retry attempts",
                    player.getName().getString(), attempt);
            }
            return;
        }

        if (attempt + 1 < maxRetries) {
            long delay = (long) (baseDelay * Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, attempt));
            RarityCore.LOGGER.warn("Packet sending to player {} failed (attempt {}/{}), retrying in {}ms",
                player.getName().getString(), attempt + 1, maxRetries, delay);
            scheduleRetry(() -> {
                sendToPlayerWithRetryAttempt(channel, packet, player, maxRetries, baseDelay, attempt + 1);
            }, delay);
        } else {
            RarityCore.LOGGER.error("Failed to send {} to player {} after {} attempts",
                packet.getClass().getSimpleName(), player.getName().getString(), maxRetries);
        }
    }
}