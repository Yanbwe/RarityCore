package org.yanbwe.raritycore.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 同步管理器
 * 处理稀有度数据的同步功能
 * 变更操作缓冲区统一由 SyncBatchManager 管理，避免重复缓冲
 */
public class SyncManager {
    
    /**
     * 配置版本号——每次重载配置时递增
     * 客户端存储最近收到的版本号，登录时比对以跳过重复同步
     */
    private static final AtomicInteger CONFIG_VERSION = new AtomicInteger(1);
    
    /**
     * 获取当前配置版本号
     */
    public static int getConfigVersion() {
        return CONFIG_VERSION.get();
    }
    
    /**
     * 递增配置版本号(配置重载时调用)
     */
    public static void bumpConfigVersion() {
        CONFIG_VERSION.incrementAndGet();
    }
    
    /**
     * 将所有稀有度数据同步到客户端(全量同步,含当前版本号)
     * @param itemRarityMap 物品稀有度映射
     */
    public static void syncRarityToClients(Map<ResourceLocation, Integer> itemRarityMap) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && itemRarityMap != null) {
            RaritySyncPacket packet = new RaritySyncPacket(CONFIG_VERSION.get(), itemRarityMap);
            sendPacketToAllPlayers(packet, RaritySyncPacket.INSTANCE);
        }
    }
    
    /**
     * 向单个玩家同步稀有度数据(版本感知)
     * 客户端会检查版本号，若已是最新则跳过数据处理
     * @param player 目标玩家
     * @param itemRarityMap 物品稀有度映射
     */
    public static void syncRarityToPlayer(ServerPlayer player, Map<ResourceLocation, Integer> itemRarityMap) {
        if (player != null && itemRarityMap != null) {
            RaritySyncPacket packet = new RaritySyncPacket(CONFIG_VERSION.get(), itemRarityMap);
            RaritySyncPacket.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
    
    /**
     * 将增量变更同步到客户端
     * 变更操作统一由 SyncBatchManager 管理，避免重复缓冲。
     * 本方法从 SyncBatchManager 获取待处理操作并立即发送
     */
    public static void syncIncrementalChangesToClients() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        
        // 从统一的 SyncBatchManager 获取待处理操作
        List<ChangeOperation> pendingOps = SyncBatchManager.getAndClearPendingOperations();
        if (pendingOps.isEmpty()) return;
        
        // 创建包含变更操作的增量同步包
        IncrementalSyncPacket packet = new IncrementalSyncPacket(new ArrayList<>(pendingOps));
        
        // 发送到所有在线玩家
        sendPacketToAllPlayers(packet, IncrementalSyncPacket.INSTANCE);
    }
    
    /**
     * 向所有在线玩家发送数据包
     * @param packet 要发送的数据包
     * @param channel 网络通道
     * @param <T> 数据包类型
     */
    private static <T> void sendPacketToAllPlayers(T packet, SimpleChannel channel) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }
    
    /**
     * 获取当前变更缓冲区中的操作数量
     * 委托给 SyncBatchManager 统一管理
     * @return 变更操作数量
     */
    public static int getPendingChangeCount() {
        return SyncBatchManager.getPendingOperationCount();
    }
    
    /**
     * 清空变更缓冲区
     * 委托给 SyncBatchManager 统一管理
     */
    public static void clearChangeBuffer() {
        SyncBatchManager.clearAllOperations();
    }
    
    /**
     * 添加变更操作到缓冲区
     * 委托给 SyncBatchManager 统一管理
     * @param operation 变更操作
     */
    public static void addChangeOperation(ChangeOperation operation) {
        SyncBatchManager.addOperation(operation);
    }
    
    /**
     * 使用重试机制将所有稀有度数据同步到客户端(全量同步)
     * @param itemRarityMap 物品稀有度映射
     */
    public static void syncRarityToClientsWithRetry(Map<ResourceLocation, Integer> itemRarityMap) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && itemRarityMap != null) {
            RaritySyncPacket packet = new RaritySyncPacket(CONFIG_VERSION.get(), itemRarityMap);
            NetworkRetryManager.sendFullSyncWithRetry(packet);
        }
    }
    
    /**
     * 使用重试机制将增量变更同步到客户端
     * 变更操作统一由 SyncBatchManager 管理
     */
    public static void syncIncrementalChangesToClientsWithRetry() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        
        // 从统一的 SyncBatchManager 获取待处理操作
        List<ChangeOperation> pendingOps = SyncBatchManager.getAndClearPendingOperations();
        if (pendingOps.isEmpty()) return;
        
        // 创建包含变更操作的增量同步包
        IncrementalSyncPacket packet = new IncrementalSyncPacket(new ArrayList<>(pendingOps));
        
        // 使用重试管理器发送
        NetworkRetryManager.sendIncrementalSyncWithRetry(packet);
    }
}
