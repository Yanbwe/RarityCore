package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NBT稀有度匹配器核心类
 * 负责根据物品的NBT标签匹配对应的稀有度配置
 */
public class NbtRarityMatcher {
    
    /**
     * 物品ID到匹配规则的映射缓存
     */
    private static final Map<ResourceLocation, List<NbtMatchRule>> RULES_CACHE = 
        new ConcurrentHashMap<>();
    
    /**
     * 获取物品的NBT匹配稀有度（带缓存）
     * @param itemStack 要检查的物品堆
     * @return 匹配的稀有度等级，如果没有匹配则返回null
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
     * 直接计算稀有度（不使用缓存，供缓存内部调用）
     * @param itemStack 物品堆
     * @return 计算的稀有度
     */
    public static Integer calculateWithoutCache(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return null;
        }
        
        Item item = itemStack.getItem();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            return null;
        }
        
        List<NbtMatchRule> rules = RULES_CACHE.getOrDefault(itemId, Collections.emptyList());
        if (rules.isEmpty()) {
            return null;
        }
        
        // 按优先级降序排列（数值大的优先级高）
        List<NbtMatchRule> sortedRules = new ArrayList<>(rules);
        sortedRules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        // 查找第一个匹配的规则
        for (NbtMatchRule rule : sortedRules) {
            if (rule.isEnabled() && rule.matches(itemStack)) {
                return rule.getRarity();
            }
        }
        
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
        
        RULES_CACHE.computeIfAbsent(rule.getItemId(), k -> new ArrayList<>())
                  .add(rule);
        
        RarityCore.LOGGER.debug("注册NBT匹配规则: {} -> 稀有度{} (优先级:{})", 
            rule.getItemId(), rule.getRarity(), rule.getPriority());
    }
    
    /**
     * 为指定物品ID清除所有规则
     * @param itemId 物品ID
     */
    public static void clearRulesForResource(ResourceLocation itemId) {
        RULES_CACHE.remove(itemId);
        RarityCore.LOGGER.debug("清除物品 {} 的所有NBT匹配规则", itemId);
    }
    
    /**
     * 重新加载所有规则（通常在配置文件更改后调用）
     */
    public static void reloadRules() {
        RULES_CACHE.clear();
        RarityCore.LOGGER.info("NBT匹配规则缓存已清空，等待重新加载配置");
        
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
     * 获取规则总数
     * @return 规则数量
     */
    public static int getRuleCount() {
        return RULES_CACHE.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    /**
     * 获取规则缓存（仅供同步使用）
     * @return 规则缓存的副本
     */
    public static Map<ResourceLocation, List<NbtMatchRule>> getRulesCacheForSync() {
        return new HashMap<>(RULES_CACHE);
    }
    
    /**
     * 清空所有规则（仅供同步使用）
     */
    public static void clearAllRules() {
        RULES_CACHE.clear();
        RarityCore.LOGGER.debug("已清空所有NBT匹配规则");
    }
}