package org.yanbwe.raritycore.network;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SyncManager {

    private static final List<ChangeOperation> CHANGE_OPERATIONS_BUFFER = new CopyOnWriteArrayList<>();

    public static void syncRarityToClients(java.util.Map<Identifier, Integer> itemRarityMap) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && itemRarityMap != null) {
            RaritySyncPayload payload = new RaritySyncPayload(itemRarityMap);
            sendToAllPlayers(payload);
        }
    }

    public static void syncIncrementalChangesToClients() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !CHANGE_OPERATIONS_BUFFER.isEmpty()) {
            List<IncrementalSyncPayload.ChangeOperationData> operations = new ArrayList<>();
            for (ChangeOperation op : CHANGE_OPERATIONS_BUFFER) {
                IncrementalSyncPayload.OperationType type = switch (op.getType()) {
                    case ADD -> IncrementalSyncPayload.OperationType.ADD;
                    case UPDATE -> IncrementalSyncPayload.OperationType.UPDATE;
                    case DELETE -> IncrementalSyncPayload.OperationType.DELETE;
                };
                operations.add(new IncrementalSyncPayload.ChangeOperationData(type, op.getItemId(), op.getRarity()));
            }
            CHANGE_OPERATIONS_BUFFER.clear();

            IncrementalSyncPayload payload = new IncrementalSyncPayload(operations);
            sendToAllPlayers(payload);
        }
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
        CHANGE_OPERATIONS_BUFFER.clear();
    }

    public static void addChangeOperation(ChangeOperation operation) {
        CHANGE_OPERATIONS_BUFFER.add(operation);
    }

    public static void syncRarityToClientsWithRetry(java.util.Map<Identifier, Integer> itemRarityMap) {
        syncRarityToClients(itemRarityMap);
    }

    public static void syncIncrementalChangesToClientsWithRetry() {
        syncIncrementalChangesToClients();
    }
}