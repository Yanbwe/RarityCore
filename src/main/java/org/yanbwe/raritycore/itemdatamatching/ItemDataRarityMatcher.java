package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ItemDataRarityMatcher {

    private static final Map<Identifier, List<ItemDataMatchRule>> RULES_CACHE =
        new ConcurrentHashMap<>();

    @Nullable
    private static DataComponentWrapper getItemStackData(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        try {
            return new DataComponentWrapper(itemStack);
        } catch (Exception e) {
            RarityCore.LOGGER.warn("无法获取ItemStack数据组件: {}", e.getMessage());
        }

        return null;
    }

    @Nullable
    public static Integer getItemDataMatchedRarity(ItemStack itemStack) {
        if (getItemStackData(itemStack) == null) {
            return null;
        }

        Integer calculatedRarity = calculateWithoutCache(itemStack);

        return calculatedRarity;
    }

    public static Integer calculateWithoutCache(ItemStack itemStack) {
        if (getItemStackData(itemStack) == null) {
            return null;
        }

        Item item = itemStack.getItem();
        if (item == null) {
            return null;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return null;
        }

        List<ItemDataMatchRule> rules = RULES_CACHE.getOrDefault(itemId, Collections.emptyList());
        if (rules.isEmpty()) {
            return null;
        }

        DataComponentWrapper data = getItemStackData(itemStack);
        if (data == null) {
            return null;
        }

        for (ItemDataMatchRule rule : rules) {
            if (rule != null && rule.isEnabled() && rule.matches(data)) {
                return rule.getRarity();
            }
        }

        return null;
    }

    public static void registerRule(ItemDataMatchRule rule) {
        if (rule == null || rule.getItemId() == null) {
            RarityCore.LOGGER.warn("尝试注册无效的物品数据匹配规则");
            return;
        }

        List<ItemDataMatchRule> rules = RULES_CACHE.computeIfAbsent(rule.getItemId(), k -> new ArrayList<>());
        rules.add(rule);

        rules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        RarityCore.LOGGER.debug("注册物品数据匹配规则: {} -> 稀有度{} (优先级:{})",
            rule.getItemId(), rule.getRarity(), rule.getPriority());
    }

    public static void clearRulesForResource(Identifier itemId) {
        RULES_CACHE.remove(itemId);
        RarityCore.LOGGER.debug("清除物品 {} 的所有物品数据匹配规则", itemId);
    }

    public static void reloadRules() {
        RULES_CACHE.clear();
        RarityCore.LOGGER.info("物品数据匹配规则缓存已清空,等待重新加载配置");

        ItemDataConfigLoader.loadAllConfigs();
    }

    public static Map<Identifier, Integer> getRuleStatistics() {
        Map<Identifier, Integer> stats = new HashMap<>();
        for (Map.Entry<Identifier, List<ItemDataMatchRule>> entry : RULES_CACHE.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }

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

    public static int getRuleCount() {
        return RULES_CACHE.values().stream()
            .mapToInt(List::size)
            .sum();
    }

    public static Map<Identifier, List<ItemDataMatchRule>> getRulesCacheForSync() {
        return new HashMap<>(RULES_CACHE);
    }

    public static void clearAllRules() {
        RULES_CACHE.clear();
        RarityCore.LOGGER.debug("已清空所有物品数据匹配规则");
    }
}