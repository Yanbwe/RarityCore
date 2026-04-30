package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;/**
 * NBT稀有度匹配器核心类
 * 负责根据物品的NBT标签匹配对应的稀有度配置
 */
public class NbtRarityMatcher {
    
    /**
     * 规则版本号——规则变更时递增
     * 供 NbtSyncManager 判断是否需要重建缓存
     */
    private static final AtomicInteger RULE_VERSION = new AtomicInteger(0);
    
    /**
     * 物品ID到匹配规则的映射缓存
     */
    private static final Map<ResourceLocation, List<NbtMatchRule>> RULES_CACHE = 
        new ConcurrentHashMap<>();
    
    /**
     * 获取物品的NBT匹配稀有度(带缓存)
     * @param itemStack 要检查的物品堆
     * @return 匹配的稀有度等级,如果没有匹配则返回null
     */
    @Nullable
    public static Integer getNbtMatchedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return null;
        }
        
        // 使用缓存获取结果
        return SimpleNbtCache.getCachedRarity(itemStack);
    }
    
    /**
     * 直接计算稀有度(不使用缓存,供缓存内部调用)
     * @param itemStack 物品堆
     * @return 计算的稀有度
     */
    public static Integer calculateWithoutCache(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            RarityCore.LOGGER.debug("[NBT匹配] calculateWithoutCache: 物品为空或无tag");
            return null;
        }

        Item item = itemStack.getItem();
        if (item == null) {
            RarityCore.LOGGER.debug("[NBT匹配] calculateWithoutCache: 物品item为null");
            return null;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            RarityCore.LOGGER.debug("[NBT匹配] calculateWithoutCache: 无法获取有效物品ID");
            return null;
        }

        RarityCore.LOGGER.debug("[NBT匹配] calculateWithoutCache: 物品ID={}, 开始匹配规则", itemId);

        List<NbtMatchRule> rules = RULES_CACHE.getOrDefault(itemId, Collections.emptyList());
        if (rules.isEmpty()) {
            RarityCore.LOGGER.debug("[NBT匹配] calculateWithoutCache: 没有为物品 {} 找到规则", itemId);
            return null;
        }

        RarityCore.LOGGER.debug("[NBT匹配] calculateWithoutCache: 为物品 {} 找到 {} 条规则,开始遍历", itemId, rules.size());

        // 规则已经在注册时按优先级排序好了
        // 查找第一个匹配的规则
        for (int i = 0; i < rules.size(); i++) {
            NbtMatchRule rule = rules.get(i);
            if (rule != null && rule.isEnabled()) {
                RarityCore.LOGGER.debug("[NBT匹配] calculateWithoutCache: 尝试规则 {}, 稀有度={}, 优先级={}", 
                    i, rule.getRarity(), rule.getPriority());
                boolean matchResult = rule.matches(itemStack);
                RarityCore.LOGGER.debug("[NBT匹配] calculateWithoutCache: 规则 {} 匹配结果={}", i, matchResult);
                if (matchResult) {
                    RarityCore.LOGGER.debug("[NBT匹配] calculateWithoutCache: 规则 {} 匹配成功,返回稀有度 {}", i, rule.getRarity());
                    return rule.getRarity();
                }
            }
        }

        RarityCore.LOGGER.debug("[NBT匹配] calculateWithoutCache: 没有规则匹配成功");
        return null;
    }
    
    /**
     * 注册匹配规则
     * @param rule 要注册的匹配规则
     */
    public static void registerRule(NbtMatchRule rule) {
        if (rule == null || rule.getItemId() == null) {
            RarityCore.LOGGER.warn("尝试注册无效的NBT匹配规则");
            return;
        }
        
        List<NbtMatchRule> rules = RULES_CACHE.computeIfAbsent(rule.getItemId(), k -> new ArrayList<>());
        rules.add(rule);
        
        // 按优先级降序排序(数值大的优先级高)
        rules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        RULE_VERSION.incrementAndGet();
        
        RarityCore.LOGGER.debug("注册NBT匹配规则: {} -> 稀有度{} (优先级:{})", 
            rule.getItemId(), rule.getRarity(), rule.getPriority());
    }
    
    /**
     * 为指定物品ID清除所有规则
     * @param itemId 物品ID
     */
    public static void clearRulesForResource(ResourceLocation itemId) {
        RULES_CACHE.remove(itemId);
        RULE_VERSION.incrementAndGet();
        RarityCore.LOGGER.debug("清除物品 {} 的所有NBT匹配规则", itemId);
    }
    
    /**
     * 重新加载所有规则(通常在配置文件更改后调用)
     */
    public static void reloadRules() {
        RULES_CACHE.clear();
        RULE_VERSION.incrementAndGet();
        RarityCore.LOGGER.info("NBT匹配规则缓存已清空,等待重新加载配置");
        
        // 触发配置重新加载
        NbtConfigLoader.loadAllConfigs();
    }
    
    /**
     * 获取当前缓存的规则数量统计
     * @return 各物品ID的规则数量映射
     */
    public static Map<ResourceLocation, Integer> getRuleStatistics() {
        Map<ResourceLocation, Integer> stats = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<NbtMatchRule>> entry : RULES_CACHE.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }
    
    /**
     * 验证规则的有效性
     * @param rule 要验证的规则
     * @return 是否有效
     */
    public static boolean validateRule(NbtMatchRule rule) {
        if (rule == null) {
            return false;
        }
        
        if (rule.getItemId() == null) {
            RarityCore.LOGGER.warn("规则缺少物品ID");
            return false;
        }
        
        if (rule.getConditions() == null || rule.getConditions().isEmpty()) {
            RarityCore.LOGGER.warn("规则 {} 缺少匹配条件", rule.getItemId());
            return false;
        }
        
        if (rule.getRarity() < 1 || rule.getRarity() > 7) {
            RarityCore.LOGGER.warn("规则 {} 的稀有度值 {} 超出有效范围[1-7]", 
                rule.getItemId(), rule.getRarity());
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查指定物品是否有匹配的NBT规则
     * @param itemId 物品资源位置
     * @return 如果有规则返回true
     */
    public static boolean hasRulesForItem(ResourceLocation itemId) {
        if (itemId == null) {
            return false;
        }
        List<NbtMatchRule> rules = RULES_CACHE.get(itemId);
        return rules != null && !rules.isEmpty();
    }

    /**
     * 获取规则总数
     * @return 规则数量
     */
    public static int getRuleCount() {
        return RULES_CACHE.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    /**
     * 获取规则缓存(仅供同步使用)
     * 返回深拷贝以避免并发修改问题
     * @return 规则缓存的深拷贝副本
     */
    public static Map<ResourceLocation, List<NbtMatchRule>> getRulesCacheForSync() {
        Map<ResourceLocation, List<NbtMatchRule>> deepCopy = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<NbtMatchRule>> entry : RULES_CACHE.entrySet()) {
            deepCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return deepCopy;
    }
    
    /**
     * 清空所有规则(仅供同步使用)
     */
    public static void clearAllRules() {
        RULES_CACHE.clear();
        RULE_VERSION.incrementAndGet();
        RarityCore.LOGGER.debug("已清空所有NBT匹配规则");
    }
    
    /**
     * 获取当前规则版本号
     */
    public static int getRuleVersion() {
        return RULE_VERSION.get();
    }
}