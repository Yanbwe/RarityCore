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

public class DelayedSyncManager {

    private static final long SYNC_DELAY_MS = 1000;
    private static final long MAX_BATCH_WAIT_MS = 5000;

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

    public static void scheduleDelayedSync() {
        long currentTime = System.currentTimeMillis();

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

    public static void forceImmediateSync() {
        if (syncScheduled) {
            syncExecutor.execute(() -> {
                performDelayedSync();
                syncScheduled = false;
            });
        }
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
                        IncrementalSyncPayload.OperationType type = switch (op.getType()) {
                            case ADD -> IncrementalSyncPayload.OperationType.ADD;
                            case UPDATE -> IncrementalSyncPayload.OperationType.UPDATE;
                            case DELETE -> IncrementalSyncPayload.OperationType.DELETE;
                        };
                        operations.add(new IncrementalSyncPayload.ChangeOperationData(type, op.getItemId(), op.getRarity()));
                    }

                    IncrementalSyncPayload payload = new IncrementalSyncPayload(operations);
                    sendPayloadToAllPlayers(payload);
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error during delayed sync", e);
            syncScheduled = false;
        }
    }

    private static void sendPayloadToAllPlayers(IncrementalSyncPayload payload) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
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
        syncScheduled = false;
        RarityCore.LOGGER.debug("DelayedSyncManager shutdown completed");
    }

    public static boolean hasPendingOperations() {
        return SyncBatchManager.getPendingOperationCount() > 0;
    }
}