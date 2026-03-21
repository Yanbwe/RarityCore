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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 同步管理器
 * 处理稀有度数据的同步功能
 */
public class SyncManager {
    
    /**
     * 变更操作缓冲区
     */
    private static final List<ChangeOperation> CHANGE_OPERATIONS_BUFFER = new CopyOnWriteArrayList<>();
    
    /**
     * 将所有稀有度数据同步到客户端(全量同步)
     * @param itemRarityMap 物品稀有度映射
     */
    public static void syncRarityToClients(Map<ResourceLocation, Integer> itemRarityMap) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && itemRarityMap != null) {
            // 直接使用原始映射，避免不必要的对象创建
            RaritySyncPacket packet = new RaritySyncPacket(itemRarityMap);
            
            // 发送到所有在线玩家
            sendPacketToAllPlayers(packet, RaritySyncPacket.INSTANCE);
        }
    }
    
    /**
     * 将增量变更同步到客户端
     */
    public static void syncIncrementalChangesToClients() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !CHANGE_OPERATIONS_BUFFER.isEmpty()) {
            // 创建包含变更操作的增量同步包
            IncrementalSyncPacket packet = new IncrementalSyncPacket(new ArrayList<>(CHANGE_OPERATIONS_BUFFER));
            
            // 清空缓冲区
            CHANGE_OPERATIONS_BUFFER.clear();
            
            // 发送到所有在线玩家
            sendPacketToAllPlayers(packet, IncrementalSyncPacket.INSTANCE);
        }
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
     * @return 变更操作数量
     */
    public static int getPendingChangeCount() {
        return CHANGE_OPERATIONS_BUFFER.size();
    }
    
    /**
     * 清空变更缓冲区
     */
    public static void clearChangeBuffer() {
        CHANGE_OPERATIONS_BUFFER.clear();
    }
    
    /**
     * 添加变更操作到缓冲区
     * @param operation 变更操作
     */
    public static void addChangeOperation(ChangeOperation operation) {
        CHANGE_OPERATIONS_BUFFER.add(operation);
    }
    
    /**
     * 使用重试机制将所有稀有度数据同步到客户端(全量同步)
     * @param itemRarityMap 物品稀有度映射
     */
    public static void syncRarityToClientsWithRetry(Map<ResourceLocation, Integer> itemRarityMap) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && itemRarityMap != null) {
            // 直接使用原始映射，避免不必要的对象创建
            RaritySyncPacket packet = new RaritySyncPacket(itemRarityMap);
            
            // 使用重试管理器发送
            NetworkRetryManager.sendFullSyncWithRetry(packet);
        }
    }
    
    /**
     * 使用重试机制将增量变更同步到客户端
     */
    public static void syncIncrementalChangesToClientsWithRetry() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !CHANGE_OPERATIONS_BUFFER.isEmpty()) {
            // 创建包含变更操作的增量同步包
            IncrementalSyncPacket packet = new IncrementalSyncPacket(new ArrayList<>(CHANGE_OPERATIONS_BUFFER));
            
            // 清空缓冲区
            CHANGE_OPERATIONS_BUFFER.clear();
            
            // 使用重试管理器发送
            NetworkRetryManager.sendIncrementalSyncWithRetry(packet);
        }
    }
}
