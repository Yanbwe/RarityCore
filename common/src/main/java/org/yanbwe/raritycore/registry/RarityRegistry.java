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
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.config.TagRarityLoader;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.event.RarityQueryEvent;
import org.yanbwe.raritycore.event.RarityRegistryChangedEvent;
import org.yanbwe.raritycore.network.ChangeOperation;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.util.ComponentBuilder;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;
import org.yanbwe.raritycore.util.StringResolver;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
     * V14 不限制上限，返回 >= {@link RarityConstants#MIN_RARITY}。
     * @param item 要查稀有度的物品
     * @return 标准化后的物品稀有度等级（>= {@link RarityConstants#MIN_RARITY}）
     */
    public static @NotNull Integer getNormalizedRarity(@Nullable Item item) {
        Integer rawRarity = getRarity(item);
        return org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rawRarity);
    }
    
    /**
     * 获取物品栈的稀有度等级(标准化版本,支持物品数据匹配)
     * V14 不限制上限，返回 >= {@link RarityConstants#MIN_RARITY}。
     * @param itemStack 要查稀有度的物品栈
     * @return 标准化后的物品稀有度等级（>= {@link RarityConstants#MIN_RARITY}）
     */
    public static @NotNull Integer getNormalizedRarity(@Nullable ItemStack itemStack) {
        Integer rawRarity = getRarity(itemStack);
        return org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rawRarity);
    }
    
    /**
     * 获取物品栈的完整稀有度工具提示字符串（V14）。
     * <p>
     * 统一使用 {@link StringResolver#resolve(String, int, String)} 解析
     * {@link RarityStyleConfigManager#getTooltipContent(int)} 模板，
     * 不再区分标准/特殊稀有度，也无需拼接旧的命名翻译键。
     *
     * @param itemStack 要获取工具提示的物品栈
     * @return 解析后的稀有度工具提示字符串
     */
    public static @NotNull String getLocalizedRarityTooltip(@Nullable ItemStack itemStack) {
        Integer rarity = getRarity(itemStack);
        if (rarity == null) {
            rarity = RarityConstants.MIN_RARITY;
        }
        rarity = RarityValidator.normalizeRarity(rarity);
        return StringResolver.resolve(
                RarityStyleConfigManager.getTooltipContent(rarity),
                RarityStyleConfigManager.getLevelDisplayName(rarity),
                ComponentBuilder.getStars(rarity)
        );
    }
    
    /**
     * 获取物品栈的稀有度等级(支持物品数据)
     * 优先级顺序:物品数据匹配 > 神化模组稀有度 > 本模组稀有度(配置和数据包) > 原版稀有度映射
     * @param itemStack 要查稀有度的物品栈
     * @return 物品的稀有度等级（>= {@link RarityConstants#MIN_RARITY}）
     */
    public static @NotNull Integer getRarity(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return RarityStyleConfigManager.getNoRarityDefaultRarity();
        }
        
        Item item = itemStack.getItem();
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return RarityStyleConfigManager.getNoRarityDefaultRarity();
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
        return RarityStyleConfigManager.getNoRarityDefaultRarity();
    }

    /**
     * 仅通过 ID 映射查询稀有度 (当 ItemStack 无法创建时的回退路径)
     */
    private static @NotNull Integer getRarityByIdOnly(Identifier itemId) {
        Integer rarity = ITEM_RARITY_MAP.get(itemId);
        if (rarity != null) return rarity;
        rarity = AUTO_RARITY_MAP.get(itemId);
        return rarity != null ? rarity : RarityStyleConfigManager.getNoRarityDefaultRarity();
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

        rarity = RarityStyleConfigManager.getNoRarityDefaultRarity();
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
     * @return 映射后的本模组稀有度等级（>= {@link RarityConstants#MIN_RARITY}）
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

    // ==================== 集合查询与批量注册 ====================

    /**
     * 返回所有被解析为指定稀有度等级的物品。
     *
     * @param rarity 稀有度等级
     * @return 匹配物品的只读集合
     */
    public static Set<Item> getItemsByRarity(int rarity) {
        Set<Item> result = new LinkedHashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                continue;
            }
            if (getRarity(item) == rarity) {
                result.add(item);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * 返回所有被解析为指定稀有度等级的物品 ID。
     *
     * @param rarity 稀有度等级
     * @return 匹配物品 ID 的只读集合
     */
    public static Set<Identifier> getItemIdsByRarity(int rarity) {
        Set<Identifier> result = new LinkedHashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                continue;
            }
            if (getRarity(item) == rarity) {
                result.add(itemId);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * 返回所有被解析为指定稀有度等级集合中任一等级的物品。
     *
     * @param rarities 稀有度等级集合
     * @return 匹配物品的只读集合
     */
    public static Set<Item> getItemsByRarities(Set<Integer> rarities) {
        if (rarities == null || rarities.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Item> result = new LinkedHashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                continue;
            }
            if (rarities.contains(getRarity(item))) {
                result.add(item);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * 返回所有被解析为指定稀有度等级集合中任一等级的物品 ID。
     *
     * @param rarities 稀有度等级集合
     * @return 匹配物品 ID 的只读集合
     */
    public static Set<Identifier> getItemIdsByRarities(Set<Integer> rarities) {
        if (rarities == null || rarities.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Identifier> result = new LinkedHashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                continue;
            }
            if (rarities.contains(getRarity(item))) {
                result.add(itemId);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * 返回被解析为指定稀有度等级的物品数量。
     *
     * @param rarity 稀有度等级
     * @return 匹配物品数量
     */
    public static int getRarityCount(int rarity) {
        int count = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                continue;
            }
            if (getRarity(item) == rarity) {
                count++;
            }
        }
        return count;
    }

    /**
     * 返回当前全部已注册稀有度条目快照（显式配置与自动计算合并，ITEM 覆盖 AUTO）。
     *
     * @return 合并后的只读映射
     */
    public static Map<Identifier, Integer> getAllRarityEntries() {
        Map<Identifier, Integer> result = new HashMap<>(AUTO_RARITY_MAP);
        result.putAll(ITEM_RARITY_MAP);
        return Collections.unmodifiableMap(result);
    }

    /**
     * 返回当前出现过的稀有度等级集合（手动配置 ∪ 自动计算 ∪ 无稀有度兜底等级）。
     *
     * @return 出现过的等级集合（只读）
     */
    public static Set<Integer> getConfiguredRarities() {
        Set<Integer> result = new HashSet<>();
        result.addAll(ITEM_RARITY_MAP.values());
        result.addAll(AUTO_RARITY_MAP.values());
        result.add(RarityStyleConfigManager.getNoRarityDefaultRarity());
        return Collections.unmodifiableSet(result);
    }

    /**
     * 批量注册物品稀有度映射。
     * <p>
     * 不逐条同步；注册全部完成后统一同步一次，并发布
     * {@link RarityRegistryChangedEvent}，事件携带本次实际写入/更新的条目。
     *
     * @param entries 物品到稀有度等级的映射
     */
    public static void registerRarities(Map<Item, Integer> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        Map<Identifier, Integer> changedEntries = new HashMap<>();
        for (Map.Entry<Item, Integer> entry : entries.entrySet()) {
            Item item = entry.getKey();
            Integer rarity = entry.getValue();
            if (item == null || rarity == null) {
                continue;
            }

            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                continue;
            }

            Integer oldRarity = ITEM_RARITY_MAP.get(itemId);
            if (oldRarity != null && oldRarity.equals(rarity)) {
                continue;
            }
            register(item, rarity, false);
            changedEntries.put(itemId, rarity);
        }

        if (!changedEntries.isEmpty()) {
            syncRarityToClients();
            NeoForge.EVENT_BUS.post(new RarityRegistryChangedEvent(changedEntries));
        }
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

    // ==================== Tag 稀有度（公开 API） ====================

    /**
     * 获取物品匹配的 Tag 规则最高稀有度。
     * 遍历 TagRarity.json 中按稀有度降序排列的规则列表，
     * 找到第一个匹配的 Tag 即返回对应的稀有度等级。
     *
     * @param item 要查询的物品
     * @return 稀有度等级，无匹配时返回 null
     */
    public static Integer getTagRarity(@Nullable Item item) {
        if (item == null) return null;
        return checkTagRarity(new ItemStack(item));
    }

    // ==================== 配置检查 ====================

    /**
     * 检查物品是否有已配置的稀有度。
     * 查找范围：手动配置 (ITEM_RARITY_MAP) 和自动计算配置 (AUTO_RARITY_MAP)。
     *
     * @param item 要检查的物品
     * @return 有配置返回 true
     */
    public static boolean hasConfiguredRarity(@Nullable Item item) {
        if (item == null) return false;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) return false;
        return ITEM_RARITY_MAP.containsKey(itemId) || AUTO_RARITY_MAP.containsKey(itemId);
    }

    // ==================== 健康检查 ====================

    /**
     * 清理 ITEM_RARITY_MAP 中引用无效物品 ID 的条目。
     *
     * @return 被移除的无效条目数量
     */
    public static int pruneInvalidEntries() {
        int removedCount = 0;
        for (Identifier itemId : ITEM_RARITY_MAP.keySet()) {
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                ITEM_RARITY_MAP.remove(itemId);
                removedCount++;
            }
        }
        if (removedCount > 0) {
            RarityCore.LOGGER.info(
                "从 ITEM_RARITY_MAP 中清理了 {} 个无效条目 (剩余 {} 个)",
                removedCount, ITEM_RARITY_MAP.size()
            );
        }
        return removedCount;
    }
}
