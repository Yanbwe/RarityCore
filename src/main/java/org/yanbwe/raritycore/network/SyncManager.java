package org.yanbwe.raritycore.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 同步管理器
 * 处理稀有度数据的同步功能
 */
public class SyncManager {

    private static final List<ChangeOperation> CHANGE_OPERATIONS_BUFFER = new CopyOnWriteArrayList<>();

    public static void syncRarityToClients(java.util.Map<net.minecraft.resources.ResourceLocation, Integer> itemRarityMap) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && itemRarityMap != null) {
            // 发送前检查 Map 大小，防止因配置错误导致的无界增长发送超大包
            int mapSize = itemRarityMap.size();
            if (mapSize > NetworkConstants.MAX_RARITY_SYNC_ENTRIES) {
                RarityCore.LOGGER.warn("SyncManager.syncRarityToClients: ITEM_RARITY_MAP has {} entries, "
                    + "exceeding recommended limit of {}. Full sync packet may be oversized and cause "
                    + "network performance issues.", mapSize, NetworkConstants.MAX_RARITY_SYNC_ENTRIES);
            }
            RaritySyncPayload payload = new RaritySyncPayload(itemRarityMap);
            sendToAllPlayers(payload);
        }
    }

    public static void syncIncrementalChangesToClients() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Atomically snapshot and clear the buffer to prevent data loss:
        // operations added between iteration and clear() in the old code
        // would be permanently lost. The synchronized block ensures
        // snapshot + clear is a single atomic operation.
        final List<ChangeOperation> snapshot;
        synchronized (CHANGE_OPERATIONS_BUFFER) {
            if (CHANGE_OPERATIONS_BUFFER.isEmpty()) return;
            snapshot = new ArrayList<>(CHANGE_OPERATIONS_BUFFER);
            CHANGE_OPERATIONS_BUFFER.clear();
        }

        // Build payload from the snapshot outside the critical section
        List<IncrementalSyncPayload.ChangeOperationData> operations = new ArrayList<>();
        for (ChangeOperation op : snapshot) {
            IncrementalSyncPayload.OperationType type = switch (op.getType()) {
                case ADD -> IncrementalSyncPayload.OperationType.ADD;
                case UPDATE -> IncrementalSyncPayload.OperationType.UPDATE;
                case DELETE -> IncrementalSyncPayload.OperationType.DELETE;
            };
            operations.add(new IncrementalSyncPayload.ChangeOperationData(type, op.getItemId(), op.getRarity()));
        }

        IncrementalSyncPayload payload = new IncrementalSyncPayload(operations);
        sendToAllPlayers(payload);
    }

    private static void sendToAllPlayers(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static int getPendingChangeCount() {
        return CHANGE_OPERATIONS_BUFFER.size();
    }

    public static void clearChangeBuffer() {
        synchronized (CHANGE_OPERATIONS_BUFFER) {
            CHANGE_OPERATIONS_BUFFER.clear();
        }
    }

    public static void addChangeOperation(ChangeOperation operation) {
        synchronized (CHANGE_OPERATIONS_BUFFER) {
            CHANGE_OPERATIONS_BUFFER.add(operation);
        }
    }

    public static void syncRarityToClientsWithRetry(java.util.Map<net.minecraft.resources.ResourceLocation, Integer> itemRarityMap) {
        syncRarityToClients(itemRarityMap);
    }

    public static void syncIncrementalChangesToClientsWithRetry() {
        syncIncrementalChangesToClients();
    }
}