package org.yanbwe.raritycore.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 延迟同步管理器
 * 通过延迟和批量处理减少频繁的网络传输
 */
public class DelayedSyncManager {
    
    private static final long SYNC_DELAY_MS = 1000; // 1秒延迟
    private static final long MAX_BATCH_WAIT_MS = 5000; // 最大等待5秒
    
    private static ScheduledExecutorService syncExecutor;
    private static volatile boolean syncScheduled = false;
    private static volatile long lastScheduleTime = 0;
    
    static {
        syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RarityCore-Delayed-Sync");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 调度延迟同步
     * 如果已经有同步计划且在合理时间内，则不重复调度
     */
    public static void scheduleDelayedSync() {
        long currentTime = System.currentTimeMillis();
        
        // 如果已经安排了同步且时间间隔合理，就不重复安排
        if (syncScheduled && (currentTime - lastScheduleTime) < (MAX_BATCH_WAIT_MS / 2)) {
            return;
        }
        
        syncScheduled = true;
        lastScheduleTime = currentTime;
        
        syncExecutor.schedule(() -> {
            performDelayedSync();
            syncScheduled = false;
        }, SYNC_DELAY_MS, TimeUnit.MILLISECONDS);
        
        RarityCore.LOGGER.debug("Scheduled delayed sync in {}ms", SYNC_DELAY_MS);
    }
    
    /**
     * 立即执行延迟同步（强制执行）
     */
    public static void forceImmediateSync() {
        syncExecutor.execute(() -> {
            performDelayedSync();
            syncScheduled = false;
        });
    }
    
    /**
     * 强制刷新所有待处理操作
     * 供 SchedulerService 定时器等外部调用者使用。
     * 与 forceImmediateSync 不同，此方法不检查 syncScheduled 状态，
     * 始终尝试刷新缓冲区内积累的操作，并将 syncScheduled 重置为 false。
     */
    public static void flushPendingOperations() {
        performDelayedSync();
        syncScheduled = false;
    }
    
    /**
     * 执行延迟同步的具体逻辑
     */
    private static void performDelayedSync() {
        try {
            // 获取待处理的操作
            List<ChangeOperation> pendingOps = SyncBatchManager.getAndClearPendingOperations();
            
            if (!pendingOps.isEmpty()) {
                // 处理操作列表
                List<ChangeOperation> optimizedOps = SyncBatchManager.optimizeOperations(pendingOps);
                
                if (!optimizedOps.isEmpty()) {
                    RarityCore.LOGGER.debug("Performing delayed sync with {} optimized operations (was {})", 
                        optimizedOps.size(), pendingOps.size());
                    
                    // 创建并发送增量同步包
                    IncrementalSyncPacket packet = new IncrementalSyncPacket(optimizedOps);
                    sendPacketToAllPlayers(packet);
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error during delayed sync", e);
            // 出错时清除同步标志，允许下次调度
            syncScheduled = false;
        }
    }
    
    /**
     * 发送包到所有在线玩家
     */
    private static void sendPacketToAllPlayers(IncrementalSyncPacket packet) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    IncrementalSyncPacket.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player), 
                        packet
                    );
                } catch (Exception e) {
                    RarityCore.LOGGER.warn("Failed to send sync packet to player {}: {}", 
                        player.getName().getString(), e.getMessage());
                }
            }
        }
    }
    
    /**
     * 关闭同步管理器
     */
    public static void shutdown() {
        if (syncExecutor != null && !syncExecutor.isShutdown()) {
            RarityCore.LOGGER.debug("Shutting down DelayedSyncManager executor");
            syncExecutor.shutdown();
            try {
                if (!syncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    RarityCore.LOGGER.warn("DelayedSyncManager executor did not terminate gracefully, forcing shutdown");
                    syncExecutor.shutdownNow();
                    if (!syncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        RarityCore.LOGGER.error("DelayedSyncManager executor could not be terminated");
                    }
                } else {
                    RarityCore.LOGGER.debug("DelayedSyncManager executor terminated gracefully");
                }
            } catch (InterruptedException e) {
                RarityCore.LOGGER.warn("Interrupted while waiting for DelayedSyncManager executor to terminate");
                syncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        syncScheduled = false;
        RarityCore.LOGGER.debug("DelayedSyncManager shutdown completed");
    }
    
    /**
     * 检查是否有待处理的同步操作
     */
    public static boolean hasPendingOperations() {
        return SyncBatchManager.getPendingOperationCount() > 0;
    }
    
    /**
     * 获取当前同步状态信息
     */
    public static SyncStatus getStatus() {
        return new SyncStatus(
            syncScheduled,
            SyncBatchManager.getPendingOperationCount(),
            System.currentTimeMillis() - lastScheduleTime
        );
    }
    
    /**
     * 同步状态信息类
     */
    public static class SyncStatus {
        private final boolean scheduled;
        private final int pendingOperations;
        private final long timeSinceLastSchedule;
        
        public SyncStatus(boolean scheduled, int pendingOperations, long timeSinceLastSchedule) {
            this.scheduled = scheduled;
            this.pendingOperations = pendingOperations;
            this.timeSinceLastSchedule = timeSinceLastSchedule;
        }
        
        public boolean isScheduled() { return scheduled; }
        public int getPendingOperations() { return pendingOperations; }
        public long getTimeSinceLastSchedule() { return timeSinceLastSchedule; }
        
        @Override
        public String toString() {
            return String.format("SyncStatus{scheduled=%s, pending=%d, sinceLast=%dms}",
                scheduled, pendingOperations, timeSinceLastSchedule);
        }
    }
}