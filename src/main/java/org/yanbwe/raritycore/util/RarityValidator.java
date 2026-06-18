package org.yanbwe.raritycore.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * 稀有度验证工具类
 * 统一处理稀有度值和物品的有效性验证
 */
public class RarityValidator {
    
    /**
     * 验证稀有度值是否有效
     * @param rarity 稀有度值
     * @return 是否有效
     */
    public static boolean isValidRarity(int rarity) {
        return rarity >= RarityConstants.MIN_RARITY && rarity <= RarityConstants.MAX_RARITY;
    }
    
    /**
     * 标准化稀有度值，遵循模组的包容性原则
     * 小于1的值视为1，大于7的值视为7
     * @param rarity 稀有度值
     * @return 标准化后的稀有度值 (1-7)
     */
    public static int normalizeRarity(int rarity) {
        if (rarity < RarityConstants.MIN_RARITY) {
            return RarityConstants.MIN_RARITY; // 小于1视为1（普通）
        } else if (rarity > RarityConstants.MAX_RARITY) {
            return RarityConstants.MAX_RARITY; // 大于7视为7（唯一）
        }
        return rarity; // 1-7范围内保持原值
    }
    
    /**
     * 验证物品是否有效
     * @param item 物品对象
     * @return 是否有效
     */
    public static boolean isValidItem(Item item) {
        if (item == null) {
            return false;
        }
        
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        return itemId != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey());
    }
    
    /**
     * 获取物品的资源位置标识符
     * @param item 物品对象
     * @return 资源位置，如果物品无效则返回null
     */
    public static Identifier getItemId(Item item) {
        if (!isValidItem(item)) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(item);
    }
    
    /**
     * 验证边框样式是否有效
     * @param borderStyle 边框样式值
     * @return 是否有效
     */
    public static boolean isValidBorderStyle(int borderStyle) {
        return borderStyle == 0 || borderStyle == 1; // 0为空心，1为实心
    }
    
    /**
     * 验证边框样式，如果无效则返回默认值
     * @param borderStyle 边框样式值
     * @return 有效的边框样式值
     */
    public static int validateBorderStyle(int borderStyle) {
        if (isValidBorderStyle(borderStyle)) {
            return borderStyle;
        }
        return RarityConstants.DEFAULT_ITEM_BORDER_STYLE; // 默认为空心
    }
}