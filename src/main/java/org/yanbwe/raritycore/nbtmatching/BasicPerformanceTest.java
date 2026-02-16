package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;

import java.util.List;
import java.util.Random;

/**
 * 基础性能测试工具
 * 用于测试NBT匹配系统的性能表现
 */
public class BasicPerformanceTest {
    
    private static final Random RANDOM = new Random();
    private static final int TEST_ITERATIONS = 1000;
    
    /**
     * 运行基础性能基准测试
     */
    public static void runBenchmark(List<ItemStack> testItems) {
        if (testItems == null || testItems.isEmpty()) {
            RarityCore.LOGGER.warn("测试物品列表为空，跳过性能测试");
            return;
        }
        
        RarityCore.LOGGER.info("开始NBT匹配性能测试...");
        
        // 预热缓存
        warmupCache(testItems);
        
        // 执行性能测试
        long startTime = System.nanoTime();
        int matchCount = 0;
        
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            ItemStack testItem = getRandomItem(testItems);
            Integer rarity = SimpleNbtCache.getCachedRarity(testItem);
            if (rarity != null) {
                matchCount++;
            }
        }
        
        long endTime = System.nanoTime();
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        double avgTimeMs = totalTimeMs / TEST_ITERATIONS;
        double matchRate = (double) matchCount / TEST_ITERATIONS * 100;
        
        // 输出测试结果
        RarityCore.LOGGER.info("=== NBT匹配性能测试结果 ===");
        RarityCore.LOGGER.info("测试次数: {}", TEST_ITERATIONS);
        RarityCore.LOGGER.info("总耗时: {} ms", String.format("%.2f", totalTimeMs));
        RarityCore.LOGGER.info("平均耗时: {} ms", String.format("%.3f", avgTimeMs));
        RarityCore.LOGGER.info("匹配成功率: {}%", String.format("%.1f", matchRate));
        RarityCore.LOGGER.info("缓存大小: {}", SimpleNbtCache.getCacheSize());
        
        // 性能评估
        evaluatePerformance(avgTimeMs, matchRate);
    }
    
    /**
     * 预热缓存
     */
    private static void warmupCache(List<ItemStack> testItems) {
        RarityCore.LOGGER.debug("正在进行缓存预热...");
        for (int i = 0; i < Math.min(100, testItems.size()); i++) {
            ItemStack item = getRandomItem(testItems);
            SimpleNbtCache.getCachedRarity(item);
        }
    }
    
    /**
     * 从测试物品列表中随机获取一个物品
     */
    private static ItemStack getRandomItem(List<ItemStack> items) {
        return items.get(RANDOM.nextInt(items.size()));
    }
    
    /**
     * 评估性能表现
     */
    private static void evaluatePerformance(double avgTimeMs, double matchRate) {
        RarityCore.LOGGER.info("=== 性能评估 ===");
        
        // 时间性能评估
        if (avgTimeMs <= 0.5) {
            RarityCore.LOGGER.info("⏱️  时间性能: 优秀 (< 0.5ms)");
        } else if (avgTimeMs <= 1.0) {
            RarityCore.LOGGER.info("⏱️  时间性能: 良好 (0.5-1.0ms)");
        } else if (avgTimeMs <= 2.0) {
            RarityCore.LOGGER.info("⏱️  时间性能: 一般 (1.0-2.0ms)");
        } else {
            RarityCore.LOGGER.warn("⏱️  时间性能: 需要优化 (> 2.0ms)");
        }
        
        // 匹配率评估
        if (matchRate >= 80) {
            RarityCore.LOGGER.info("🎯 匹配率: 优秀 (≥ 80%)");
        } else if (matchRate >= 60) {
            RarityCore.LOGGER.info("🎯 匹配率: 良好 (60-80%)");
        } else if (matchRate >= 40) {
            RarityCore.LOGGER.info("🎯 匹配率: 一般 (40-60%)");
        } else {
            RarityCore.LOGGER.warn("🎯 匹配率: 较低 (< 40%)");
        }
        
        // 缓存效率评估
        long cacheSize = SimpleNbtCache.getCacheSize();
        if (cacheSize > 400) {
            RarityCore.LOGGER.info("💾 缓存效率: 高效 (缓存条目 > 400)");
        } else if (cacheSize > 200) {
            RarityCore.LOGGER.info("💾 缓存效率: 良好 (缓存条目 200-400)");
        } else {
            RarityCore.LOGGER.info("💾 缓存效率: 一般 (缓存条目 < 200)");
        }
    }
    
    /**
     * 生成测试报告摘要
     */
    public static String generateSummary() {
        return String.format("NBT匹配系统 - 缓存大小: %d, 配置规则数: %d", 
            SimpleNbtCache.getCacheSize(), 
            NbtRarityMatcher.getRuleCount());
    }
}