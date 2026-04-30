package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品数据稀有度匹配器核心类
 * 负责根据物品的数据组件匹配对应的稀有度配置
 */
public class ItemDataRarityMatcher {
    
    /**
     * 物品ID到匹配规则的映射缓存
     */
    private static final Map<ResourceLocation, List<ItemDataMatchRule>> RULES_CACHE = 
        new ConcurrentHashMap<>();

    @Nullable
    private static CompoundTag getItemStackTag(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        
        try {
            net.minecraft.nbt.Tag nbt = itemStack.save(net.minecraft.core.RegistryAccess.EMPTY);
            if (nbt instanceof CompoundTag itemNbt) {
                return itemNbt;
            }
        } catch (Exception e) {
            RarityCore.LOGGER.warn("无法将ItemStack序列化为NBT: {}", e.getMessage());
        }
        
        return null;
    }

    
    /**
     * 获取物品的物品数据匹配稀有度(性能优化版)
     * 先检查是否有匹配规则，仅当规则存在时才序列化NBT，
     * 避免对无规则物品（绝大多数）进行昂贵的全量NBT序列化
     * @param itemStack 要检查的物品堆
     * @return 匹配的稀有度等级,如果没有匹配则返回null
     */
    @Nullable
    public static Integer getItemDataMatchedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        
        Item item = itemStack.getItem();
        if (item == null) {
            return null;
        }
        
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return null;
        }
        
        // P0 FIX: 先检查规则，无规则直接返回，避免昂贵的NBT序列化
        List<ItemDataMatchRule> rules = RULES_CACHE.getOrDefault(itemId, Collections.emptyList());
        if (rules.isEmpty()) {
            return null;
        }
        
        // P1 FIX: 仅序列化一次，将tag传递给matchNbt避免重复序列化
        return calculateWithPrecomputedRules(itemStack, rules);
    }
    
    /**
     * 使用预获取的规则列表计算稀有度（内部优化路径）
     * 仅序列化ItemStack一次，使用matchesNbt避免规则内重复序列化
     * @param itemStack 物品堆
     * @param rules 预获取的匹配规则列表（非空）
     * @return 计算的稀有度
     */
    @Nullable
    private static Integer calculateWithPrecomputedRules(ItemStack itemStack, List<ItemDataMatchRule> rules) {
        CompoundTag nbt = getItemStackTag(itemStack);
        if (nbt == null) {
            return null;
        }
        
        // 规则已经在注册时按优先级排序好了
        // 查找第一个匹配的规则（使用matchesNbt避免重复序列化）
        for (ItemDataMatchRule rule : rules) {
            if (rule != null && rule.isEnabled() && rule.matchesNbt(nbt)) {
                return rule.getRarity();
            }
        }
        
        return null;
    }
    
    /**
     * 直接计算稀有度(不使用缓存,供缓存内部调用)
     * @param itemStack 物品堆
     * @return 计算的稀有度
     */
    public static Integer calculateWithoutCache(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        
        Item item = itemStack.getItem();
        if (item == null) {
            return null;
        }
        
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return null;
        }
        
        List<ItemDataMatchRule> rules = RULES_CACHE.getOrDefault(itemId, Collections.emptyList());
        if (rules.isEmpty()) {
            return null;
        }
        
        // 使用优化路径：序列化一次，规则复用该tag
        return calculateWithPrecomputedRules(itemStack, rules);
    }
    
    /**
     * 注册匹配规则
     * @param rule 要注册的匹配规则
     */
    public static void registerRule(ItemDataMatchRule rule) {
        if (rule == null || rule.getItemId() == null) {
            RarityCore.LOGGER.warn("尝试注册无效的物品数据匹配规则");
            return;
        }
        
        List<ItemDataMatchRule> rules = RULES_CACHE.computeIfAbsent(rule.getItemId(), k -> new ArrayList<>());
        rules.add(rule);
        
        // 按优先级降序排序(数值大的优先级高)
        rules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        RarityCore.LOGGER.debug("注册物品数据匹配规则: {} -> 稀有度{} (优先级:{})", 
            rule.getItemId(), rule.getRarity(), rule.getPriority());
    }
    
    /**
     * 为指定物品ID清除所有规则
     * @param itemId 物品ID
     */
    public static void clearRulesForResource(ResourceLocation itemId) {
        RULES_CACHE.remove(itemId);
        RarityCore.LOGGER.debug("清除物品 {} 的所有物品数据匹配规则", itemId);
    }
    
    /**
     * 重新加载所有规则(通常在配置文件更改后调用)
     */
    public static void reloadRules() {
        RULES_CACHE.clear();
        RarityCore.LOGGER.info("物品数据匹配规则缓存已清空,等待重新加载配置");
        
        // 触发配置重新加载
        ItemDataConfigLoader.loadAllConfigs();
    }
    
    /**
     * 获取当前缓存的规则数量统计
     * @return 各物品ID的规则数量映射
     */
    public static Map<ResourceLocation, Integer> getRuleStatistics() {
        Map<ResourceLocation, Integer> stats = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<ItemDataMatchRule>> entry : RULES_CACHE.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }
    
    /**
     * 验证规则的有效性
     * @param rule 要验证的规则
     * @return 是否有效
     */
    public static boolean validateRule(ItemDataMatchRule rule) {
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
     * 检查指定物品是否有组件匹配规则
     * @param itemId 物品资源位置
     * @return 如果有规则返回true
     */
    public static boolean hasRulesForItem(ResourceLocation itemId) {
        if (itemId == null) {
            return false;
        }
        List<ItemDataMatchRule> rules = RULES_CACHE.get(itemId);
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
     * @return 规则缓存的副本
     */
    public static Map<ResourceLocation, List<ItemDataMatchRule>> getRulesCacheForSync() {
        return new HashMap<>(RULES_CACHE);
    }
    
    /**
     * 清空所有规则(仅供同步使用)
     */
    public static void clearAllRules() {
        RULES_CACHE.clear();
        RarityCore.LOGGER.debug("已清空所有物品数据匹配规则");
    }
}