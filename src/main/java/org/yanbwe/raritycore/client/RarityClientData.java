package org.yanbwe.raritycore.client;

import net.minecraft.util.Identifier;
import org.yanbwe.raritycore.Raritycore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RarityClientData {
    /**
     * 客户端稀有度数据缓存
     */
    private static final ConcurrentHashMap<Identifier, Integer> CLIENT_RARITY_MAP = new ConcurrentHashMap<>();
    
    /**
     * 更新客户端稀有度数据
     * @param rarityData 新的稀有度数据
     */
    public static void updateRarityData(Map<Identifier, Integer> rarityData) {
        CLIENT_RARITY_MAP.clear();
        CLIENT_RARITY_MAP.putAll(rarityData);
        Raritycore.LOGGER.debug("Client rarity data updated with {} entries", rarityData.size());
    }
    
    /**
     * 获取物品的稀有度（客户端）
     * @param itemId 物品ID
     * @return 稀有度等级
     */
    public static int getRarity(Identifier itemId) {
        return CLIENT_RARITY_MAP.getOrDefault(itemId, 1);
    }
    
    /**
     * 检查是否存在指定物品的稀有度数据
     * @param itemId 物品ID
     * @return 是否存在
     */
    public static boolean hasRarityData(Identifier itemId) {
        return CLIENT_RARITY_MAP.containsKey(itemId);
    }
    
    /**
     * 获取当前缓存的稀有度数据条目数
     * @return 条目数量
     */
    public static int getCachedEntriesCount() {
        return CLIENT_RARITY_MAP.size();
    }
    
    /**
     * 清空客户端缓存
     */
    public static void clearCache() {
        CLIENT_RARITY_MAP.clear();
        Raritycore.LOGGER.debug("Client rarity cache cleared");
    }
}