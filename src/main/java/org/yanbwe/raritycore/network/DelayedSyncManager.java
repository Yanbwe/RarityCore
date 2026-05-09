package org.yanbwe.raritycore.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DelayedSyncManager {

    private static final long SYNC_DELAY_MS = 1000;
    private static final long MAX_BATCH_WAIT_MS = 5000;

    private static ScheduledExecutorService syncExecutor;
    private static final AtomicBoolean syncScheduled = new AtomicBoolean(false);
    private static volatile long lastScheduleTime = 0;

    /**
     * 懒初始化同步执行器，避免类加载时就创建线程
     */
    private static synchronized ScheduledExecutorService getSyncExecutor() {
        if (syncExecutor == null || syncExecutor.isShutdown()) {
            syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "RarityCore-Delayed-Sync");
                t.setDaemon(true);
                return t;
            });
        }
        return syncExecutor;
    }

    private static void performDelayedSync() {
        try {
            List<ChangeOperation> pendingOps = SyncBatchManager.getAndClearPendingOperations();

            if (!pendingOps.isEmpty()) {
                List<ChangeOperation> optimizedOps = SyncBatchManager.optimizeOperations(pendingOps);

                if (!optimizedOps.isEmpty()) {
                    RarityCore.LOGGER.debug("Performing delayed sync with {} optimized operations (was {})",
                        optimizedOps.size(), pendingOps.size());

                    List<IncrementalSyncPayload.ChangeOperationData> operations = new ArrayList<>();
                    for (ChangeOperation op : optimizedOps) {
                        operations.add(IncrementalSyncPayload.ChangeOperationData.from(op));
                    }

                    IncrementalSyncPayload payload = new IncrementalSyncPayload(operations);
                    sendPayloadToAllPlayers(payload);
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error during delayed sync", e);
            syncScheduled.set(false);
        }
    }

    private static void sendPayloadToAllPlayers(IncrementalSyncPayload payload) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // 使用快照避免遍历玩家列表时的并发修改异常
            List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
            for (ServerPlayer player : players) {
                try {
                    PacketDistributor.sendToPlayer(player, payload);
                } catch (Exception e) {
                    RarityCore.LOGGER.warn("Failed to send sync payload to player {}: {}",
                        player.getName().getString(), e.getMessage());
                }
            }
        }
    }

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
        syncScheduled.set(false);
        RarityCore.LOGGER.debug("DelayedSyncManager shutdown completed");
    }

    public static boolean hasPendingOperations() {
        return SyncBatchManager.getPendingOperationCount() > 0;
    }
}