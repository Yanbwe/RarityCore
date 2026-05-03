package org.yanbwe.raritycore.service;

import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.network.SyncManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 调度器服务
 * 负责管理所有定时任务和调度器
 */
public class SchedulerService {
    
    private ScheduledExecutorService syncScheduler;
    private final ServiceFactory serviceFactory;
    
    public SchedulerService(ServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
    }
    
    /**
     * 启动所有调度任务
     */
    public void startScheduledTasks() {
        // 启动智能计划同步任务
        syncScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RarityCore-Incremental-Sync");
            t.setDaemon(true);  // 设置为守护线程
            return t;
        });
        
        // 延时发送兼容性提示(等待世界完全加载)
        syncScheduler.schedule(() -> {
            try {
                serviceFactory.getCompatibilityChecker().notifyPlayersOfCompatibilityIssue();
            } catch (Exception e) {
                RarityCore.LOGGER.debug("Failed to send compatibility notification", e);
            }
        }, 5, TimeUnit.SECONDS); // 5 秒后发送提示
        
        // 延时启动自动稀有度计算(世界启动 5 秒后检测)
        syncScheduler.schedule(() -> {
            try {
                checkAndStartAutoCalculation();
            } catch (Exception e) {
                RarityCore.LOGGER.error("Failed to start auto rarity calculation", e);
            }
        }, 5, TimeUnit.SECONDS);
        
        // 增量同步检查任务：此处保持 scheduleAtFixedRate 是安全的，
        // 因为实际同步工作由 syncIncrementalChangesToClients() 内部管理，
        // 且本调度器为单线程，即使单次执行超时也不会并发执行。
        // 若未来改为多线程调度器，应同步改为 scheduleWithFixedDelay。
        syncScheduler.scheduleAtFixedRate(() -> {
            try {
                // 使用批处理管理器检查是否需要同步
                int pendingCount = serviceFactory.getSyncBatchManager().getPendingOperationCount();
                if (pendingCount > 0) {
                    SyncManager.syncIncrementalChangesToClients();
                }
            } catch (Exception e) {
                RarityCore.LOGGER.error("增量同步过程中发生错误", e);
            }
        }, 0, 2000, TimeUnit.MILLISECONDS); // 每2秒检查一次,与批处理窗口匹配
        
        // 添加自动稀有度计算的 tick 任务(仅计算时实际执行)
        // 使用 scheduleWithFixedDelay 而非 scheduleAtFixedRate，确保两次执行之间至少间隔 100ms，
        // 避免因单次 tick() 超时（如大量物品计算）导致任务堆积
        syncScheduler.scheduleWithFixedDelay(() -> {
            try {
                var calculator = serviceFactory.getAutoRarityCalculator();
                if (calculator.isCalculating()) {
                    calculator.tick();
                }
            } catch (Exception e) {
                RarityCore.LOGGER.error("Error occurred during auto rarity calculation tick", e);
            }
        }, 100, 100, TimeUnit.MILLISECONDS); // 首次 100ms 后开始，上次执行完成后至少间隔 100ms 再执行
    }
    
    /**
     * 停止所有调度任务
     */
    public void stopScheduledTasks() {
        // 服务器停止时关闭调度器
        if (syncScheduler != null) {
            syncScheduler.shutdown();
            try {
                if (!syncScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    syncScheduler.shutdownNow();
                    if (!syncScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        RarityCore.LOGGER.error("线程池未能正确终止");
                    }
                }
            } catch (InterruptedException e) {
                syncScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            syncScheduler = null;
        }
    }
    
    /**
     * 检查并启动自动稀有度计算
     */
    private void checkAndStartAutoCalculation() {
        // 检查 auto_rarity.json 是否存在
        java.nio.file.Path autoRarityFile = serviceFactory.getAutoRarityConfigManager().getAutoRarityFilePath();
        if (!java.nio.file.Files.exists(autoRarityFile)) {
            // 文件不存在,开始自动计算
            serviceFactory.getAutoRarityCalculator().startAutoCalculation();
        } else {
            RarityCore.LOGGER.debug("自动稀有度配置已存在,跳过计算: {}", autoRarityFile);
        }
    }
}
