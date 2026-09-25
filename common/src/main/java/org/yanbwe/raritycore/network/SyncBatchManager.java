package org.yanbwe.raritycore.network;

import net.minecraft.resources.Identifier;
import org.yanbwe.raritycore.RarityCore;

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
     * 积压阈值：待处理操作数达到该值时，{@link #addOperation} 返回 true 请求调用方立即排空缓冲区。
     * <p><b>这不是丢弃上限</b>——{@link #addOperation} 永远会先把操作入队，
     * 绝不会因为积压达到该值而丢弃任何操作。
     */
    private static final int URGENT_FLUSH_THRESHOLD = 1000;
    private static final int HIGH_PRIORITY_THRESHOLD = 10; // 高优先级阈值
    
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
     * <p><b>本方法保证不丢弃任何操作。</b>历史缺陷：待处理数达到上限（旧名 {@code MAX_PENDING_OPERATIONS}）
     * 时曾直接 {@code return true} 而不入队，注释写的"立即发送"需要调用方响应返回值才会发生，
     * 而调用方 {@code ConfigLoaderUtils} 丢弃了返回值，于是超限之后的操作被静默丢弃
     * （既不注册到 RarityRegistry，也不下发到客户端，且没有任何日志）。
     * 缓冲区只负责合并待发送操作、不是调度器，因此现在改为：<b>始终入队</b>，
     * 到达 {@link #URGENT_FLUSH_THRESHOLD} 时通过返回 true 并打印 WARN 提示调用方立即排空。</p>
     *
     * @param operation 变更操作
     * @param priority 同步优先级
     * @return 是否需要立即发送批次
     */
    public static boolean addOperation(ChangeOperation operation, SyncPriority priority) {
        if (operation == null) {
            RarityCore.LOGGER.warn(
                "SyncBatchManager received a null operation, ignoring");
            return false;
        }

        // 优先级为 null 时退化为 NORMAL，避免 EnumMap.computeIfAbsent 抛 NPE 导致
        // pendingOperations 与 priorityQueues 不一致（那样操作会在排空时被漏掉）
        SyncPriority effectivePriority = (priority != null) ? priority : SyncPriority.NORMAL;

        synchronized (batchLock) {
            // 始终入队：缓冲区只负责合并待发送操作，绝不因积压而静默丢弃
            pendingOperations.add(operation);
            
            // 添加到优先级队列
            priorityQueues.computeIfAbsent(effectivePriority, k -> new ArrayList<>()).add(operation);
            
            // 根据优先级决定是否立即发送
            if (effectivePriority == SyncPriority.IMMEDIATE) {
                return true;
            } else if (effectivePriority == SyncPriority.HIGH && 
                      pendingOperations.size() >= HIGH_PRIORITY_THRESHOLD) {
                return true;
            }

            // 积压达到阈值：操作已经在队列里，这里只是请求调用方立即排空（不丢弃）
            if (pendingOperations.size() >= URGENT_FLUSH_THRESHOLD) {
                RarityCore.LOGGER.warn(
                    "SyncBatchManager backlog reached {} operations (urgent flush threshold {}), requesting immediate flush",
                    pendingOperations.size(), URGENT_FLUSH_THRESHOLD);
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
     * 合并重复操作以减少网络传输
     * @param operations 原始操作列表
     * @return 处理后的操作列表
     */
    public static List<ChangeOperation> optimizeOperations(List<ChangeOperation> operations) {
        if (operations.size() <= 1) {
            return operations;
        }
        
        // 使用Map来跟踪每个物品的最新状态
        Map<Identifier, ChangeOperation> latestOperations = new LinkedHashMap<>();
        
        for (ChangeOperation op : operations) {
            Identifier itemId = op.getItemId();
            
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