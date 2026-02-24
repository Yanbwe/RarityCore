package org.yanbwe.raritycore.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.yanbwe.raritycore.Raritycore;

import java.util.concurrent.ConcurrentHashMap;

public class SimpleCacheManager {
    /**
     * 物品稀有度缓存
     */
    private static final ConcurrentHashMap<Item, Integer> ITEM_RARITY_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 物品栈缓存（基于哈希码）
     */
    private static final ConcurrentHashMap<Integer, Integer> ITEMSTACK_CACHE = new ConcurrentHashMap<>();
    
    // 缓存统计
    private static long cacheHits = 0;
    private static long cacheMisses = 0;
    
    /**
     * 获取物品的稀有度（带缓存）
     * @param item 物品
     * @return 稀有度等级
     */
    public static int getCachedRarity(Item item) {
        Integer cached = ITEM_RARITY_CACHE.get(item);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        
        cacheMisses++;
        // 从注册表获取并缓存
        int rarity = org.yanbwe.raritycore.registry.RarityRegistry.getRarity(item);
        ITEM_RARITY_CACHE.put(item, rarity);
        return rarity;
    }
    
    /**
     * 获取物品栈的稀有度（带缓存）
     * @param itemStack 物品栈
     * @return 稀有度等级
     */
    public static int getCachedRarity(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return 1;
        }
        
        int hash = itemStack.hashCode();
        Integer cached = ITEMSTACK_CACHE.get(hash);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        
        cacheMisses++;
        // 从物品获取并缓存
        int rarity = getCachedRarity(itemStack.getItem());
        ITEMSTACK_CACHE.put(hash, rarity);
        return rarity;
    }
    
    /**
     * 清空所有缓存
     */
    public static void clearCache() {
        ITEM_RARITY_CACHE.clear();
        ITEMSTACK_CACHE.clear();
        cacheHits = 0;
        cacheMisses = 0;
        Raritycore.LOGGER.debug("Cache cleared");
    }
    
    /**
     * 获取缓存统计信息
     * @return 统计信息字符串
     */
    public static String getCacheStats() {
        long total = cacheHits + cacheMisses;
        double hitRate = total > 0 ? (double) cacheHits / total * 100 : 0;
        
        return String.format("缓存统计 - 命中: %d, 未命中: %d, 命中率: %.1f%%, 物品缓存: %d, 物品栈缓存: %d",
            cacheHits, cacheMisses, hitRate, ITEM_RARITY_CACHE.size(), ITEMSTACK_CACHE.size());
    }
    
    /**
     * 获取缓存大小
     * @return 总缓存条目数
     */
    public static int getCacheSize() {
        return ITEM_RARITY_CACHE.size() + ITEMSTACK_CACHE.size();
    }
    
    /**
     * 预加载常用物品的稀有度到缓存
     */
    public static void preloadCommonItems() {
        // 预加载一些常见的原版物品
        try {
            String[] commonItems = {
                "minecraft:diamond", "minecraft:gold_ingot", "minecraft:iron_ingot",
                "minecraft:netherite_ingot", "minecraft:emerald", "minecraft:diamond_sword",
                "minecraft:netherite_sword", "minecraft:enchanted_golden_apple", "minecraft:dragon_egg"
            };
            
            for (String itemId : commonItems) {
                Identifier id = new Identifier(itemId);
                Item item = Registries.ITEM.get(id);
                if (item != null && item != Registries.ITEM.get(Registries.ITEM.getDefaultId())) {
                    int rarity = org.yanbwe.raritycore.registry.RarityRegistry.getRarity(item);
                    ITEM_RARITY_CACHE.put(item, rarity);
                }
            }
            
            Raritycore.LOGGER.debug("Preloaded {} common items into cache", commonItems.length);
        } catch (Exception e) {
            Raritycore.LOGGER.warn("Failed to preload common items", e);
        }
    }
}