package org.yanbwe.raritycore.network;

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
import net.minecraft.resources.ResourceLocation;
import java.util.concurrent.CompletableFuture;

/**
 * NBT匹配规则同步管理器
 * 负责管理NBT配置在服务端和客户端之间的同步
 */
public class NbtSyncManager {
    
    /**
     * 将所有NBT规则同步到指定玩家
     * @param player 目标玩家
     */
    public static void syncNbtRulesToPlayer(ServerPlayer player) {
        CompletableFuture.runAsync(() -> {
            try {
                List<NbtSyncPacket.NbtRuleData> ruleDataList = getAllRulesAsData();
                NbtSyncPacket packet = new NbtSyncPacket(ruleDataList, true);
                
                NbtSyncPacket.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player), 
                    packet
                );
                
                RarityCore.LOGGER.debug("已向玩家 {} 发送NBT规则同步包，规则数量: {}", 
                    player.getName().getString(), ruleDataList.size());
                    
            } catch (Exception e) {
                RarityCore.LOGGER.error("向玩家 {} 同步NBT规则时出错: {}", 
                    player.getName().getString(), e.getMessage());
            }
        });
    }
    
    /**
     * 将所有NBT规则同步到所有在线玩家
     */
    public static void syncNbtRulesToAllPlayers() {
        CompletableFuture.runAsync(() -> {
            try {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    RarityCore.LOGGER.warn("无法获取服务器实例，跳过NBT规则同步");
                    return;
                }
                
                List<NbtSyncPacket.NbtRuleData> ruleDataList = getAllRulesAsData();
                NbtSyncPacket packet = new NbtSyncPacket(ruleDataList, true);
                
                // 发送给所有在线玩家
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    NbtSyncPacket.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player), 
                        packet
                    );
                }
                
                RarityCore.LOGGER.info("已向所有玩家发送NBT规则同步包，规则数量: {}", 
                    ruleDataList.size());
                    
            } catch (Exception e) {
                RarityCore.LOGGER.error("向所有玩家同步NBT规则时出错: {}", e.getMessage());
            }
        });
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
                        RarityCore.LOGGER.info("NBT规则同步重试成功，第 {} 次尝试", attempt);
                    }
                    return;
                    
                } catch (Exception e) {
                    lastException = e;
                    if (attempt < maxRetries) {
                        long delay = 1000L * attempt; // 递增延迟
                        RarityCore.LOGGER.warn("NBT规则同步失败 (尝试 {}/{}), {}ms后重试: {}", 
                            attempt, maxRetries, delay, e.getMessage());
                        
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            RarityCore.LOGGER.error("NBT规则同步最终失败，已重试 {} 次。最后错误: {}", 
                maxRetries, lastException != null ? lastException.getMessage() : "未知错误");
        });
    }
    
    /**
     * 增量同步变更的规则
     * @param changedRules 变更的规则列表
     */
    public static void syncChangedRules(List<NbtMatchRule> changedRules) {
        CompletableFuture.runAsync(() -> {
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
                
                RarityCore.LOGGER.debug("已发送增量NBT规则同步包，变更规则数量: {}", 
                    ruleDataList.size());
                    
            } catch (Exception e) {
                RarityCore.LOGGER.error("增量同步NBT规则时出错: {}", e.getMessage());
            }
        });
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