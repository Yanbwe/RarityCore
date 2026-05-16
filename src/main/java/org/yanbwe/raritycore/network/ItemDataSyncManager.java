package org.yanbwe.raritycore.network;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.itemdatamatching.ItemDataCondition;
import org.yanbwe.raritycore.itemdatamatching.ItemDataMatchRule;
import org.yanbwe.raritycore.itemdatamatching.ItemDataRarityMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ItemDataSyncManager {

    /**
     * 专有单线程执行器，避免使用 ForkJoinPool.commonPool() 与 Minecraft 服务端 Tick 线程竞争。
     */
    private static final ExecutorService ITEM_DATA_SYNC_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RarityCore-ItemDataSync");
        t.setDaemon(true);
        return t;
    });

    public static void syncItemDataRulesToPlayer(ServerPlayer player) {
        ITEM_DATA_SYNC_EXECUTOR.execute(() -> {
            try {
                List<ItemDataSyncPayload.ItemDataRuleDataPayload> ruleDataList = getAllRulesAsData();
                ItemDataSyncPayload payload = new ItemDataSyncPayload(ruleDataList, true);

                PacketDistributor.sendToPlayer(player, payload);

                RarityCore.LOGGER.debug("Sent item data rules sync payload to player {}, rule count: {}",
                    player.getName().getString(), ruleDataList.size());

            } catch (Exception e) {
                RarityCore.LOGGER.error("Error syncing item data rules to player {}: {}",
                    player.getName().getString(), e.getMessage());
            }
        });
    }

    public static void syncItemDataRulesToAllPlayers() {
        ITEM_DATA_SYNC_EXECUTOR.execute(() -> {
            try {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    RarityCore.LOGGER.warn("Cannot get server instance, skipping item data rules sync");
                    return;
                }

                List<ItemDataSyncPayload.ItemDataRuleDataPayload> ruleDataList = getAllRulesAsData();
                ItemDataSyncPayload payload = new ItemDataSyncPayload(ruleDataList, true);

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    PacketDistributor.sendToPlayer(player, payload);
                }

                RarityCore.LOGGER.info("Sent item data rules sync payload to all players, rule count: {}",
                    ruleDataList.size());

            } catch (Exception e) {
                RarityCore.LOGGER.error("Error syncing item data rules to all players: {}", e.getMessage());
            }
        });
    }

    private static List<ItemDataSyncPayload.ItemDataRuleDataPayload> getAllRulesAsData() {
        List<ItemDataSyncPayload.ItemDataRuleDataPayload> dataList = new ArrayList<>();

        Map<Identifier, List<ItemDataMatchRule>> rulesCache =
            ItemDataRarityMatcher.getRulesCacheForSync();

        for (List<ItemDataMatchRule> rules : rulesCache.values()) {
            for (ItemDataMatchRule rule : rules) {
                dataList.add(ruleToDataPayload(rule));
            }
        }

        return dataList;
    }

    private static ItemDataSyncPayload.ItemDataRuleDataPayload ruleToDataPayload(ItemDataMatchRule rule) {
        List<ItemDataSyncPayload.ConditionDataPayload> conditions = new ArrayList<>();
        for (ItemDataCondition condition : rule.getConditions()) {
            conditions.add(conditionToDataPayload(condition));
        }
        return new ItemDataSyncPayload.ItemDataRuleDataPayload(
            rule.getItemId().toString(),
            rule.getPriority(),
            rule.getRarity(),
            rule.isEnabled(),
            rule.getDescription(),
            conditions
        );
    }

    private static ItemDataSyncPayload.ConditionDataPayload conditionToDataPayload(ItemDataCondition condition) {
        return new ItemDataSyncPayload.ConditionDataPayload(
            condition.getPath(),
            condition.getType().name(),
            condition.getDescription(),
            serializeConditionData(condition)
        );
    }

    private static String serializeConditionData(ItemDataCondition condition) {
        com.google.gson.JsonObject data = new com.google.gson.JsonObject();

        if (condition instanceof org.yanbwe.raritycore.itemdatamatching.EqualsCondition equalsCondition) {
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
        } else if (condition instanceof org.yanbwe.raritycore.itemdatamatching.RangeCondition rangeCondition) {
            data.addProperty("min", rangeCondition.getMinValue());
            data.addProperty("max", rangeCondition.getMaxValue());
        } else if (condition instanceof org.yanbwe.raritycore.itemdatamatching.ContainsCondition containsCondition) {
            data.addProperty("value", containsCondition.getSubstring());
        }

        return data.toString();
    }

    /**
     * 带重试的单玩家同步。重试时使用递增延迟，且确保只发给目标玩家。
     */
    public static void syncItemDataRulesToPlayerWithRetry(ServerPlayer player, int maxRetries) {
        ITEM_DATA_SYNC_EXECUTOR.execute(() -> {
            Exception lastException = null;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    syncItemDataRulesToPlayerInternal(player);
                    if (attempt > 1) {
                        RarityCore.LOGGER.info("Item data rules sync retry successful, attempt {}", attempt);
                    }
                    return;

                } catch (Exception e) {
                    lastException = e;
                    if (attempt < maxRetries) {
                        long delay = 1000L * attempt;
                        RarityCore.LOGGER.warn("Item data rules sync failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt, maxRetries, delay, e.getMessage());

                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }

            RarityCore.LOGGER.error("Item data rules sync finally failed after {} retries. Last error: {}",
                maxRetries, lastException != null ? lastException.getMessage() : "Unknown error");
        });
    }

    /**
     * 同步逻辑的内部实现（不包含异步包装），供重试方法复用。
     */
    private static void syncItemDataRulesToPlayerInternal(ServerPlayer player) {
        List<ItemDataSyncPayload.ItemDataRuleDataPayload> ruleDataList = getAllRulesAsData();
        ItemDataSyncPayload payload = new ItemDataSyncPayload(ruleDataList, true);
        PacketDistributor.sendToPlayer(player, payload);
        RarityCore.LOGGER.debug("Sent item data rules sync payload to player {}, rule count: {}",
            player.getName().getString(), ruleDataList.size());
    }

    public static void syncChangedRules(List<ItemDataMatchRule> changedRules) {
        ITEM_DATA_SYNC_EXECUTOR.execute(() -> {
            try {
                List<ItemDataSyncPayload.ItemDataRuleDataPayload> ruleDataList = new ArrayList<>();
                for (ItemDataMatchRule rule : changedRules) {
                    ruleDataList.add(ruleToDataPayload(rule));
                }

                ItemDataSyncPayload payload = new ItemDataSyncPayload(ruleDataList, false);

                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        PacketDistributor.sendToPlayer(player, payload);
                    }
                }

                RarityCore.LOGGER.debug("Sent incremental item data rules sync payload, changed rules count: {}",
                    ruleDataList.size());

            } catch (Exception e) {
                RarityCore.LOGGER.error("Error in incremental item data rules sync: {}", e.getMessage());
            }
        });
    }

    public static SyncStatus getSyncStatus() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return new SyncStatus(false, 0, 0, "服务器未运行");
        }

        int onlinePlayers = server.getPlayerList().getPlayerCount();
        int ruleCount = ItemDataRarityMatcher.getRuleCount();

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