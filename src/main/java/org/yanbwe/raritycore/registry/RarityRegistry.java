package org.yanbwe.raritycore.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.compat.CompatibilityChecker;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.event.RarityQueryEvent;
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
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                Integer oldRarity = ITEM_RARITY_MAP.put(itemId, rarity);
                
                // 发布稀有度变更事件
                RarityChangeEvent.ChangeType changeType = (oldRarity == null) ? 
                    RarityChangeEvent.ChangeType.REGISTER : RarityChangeEvent.ChangeType.UPDATE;
                MinecraftForge.EVENT_BUS.post(new RarityChangeEvent(item, oldRarity, rarity, changeType));
                
                // 如果需要同步到客户端且当前在服务端环境中,记录变更操作
                // 变更操作统一由增量同步机制处理,避免重复全量同步
                if (syncToClients) {
                    if (oldRarity == null) {
                        SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.ADD, itemId, rarity));
                    } else {
                        SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.UPDATE, itemId, rarity));
                    }
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
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                Integer removedRarity = ITEM_RARITY_MAP.remove(itemId);
                
                // 发布稀有度变更事件
                if (removedRarity != null) {
                    MinecraftForge.EVENT_BUS.post(new RarityChangeEvent(
                        item, removedRarity, null, RarityChangeEvent.ChangeType.REMOVE));
                }
                
                // 如果需要同步到客户端且当前在服务端环境中,记录删除操作
                // 变更操作统一由增量同步机制处理,避免重复全量同步
                if (syncToClients) {
                    if (removedRarity != null) {
                        SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.DELETE, itemId, null));
                    }
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
     * 获取物品栈的稀有度等级(标准化版本,支持NBT匹配)
     * 遵循模组的包容性原则:小于1的值视为1,大于7的值视为7
     * @param itemStack 要查稀有度的物品栈
     * @return 标准化后的物品稀有度等级(1-7)
     */
    public static @NotNull Integer getNormalizedRarity(@Nullable ItemStack itemStack) {
        Integer rawRarity = getRarity(itemStack);
        return org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rawRarity);
    }
    
    /**
     * 获取本地化文本（仅客户端可用）
     * @param key 本地化键
     * @return 本地化文本
     */
    @OnlyIn(Dist.CLIENT)
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
     * 获取物品的完整稀有度工具提示字符串(支持本地化,基于Item) - 仅客户端
     * 注意:此方法基于Item,无法检测神化NBT稀有度.如需支持神化检测请使用 getLocalizedRarityTooltip(ItemStack)
     * 返回格式示例:
     * - 普通物品:"[普通] ⭐" (中文) 或 "[Common] ⭐" (英文)
     * - 高级物品:"[5级稀有度-⭐⭐⭐⭐⭐]"
     * @param item 要获取工具提示的物品
     * @return 本地化的稀有度工具提示字符串
     */
    @OnlyIn(Dist.CLIENT)
    public static @NotNull String getLocalizedRarityTooltip(@Nullable Item item) {
        if (item == null) {
            return "[普通]";
        }
        Integer rarity = getRarity(item);
        if (rarity == null) rarity = RarityConstants.RARITY_COMMON;
        return buildLocalizedRarityTooltip(rarity);
    }
    
    /**
     * 获取物品栈的完整稀有度工具提示字符串(支持神化NBT稀有度检测) - 仅客户端
     * 与 getLocalizedRarityTooltip(Item) 不同,此方法接受 ItemStack 并支持神化NBT数据,
     * 能正确反映神化稀有度
     * @param itemStack 要获取工具提示的物品栈
     * @return 本地化的稀有度工具提示字符串
     */
    @OnlyIn(Dist.CLIENT)
    public static @NotNull String getLocalizedRarityTooltip(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return "[普通]";
        }
        Integer rarity = getRarity(itemStack);
        if (rarity == null) rarity = RarityConstants.RARITY_COMMON;
        return buildLocalizedRarityTooltip(rarity);
    }
    
    /**
     * 根据稀有度值构建本地化工具提示字符串(内部公用方法) - 仅客户端
     * 被 getLocalizedRarityTooltip(Item) 和 getLocalizedRarityTooltip(ItemStack) 共用
     */
    @OnlyIn(Dist.CLIENT)
    private static @NotNull String buildLocalizedRarityTooltip(int rawRarity) {
        boolean isSpecialRarity = rawRarity > RarityConstants.RARITY_UNIQUE;
        int displayRarity = rawRarity;
        int normalizedRarity = org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rawRarity);
        
        if (isSpecialRarity) {
            String stars = org.yanbwe.raritycore.util.ComponentBuilder.getStars(displayRarity);
            String customText = org.yanbwe.raritycore.config.StarDisplayConfigManager.getCustomSpecialRarityText(displayRarity);
            if (customText != null && !customText.isEmpty()) {
                return "[" + customText + "-" + stars + "]";
            } else {
                return "[" + displayRarity + "级稀有度-" + stars + "]";
            }
        } else {
            String rarityKey;
            switch (normalizedRarity) {
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
            String localizedLabel = net.minecraft.client.resources.language.I18n.get(rarityKey);
            String stars = org.yanbwe.raritycore.util.ComponentBuilder.getStars(normalizedRarity);
            return localizedLabel + " " + stars;
        }
    }
    
    /**
     * 获取物品栈的稀有度等级(支持NBT数据)
     * 优先级顺序:NBT匹配 > 神化模组稀有度 > 本模组稀有度(配置和数据包) > 原版稀有度映射
     * @param itemStack 要查稀有度的物品栈
     * @return 物品的稀有度等级(1-7)
     */
    public static @NotNull Integer getRarity(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return 1;
        }
        
        Item item = itemStack.getItem();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            return 1;
        }
        
        // 使用统一的稀有度获取逻辑
        return getRarityInternal(itemId, itemStack, item);
    }
    
    /**
     * 获取物品的稀有度等级
     * 优先级顺序:NBT匹配 > 神化模组稀有度 > 本模组稀有度(配置和数据包) > 原版稀有度映射
     * @param item 要查稀有度的物品
     * @return 物品的稀有度等级(1-7)
     */
    public static @NotNull Integer getRarity(@Nullable Item item) {
        if (item != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                return getRarityFromItemId(itemId, item);
            }
        }
        return 1;
    }

    private static @NotNull Integer getRarityFromItemId(ResourceLocation itemId, Item item) {
        Integer rarity = ITEM_RARITY_MAP.get(itemId);
        if (rarity != null) {
            return rarity;
        }

        rarity = AUTO_RARITY_MAP.get(itemId);
        if (rarity != null) {
            return rarity;
        }

        if (org.yanbwe.raritycore.config.ServerConfigManager.isCheckVanillaRarity()) {
            if (CompatibilityChecker.isVanillaRarityApiAvailable()) {
                try {
                    net.minecraft.world.item.ItemStack stackForRarity = item.getDefaultInstance();
                    Rarity vanillaRarity = stackForRarity.getRarity();
                    Integer mappedVanilla = mapVanillaRarity(vanillaRarity);
                    if (mappedVanilla > 1) {
                        return mappedVanilla;
                    }
                } catch (Throwable e) {
                    RarityCore.LOGGER.debug("Error checking vanilla rarity for item: {}", itemId, e);
                }
            }
        }

        return 1;
    }
    
    /**
     * 统一的稀有度获取逻辑
     * 按照以下优先级顺序获取稀有度:
     * 1. NBT匹配配置(最高优先级)
     * 2. 神化模组稀有度
     * 3. FinalRarity.json文件配置
     * 4. FinalRarityConfig文件夹配置
     * 5. 数据包内的ID匹配配置
     * 6. 自动计算的稀有度配置
     * 7. 原版稀有度映射(最低优先级)
     * 
     * @param itemId 物品资源位置,用于查找配置的稀有度
     * @param itemStack 物品栈,用于检查NBT数据和神化模组稀有度
     * @param item 物品实例,用于获取默认稀有度
     * @return 物品的稀有度等级(1-7),如果没有找到匹配的稀有度,返回1(普通)
     */
    private static @NotNull Integer getRarityInternal(ResourceLocation itemId, @Nullable ItemStack itemStack, Item item) {
        int result;
        String source = "vanilla";

        // 预读取 NBT tag 一次，避免后续 checkNbtRarity/checkApotheosisRarity/checkIronSpellbooksRarity
        // 各自重复调用 itemStack.hasTag()/getTag()（Forge 中 getTag() 可能创建防御性副本）
        CompoundTag tag = (itemStack != null && itemStack.hasTag()) ? itemStack.getTag() : null;
        
        if (tag != null) {
            // NBT 稀有度控制（最高优先级）—— 直接使用已读取的 tag，跳过 NbtRarityControlHandler 内部的重复 hasTag/getTag
            if (org.yanbwe.raritycore.config.ServerConfigManager.isEnableNbtRarityControl()
                    && tag.contains("raritycore", CompoundTag.TAG_COMPOUND)) {
                CompoundTag data = tag.getCompound("raritycore");
                if (data.contains("Level", CompoundTag.TAG_INT)) {
                    result = data.getInt("Level");
                    source = "nbt_control";
                    return fireQueryEvent(itemStack, result, source);
                }
            }
            
            Integer rarity = checkNbtRarity(itemStack);
            if (rarity != null) {
                result = rarity;
                source = "nbt";
                return fireQueryEvent(itemStack, result, source);
            }
            
            rarity = checkApotheosisRarityWithTag(itemStack, tag);
            if (rarity != null) {
                result = rarity;
                source = "apotheosis";
                return fireQueryEvent(itemStack, result, source);
            }

            rarity = checkIronSpellbooksRarity(itemStack);
            if (rarity != null) {
                result = rarity;
                source = "irons_spellbooks";
                return fireQueryEvent(itemStack, result, source);
            }
        }
        
        Integer rarity = ITEM_RARITY_MAP.get(itemId);
        if (rarity != null) {
            result = rarity;
            source = "registry";
            return fireQueryEvent(itemStack, result, source);
        }

        int tagRarity = org.yanbwe.raritycore.config.TagRarityConfigManager.getHighestTagRarity(item);
        if (tagRarity > 1) {
            result = tagRarity;
            source = "tag";
            return fireQueryEvent(itemStack, result, source);
        }
        
        rarity = AUTO_RARITY_MAP.get(itemId);
        if (rarity != null) {
            result = rarity;
            source = "auto";
            return fireQueryEvent(itemStack, result, source);
        }
        
        rarity = checkVanillaRarity(itemStack, item);
        if (rarity != null) {
            result = rarity;
            source = "vanilla";
            return fireQueryEvent(itemStack, result, source);
        }
        
        result = 1;
        return fireQueryEvent(itemStack, result, source);
    }

    /** 触发 RarityQueryEvent 并返回最终稀有度 */
    private static int fireQueryEvent(@Nullable ItemStack itemStack, int rarity, String source) {
        RarityQueryEvent event = new RarityQueryEvent(
            itemStack != null ? itemStack : ItemStack.EMPTY, rarity, source);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getRarity();
    }
    
    /**
     * 检查NBT匹配稀有度
     * @param itemStack 物品栈
     * @return 稀有度等级,如果没有匹配则返回null
     */
    private static Integer checkNbtRarity(@Nullable ItemStack itemStack) {
        if (itemStack != null && itemStack.hasTag()) {
            return org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher.getNbtMatchedRarity(itemStack);
        }
        return null;
    }
    
    /**
     * 获取物品的神化稀有度（公共入口,供外部直接调用）
     * 当标准获取流程因缓存或配置回退原因未正确返回神化稀有度时,外部可直接调用此方法作为兜底
     * @param itemStack 物品栈
     * @return 神化稀有度等级,如果没有神化数据则返回null
     */
    @Nullable
    public static Integer getDirectApotheosisRarity(@Nullable ItemStack itemStack) {
        return checkApotheosisRarity(itemStack);
    }
    
    /**
     * 检查物品是否有配置的稀有度(含神化NBT检测)
     * @param item 要检查的物品
     * @param itemStack 物品栈(用于神化NBT检测)
     * @return 如果物品有配置稀有度返回true,否则返回false
     */
    public static boolean hasConfiguredRarity(@Nullable Item item, @Nullable ItemStack itemStack) {
        if (item == null) {
            return false;
        }
        
        // 获取物品ID
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            return false;
        }
        
        // 检查是否在注册表中有配置
        if (ITEM_RARITY_MAP.containsKey(itemId)) {
            return true;
        }
        
        // 检查神化NBT数据(有神化稀有度也算有配置)
        if (itemStack != null && itemStack.hasTag()) {
            Integer apothRarity = getDirectApotheosisRarity(itemStack);
            if (apothRarity != null) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 检查神化模组稀有度
     * 神化稀有度不应该被缓存，因为依赖于物品的实时NBT数据
     * @param itemStack 物品栈
     * @return 稀有度等级,如果没有匹配则返回null
     */
    @Nullable
    private static Integer checkApotheosisRarity(@Nullable ItemStack itemStack) {
        if (!org.yanbwe.raritycore.config.ServerConfigManager.isCheckApotheosisRarity() || itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        return org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter.getMappedRarity(itemStack);
    }

    /**
     * 检查神化模组稀有度（使用已读取的 CompoundTag，避免重复 getTag()）
     * @param itemStack 物品栈（仅用于状态检查）
     * @param tag 已读取的 CompoundTag
     * @return 稀有度等级,如果没有匹配则返回null
     */
    @Nullable
    private static Integer checkApotheosisRarityWithTag(@Nullable ItemStack itemStack, @Nullable CompoundTag tag) {
        if (!org.yanbwe.raritycore.config.ServerConfigManager.isCheckApotheosisRarity() || itemStack == null || itemStack.isEmpty() || tag == null) {
            return null;
        }

        return org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter.calculateApotheosisRarityFromTag(tag);
    }
    
    /**
     * 检查 Iron's Spellbooks 稀有度
     */
    @Nullable
    private static Integer checkIronSpellbooksRarity(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return null;
        }
        return org.yanbwe.raritycore.compat.ironsspellbooks.IronSpellbooksAdapter.getMappedRarity(itemStack);
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
                    if (mappedVanilla > 1) {
                        return mappedVanilla;
                    }
                } catch (Throwable e) {
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
    public static java.util.Map<net.minecraft.resources.ResourceLocation, Integer> getItemRarityMap() {
        return ITEM_RARITY_MAP;
    }
    
    /**
     * 获取自动计算的稀有度映射
     * @return 自动计算的稀有度映射
     */
    public static java.util.Map<net.minecraft.resources.ResourceLocation, Integer> getAutoRarityMap() {
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