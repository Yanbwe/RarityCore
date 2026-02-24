package org.yanbwe.raritycore.compat.apotheosis;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.lang.reflect.Method;

/**
 * 神化模组兼容性适配器
 * 实现神化模组稀有度与本模组稀有度的映射
 */
public class ApotheosisAdapter {
    
    private static boolean isInitialized = false;
    private static Class<?> lootRarityClass;
    private static Class<?> rarityRegistryClass;
    private static Method getMaterialMethod;
    private static Method isMaterialMethod;
    private static Method getMaterialRarityMethod;
    
    /**
     * 初始化神化模组兼容性适配器
     */
    public static void init() {
        if (isInitialized) {
            return;
        }
        
        if (!ModList.get().isLoaded("apotheosis")) {
            RarityCore.LOGGER.debug("Apotheosis mod not detected, skipping initialization");
            return;
        }
        
        try {
            // 加载神化模组的类
            lootRarityClass = Class.forName("dev.shadowsoffire.apotheosis.adventure.loot.LootRarity");
            rarityRegistryClass = Class.forName("dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry");
            
            // 获取必要的方法
            getMaterialMethod = lootRarityClass.getMethod("getMaterial");
            isMaterialMethod = rarityRegistryClass.getMethod("isMaterial", net.minecraft.world.item.Item.class);
            getMaterialRarityMethod = rarityRegistryClass.getMethod("getMaterialRarity", net.minecraft.world.item.Item.class);
            
            isInitialized = true;
            RarityCore.LOGGER.info("Apotheosis compatibility adapter initialized");
            
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize Apotheosis compatibility adapter", e);
        }
    }
    
    /**
     * 检查物品是否具有神化稀有度
     */
    public static boolean hasApotheosisRarity(ItemStack itemStack) {
        if (!isInitialized || itemStack.isEmpty()) {
            return false;
        }
        
        try {
            // 首先检查是否有NBT数据中的稀有度信息（适用于装备）
            if (itemStack.hasTag() && itemStack.getTag().contains("affix_data")) {
                return true;
            }
            
            // 检查物品是否是稀有度材料（适用于材料物品）
            Object item = itemStack.getItem();
            Boolean isMaterial = (Boolean) isMaterialMethod.invoke(null, item);
            return isMaterial != null && isMaterial;
            
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to check Apotheosis rarity for item: {}", itemStack.getItem(), e);
            return false;
        }
    }
    
    /**
     * 获取物品的神化稀有度并映射到本模组稀有度
     * @param itemStack 要检查的物品
     * @return 映射后的稀有度等级 (1-6)，如果没有神化稀有度则返回null
     */
    public static Integer getMappedRarity(ItemStack itemStack) {
        if (!isInitialized || itemStack.isEmpty()) {
            return null;
        }
        
        try {
            // 首先尝试从NBT数据中获取稀有度（适用于装备、宝石等所有物品）
            if (itemStack.hasTag() && itemStack.getTag().contains("affix_data")) {
                net.minecraft.nbt.CompoundTag affixData = itemStack.getTag().getCompound("affix_data");
                if (affixData.contains("rarity")) {
                    String rarityString = affixData.getString("rarity");
                    return mapApotheosisRarityString(rarityString);
                }
            }
            
            // 如果NBT中没有，则回退到材料稀有度检查（适用于特殊材料物品）
            Object item = itemStack.getItem();
            Boolean isMaterial = (Boolean) isMaterialMethod.invoke(null, item);
            
            if (isMaterial != null && isMaterial) {
                // 获取对应的稀有度
                Object rarityHolder = getMaterialRarityMethod.invoke(null, item);
                
                // 检查holder是否绑定
                Method isBoundMethod = rarityHolder.getClass().getMethod("isBound");
                Boolean isBound = (Boolean) isBoundMethod.invoke(rarityHolder);
                
                if (isBound != null && isBound) {
                    // 获取稀有度对象
                    Method getMethod = rarityHolder.getClass().getMethod("get");
                    Object rarity = getMethod.invoke(rarityHolder);
                    
                    // 获取ordinal值
                    Method ordinalMethod = rarity.getClass().getMethod("ordinal");
                    Integer ordinal = (Integer) ordinalMethod.invoke(rarity);
                    
                    // 映射神化稀有度到本模组稀有度
                    // Apotheosis: Common(0)→1, Uncommon(1)→2, Rare(2)→3, Epic(3)→4, Mythic(4)→5, Ancient(5)→6
                    if (ordinal != null && ordinal >= 0 && ordinal <= 5) {
                        return ordinal + 1; // 神化的ordinal + 1 = 本模组稀有度
                    }
                }
            }
            
            return null;
            
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to get mapped Apotheosis rarity for item: {}", itemStack.getItem(), e);
            return null;
        }
    }
    
    /**
     * 根据神化稀有字符串映射到本模组稀有度
     * @param rarityString 神化稀有度字符串，格式如 "apotheosis:common", "apotheosis:epic" 等
     * @return 对应的本模组稀有度等级 (1-6)
     */
    private static Integer mapApotheosisRarityString(String rarityString) {
        if (rarityString == null || rarityString.isEmpty()) {
            return null;
        }
        
        // 移除命名空间前缀
        String rarityName = rarityString;
        if (rarityName.contains(":")) {
            rarityName = rarityName.substring(rarityName.indexOf(":") + 1);
        }
        
        // 根据神化稀有度名称进行映射
        switch (rarityName.toLowerCase()) {
            case "common":
                return 1;    // Common → 稀有度1
            case "uncommon":
                return 2;    // Uncommon → 稀有度2
            case "rare":
                return 3;    // Rare → 稀有度3
            case "epic":
                return 4;    // Epic → 稀有度4
            case "mythic":
                return 5;    // Mythic → 稀有度5
            case "ancient":
                return 6;    // Ancient → 稀有度6
            default:
                // 根据调试日志管理规范，删除高频触发的未知稀有度日志输出
                // 神化模组稀有度种类不可控，避免日志刷屏
                return null;
        }
    }
    
    /**
     * 重置初始化状态（主要用于测试）
     */
    public static void reset() {
        isInitialized = false;
        lootRarityClass = null;
        rarityRegistryClass = null;
        getMaterialMethod = null;
        isMaterialMethod = null;
        getMaterialRarityMethod = null;
    }
}