package org.yanbwe.raritycore.network;

import net.minecraft.resources.ResourceLocation;
import org.yanbwe.raritycore.RarityCore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网络同步批处理管理器
 * 优化网络包发送效率，减少频繁的小数据包传输
 */
public class SyncBatchManager {
    
    // 批处理配置常量
    private static final int BATCH_SIZE_THRESHOLD = 50; // 批量阈值
    private static final long BATCH_TIME_WINDOW_MS = 2000; // 2秒时间窗口
    private static final int MAX_PENDING_OPERATIONS = 1000; // 最大待处理操作数
    
    // 待处理的变更操作缓冲区
    private static final List<ChangeOperation> pendingOperations = new ArrayList<>();
    
    // 最后一批处理时间戳
    private static volatile long lastBatchTime = 0;
    
    // 批处理锁
    private static final Object batchLock = new Object();
    
    /**
     * 添加变更操作到批处理队列
     * @param operation 变更操作
     * @return 是否需要立即发送批次
     */
    public static boolean addOperation(ChangeOperation operation) {
        synchronized (batchLock) {
            // 检查是否超过最大容量
            if (pendingOperations.size() >= MAX_PENDING_OPERATIONS) {
                RarityCore.LOGGER.warn("Sync batch buffer is full, forcing immediate sync");
                return true; // 立即发送
            }
            
            // 添加操作到缓冲区
            pendingOperations.add(operation);
            
            // 检查是否达到批量阈值
            if (pendingOperations.size() >= BATCH_SIZE_THRESHOLD) {
                RarityCore.LOGGER.debug("Batch threshold reached: {} operations", pendingOperations.size());
                return true;
            }
            
            // 检查时间窗口是否超时
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBatchTime >= BATCH_TIME_WINDOW_MS && !pendingOperations.isEmpty()) {
                RarityCore.LOGGER.debug("Batch time window expired: {}ms since last batch", 
                    currentTime - lastBatchTime);
                return true;
            }
            
            return false;
        }
    }
    
    /**
     * 获取并清空待处理的操作列表
     * @return 待处理的操作列表副本
     */
    public static List<ChangeOperation> getAndClearPendingOperations() {
        synchronized (batchLock) {
            if (pendingOperations.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<ChangeOperation> operationsToSend = new ArrayList<>(pendingOperations);
            pendingOperations.clear();
            lastBatchTime = System.currentTimeMillis();
            
            RarityCore.LOGGER.debug("Sending batch of {} operations", operationsToSend.size());
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
            lastBatchTime = System.currentTimeMillis();
        }
    }
    
    /**
     * 合并重复操作以减少网络传输
     * @param operations 原始操作列表
     * @return 优化后的操作列表
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
                    // ADD和UPDATE操作可以合并，保留最新的
                    latestOperations.put(itemId, op);
                    break;
                    
                case DELETE:
                    // DELETE操作会移除之前的ADD/UPDATE操作
                    latestOperations.remove(itemId);
                    // 但仍然保留DELETE操作本身
                    latestOperations.put(itemId, op);
                    break;
            }
        }
        
        List<ChangeOperation> optimized = new ArrayList<>(latestOperations.values());
        if (optimized.size() < operations.size()) {
            RarityCore.LOGGER.debug("Optimized operations: {} -> {}", operations.size(), optimized.size());
        }
        
        return optimized;
    }
    
    /**
     * 获取批处理统计信息
     */
    public static BatchStats getBatchStats() {
        synchronized (batchLock) {
            return new BatchStats(
                pendingOperations.size(),
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
        private final int threshold;
        private final long timeWindowMs;
        private final long timeSinceLastBatch;
        
        public BatchStats(int pendingCount, int threshold, long timeWindowMs, long timeSinceLastBatch) {
            this.pendingCount = pendingCount;
            this.threshold = threshold;
            this.timeWindowMs = timeWindowMs;
            this.timeSinceLastBatch = timeSinceLastBatch;
        }
        
        public int getPendingCount() { return pendingCount; }
        public int getThreshold() { return threshold; }
        public long getTimeWindowMs() { return timeWindowMs; }
        public long getTimeSinceLastBatch() { return timeSinceLastBatch; }
        
        @Override
        public String toString() {
            return String.format("BatchStats{pending=%d, threshold=%d, timeWindow=%dms, sinceLast=%dms}",
                pendingCount, threshold, timeWindowMs, timeSinceLastBatch);
        }
    }
}