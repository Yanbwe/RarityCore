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
import org.yanbwe.raritycore.compat.ironsspells.IronSpellsAdapter;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.ServerConfigManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.config.TagRarityConfig;
import org.yanbwe.raritycore.config.TagRarityConfigLoader;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.event.RarityQueryEvent;
import org.yanbwe.raritycore.itemdatamatching.ItemDataRarityMatcher;
import org.yanbwe.raritycore.network.ChangeOperation;
import org.yanbwe.raritycore.network.SyncManager;
import org.yanbwe.raritycore.util.ComponentBuilder;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

import javax.annotation.Nullable;
import java.util.Map;
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
     * 检查物品是否有配置的稀有度。
     * 查找范围：手动配置 (ITEM_RARITY_MAP) 和自动计算配置 (AUTO_RARITY_MAP)。
     *
     * @param item 要检查的物品
     * @return 如果物品有配置稀有度返回 true，否则返回 false
     */
    public static boolean hasConfiguredRarity(@Nullable Item item) {
        if (item == null) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return false;
        }
        return ITEM_RARITY_MAP.containsKey(itemId) || AUTO_RARITY_MAP.containsKey(itemId);
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
     * 注册操作的锁对象，保证 register/unregister 与缓存更新、事件发布的原子性
     */
    private static final Object REGISTRY_LOCK = new Object();

    /**
     * 注册物品的稀有度等级
     * 1普通,2稀有,3罕见,4史诗,5传说,6神话,7唯一
     * 不注册视为普通品质
     * @param item 要注册稀有度的物品
     * @param rarity 稀有度等级
     * @param syncToClients 是否同步到客户端
     */
    public static void register(@Nullable Item item, int rarity, boolean syncToClients) {
        if (item == null) return;
        synchronized (REGISTRY_LOCK) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) return;

            Integer oldRarity = ITEM_RARITY_MAP.put(itemId, rarity);

            // 监控 ITEM_RARITY_MAP 无界增长：当条目数超过阈值时记录警告
            checkMapGrowthWarning(ITEM_RARITY_MAP.size());

            // 发布稀有度变更事件
            RarityChangeEvent.ChangeType changeType = (oldRarity == null) ?
                RarityChangeEvent.ChangeType.REGISTER : RarityChangeEvent.ChangeType.UPDATE;
            NeoForge.EVENT_BUS.post(new RarityChangeEvent(item, oldRarity, rarity, changeType));

            // 实时更新ID缓存（编辑模式支持）
            DualCacheManager.updateIdCache(new ItemStack(item), rarity);

            // 记录变更操作到缓冲区（锁内完成，轻量操作）
            if (syncToClients) {
                if (oldRarity == null) {
                    SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.ADD, itemId, rarity));
                } else {
                    SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.UPDATE, itemId, rarity));
                }
            }
        }
        // 锁外发送网络包，避免持有 REGISTRY_LOCK 时进行 I/O 操作
        // 这防止了因网络包拥塞/分包导致的锁竞争，同时也降低了 GenericPacketSplitter
        // 在连接关闭时处理分包造成 NPE 的概率（减少在临界区中的时间窗口）
        if (syncToClients) {
            SyncManager.syncIncrementalChangesToClients();
        }
    }

    /**
     * 删除物品的稀有度注册
     * @param item 要删除稀有度注册的物品
     * @param syncToClients 是否同步到客户端
     */
    public static void unregister(@Nullable Item item, boolean syncToClients) {
        if (item == null) return;
        synchronized (REGISTRY_LOCK) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) return;

            Integer removedRarity = ITEM_RARITY_MAP.remove(itemId);

            // 发布稀有度变更事件
            if (removedRarity != null) {
                NeoForge.EVENT_BUS.post(new RarityChangeEvent(
                    item, removedRarity, null, RarityChangeEvent.ChangeType.REMOVE));
            }

            // 实时更新ID缓存（编辑模式支持）- 删除时使缓存失效
            DualCacheManager.updateIdCache(new ItemStack(item), null);

            // 记录变更操作到缓冲区（锁内完成，轻量操作）
            if (syncToClients && removedRarity != null) {
                SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.DELETE, itemId, null));
            }
        }
        // 锁外发送网络包（理由同 register()）
        if (syncToClients) {
            SyncManager.syncIncrementalChangesToClients();
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
     * 获取物品栈的完整稀有度工具提示字符串(支持本地化,支持物品数据匹配)
     * 返回格式示例:
     * - 普通物品:"[普通] ⭐" (中文) 或 "[Common] ⭐" (英文)
     * - 高级物品:"[5级稀有度] ⭐⭐⭐⭐⭐"
     * @param itemStack 要获取工具提示的物品栈
     * @return 本地化的稀有度工具提示字符串
     */
    public static @NotNull String getLocalizedRarityTooltip(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return "[普通]"; // 默认返回普通稀有度
        }
        
        // 获取物品栈稀有度(支持 NBT/组件数据匹配)
        Integer rarity = getRarity(itemStack);
        if (rarity == null) {
            rarity = RarityConstants.MIN_RARITY;
        }
        
        int level = RarityValidator.normalizeRarity(rarity);
        String stars = ComponentBuilder.getStars(rarity);
        
        RarityStyleConfigManager styleMgr = RarityStyleConfigManager.getInstance();
        
        if (level <= 7) {
            // 标准稀有度(1-7级)：使用数字翻译键 "rarity.core.{level}"
            String translationKey = "rarity.core." + level;
            String localizedLabel = Component.translatable(translationKey).getString();
            return localizedLabel + " " + stars;
        } else {
            // 超出内置档位(>7)：使用 RarityStyleConfigManager 获取等级名称组件
            String levelName = styleMgr.resolveLevelNameComponent(level).getString();
            return levelName + " " + stars;
        }
    }
    
    /**
     * 获取物品栈的稀有度等级(支持物品数据)
     * 优先级顺序:物品数据匹配 > 神化模组稀有度 > Iron's Spells法术稀有度 > 本模组稀有度(配置和数据包) > Tag稀有度 > 自动计算稀有度 > 原版稀有度映射
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
     * 优先级顺序:物品数据匹配 > 神化模组稀有度 > Iron's Spells法术稀有度 > 本模组稀有度(配置和数据包) > Tag稀有度 > 自动计算稀有度 > 原版稀有度映射
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
     * 3. Iron's Spells 法术稀有度
     * 4. 本模组的稀有度配置(包括配置文件和数据包)
     * 5. Tag 稀有度配置(TagRarity.json)
     * 6. 自动计算的稀有度配置
     * 7. 原版稀有度映射(最低优先级)
     * 
     * @param itemId 物品资源位置,用于查找配置的稀有度
     * @param itemStack 物品栈,用于检查物品数据和神化模组稀有度
     * @param item 物品实例,用于获取默认稀有度
     * @return 物品的稀有度等级(1-7),如果没有找到匹配的稀有度,返回1(普通)
     */
    private static @NotNull Integer getRarityInternal(ResourceLocation itemId, @Nullable ItemStack itemStack, Item item) {
        // 早期缓存返回：减少热路径上的完整优先级链遍历
        if (itemStack != null) {
            Integer cached = DualCacheManager.getCachedRarity(itemStack);
            if (cached != null) {
                return cached;
            }
        }

        Integer rarity;
        
        // 最高优先级：检查组件稀有度控制（Component 驱动，凌驾一切）
        rarity = checkComponentRarity(itemStack);
        if (rarity != null) {
            rarity = postRarityQuery(itemStack, rarity, "component");
            if (itemStack != null) {
                ComponentCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 首先检查物品数据匹配配置(最高优先级)
        rarity = checkItemDataRarity(itemStack);
        if (rarity != null) {
            rarity = postRarityQuery(itemStack, rarity, "itemdata");
            // 填充缓存
            if (itemStack != null) {
                DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 然后检查神化模组稀有度
        rarity = checkApotheosisRarity(itemStack);
        if (rarity != null) {
            rarity = postRarityQuery(itemStack, rarity, "apotheosis");
            // 神化稀有度取决于ItemStack的数据组件,不是物品类型级别
            // 使用组件缓存(基于ItemStack NBT哈希)而非ID缓存,防止泄漏到同类型的非神化物品
            if (itemStack != null) {
                ComponentCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 然后检查 Iron's Spells 法术稀有度
        rarity = checkIronSpellsRarity(itemStack);
        if (rarity != null) {
            rarity = postRarityQuery(itemStack, rarity, "ironspells");
            // Iron's Spells 稀有度基于 ItemStack 的 spell_container 组件
            // 使用组件缓存(基于ItemStack NBT哈希)而非ID缓存,防止泄漏到同类型的非法术物品
            if (itemStack != null) {
                ComponentCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 然后检查本模组的稀有度配置(包括配置文件和数据包)- 最高优先级
        rarity = ITEM_RARITY_MAP.get(itemId);
        if (rarity != null) {
            rarity = postRarityQuery(itemStack, rarity, "itemmap");
            // 填充缓存
            if (itemStack != null) {
                DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 然后检查 Tag 稀有度配置 - 中等优先级(介于 ITEM_RARITY_MAP 和 AUTO_RARITY_MAP 之间)
        rarity = checkTagRarity(itemStack);
        if (rarity != null) {
            rarity = postRarityQuery(itemStack, rarity, "tag");
            // Tag 稀有度基于物品 Tag 成员关系,而非物品类型级别
            // 使用组件缓存(基于 ItemStack NBT 哈希)而非 ID 缓存,防止泄漏到同类型非 Tag 物品
            if (itemStack != null) {
                ComponentCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }

        // 然后检查自动计算的稀有度配置 - 中等优先级(低于 FinalRarity,高于原版)
        rarity = AUTO_RARITY_MAP.get(itemId);
        if (rarity != null) {
            rarity = postRarityQuery(itemStack, rarity, "autorarity");
            // 填充缓存
            if (itemStack != null) {
                DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 最后检查原版稀有度映射(最低优先级)
        rarity = checkVanillaRarity(itemStack, item);
        if (rarity != null) {
            rarity = postRarityQuery(itemStack, rarity, "vanilla");
            // 填充缓存
            if (itemStack != null) {
                DualCacheManager.cacheRarity(itemStack, rarity);
            }
            return rarity;
        }
        
        // 默认返回普通稀有度
        rarity = 1;
        rarity = postRarityQuery(itemStack, rarity, "default");
        // 填充缓存
        if (itemStack != null) {
            DualCacheManager.cacheRarity(itemStack, rarity);
        }
        return rarity;
    }
    
    /**
     * 发布 RarityQueryEvent 并处理覆盖逻辑。
     * <p>
     * 在 {@link #getRarityInternal(ResourceLocation, ItemStack, Item)} 确定稀有度后、
     * 返回结果前调用。如果事件被监听器取消，返回覆盖后的稀有度值。
     * </p>
     *
     * @param itemStack 被查询的物品栈
     * @param rarity    当前确定的稀有度等级
     * @param source    查询来源标识（如 "component"、"apotheosis"、"tag" 等）
     * @return 事件未取消时返回原始稀有度，否则返回覆盖后的稀有度
     */
    private static int postRarityQuery(@Nullable ItemStack itemStack, int rarity, String source) {
        if (itemStack == null || itemStack.isEmpty()) {
            return rarity;
        }
        RarityQueryEvent event = new RarityQueryEvent(itemStack, rarity, source);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return event.getOverriddenRarity();
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
     * 检查组件稀有度控制（最高优先级）
     * 从物品的 DataComponents.CUSTOM_DATA 中读取 "raritycore" 复合标签中的 Level 字段。
     * 仅当 {@link ServerConfigManager#isEnableComponentRarityControl()} 返回 true 时生效。
     * 使用 {@link ComponentCacheManager} 单独缓存，避免污染 ID 缓存。
     *
     * @param itemStack 物品栈
     * @return 稀有度等级，无效或未启用时返回 null
     */
    private static Integer checkComponentRarity(@Nullable ItemStack itemStack) {
        if (!ServerConfigManager.isEnableComponentRarityControl()) {
            return null;
        }
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        Integer rarity = ComponentRarityReader.readLevel(itemStack);
        if (rarity != null) {
            // 组件稀有度基于 ItemStack 的 DataComponent 内容，而非物品类型级别
            // 使用组件缓存（基于 ItemStack NBT 哈希）而非 ID 缓存，防止泄漏到同类型不同物品
            ComponentCacheManager.cacheRarity(itemStack, rarity);
        }
        return rarity;
    }

    /**
     * 检查 Iron's Spells 法术稀有度
     * <p>
     * 通过 {@link IronSpellsAdapter} 读取法术卷轴的 spell_container 组件，
     * 提取法术等级并映射为本模组稀有度。
     * 仅当 Iron's Spells 模组已加载且有有效法术数据时返回非 null 值。
     * </p>
     *
     * @param itemStack 物品栈
     * @return 稀有度等级，如果模组未加载或无有效法术数据则返回 null
     */
    private static Integer checkIronSpellsRarity(@Nullable ItemStack itemStack) {
        if (!ClientConfigManager.isEnableIronSpellsAdapter()) {
            return null;
        }
        if (!IronSpellsAdapter.isLoaded()) {
            return null;
        }
        if (itemStack == null) {
            return null;
        }
        return IronSpellsAdapter.getMappedRarity(itemStack);
    }

    /**
     * 检查 Tag 稀有度配置。
     *
     * <p>遍历 {@link TagRarityConfig} 中按稀有度降序排列的规则列表,
     * 对每个规则使用 {@code itemStack.getItem().builtInRegistryHolder().is(tagKey)}
     * 判断物品是否属于该 Tag。找到第一个匹配即返回对应的稀有度等级,
     * 因为规则已按稀有度降序排列,第一个匹配的即为最高稀有度。</p>
     *
     * @param itemStack 物品栈
     * @return 稀有度等级,如果没有匹配则返回 null
     */
    private static Integer checkTagRarity(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        for (TagRarityConfig.TagRarityEntry entry : TagRarityConfig.getRules()) {
            if (itemStack.getItem().builtInRegistryHolder().is(entry.tagKey())) {
                return entry.rarity();
            }
        }

        return null;
    }

    /**
     * 获取物品匹配的 Tag 规则最高稀有度。
     * 遍历 {@link TagRarityConfig} 中按稀有度降序排列的规则列表，
     * 找到第一个匹配的 Tag 即返回对应的稀有度等级。
     *
     * @param item 要查询的物品
     * @return 稀有度等级，无匹配时返回 null
     */
    public static Integer getTagRarity(@Nullable Item item) {
        if (item == null) return null;
        return checkTagRarity(new ItemStack(item));
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

    // ==================== 遍历查询族 ====================

    /**
     * 返回所有被解析为指定稀有度等级的物品（遍历全注册表）
     * @param rarity 稀有度等级
     * @return 匹配物品的只读列表
     */
    public static java.util.List<Item> getItemsByRarity(int rarity) {
        java.util.List<Item> result = new java.util.ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (getRarity(item) == rarity) result.add(item);
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * 返回所有被解析为指定稀有度等级的物品 ID
     * @param rarity 稀有度等级
     * @return 匹配物品 ID 的只读列表
     */
    public static java.util.List<ResourceLocation> getItemIdsByRarity(int rarity) {
        java.util.List<ResourceLocation> result = new java.util.ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (getRarity(item) == rarity) result.add(BuiltInRegistries.ITEM.getKey(item));
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * 返回当前出现过的稀有度等级集合（手动配置 ∪ 自动计算 ∪ 无稀有度兜底等级）
     * @return 出现过的等级集合（只读）
     */
    public static java.util.Set<Integer> getConfiguredRarities() {
        java.util.Set<Integer> set = new java.util.HashSet<>(ITEM_RARITY_MAP.values());
        set.addAll(AUTO_RARITY_MAP.values());
        set.add(RarityStyleConfigManager.getInstance().getNoRarityDefaultRarity());
        return java.util.Collections.unmodifiableSet(set);
    }

    /**
     * 返回所有被解析为指定稀有度等级集合中任一等级的物品
     * @param rarities 稀有度等级集合
     * @return 匹配物品的只读列表
     */
    public static java.util.List<Item> getItemsByRarities(java.util.Set<Integer> rarities) {
        if (rarities == null || rarities.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Item> result = new java.util.ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (rarities.contains(getRarity(item))) result.add(item);
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * 返回被解析为指定稀有度等级的物品数量
     * @param rarity 稀有度等级
     * @return 匹配物品数量
     */
    public static int getRarityCount(int rarity) {
        int count = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (getRarity(item) == rarity) count++;
        }
        return count;
    }

    /**
     * 返回当前全部已解析稀有度等级的快照（显式配置与自动计算合并，ITEM 覆盖 AUTO）
     * @return 合并后的只读映射
     */
    public static java.util.Map<ResourceLocation, Integer> getAllRarityEntries() {
        java.util.Map<ResourceLocation, Integer> map = new java.util.HashMap<>(AUTO_RARITY_MAP);
        map.putAll(ITEM_RARITY_MAP); // ITEM 覆盖 AUTO
        return java.util.Collections.unmodifiableMap(map);
    }

    /**
     * 应用来自服务端同步的自动稀有度映射（仅客户端）
     */
    public static void applySyncedAutoRarity(Map<ResourceLocation, Integer> autoRarityMap) {
        AUTO_RARITY_MAP.clear();
        if (autoRarityMap != null) {
            AUTO_RARITY_MAP.putAll(autoRarityMap);
        }
    }

    public static void syncRarityToClients() {
        SyncManager.syncRarityToClients(ITEM_RARITY_MAP, getAutoRarityMap(), TagRarityConfigLoader.getSyncedRules());
    }
    
    /**
     * 同步稀有度数据到客户端(带重试机制)
     */
    public static void syncRarityToClientsWithRetry() {
        SyncManager.syncRarityToClientsWithRetry(ITEM_RARITY_MAP, getAutoRarityMap(), TagRarityConfigLoader.getSyncedRules());
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