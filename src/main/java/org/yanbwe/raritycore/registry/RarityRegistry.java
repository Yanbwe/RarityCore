package org.yanbwe.raritycore.registry;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.yanbwe.raritycore.Raritycore;
import org.yanbwe.raritycore.network.RaritySyncPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RarityRegistry {
    /**
     * 物品稀有度映射
     */
    public static final ConcurrentHashMap<Identifier, Integer> ITEM_RARITY_MAP = new ConcurrentHashMap<>();
    
    /**
     * 数据包加载的稀有度数据（优先级较低）
     */
    private static final ConcurrentHashMap<Identifier, Integer> DATAPACK_RARITY_MAP = new ConcurrentHashMap<>();
    
    /**
     * 数据加载器实例
     */
    public static final RarityDataLoader DATA_LOADER = new RarityDataLoader();
    
    /**
     * 注册物品的稀有度等级
     * @param item 要注册稀有度的物品
     * @param rarity 稀有度等级 (1-7)
     */
    public static void register(Item item, int rarity) {
        Identifier itemId = Registries.ITEM.getId(item);
        if (itemId != null && !itemId.equals(Registries.ITEM.getDefaultId())) {
            ITEM_RARITY_MAP.put(itemId, rarity);
            Raritycore.LOGGER.debug("Registered rarity {} for item {}", rarity, itemId);
        }
    }
    
    /**
     * 获取物品的稀有度等级
     * 优先级：直接注册 > FinalRarity.json配置 > FinalRarityConfig文件夹配置 > 数据包配置
     * @param item 要查询稀有度的物品
     * @return 稀有度等级，未注册则返回1(普通)
     */
    public static int getRarity(Item item) {
        Identifier itemId = Registries.ITEM.getId(item);
        if (itemId != null) {
            // 1. 首先检查直接注册的数据（最高优先级）
            Integer directRarity = ITEM_RARITY_MAP.get(itemId);
            if (directRarity != null) {
                return directRarity;
            }
            
            // 2. 检查FinalRarity.json配置（通过命令设置的，较高优先级）
            Integer finalRarity = DATAPACK_RARITY_MAP.get(itemId);
            if (finalRarity != null) {
                return finalRarity;
            }
        }
        return 1;
    }
    
    /**
     * 获取物品栈的稀有度等级
     * @param itemStack 要查询稀有度的物品栈
     * @return 稀有度等级
     */
    public static int getRarity(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return 1;
        }
        return getRarity(itemStack.getItem());
    }
    
    /**
     * 将所有稀有度数据同步到指定客户端
     * @param player 目标玩家
     */
    public static void syncRarityToClient(ServerPlayerEntity player) {
        RaritySyncPacket.sendToClient(player);
        Raritycore.LOGGER.debug("Synced rarity data to player {}", player.getName().getString());
    }
    
    /**
     * 从数据包配置注册稀有度
     * @param itemId 物品ID
     * @param rarity 稀有度等级
     */
    public static void registerDatapackRarity(Identifier itemId, int rarity) {
        if (rarity >= 1 && rarity <= 7) {
            DATAPACK_RARITY_MAP.put(itemId, rarity);
            Raritycore.LOGGER.debug("Registered datapack rarity {} for item {}", rarity, itemId);
        }
    }
    
    /**
     * 移除物品的稀有度配置
     * @param item 要移除稀有度的物品
     */
    public static void removeRarity(Item item) {
        Identifier itemId = Registries.ITEM.getId(item);
        if (itemId != null && !itemId.equals(Registries.ITEM.getDefaultId())) {
            ITEM_RARITY_MAP.remove(itemId);
            DATAPACK_RARITY_MAP.remove(itemId);
            Raritycore.LOGGER.debug("Removed rarity configuration for item {}", itemId);
        }
    }
    
    /**
     * 移除指定物品ID的稀有度配置
     * @param itemId 物品ID
     */
    public static void removeRarity(Identifier itemId) {
        if (itemId != null) {
            ITEM_RARITY_MAP.remove(itemId);
            DATAPACK_RARITY_MAP.remove(itemId);
            Raritycore.LOGGER.debug("Removed rarity configuration for item {}", itemId);
        }
    }
    
    /**
     * 清空数据包稀有度数据
     */
    public static void clearDatapackData() {
        DATAPACK_RARITY_MAP.clear();
        Raritycore.LOGGER.debug("Cleared datapack rarity data");
    }
    
    /**
     * 获取所有稀有度数据
     * @return 包含所有注册稀有度的映射
     */
    public static Map<Identifier, Integer> getAllRarities() {
        Map<Identifier, Integer> allRarities = new HashMap<>(ITEM_RARITY_MAP);
        // 添加数据包数据（直接注册的优先级更高）
        for (Map.Entry<Identifier, Integer> entry : DATAPACK_RARITY_MAP.entrySet()) {
            allRarities.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return allRarities;
    }
    
    /**
     * 获取数据包条目数量
     * @return 数据包中注册的物品数量
     */
    public static int getDatapackEntryCount() {
        return DATAPACK_RARITY_MAP.size();
    }
    
    /**
     * 稀有度数据加载器
     */
    public static class RarityDataLoader extends SinglePreparationResourceReloader<Map<Identifier, Integer>> {
        
        @Override
        protected Map<Identifier, Integer> prepare(ResourceManager manager, Profiler profiler) {
            Map<Identifier, Integer> rarityData = new HashMap<>();
            // 这里将在后续实现具体的配置文件加载逻辑
            Raritycore.LOGGER.info("Preparing rarity data loader");
            return rarityData;
        }
        
        @Override
        protected void apply(Map<Identifier, Integer> prepared, ResourceManager manager, Profiler profiler) {
            ITEM_RARITY_MAP.clear();
            ITEM_RARITY_MAP.putAll(prepared);
            Raritycore.LOGGER.info("Applied {} rarity entries from data loader", prepared.size());
        }
    }
}