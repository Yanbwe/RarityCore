package org.yanbwe.raritycore.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.network.RaritySyncPayload.TagRuleTransfer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.List;

/**
 * 同步管理器
 * 处理稀有度数据的同步功能
 */
public class SyncManager {

    private static final List<ChangeOperation> CHANGE_OPERATIONS_BUFFER = new ArrayList<>();

    /**
     * 配置版本号（1.20.1 兼容）：初始 1，每次配置重载 +1
     */
    private static final java.util.concurrent.atomic.AtomicInteger CONFIG_VERSION =
            new java.util.concurrent.atomic.AtomicInteger(1);

    /**
     * 获取当前配置版本号（配置重载时递增，供外部探测）
     */
    public static int getConfigVersion() {
        return CONFIG_VERSION.get();
    }

    /**
     * 配置版本号递增（配置重载流程中调用）
     */
    public static void bumpConfigVersion() {
        CONFIG_VERSION.incrementAndGet();
    }

    /**
     * 向所有在线玩家发送完整的稀有度映射表（全量同步）
     * 仅在配置重载、服务器启动等需要完整状态同步的场景使用
     */
    public static void syncRarityToClients(Map<ResourceLocation, Integer> itemRarityMap,
                                            Map<ResourceLocation, Integer> autoRarityMap,
                                            List<TagRuleTransfer> tagRules) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && itemRarityMap != null) {
            RaritySyncPayload payload = new RaritySyncPayload(
                itemRarityMap,
                autoRarityMap != null ? autoRarityMap : Collections.emptyMap(),
                tagRules != null ? tagRules : Collections.emptyList());
            sendToAllPlayers(payload);
        }
    }

    /**
     * 向单个玩家发送完整的稀有度映射表
     * 适用于玩家登录等仅需同步给单个玩家的场景，避免不必要的全服广播
     */
    public static void syncRarityToPlayer(ServerPlayer player,
                                           Map<ResourceLocation, Integer> itemRarityMap,
                                           Map<ResourceLocation, Integer> autoRarityMap,
                                           List<TagRuleTransfer> tagRules) {
        if (player == null || itemRarityMap == null) return;
        RaritySyncPayload payload = new RaritySyncPayload(
            itemRarityMap,
            autoRarityMap != null ? autoRarityMap : Collections.emptyMap(),
            tagRules != null ? tagRules : Collections.emptyList());
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to sync rarity to player {}", player.getName().getString(), e);
        }
    }

    /**
     * 发送增量变更给所有在线玩家
     * 消费 CHANGE_OPERATIONS_BUFFER 中积累的变更操作，按 MAX_INCREMENTAL_OPERATIONS 分批发送
     */
    public static void syncIncrementalChangesToClients() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        final List<ChangeOperation> snapshot;
        synchronized (CHANGE_OPERATIONS_BUFFER) {
            if (CHANGE_OPERATIONS_BUFFER.isEmpty()) return;
            snapshot = new ArrayList<>(CHANGE_OPERATIONS_BUFFER);
            CHANGE_OPERATIONS_BUFFER.clear();
        }

        // 按 MAX_INCREMENTAL_OPERATIONS 分批发送，防止超大增量载荷导致客户端缓冲区溢出
        for (int offset = 0; offset < snapshot.size(); offset += NetworkConstants.MAX_INCREMENTAL_OPERATIONS) {
            int end = Math.min(offset + NetworkConstants.MAX_INCREMENTAL_OPERATIONS, snapshot.size());
            List<ChangeOperation> batch = snapshot.subList(offset, end);

            List<IncrementalSyncPayload.ChangeOperationData> operations = new ArrayList<>();
            for (ChangeOperation op : batch) {
                operations.add(IncrementalSyncPayload.ChangeOperationData.from(op));
            }

            IncrementalSyncPayload payload = new IncrementalSyncPayload(operations);
            sendToAllPlayers(payload);
        }
    }

    /**
     * 向所有在线玩家发送载荷
     * 使用快照避免遍历玩家列表时的并发修改异常
     */
    private static void sendToAllPlayers(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // List.copyOf 创建不可变快照，防止并发修改
            List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
            for (ServerPlayer player : players) {
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

    /**
     * 全量同步到所有客户端（带重试机制）
     * 委托给 NetworkRetryManager 实现指数退避重试
     */
    public static void syncRarityToClientsWithRetry(Map<ResourceLocation, Integer> itemRarityMap,
                                                      Map<ResourceLocation, Integer> autoRarityMap,
                                                      List<TagRuleTransfer> tagRules) {
        if (itemRarityMap == null) return;
        RaritySyncPayload payload = new RaritySyncPayload(
            itemRarityMap,
            autoRarityMap != null ? autoRarityMap : Collections.emptyMap(),
            tagRules != null ? tagRules : Collections.emptyList());
        int mapSize = itemRarityMap.size();
        if (mapSize > NetworkConstants.MAX_RARITY_SYNC_ENTRIES) {
            RarityCore.LOGGER.warn("SyncManager.syncRarityToClientsWithRetry: Map has {} entries, "
                + "exceeding recommended limit of {}.", mapSize, NetworkConstants.MAX_RARITY_SYNC_ENTRIES);
        }
        NetworkRetryManager.sendFullSyncWithRetry(payload);
    }

    /**
     * 增量同步到所有客户端（带重试机制）
     * 先原子快照并清空缓冲区，再委托给 NetworkRetryManager 分批发送
     */
    public static void syncIncrementalChangesToClientsWithRetry() {
        final List<ChangeOperation> snapshot;
        synchronized (CHANGE_OPERATIONS_BUFFER) {
            if (CHANGE_OPERATIONS_BUFFER.isEmpty()) return;
            snapshot = new ArrayList<>(CHANGE_OPERATIONS_BUFFER);
            CHANGE_OPERATIONS_BUFFER.clear();
        }

        // 按 MAX_INCREMENTAL_OPERATIONS 分批发送
        for (int offset = 0; offset < snapshot.size(); offset += NetworkConstants.MAX_INCREMENTAL_OPERATIONS) {
            int end = Math.min(offset + NetworkConstants.MAX_INCREMENTAL_OPERATIONS, snapshot.size());
            List<ChangeOperation> batch = snapshot.subList(offset, end);

            List<IncrementalSyncPayload.ChangeOperationData> operations = new ArrayList<>();
            for (ChangeOperation op : batch) {
                operations.add(IncrementalSyncPayload.ChangeOperationData.from(op));
            }

            IncrementalSyncPayload payload = new IncrementalSyncPayload(operations);
            NetworkRetryManager.sendIncrementalSyncWithRetry(payload);
        }
    }
}
