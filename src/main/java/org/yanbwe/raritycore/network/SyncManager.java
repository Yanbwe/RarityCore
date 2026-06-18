package org.yanbwe.raritycore.network;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.List;
import java.util.Map;

public class SyncManager {

    /** 用于保护 {@link #changeOpsBuffer} 的锁，确保排空操作原子性。 */
    private static final Object BUFFER_LOCK = new Object();
    private static final List<ChangeOperation> changeOpsBuffer = new ArrayList<>();

    // ───── 同步入口 ─────

    public static void syncRarityToClients(Map<Identifier, Integer> itemRarityMap,
                                            Map<Identifier, Integer> autoRarityMap,
                                            List<TagRuleTransfer> tagRules) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && itemRarityMap != null) {
            sendToAllPlayers(new RaritySyncPayload(itemRarityMap,
                autoRarityMap != null ? autoRarityMap : Collections.emptyMap(),
                tagRules != null ? tagRules : Collections.emptyList()));
        }
    }

    /**
     * 原子排空缓冲区并发送增量同步包。
     * 使用 synchronized 保证迭代 → 清空之间不会有新操作被静默丢弃。
     * 若 server 不可用则保留缓冲区数据（下次再发）。
     */
    public static void syncIncrementalChangesToClients() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        List<ChangeOperation> snapshot;
        synchronized (BUFFER_LOCK) {
            if (changeOpsBuffer.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(changeOpsBuffer);
            changeOpsBuffer.clear();
        }

        sendIncrementalPayload(snapshot);
    }

    // ───── 带重试的同步入口 ─────

    public static void syncRarityToClientsWithRetry(Map<Identifier, Integer> itemRarityMap,
                                                      Map<Identifier, Integer> autoRarityMap,
                                                      List<TagRuleTransfer> tagRules) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && itemRarityMap != null) {
            NetworkRetryManager.sendFullSyncWithRetry(new RaritySyncPayload(itemRarityMap,
                autoRarityMap != null ? autoRarityMap : Collections.emptyMap(),
                tagRules != null ? tagRules : Collections.emptyList()));
        }
    }

    public static void syncIncrementalChangesToClientsWithRetry() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        List<ChangeOperation> snapshot;
        synchronized (BUFFER_LOCK) {
            if (changeOpsBuffer.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(changeOpsBuffer);
            changeOpsBuffer.clear();
        }

        List<IncrementalSyncPayload.ChangeOperationData> ops = convertToChangeData(snapshot);
        NetworkRetryManager.sendIncrementalSyncWithRetry(new IncrementalSyncPayload(ops));
    }

    // ───── 缓冲区操作 ─────

    public static void addChangeOperation(ChangeOperation operation) {
        synchronized (BUFFER_LOCK) {
            changeOpsBuffer.add(operation);
        }
    }

    public static void clearChangeBuffer() {
        synchronized (BUFFER_LOCK) {
            changeOpsBuffer.clear();
        }
    }

    public static int getPendingChangeCount() {
        synchronized (BUFFER_LOCK) {
            return changeOpsBuffer.size();
        }
    }

    // ───── 内部工具 ─────

    private static void sendToAllPlayers(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.getPlayerList() != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static void sendIncrementalPayload(List<ChangeOperation> ops) {
        List<IncrementalSyncPayload.ChangeOperationData> data = convertToChangeData(ops);
        sendToAllPlayers(new IncrementalSyncPayload(data));
    }

    private static List<IncrementalSyncPayload.ChangeOperationData> convertToChangeData(List<ChangeOperation> ops) {
        List<IncrementalSyncPayload.ChangeOperationData> data = new ArrayList<>();
        for (ChangeOperation op : ops) {
            IncrementalSyncPayload.OperationType type = switch (op.getType()) {
                case ADD -> IncrementalSyncPayload.OperationType.ADD;
                case UPDATE -> IncrementalSyncPayload.OperationType.UPDATE;
                case DELETE -> IncrementalSyncPayload.OperationType.DELETE;
            };
            data.add(new IncrementalSyncPayload.ChangeOperationData(type, op.getItemId(), op.getRarity()));
        }
        return data;
    }
}