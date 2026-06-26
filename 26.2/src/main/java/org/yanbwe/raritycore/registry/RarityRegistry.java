package org.yanbwe.raritycore.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.compat.CompatibilityChecker;
import org.yanbwe.raritycore.config.TagRarityLoader;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.event.RarityQueryEvent;
import org.yanbwe.raritycore.network.ChangeOperation;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.util.RarityConstants;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RarityRegistry {


    public static final ConcurrentHashMap<Identifier, Integer> ITEM_RARITY_MAP = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Identifier, Integer> AUTO_RARITY_MAP = new ConcurrentHashMap<>();

    public static void putAutoRarity(Identifier itemId, int rarity) {
        AUTO_RARITY_MAP.put(itemId, rarity);
    }

    public static void removeAutoRarity(Identifier itemId) {
        AUTO_RARITY_MAP.remove(itemId);
    }

    public static boolean hasAutoRarity(Identifier itemId) {
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
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                Integer oldRarity = ITEM_RARITY_MAP.put(itemId, rarity);

                // 实时更新ID缓存（编辑模式支持）
                // 保护: 数据加载时 Holder 组件可能尚未绑定, 静默跳过缓存更新
                try {
                    org.yanbwe.raritycore.cache.DualCacheManager.updateIdCache(new ItemStack(item), rarity);
                } catch (Exception e) {
                    RarityCore.LOGGER.debug("无法更新ID缓存 (物品组件未绑定): {}", itemId);
                }

                RarityChangeEvent.ChangeType changeType = (oldRarity == null) ?
                    RarityChangeEvent.ChangeType.REGISTER : RarityChangeEvent.ChangeType.UPDATE;
                NeoForge.EVENT_BUS.post(new RarityChangeEvent(item, oldRarity, rarity, changeType));

                if (syncToClients) {
                    if (oldRarity == null) {
                        SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.ADD, itemId, rarity));
                    } else {
                        SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.UPDATE, itemId, rarity));
                    }

                    SyncManager.syncRarityToClients(ITEM_RARITY_MAP, getAutoRarityMap(), TagRarityLoader.getSyncedRules());
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
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                Integer removedRarity = ITEM_RARITY_MAP.remove(itemId);

                // 实时更新ID缓存（编辑模式支持）- 删除时使缓存失效
                // 保护: 数据加载时 Holder 组件可能尚未绑定, 静默跳过缓存更新
                try {
                    org.yanbwe.raritycore.cache.DualCacheManager.updateIdCache(new ItemStack(item), null);
                } catch (Exception e) {
                    RarityCore.LOGGER.debug("无法更新ID缓存 (物品组件未绑定): {}", itemId);
                }

                if (removedRarity != null) {
                    NeoForge.EVENT_BUS.post(new RarityChangeEvent(
                        item, removedRarity, null, RarityChangeEvent.ChangeType.REMOVE));
                }

                if (syncToClients) {
                    if (removedRarity != null) {
                        SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.DELETE, itemId, null));
                    }

                    SyncManager.syncRarityToClients(ITEM_RARITY_MAP, getAutoRarityMap(), TagRarityLoader.getSyncedRules());
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
            // 使用 Component.translatable() 替代 I18n.get()，避免服务端加载客户端类导致 NoClassDefFoundError
            return net.minecraft.network.chat.Component.translatable(key).getString();
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
                // 使用自定义文本,保持与标准格式一致:[自定义文本] <星星>
                // $前缀表示翻译键
                String displayText = customText.startsWith("$") && customText.length() > 1
                    ? net.minecraft.network.chat.Component.translatable(customText.substring(1)).getString()
                    : customText;
                return "[" + displayText + "] " + stars;
            } else {
                // 使用默认格式,使用本地化文本:[xx级稀有度] <星星>
                String localizedSuffix = net.minecraft.network.chat.Component.translatable("rarity.core.unusual.tips").getString();
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
            String localizedLabel = net.minecraft.network.chat.Component.translatable(rarityKey).getString();
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
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return 1;
        }

        return getRarityInternal(itemId, itemStack, item);
    }

    public static @NotNull Integer getRarity(@Nullable Item item) {
        if (item != null) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                try {
                    ItemStack tempStack = new ItemStack(item);
                    return getRarityInternal(itemId, tempStack, item);
                } catch (Exception e) {
                    // 数据加载早期阶段, Holder 组件尚未绑定
                    // 回退: 只检查 ID 映射, 跳过需要 ItemStack 的检查
                    RarityCore.LOGGER.debug("无法创建临时ItemStack (组件未绑定): {}, 回退到ID检查", itemId);
                    return getRarityByIdOnly(itemId);
                }
            }
        }
        return 1; // 默认为普通
    }

    /**
     * 仅通过 ID 映射查询稀有度 (当 ItemStack 无法创建时的回退路径)
     */
    private static @NotNull Integer getRarityByIdOnly(Identifier itemId) {
        Integer rarity = ITEM_RARITY_MAP.get(itemId);
        if (rarity != null) return rarity;
        rarity = AUTO_RARITY_MAP.get(itemId);
        return rarity != null ? rarity : 1;
    }
    
    private static @NotNull Integer getRarityInternal(Identifier itemId, @Nullable ItemStack itemStack, Item item) {
        // Component 稀有度检查 — 最高优先级，凌驾一切其他来源
        // 仅 ItemStack 触发（纯 Item 查询无 Component 数据）
        if (itemStack != null) {
            ComponentRarityResolver.ComponentRarityData componentData = ComponentRarityResolver.resolveComponentRarity(itemStack);
            if (componentData != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, componentData.level());
                return componentData.level();
            }
        }

        Integer rarity;

        // Post RarityQueryEvent as pre-override — allows other mods to override rarity before any lookup
        RarityQueryEvent queryEvent = new RarityQueryEvent(itemStack, item, 0, "getRarityInternal");
        NeoForge.EVENT_BUS.post(queryEvent);
        if (queryEvent.getRarity() > 0) {
            if (itemStack != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, queryEvent.getRarity());
            }
            return queryEvent.getRarity();
        }

        rarity = checkItemDataRarity(itemStack);
        if (rarity != null) {
            if (itemStack != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }

        rarity = ITEM_RARITY_MAP.get(itemId);
        if (rarity != null) {
            if (itemStack != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }

        // Tag-based rarity check — between ITEM_RARITY_MAP and AUTO_RARITY_MAP
        // Only applies to ItemStack; rules sorted by rarity descending → first match = highest rarity
        if (itemStack != null) {
            rarity = checkTagRarity(itemStack);
            if (rarity != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
                return rarity;
            }
        }

        rarity = AUTO_RARITY_MAP.get(itemId);
        if (rarity != null) {
            if (itemStack != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }

        rarity = checkVanillaRarity(itemStack, item);
        if (rarity != null) {
            if (itemStack != null) {
                org.yanbwe.raritycore.cache.DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }

        rarity = 1;
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
     * Checks tag-based rarity assignment rules from TagRarity.json.
     * Rules are sorted by rarity descending, so the first matching tag provides
     * the highest applicable rarity for this item.
     *
     * @param itemStack the item stack to check (must be non-null)
     * @return the assigned rarity level if any tag rule matches, null otherwise
     */
    private static Integer checkTagRarity(@NotNull ItemStack itemStack) {
        for (TagRarityLoader.TagRarityEntry entry : TagRarityLoader.getTagRules()) {
            TagKey<Item> tagKey = entry.toTagKey();
            try {
                if (itemStack.typeHolder().is(tagKey)) {
                    return entry.rarity();
                }
            } catch (Exception e) {
                RarityCore.LOGGER.debug("Error checking tag rarity for {}: {}", entry.toTagString(), e.getMessage());
            }
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
    
    public static java.util.Map<Identifier, Integer> getItemRarityMap() {
        return ITEM_RARITY_MAP;
    }

    public static java.util.Map<Identifier, Integer> getAutoRarityMap() {
        return AUTO_RARITY_MAP;
    }
    
    /**
     * 应用来自服务端同步的自动稀有度映射（仅客户端）
     */
    public static void applySyncedAutoRarity(Map<Identifier, Integer> autoRarityMap) {
        AUTO_RARITY_MAP.clear();
        if (autoRarityMap != null) {
            AUTO_RARITY_MAP.putAll(autoRarityMap);
        }
    }

    public static void syncRarityToClients() {
        SyncManager.syncRarityToClients(ITEM_RARITY_MAP, getAutoRarityMap(), TagRarityLoader.getSyncedRules());
    }
    
    /**
     * 同步稀有度数据到客户端(带重试机制)
     */
    public static void syncRarityToClientsWithRetry() {
        SyncManager.syncRarityToClientsWithRetry(ITEM_RARITY_MAP, getAutoRarityMap(), TagRarityLoader.getSyncedRules());
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