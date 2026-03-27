package org.yanbwe.raritycore.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.NotNull;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.compat.CompatibilityChecker;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.network.ChangeOperation;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.util.RarityConstants;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

public class RarityRegistry {

    
    /**
     * 物品稀有度映射(来自 FinalRarity.json、数据包等用户手动配置)
     */
    public static final ConcurrentHashMap<ResourceLocation, Integer> ITEM_RARITY_MAP = new ConcurrentHashMap<>();
    
    /**
     * 自动计算的稀有度映射(来自 auto_rarity.json,优先级低于 ITEM_RARITY_MAP)
     */
    private static final ConcurrentHashMap<ResourceLocation, Integer> AUTO_RARITY_MAP = new ConcurrentHashMap<>();
    

    
    /**
     * 放入自动计算的稀有度配置
     * @param itemId 物品资源位置
     * @param rarity 稀有度等级
     */
    public static void putAutoRarity(ResourceLocation itemId, int rarity) {
        AUTO_RARITY_MAP.put(itemId, rarity);
    }
    
    /**
     * 移除自动计算的稀有度配置
     * @param itemId 物品资源位置
     */
    public static void removeAutoRarity(ResourceLocation itemId) {
        AUTO_RARITY_MAP.remove(itemId);
    }

    /**
     * 注册物品的稀有度等级
     * 1普通,2稀有,3罕见,4史诗,5传说,6神话,7唯一
     * 不注册视为普通品质
     * @param item 要注册稀有度的物品
     * @param rarity 稀有度等级
     */
    public static void register(@Nullable Item item, int rarity) {
        register(item, rarity, true);
    }
    
    /**
     * 注册物品的稀有度等级
     * 1普通,2稀有,3罕见,4史诗,5传说,6神话,7唯一
     * 不注册视为普通品质
     * @param item 要注册稀有度的物品
     * @param rarity 稀有度等级
     * @param syncToClients 是否同步到客户端
     */
    public static void register(@Nullable Item item, int rarity, boolean syncToClients) {
        if (item != null) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                Integer oldRarity = ITEM_RARITY_MAP.put(itemId, rarity);
                
                // 发布稀有度变更事件
                RarityChangeEvent.ChangeType changeType = (oldRarity == null) ? 
                    RarityChangeEvent.ChangeType.REGISTER : RarityChangeEvent.ChangeType.UPDATE;
                NeoForge.EVENT_BUS.post(new RarityChangeEvent(item, oldRarity, rarity, changeType));
                
                // 如果需要同步到客户端且当前在服务端环境中,记录变更操作
                if (syncToClients) {
                    // 记录变更操作
                    if (oldRarity == null) {
                        // 新增操作
                        SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.ADD, itemId, rarity));
                    } else {
                        // 更新操作
                        SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.UPDATE, itemId, rarity));
                    }
                    
                    // 同步到客户端
                    SyncManager.syncRarityToClients(ITEM_RARITY_MAP);
                }
            }
        }
    }
    
    /**
     * 删除物品的稀有度注册
     * @param item 要删除稀有度注册的物品
     * @param syncToClients 是否同步到客户端
     */
    public static void unregister(@Nullable Item item, boolean syncToClients) {
        if (item != null) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                Integer removedRarity = ITEM_RARITY_MAP.remove(itemId);
                
                // 发布稀有度变更事件
                if (removedRarity != null) {
                    NeoForge.EVENT_BUS.post(new RarityChangeEvent(
                        item, removedRarity, null, RarityChangeEvent.ChangeType.REMOVE));
                }
                
                // 如果需要同步到客户端且当前在服务端环境中,记录删除操作
                if (syncToClients) {
                    // 记录删除操作
                    if (removedRarity != null) {
                        SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.DELETE, itemId, null));
                    }
                    
                    // 同步到客户端
                    SyncManager.syncRarityToClients(ITEM_RARITY_MAP);
                }
            }
        }
    }
    


    /**
     * 获取物品的稀有度等级(标准化版本)
     * 遵循模组的包容性原则:小于1的值视为1,大于7的值视为7
     * @param item 要查稀有度的物品
     * @return 标准化后的物品稀有度等级(1-7)
     */
    public static @NotNull Integer getNormalizedRarity(@Nullable Item item) {
        Integer rawRarity = getRarity(item);
        return org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rawRarity);
    }
    
    /**
     * 获取物品栈的稀有度等级(标准化版本,支持物品数据匹配)
     * 遵循模组的包容性原则:小于1的值视为1,大于7的值视为7
     * @param itemStack 要查稀有度的物品栈
     * @return 标准化后的物品稀有度等级(1-7)
     */
    public static @NotNull Integer getNormalizedRarity(@Nullable ItemStack itemStack) {
        Integer rawRarity = getRarity(itemStack);
        return org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rawRarity);
    }
    
    /**
     * 获取本地化文本
     * @param key 本地化键
     * @return 本地化文本
     */
    private static String getLocalizedText(String key) {
        try {
            // 直接使用和原版工具提示系统一样的方式
            return net.minecraft.client.resources.language.I18n.get(key);
        } catch (Exception e) {
            // 本地化失败时返回原始键
            RarityCore.LOGGER.debug("Error getting localized text for key: {}", key, e);
            return key;
        }
    }
    
    /**
     * 获取物品的完整稀有度工具提示字符串(支持本地化)
     * 返回格式示例:
     * - 普通物品:"[普通] ⭐" (中文) 或 "[Common] ⭐" (英文)
     * - 高级物品:"[5级稀有度-⭐⭐⭐⭐⭐]"
     * @param item 要获取工具提示的物品
     * @return 本地化的稀有度工具提示字符串
     */
    public static @NotNull String getLocalizedRarityTooltip(@Nullable Item item) {
        if (item == null) {
            return "[普通]"; // 默认返回普通稀有度
        }
        
        // 获取物品稀有度
        Integer rarity = getRarity(item);
        if (rarity == null) {
            rarity = RarityConstants.RARITY_COMMON;
        }
        
        // 先检查是否为特殊稀有度(大于7),保存原始值用于显示
        boolean isSpecialRarity = rarity > RarityConstants.RARITY_UNIQUE;
        int displayRarity = rarity; // 保存用于显示的原始稀有度值
        
        // 标准化稀有度值用于内部处理
        rarity = org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rarity);
        
        // 构建工具提示字符串
        if (isSpecialRarity) {
            // 特殊稀有度(大于 7 级)
            String stars = org.yanbwe.raritycore.util.ComponentBuilder.getStars(displayRarity);
            
            // 检查是否有自定义特殊稀有度文本
            String customText = org.yanbwe.raritycore.config.StarDisplayConfigManager.getCustomSpecialRarityText(displayRarity);
            
            if (customText != null && !customText.isEmpty()) {
                // 使用自定义文本,但保持完整格式:[自定义文本 - 星星]
                return "[" + customText + "-" + stars + "]";
            } else {
                // 使用默认格式:[xx 级稀有度 - 星星]
                return "[" + displayRarity + "级稀有度-" + stars + "]";
            }
        } else {
            // 标准稀有度(1-7级)
            String rarityKey;
            switch (rarity) {
                case RarityConstants.RARITY_COMMON:
                    rarityKey = "rarity.core.common";
                    break;
                case RarityConstants.RARITY_UNCOMMON:
                    rarityKey = "rarity.core.uncommon";
                    break;
                case RarityConstants.RARITY_RARE:
                    rarityKey = "rarity.core.rare";
                    break;
                case RarityConstants.RARITY_EPIC:
                    rarityKey = "rarity.core.epic";
                    break;
                case RarityConstants.RARITY_LEGENDARY:
                    rarityKey = "rarity.core.legendary";
                    break;
                case RarityConstants.RARITY_MYTHICAL:
                    rarityKey = "rarity.core.mythical";
                    break;
                case RarityConstants.RARITY_UNIQUE:
                    rarityKey = "rarity.core.unique";
                    break;
                default:
                    rarityKey = "rarity.core.common";
                    break;
            }
            
            // 获取本地化文本
            String localizedLabel = net.minecraft.client.resources.language.I18n.get(rarityKey);
            String stars = org.yanbwe.raritycore.util.ComponentBuilder.getStars(rarity);
            return localizedLabel + " " + stars;
        }
    }
    
    /**
     * 获取物品栈的稀有度等级(支持物品数据)
     * 优先级顺序:物品数据匹配 > 神化模组稀有度 > 本模组稀有度(配置和数据包) > 原版稀有度映射
     * @param itemStack 要查稀有度的物品栈
     * @return 物品的稀有度等级(1-7)
     */
    public static @NotNull Integer getRarity(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return 1;
        }
        
        Item item = itemStack.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return 1;
        }
        
        // 使用统一的稀有度获取逻辑
        return getRarityInternal(itemId, itemStack, item);
    }
    
    /**
     * 获取物品的稀有度等级
     * 优先级顺序:物品数据匹配 > 神化模组稀有度 > 本模组稀有度(配置和数据包) > 原版稀有度映射
     * @param item 要查稀有度的物品
     * @return 物品的稀有度等级(1-7)
     */
    public static @NotNull Integer getRarity(@Nullable Item item) {
        if (item != null) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                ItemStack tempStack = new ItemStack(item);
                return getRarityInternal(itemId, tempStack, item);
            }
        }
        return 1; // 默认为普通
    }
    
    /**
     * 统一的稀有度获取逻辑
     * 按照以下优先级顺序获取稀有度:
     * 1. 物品数据匹配配置(最高优先级)
     * 2. 神化模组稀有度
     * 3. 本模组的稀有度配置(包括配置文件和数据包)
     * 4. 自动计算的稀有度配置
     * 5. 原版稀有度映射(最低优先级)
     * 
     * @param itemId 物品资源位置,用于查找配置的稀有度
     * @param itemStack 物品栈,用于检查物品数据和神化模组稀有度
     * @param item 物品实例,用于获取默认稀有度
     * @return 物品的稀有度等级(1-7),如果没有找到匹配的稀有度,返回1(普通)
     */
    private static @NotNull Integer getRarityInternal(ResourceLocation itemId, @Nullable ItemStack itemStack, Item item) {
        Integer rarity;
        
        // 首先检查物品数据匹配配置(最高优先级)
        rarity = checkItemDataRarity(itemStack);
        if (rarity != null) {
            // 填充缓存
            if (itemStack != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 然后检查神化模组稀有度
        rarity = checkApotheosisRarity(itemStack);
        if (rarity != null) {
            // 填充缓存
            if (itemStack != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 然后检查本模组的稀有度配置(包括配置文件和数据包)- 最高优先级
        rarity = ITEM_RARITY_MAP.get(itemId);
        if (rarity != null) {
            // 填充缓存
            if (itemStack != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 然后检查自动计算的稀有度配置 - 中等优先级(低于 FinalRarity,高于原版)
        rarity = AUTO_RARITY_MAP.get(itemId);
        if (rarity != null) {
            // 填充缓存
            if (itemStack != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 最后检查原版稀有度映射(最低优先级)
        rarity = checkVanillaRarity(itemStack, item);
        if (rarity != null) {
            // 填充缓存
            if (itemStack != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 默认返回普通稀有度
        rarity = 1;
        // 填充缓存
        if (itemStack != null) {
            org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
        }
        return rarity;
    }
    
    /**
     * 检查物品数据匹配稀有度
     * @param itemStack 物品栈
     * @return 稀有度等级,如果没有匹配则返回null
     */
    private static Integer checkItemDataRarity(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        return org.yanbwe.raritycore.itemdatamatching.ItemDataRarityMatcher.getItemDataMatchedRarity(itemStack);
    }
    
    /**
     * 检查神化模组稀有度
     * @param itemStack 物品栈
     * @return 稀有度等级,如果没有匹配则返回null
     */
    private static Integer checkApotheosisRarity(@Nullable ItemStack itemStack) {
        if (org.yanbwe.raritycore.config.ServerConfigManager.isCheckApotheosisRarity() && itemStack != null) {
            return org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter.getMappedRarity(itemStack);
        }
        return null;
    }
    
    /**
     * 检查原版稀有度
     * @param itemStack 物品栈
     * @param item 物品
     * @return 稀有度等级,如果没有匹配则返回null
     */
    private static Integer checkVanillaRarity(@Nullable ItemStack itemStack, Item item) {
        if (org.yanbwe.raritycore.config.ServerConfigManager.isCheckVanillaRarity()) {
            if (CompatibilityChecker.isVanillaRarityApiAvailable()) {
                try {
                    Rarity vanillaRarity;
                    if (itemStack != null) {
                        vanillaRarity = itemStack.getRarity();
                    } else if (item != null) {
                        vanillaRarity = item.getDefaultInstance().getRarity();
                    } else {
                        return null;
                    }
                    Integer mappedVanilla = mapVanillaRarity(vanillaRarity);
                    if (mappedVanilla > 1) { // 只有当原版稀有度不是普通时才使用
                        return mappedVanilla;
                    }
                } catch (Throwable e) {
                    // 记录异常信息,便于调试
                    RarityCore.LOGGER.debug("Error checking vanilla rarity", e);
                }
            }
        }
        return null;
    }
    
    /**
     * 映射原版稀有度到本模组稀有度
     * @param vanillaRarity 原版稀有度
     * @return 映射后的稀有度等级(1-7)
     */
    private static Integer mapVanillaRarity(Rarity vanillaRarity) {
        if (vanillaRarity == Rarity.UNCOMMON) {
            return 3; // 罕见
        } else if (vanillaRarity == Rarity.RARE) {
            return 4; // 史诗
        } else if (vanillaRarity == Rarity.EPIC) {
            return 5; // 传说
        } else {
            return 1; // 普通
        }
    }
    
    /**
     * 获取物品稀有度映射
     * @return 物品稀有度映射
     */
    public static java.util.Map<ResourceLocation, Integer> getItemRarityMap() {
        return ITEM_RARITY_MAP;
    }
    
    /**
     * 获取自动计算的稀有度映射
     * @return 自动计算的稀有度映射
     */
    public static java.util.Map<ResourceLocation, Integer> getAutoRarityMap() {
        return AUTO_RARITY_MAP;
    }
    
    /**
     * 同步稀有度数据到客户端
     */
    public static void syncRarityToClients() {
        SyncManager.syncRarityToClients(ITEM_RARITY_MAP);
    }
    
    /**
     * 同步稀有度数据到客户端(带重试机制)
     */
    public static void syncRarityToClientsWithRetry() {
        SyncManager.syncRarityToClientsWithRetry(ITEM_RARITY_MAP);
    }
    
    /**
     * 同步增量变更到客户端
     */
    public static void syncIncrementalChangesToClients() {
        SyncManager.syncIncrementalChangesToClients();
    }
    
    /**
     * 同步增量变更到客户端(带重试机制)
     */
    public static void syncIncrementalChangesToClientsWithRetry() {
        SyncManager.syncIncrementalChangesToClientsWithRetry();
    }
    
    /**
     * 获取待处理的变更操作数量
     * @return 变更操作数量
     */
    public static int getPendingChangeCount() {
        return SyncManager.getPendingChangeCount();
    }
    

    

}