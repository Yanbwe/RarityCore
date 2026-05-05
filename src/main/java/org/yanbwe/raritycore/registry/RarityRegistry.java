package org.yanbwe.raritycore.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.ComponentCacheManager;
import org.yanbwe.raritycore.cache.DualCacheManager;
import org.yanbwe.raritycore.compat.CompatibilityChecker;
import org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.config.StarDisplayConfigManager;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.itemdatamatching.ItemDataRarityMatcher;
import org.yanbwe.raritycore.network.ChangeOperation;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.util.ComponentBuilder;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

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
     * 检查物品是否有自动计算的稀有度配置
     * @param itemId 物品资源位置
     * @return 如果有配置返回true
     */
    public static boolean hasAutoRarity(ResourceLocation itemId) {
        return AUTO_RARITY_MAP.containsKey(itemId);
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
                
                // 监控 ITEM_RARITY_MAP 无界增长：当条目数超过阈值时记录警告
                checkMapGrowthWarning(ITEM_RARITY_MAP.size());
                
                // 发布稀有度变更事件
                RarityChangeEvent.ChangeType changeType = (oldRarity == null) ? 
                    RarityChangeEvent.ChangeType.REGISTER : RarityChangeEvent.ChangeType.UPDATE;
                NeoForge.EVENT_BUS.post(new RarityChangeEvent(item, oldRarity, rarity, changeType));
                
                // 实时更新ID缓存（编辑模式支持）
                DualCacheManager.updateIdCache(new ItemStack(item), rarity);
                
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
                
                // 实时更新ID缓存（编辑模式支持）- 删除时使缓存失效
                DualCacheManager.updateIdCache(new ItemStack(item), null);
                
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
        return RarityValidator.normalizeRarity(rawRarity);
    }
    
    /**
     * 获取物品栈的稀有度等级(标准化版本,支持物品数据匹配)
     * 遵循模组的包容性原则:小于1的值视为1,大于7的值视为7
     * @param itemStack 要查稀有度的物品栈
     * @return 标准化后的物品稀有度等级(1-7)
     */
    public static @NotNull Integer getNormalizedRarity(@Nullable ItemStack itemStack) {
        Integer rawRarity = getRarity(itemStack);
        return RarityValidator.normalizeRarity(rawRarity);
    }
    
    /**
     * 获取物品的完整稀有度工具提示字符串(支持本地化)
     * 返回格式示例:
     * - 普通物品:"[普通] ⭐" (中文) 或 "[Common] ⭐" (英文)
     * - 高级物品:"[5级稀有度] ⭐⭐⭐⭐⭐"
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
        rarity = RarityValidator.normalizeRarity(rarity);
        
        // 构建工具提示字符串
        if (isSpecialRarity) {
            // 特殊稀有度(大于 7 级)
            String stars = ComponentBuilder.getStars(displayRarity);
            
            // 检查是否有自定义特殊稀有度文本
            String customText = StarDisplayConfigManager.getCustomSpecialRarityText(displayRarity);
            
            if (customText != null && !customText.isEmpty()) {
                // 使用自定义文本,保持与标准格式一致:[自定义文本] <星星>
                return "[" + customText + "] " + stars;
            } else {
                // 使用默认格式,使用本地化文本:[xx级稀有度] <星星>
                String localizedSuffix = Component.translatable("rarity.core.unusual.tips").getString();
                return "[" + displayRarity + localizedSuffix + "]" + stars;
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
            String localizedLabel = Component.translatable(rarityKey).getString();
            String stars = ComponentBuilder.getStars(rarity);
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
                DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 然后检查神化模组稀有度
        rarity = checkApotheosisRarity(itemStack);
        if (rarity != null) {
            // 神化稀有度取决于ItemStack的数据组件,不是物品类型级别
            // 使用组件缓存(基于ItemStack NBT哈希)而非ID缓存,防止泄漏到同类型的非神化物品
            if (itemStack != null) {
                ComponentCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 然后检查本模组的稀有度配置(包括配置文件和数据包)- 最高优先级
        rarity = ITEM_RARITY_MAP.get(itemId);
        if (rarity != null) {
            // 填充缓存
            if (itemStack != null) {
                DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 然后检查自动计算的稀有度配置 - 中等优先级(低于 FinalRarity,高于原版)
        rarity = AUTO_RARITY_MAP.get(itemId);
        if (rarity != null) {
            // 填充缓存
            if (itemStack != null) {
                DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 最后检查原版稀有度映射(最低优先级)
        rarity = checkVanillaRarity(itemStack, item);
        if (rarity != null) {
            // 填充缓存
            if (itemStack != null) {
                DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 默认返回普通稀有度
        rarity = 1;
        // 填充缓存
        if (itemStack != null) {
            DualCacheManager.cacheRarity(itemStack, rarity);
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
        return ItemDataRarityMatcher.getItemDataMatchedRarity(itemStack);
    }
    
    /**
     * 检查神化模组稀有度
     * @param itemStack 物品栈
     * @return 稀有度等级,如果没有匹配则返回null
     */
    private static Integer checkApotheosisRarity(@Nullable ItemStack itemStack) {
        if (!ServerConfigManager.isCheckApotheosisRarity()) {
            return null;
        }
        if (!ApotheosisAdapter.isLoaded()) {
            return null;
        }
        if (itemStack == null) {
            return null;
        }
        return ApotheosisAdapter.getMappedRarity(itemStack);
    }
    
    /**
     * 检查原版稀有度
     * @param itemStack 物品栈
     * @param item 物品
     * @return 稀有度等级,如果没有匹配则返回null
     */
    private static Integer checkVanillaRarity(@Nullable ItemStack itemStack, Item item) {
        if (ServerConfigManager.isCheckVanillaRarity()) {
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
    
    // ==================== 监控与健康检查 ====================
    
    /**
     * 获取 ITEM_RARITY_MAP 当前条目数量，方便外部监控
     * @return 当前映射中的条目总数
     */
    public static int getMapSize() {
        return ITEM_RARITY_MAP.size();
    }
    
    /**
     * 检查 ITEM_RARITY_MAP 大小并记录警告。
     * 当条目数超过 {@link RarityConstants#ITEM_RARITY_MAP_WARNING_THRESHOLD} 时记录 WARN 日志。
     * 这通常表明配置错误（如循环配置加载）导致的无界增长。
     *
     * @param currentSize 当前映射大小
     */
    private static void checkMapGrowthWarning(int currentSize) {
        if (currentSize > RarityConstants.ITEM_RARITY_MAP_WARNING_THRESHOLD) {
            RarityCore.LOGGER.warn(
                "ITEM_RARITY_MAP 条目数 ({}) 已超过警告阈值 ({}). "
                    + "这可能表明配置加载循环或数据包错误导致无界增长. "
                    + "请检查配置文件和数据包是否正确. "
                    + "可以使用 pruneInvalidEntries() 手动清理无效条目.",
                currentSize,
                RarityConstants.ITEM_RARITY_MAP_WARNING_THRESHOLD
            );
        }
    }
    
    /**
     * 清理 ITEM_RARITY_MAP 中引用无效物品 ID 的条目。
     * 
     * <p><b>使用建议：</b>此方法应在配置重载（handleConfigReload）或定期
     * 健康检查时手动调用，不建议自动调用 — 可能意外移除用户自定义稀有度
     * 配置中有效但尚未加载的物品。</p>
     * 
     * <p>遍历当前映射中的所有条目，移除那些在 {@link BuiltInRegistries#ITEM}
     * 中不存在的物品 ID 对应的条目。此操作不会触发同步或事件发布，
     * 仅执行清理。</p>
     *
     * @return 被移除的无效条目数量
     */
    public static int pruneInvalidEntries() {
        int removedCount = 0;
        for (ResourceLocation itemId : ITEM_RARITY_MAP.keySet()) {
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                ITEM_RARITY_MAP.remove(itemId);
                removedCount++;
            }
        }
        if (removedCount > 0) {
            RarityCore.LOGGER.info(
                "从 ITEM_RARITY_MAP 中清理了 {} 个无效条目 (剩余 {} 个)",
                removedCount,
                ITEM_RARITY_MAP.size()
            );
        }
        return removedCount;
    }

}