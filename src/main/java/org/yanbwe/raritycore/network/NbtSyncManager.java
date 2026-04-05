package org.yanbwe.raritycore.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.nbtmatching.NbtMatchRule;
import org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * NBT匹配规则同步管理器
 * 负责管理NBT配置在服务端和客户端之间的同步
 */
public class NbtSyncManager {
    
    /**
     * 将所有NBT规则同步到指定玩家
     * 同步执行,确保数据包在配置加载完成后构建
     * @param player 目标玩家
     */
    public static void syncNbtRulesToPlayer(ServerPlayer player) {
        try {
            List<NbtSyncPacket.NbtRuleData> ruleDataList = getAllRulesAsData();
            NbtSyncPacket packet = new NbtSyncPacket(ruleDataList, true);

            NbtSyncPacket.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                packet
            );

            RarityCore.LOGGER.debug("Sent NBT rules sync packet to player {}, rule count: {}",
                player.getName().getString(), ruleDataList.size());

        } catch (Exception e) {
            RarityCore.LOGGER.error("Error syncing NBT rules to player {}: {}",
                player.getName().getString(), e.getMessage());
        }
    }
    
    /**
     * 将所有NBT规则同步到所有在线玩家
     * 同步执行,确保数据包在配置加载完成后构建
     */
    public static void syncNbtRulesToAllPlayers() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                RarityCore.LOGGER.warn("Cannot get server instance, skipping NBT rules sync");
                return;
            }

            List<NbtSyncPacket.NbtRuleData> ruleDataList = getAllRulesAsData();
            NbtSyncPacket packet = new NbtSyncPacket(ruleDataList, true);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                NbtSyncPacket.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    packet
                );
            }

            RarityCore.LOGGER.info("Sent NBT rules sync packet to all players, rule count: {}",
                ruleDataList.size());

        } catch (Exception e) {
            RarityCore.LOGGER.error("Error syncing NBT rules to all players: {}", e.getMessage());
        }
    }
    
    /**
     * 获取所有规则的数据表示
     */
    private static List<NbtSyncPacket.NbtRuleData> getAllRulesAsData() {
        List<NbtSyncPacket.NbtRuleData> dataList = new ArrayList<>();
        
        Map<ResourceLocation, List<NbtMatchRule>> rulesCache = 
            NbtRarityMatcher.getRulesCacheForSync();
            
        for (List<NbtMatchRule> rules : rulesCache.values()) {
            for (NbtMatchRule rule : rules) {
                dataList.add(new NbtSyncPacket.NbtRuleData(rule));
            }
        }
        
        return dataList;
    }
    
    /**
     * 带重试机制的同步
     * @param player 目标玩家
     * @param maxRetries 最大重试次数
     */
    public static void syncNbtRulesToPlayerWithRetry(ServerPlayer player, int maxRetries) {
        CompletableFuture.runAsync(() -> {
            Exception lastException = null;
            
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    syncNbtRulesToPlayer(player);
                    if (attempt > 1) {
                        RarityCore.LOGGER.info("NBT rules sync retry successful, attempt {}", attempt);
                    }
                    return;
                    
                } catch (Exception e) {
                    lastException = e;
                    if (attempt < maxRetries) {
                        long delay = 1000L * attempt; // 递增延迟
                        RarityCore.LOGGER.warn("NBT rules sync failed (attempt {}/{}), retrying in {}ms: {}", 
                            attempt, maxRetries, delay, e.getMessage());
                        
                        // 使用CompletableFuture延迟执行替代Thread.sleep
                        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
                            syncNbtRulesToAllPlayers(); // 重新尝试同步
                        });
                        return;
                    }
                }
            }
            
            RarityCore.LOGGER.error("NBT rules sync finally failed after {} retries. Last error: {}", 
                maxRetries, lastException != null ? lastException.getMessage() : "Unknown error");
        });
    }
    
    /**
     * 增量同步变更的规则
     * 同步执行,确保数据包在配置加载完成后构建
     * @param changedRules 变更的规则列表
     */
    public static void syncChangedRules(List<NbtMatchRule> changedRules) {
        try {
            List<NbtSyncPacket.NbtRuleData> ruleDataList = new ArrayList<>();
            for (NbtMatchRule rule : changedRules) {
                ruleDataList.add(new NbtSyncPacket.NbtRuleData(rule));
            }

            NbtSyncPacket packet = new NbtSyncPacket(ruleDataList, false);

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    NbtSyncPacket.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        packet
                    );
                }
            }

            RarityCore.LOGGER.debug("Sent incremental NBT rules sync packet, changed rules count: {}",
                ruleDataList.size());

        } catch (Exception e) {
            RarityCore.LOGGER.error("Error in incremental NBT rules sync: {}", e.getMessage());
        }
    }
    
    /**
     * 检查同步状态
     */
    public static SyncStatus getSyncStatus() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return new SyncStatus(false, 0, 0, "服务器未运行");
        }
        
        int onlinePlayers = server.getPlayerList().getPlayerCount();
        int ruleCount = NbtRarityMatcher.getRuleCount();
        
        return new SyncStatus(true, onlinePlayers, ruleCount, "正常");
    }
    
    /**
     * 同步状态数据类
     */
    public static class SyncStatus {
        private final boolean serverRunning;
        private final int onlinePlayers;
        private final int ruleCount;
        private final String statusMessage;
        
        public SyncStatus(boolean serverRunning, int onlinePlayers, int ruleCount, String statusMessage) {
            this.serverRunning = serverRunning;
            this.onlinePlayers = onlinePlayers;
            this.ruleCount = ruleCount;
            this.statusMessage = statusMessage;
        }
        
        public boolean isServerRunning() { return serverRunning; }
        public int getOnlinePlayers() { return onlinePlayers; }
        public int getRuleCount() { return ruleCount; }
        public String getStatusMessage() { return statusMessage; }
        
        @Override
        public String toString() {
            return String.format("SyncStatus{server=%s, players=%d, rules=%d, status='%s'}",
                serverRunning, onlinePlayers, ruleCount, statusMessage);
        }
    }
}