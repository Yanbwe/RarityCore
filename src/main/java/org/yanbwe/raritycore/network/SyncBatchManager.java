package org.yanbwe.raritycore.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.JsonPerformanceOptimizer;
import org.yanbwe.raritycore.util.RarityConstants;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 网络同步批处理管理器
 * 网络包发送优化器,减少频繁的小数据包传输
 * 支持优先级调度和智能批处理
 */
public class SyncBatchManager {
    
    // 批处理配置常量
    private static final int BATCH_SIZE_THRESHOLD = 50; // 批量阈值
    private static final long BATCH_TIME_WINDOW_MS = 2000; // 2秒时间窗口
    /**
     * 积压排空阈值（对应配置文件 {@code sync_batch.json} 的 {@code maxPendingOperations} 键，
     * 默认 {@link RarityConstants#DEFAULT_MAX_PENDING_OPERATIONS}）。
     *
     * <p><b>它不是丢弃上限。</b>达到该数量时 {@link #addOperation} 会返回 true 请求调用方立即排空，
     * 并记录 WARN 日志；操作本身永远会入队，绝不会因为积压而被静默丢弃。</p>
     */
    private static volatile int maxPendingOperations = RarityConstants.DEFAULT_MAX_PENDING_OPERATIONS;
    private static final int HIGH_PRIORITY_THRESHOLD = 10; // 高优先级阈值

    // 配置文件路径（遵循 CacheConfig 模式）
    private static final Path SYNC_BATCH_CONFIG_FILE = Paths.get(RarityConstants.CONFIG_DIR_PARENT)
        .resolve(RarityConstants.CONFIG_DIR_NAME)
        .resolve("sync_batch.json");

    static {
        loadMaxPendingOperations();
    }
    
    // 待处理的变更操作缓冲区
    private static final List<ChangeOperation> pendingOperations = new ArrayList<>();
    
    // 优先级队列
    private static final Map<SyncPriority, List<ChangeOperation>> priorityQueues = 
        new EnumMap<>(SyncPriority.class);
    
    // 最后一批处理时间戳
    private static volatile long lastBatchTime = 0;
    
    // 批处理锁
    private static final Object batchLock = new Object();
    
    // 同步优先级枚举
    public enum SyncPriority {
        IMMEDIATE,    // 立即发送
        HIGH,         // 高优先级
        NORMAL,       // 正常优先级
        LOW           // 低优先级
    }
    
    /**
     * 添加变更操作到批处理队列(默认正常优先级)
     * @param operation 变更操作
     * @return 是否需要立即发送批次
     */
    public static boolean addOperation(ChangeOperation operation) {
        return addOperation(operation, SyncPriority.NORMAL);
    }
    
    /**
     * 添加变更操作到批处理队列(指定优先级)
     *
     * <p><b>本方法保证不丢弃操作。</b>历史缺陷：待处理数量达到 {@link #maxPendingOperations} 时，
     * 曾直接 {@code return true} 而不执行入队——注释写的"立即发送"需要调用方响应返回值才会发生，
     * 而已知调用方（{@code ConfigLoaderUtils.loadJsonConfigFileWithBatch} 等）忽略了返回值，
     * 于是超阈值之后的操作既不注册也不下发，且没有任何日志。</p>
     *
     * <p>现在改为：无论积压多少都先入队；到达积压阈值时返回 true 请求调用方立即排空，
     * 并记录含当前积压数的 WARN 日志。缓冲区只负责合并待发送操作，不负责调度。</p>
     *
     * @param operation 变更操作
     * @param priority 同步优先级
     * @return 是否需要立即发送批次
     */
    public static boolean addOperation(ChangeOperation operation, SyncPriority priority) {
        if (operation == null) {
            RarityCore.LOGGER.warn("SyncBatchManager received a null operation, ignoring");
            return false;
        }
        synchronized (batchLock) {
            // 始终入队：缓冲区只负责合并待发送操作，绝不因为积压而静默丢弃
            pendingOperations.add(operation);

            // 添加到优先级队列
            priorityQueues.computeIfAbsent(priority, k -> new ArrayList<>()).add(operation);
            
            // 根据优先级决定是否立即发送
            if (priority == SyncPriority.IMMEDIATE) {
                return true;
            } else if (priority == SyncPriority.HIGH && 
                      pendingOperations.size() >= HIGH_PRIORITY_THRESHOLD) {
                return true;
            }

            // 积压达到阈值：请求调用方立即排空（操作已入队，不会被丢弃）
            // 注意必须放在 BATCH_SIZE_THRESHOLD 判断之前，否则永远走不到这里、日志也不会出现
            if (pendingOperations.size() >= maxPendingOperations) {
                RarityCore.LOGGER.warn("SyncBatchManager backlog reached {} operations (threshold={}), requesting immediate flush",
                    pendingOperations.size(), maxPendingOperations);
                return true;
            }
            
            // 检查是否达到批量阈值
            if (pendingOperations.size() >= BATCH_SIZE_THRESHOLD) {
                return true;
            }
            
            // 检查时间窗口是否超时
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBatchTime >= BATCH_TIME_WINDOW_MS && !pendingOperations.isEmpty()) {
                return true;
            }
            
            return false;
        }
    }
    
    /**
     * 获取并清空待处理的操作列表(按优先级排序)
     * @return 待处理的操作列表副本
     */
    public static List<ChangeOperation> getAndClearPendingOperations() {
        return getAndClearPendingOperations(true);
    }
    
    /**
     * 获取并清空待处理的操作列表
     * @param sortByPriority 是否按优先级排序
     * @return 待处理的操作列表副本
     */
    public static List<ChangeOperation> getAndClearPendingOperations(boolean sortByPriority) {
        synchronized (batchLock) {
            if (pendingOperations.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<ChangeOperation> operationsToSend;
            
            if (sortByPriority) {
                // 按优先级排序:IMMEDIATE > HIGH > NORMAL > LOW
                operationsToSend = new ArrayList<>();
                for (SyncPriority priority : SyncPriority.values()) {
                    List<ChangeOperation> priorityOps = priorityQueues.get(priority);
                    if (priorityOps != null) {
                        operationsToSend.addAll(priorityOps);
                        priorityOps.clear();
                    }
                }
            } else {
                operationsToSend = new ArrayList<>(pendingOperations);
            }
            
            pendingOperations.clear();
            lastBatchTime = System.currentTimeMillis();
            
            return operationsToSend;
        }
    }
    
    /**
     * 获取当前待处理操作数量
     */
    public static int getPendingOperationCount() {
        synchronized (batchLock) {
            return pendingOperations.size();
        }
    }
    
    /**
     * 清空所有待处理操作
     */
    public static void clearAllOperations() {
        synchronized (batchLock) {
            pendingOperations.clear();
            for (List<ChangeOperation> ops : priorityQueues.values()) {
                ops.clear();
            }
            lastBatchTime = System.currentTimeMillis();
        }
    }
    
    /**
     * 获取当前积压排空阈值（配置文件 sync_batch.json 的 maxPendingOperations 键）。
     * 达到该值时 {@link #addOperation} 会请求调用方立即排空，但不会丢弃任何操作。
     */
    public static int getMaxPendingOperations() {
        return maxPendingOperations;
    }

    /**
     * 从配置文件加载积压排空阈值（sync_batch.json 的 maxPendingOperations 键）。
     * 该值只决定"何时请求调用方立即排空缓冲区"，<b>不是丢弃上限</b>。
     * 遵循 CacheConfig.loadFromConfig() 模式：
     * - 配置文件不存在时使用硬编码默认值
     * - 读取失败时回退到默认值
     * - 记录日志显示当前使用的值
     */
    public static void loadMaxPendingOperations() {
        if (!Files.exists(SYNC_BATCH_CONFIG_FILE)) {
            RarityCore.LOGGER.info("SyncBatchManager config file not found, using default maxPendingOperations={}",
                maxPendingOperations);
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(SYNC_BATCH_CONFIG_FILE)) {
            Gson gson = JsonPerformanceOptimizer.getOptimizedGson();
            JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);

            if (jsonObject != null && jsonObject.has("maxPendingOperations")) {
                int loadedValue = jsonObject.get("maxPendingOperations").getAsInt();
                // 验证范围：最少 1，最大不限（但建议合理范围）
                maxPendingOperations = Math.max(1, loadedValue);
                RarityCore.LOGGER.info("SyncBatchManager config loaded: maxPendingOperations={} (from file)",
                    maxPendingOperations);
            } else {
                RarityCore.LOGGER.info("SyncBatchManager config file has no maxPendingOperations, using default={}",
                    maxPendingOperations);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to load SyncBatchManager config from {}, using default maxPendingOperations={}",
                SYNC_BATCH_CONFIG_FILE, maxPendingOperations, e);
        }
    }

    /**
     * 重新加载积压排空阈值配置（支持运行时配置热更新）
     */
    public static void reloadConfig() {
        loadMaxPendingOperations();
        RarityCore.LOGGER.info("SyncBatchManager config reloaded: maxPendingOperations={}", maxPendingOperations);
    }

    /**
     * 合并重复操作以减少网络传输
     * @param operations 原始操作列表
     * @return 处理后的操作列表
     */
    public static List<ChangeOperation> optimizeOperations(List<ChangeOperation> operations) {
        if (operations.size() <= 1) {
            return operations;
        }
        
        // 使用Map来跟踪每个物品的最新状态
        Map<ResourceLocation, ChangeOperation> latestOperations = new LinkedHashMap<>();
        
        for (ChangeOperation op : operations) {
            ResourceLocation itemId = op.getItemId();
            
            switch (op.getType()) {
                case ADD:
                case UPDATE:
                    // ADD和UPDATE操作可以合并,保留最新的
                    latestOperations.put(itemId, op);
                    break;
                    
                case DELETE:
                    // DELETE操作会清除之前的ADD/UPDATE操作
                    latestOperations.remove(itemId);
                    // 但仍然保留DELETE操作本身
                    latestOperations.put(itemId, op);
                    break;
            }
        }
        
        List<ChangeOperation> optimized = new ArrayList<>(latestOperations.values());
        
        return optimized;
    }
    
    /**
     * 获取批处理统计信息
     */
    public static BatchStats getBatchStats() {
        synchronized (batchLock) {
            Map<SyncPriority, Integer> priorityCounts = new EnumMap<>(SyncPriority.class);
            for (Map.Entry<SyncPriority, List<ChangeOperation>> entry : priorityQueues.entrySet()) {
                priorityCounts.put(entry.getKey(), entry.getValue().size());
            }
            
            return new BatchStats(
                pendingOperations.size(),
                new HashMap<>(priorityCounts),
                BATCH_SIZE_THRESHOLD,
                BATCH_TIME_WINDOW_MS,
                System.currentTimeMillis() - lastBatchTime
            );
        }
    }
    
    /**
     * 批处理统计信息类
     */
    public static class BatchStats {
        private final int pendingCount;
        private final Map<SyncPriority, Integer> priorityCounts;
        private final int threshold;
        private final long timeWindowMs;
        private final long timeSinceLastBatch;
        
        public BatchStats(int pendingCount, Map<SyncPriority, Integer> priorityCounts, 
                         int threshold, long timeWindowMs, long timeSinceLastBatch) {
            this.pendingCount = pendingCount;
            this.priorityCounts = priorityCounts;
            this.threshold = threshold;
            this.timeWindowMs = timeWindowMs;
            this.timeSinceLastBatch = timeSinceLastBatch;
        }
        
        public int getPendingCount() { return pendingCount; }
        public Map<SyncPriority, Integer> getPriorityCounts() { return priorityCounts; }
        public int getThreshold() { return threshold; }
        public long getTimeWindowMs() { return timeWindowMs; }
        public long getTimeSinceLastBatch() { return timeSinceLastBatch; }
        
        @Override
        public String toString() {
            return String.format("BatchStats{pending=%d, priorities=%s, threshold=%d, timeWindow=%dms, sinceLast=%dms}",
                pendingCount, priorityCounts, threshold, timeWindowMs, timeSinceLastBatch);
        }
    }
}