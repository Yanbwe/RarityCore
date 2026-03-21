package org.yanbwe.raritycore.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.nbtmatching.NbtCondition;
import org.yanbwe.raritycore.nbtmatching.NbtMatchRule;
import org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class NbtSyncManager {

    public static void syncNbtRulesToPlayer(ServerPlayer player) {
        CompletableFuture.runAsync(() -> {
            try {
                List<NbtSyncPayload.NbtRuleDataPayload> ruleDataList = getAllRulesAsData();
                NbtSyncPayload payload = new NbtSyncPayload(ruleDataList, true);

                PacketDistributor.sendToPlayer(player, payload);

                RarityCore.LOGGER.debug("Sent NBT rules sync payload to player {}, rule count: {}",
                    player.getName().getString(), ruleDataList.size());

            } catch (Exception e) {
                RarityCore.LOGGER.error("Error syncing NBT rules to player {}: {}",
                    player.getName().getString(), e.getMessage());
            }
        });
    }

    public static void syncNbtRulesToAllPlayers() {
        CompletableFuture.runAsync(() -> {
            try {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    RarityCore.LOGGER.warn("Cannot get server instance, skipping NBT rules sync");
                    return;
                }

                List<NbtSyncPayload.NbtRuleDataPayload> ruleDataList = getAllRulesAsData();
                NbtSyncPayload payload = new NbtSyncPayload(ruleDataList, true);

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    PacketDistributor.sendToPlayer(player, payload);
                }

                RarityCore.LOGGER.info("Sent NBT rules sync payload to all players, rule count: {}",
                    ruleDataList.size());

            } catch (Exception e) {
                RarityCore.LOGGER.error("Error syncing NBT rules to all players: {}", e.getMessage());
            }
        });
    }

    private static List<NbtSyncPayload.NbtRuleDataPayload> getAllRulesAsData() {
        List<NbtSyncPayload.NbtRuleDataPayload> dataList = new ArrayList<>();

        Map<ResourceLocation, List<NbtMatchRule>> rulesCache =
            NbtRarityMatcher.getRulesCacheForSync();

        for (List<NbtMatchRule> rules : rulesCache.values()) {
            for (NbtMatchRule rule : rules) {
                dataList.add(ruleToDataPayload(rule));
            }
        }

        return dataList;
    }

    private static NbtSyncPayload.NbtRuleDataPayload ruleToDataPayload(NbtMatchRule rule) {
        List<NbtSyncPayload.ConditionDataPayload> conditions = new ArrayList<>();
        for (NbtCondition condition : rule.getConditions()) {
            conditions.add(conditionToDataPayload(condition));
        }
        return new NbtSyncPayload.NbtRuleDataPayload(
            rule.getItemId().toString(),
            rule.getPriority(),
            rule.getRarity(),
            rule.isEnabled(),
            rule.getDescription(),
            conditions
        );
    }

    private static NbtSyncPayload.ConditionDataPayload conditionToDataPayload(NbtCondition condition) {
        return new NbtSyncPayload.ConditionDataPayload(
            condition.getPath(),
            condition.getType().name(),
            condition.getDescription(),
            serializeConditionData(condition)
        );
    }

    private static String serializeConditionData(NbtCondition condition) {
        com.google.gson.JsonObject data = new com.google.gson.JsonObject();

        if (condition instanceof org.yanbwe.raritycore.nbtmatching.EqualsCondition equalsCondition) {
            Object value = equalsCondition.getExpectedValue();
            if (value instanceof String s) {
                data.addProperty("value", s);
            } else if (value instanceof Number n) {
                data.addProperty("value", n.longValue());
            } else if (value instanceof Boolean b) {
                data.addProperty("value", b);
            } else {
                data.addProperty("value", value.toString());
            }
        } else if (condition instanceof org.yanbwe.raritycore.nbtmatching.RangeCondition rangeCondition) {
            data.addProperty("min", rangeCondition.getMinValue());
            data.addProperty("max", rangeCondition.getMaxValue());
        } else if (condition instanceof org.yanbwe.raritycore.nbtmatching.ContainsCondition containsCondition) {
            data.addProperty("value", containsCondition.getSubstring());
        }

        return data.toString();
    }

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
                        long delay = 1000L * attempt;
                        RarityCore.LOGGER.warn("NBT rules sync failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt, maxRetries, delay, e.getMessage());

                        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
                            syncNbtRulesToAllPlayers();
                        });
                        return;
                    }
                }
            }

            RarityCore.LOGGER.error("NBT rules sync finally failed after {} retries. Last error: {}",
                maxRetries, lastException != null ? lastException.getMessage() : "Unknown error");
        });
    }

    public static void syncChangedRules(List<NbtMatchRule> changedRules) {
        CompletableFuture.runAsync(() -> {
            try {
                List<NbtSyncPayload.NbtRuleDataPayload> ruleDataList = new ArrayList<>();
                for (NbtMatchRule rule : changedRules) {
                    ruleDataList.add(ruleToDataPayload(rule));
                }

                NbtSyncPayload payload = new NbtSyncPayload(ruleDataList, false);

                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        PacketDistributor.sendToPlayer(player, payload);
                    }
                }

                RarityCore.LOGGER.debug("Sent incremental NBT rules sync payload, changed rules count: {}",
                    ruleDataList.size());

            } catch (Exception e) {
                RarityCore.LOGGER.error("Error in incremental NBT rules sync: {}", e.getMessage());
            }
        });
    }

    public static SyncStatus getSyncStatus() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return new SyncStatus(false, 0, 0, "服务器未运行");
        }

        int onlinePlayers = server.getPlayerList().getPlayerCount();
        int ruleCount = NbtRarityMatcher.getRuleCount();

        return new SyncStatus(true, onlinePlayers, ruleCount, "正常");
    }

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
    }
}