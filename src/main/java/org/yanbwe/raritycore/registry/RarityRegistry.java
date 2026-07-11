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

    // 物品稀有度映射（来自 FinalRarity.json、数据包等用户手动配置）
    public static final ConcurrentHashMap<ResourceLocation, Integer> ITEM_RARITY_MAP = new ConcurrentHashMap<>();

    // 自动计算的稀有度映射（来自 auto_rarity.json，优先级低于 ITEM_RARITY_MAP）
    private static final ConcurrentHashMap<ResourceLocation, Integer> AUTO_RARITY_MAP = new ConcurrentHashMap<>();

    // 放入自动计算的稀有度配置
    public static void putAutoRarity(ResourceLocation itemId, int rarity) {
        AUTO_RARITY_MAP.put(itemId, rarity);
    }

    // 移除自动计算的稀有度配置
    public static void removeAutoRarity(ResourceLocation itemId) {
        AUTO_RARITY_MAP.remove(itemId);
    }

    // 注册物品的稀有度等级，不注册视为最低档位
    public static void register(@Nullable Item item, int rarity) {
        register(item, rarity, true);
    }

    // 注册物品的稀有度等级，可选是否同步到客户端
    public static void register(@Nullable Item item, int rarity, boolean syncToClients) {
        if (item != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                Integer oldRarity = ITEM_RARITY_MAP.put(itemId, rarity);


                RarityChangeEvent.ChangeType changeType = (oldRarity == null) ?
                    RarityChangeEvent.ChangeType.REGISTER : RarityChangeEvent.ChangeType.UPDATE;
                MinecraftForge.EVENT_BUS.post(new RarityChangeEvent(item, oldRarity, rarity, changeType));



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



    // 删除物品的稀有度注册
    public static void unregister(@Nullable Item item, boolean syncToClients) {
        if (item != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                Integer removedRarity = ITEM_RARITY_MAP.remove(itemId);


                if (removedRarity != null) {
                    MinecraftForge.EVENT_BUS.post(new RarityChangeEvent(
                        item, removedRarity, null, RarityChangeEvent.ChangeType.REMOVE));
                }



                if (syncToClients) {
                    if (removedRarity != null) {
                        SyncManager.addChangeOperation(new ChangeOperation(ChangeOperation.OperationType.DELETE, itemId, null));
                    }
                }
            }
        }
    }




    // 获取物品的稀有度等级（标准化版本，小于 1 钳制为 1）
    public static @NotNull Integer getNormalizedRarity(@Nullable Item item) {
        Integer rawRarity = getRarity(item);
        return org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rawRarity);
    }

    // 获取物品栈的稀有度等级（标准化版本，支持 NBT 匹配，小于 1 钳制为 1）
    public static @NotNull Integer getNormalizedRarity(@Nullable ItemStack itemStack) {
        Integer rawRarity = getRarity(itemStack);
        return org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rawRarity);
    }

    // 获取本地化文本（仅客户端可用）
    @OnlyIn(Dist.CLIENT)
    private static String getLocalizedText(String key) {
        try {
            return net.minecraft.network.chat.Component.translatable(key).getString();
        } catch (Exception e) {
            return key;
        }
    }

    // 获取物品的完整稀有度工具提示字符串（仅客户端，基于 Item）
    @OnlyIn(Dist.CLIENT)
    public static @NotNull String getLocalizedRarityTooltip(@Nullable Item item) {
        if (item == null) {
            return "[普通]";
        }
        Integer rarity = getRarity(item);
        if (rarity == null) rarity = RarityConstants.MIN_RARITY;
        return buildLocalizedRarityTooltip(rarity);
    }

    // 获取物品栈的完整稀有度工具提示字符串（支持神化 NBT 稀有度检测，仅客户端）
    @OnlyIn(Dist.CLIENT)
    public static @NotNull String getLocalizedRarityTooltip(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return "[普通]";
        }
        Integer rarity = getRarity(itemStack);
        if (rarity == null) rarity = RarityConstants.MIN_RARITY;
        return buildLocalizedRarityTooltip(rarity);
    }

    // 根据稀有度值构建本地化工具提示字符串（内部公用方法，仅客户端）
    @OnlyIn(Dist.CLIENT)
    private static @NotNull String buildLocalizedRarityTooltip(int rawRarity) {
        int normalizedRarity = org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rawRarity);
        String key = org.yanbwe.raritycore.config.RarityStyleConfigManager.getLevelTranslationKey(normalizedRarity)
            .replace("{level}", String.valueOf(normalizedRarity));
        String localizedLabel;
        if (org.yanbwe.raritycore.util.StringResolver.isTranslationKey(key)) {
            String realKey = org.yanbwe.raritycore.util.StringResolver.extractKey(key);
            if (org.yanbwe.raritycore.util.StringResolver.isKeyMissing(realKey)) {
                localizedLabel = net.minecraft.network.chat.Component.translatable(
                    org.yanbwe.raritycore.util.StringResolver.extractKey(
                        org.yanbwe.raritycore.config.RarityStyleConfigManager.getLevelFallbackKey(normalizedRarity)
                            .replace("{level}", String.valueOf(normalizedRarity)))).getString();
            } else {
                localizedLabel = net.minecraft.network.chat.Component.translatable(realKey).getString();
            }
        } else {
            localizedLabel = org.yanbwe.raritycore.util.StringResolver.resolveEmbeddedKeys(key);
        }
        String stars = org.yanbwe.raritycore.util.ComponentBuilder.getStars(normalizedRarity);
        return localizedLabel + " " + stars;
    }



    // 获取物品栈的稀有度等级（支持 NBT 数据）
    public static @NotNull Integer getRarity(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return getNoRarityDefault();
        }

        Item item = itemStack.getItem();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            return getNoRarityDefault();
        }

        return getRarityInternal(itemId, itemStack, item);
    }

    // 获取物品的稀有度等级
    public static @NotNull Integer getRarity(@Nullable Item item) {
        if (item != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                return getRarityFromItemId(itemId, item);
            }
        }
        return getNoRarityDefault();
    }

    // 获取未配置稀有度物品的默认等级（来自 RarityStyle 配置 defaults.noRarity.defaultRarity）
    private static int getNoRarityDefault() {
        try {
            return org.yanbwe.raritycore.config.RarityStyleConfigManager.getDefaultsNoRarityDefaultRarity();
        } catch (Exception ignored) {
            return 1;
        }
    }

    // 从物品 ID 解析稀有度（注册表 > 自动计算 > 原版映射）
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

        return getNoRarityDefault();
    }

    // 统一的稀有度获取逻辑，按优先级依次尝试各来源
    private static @NotNull Integer getRarityInternal(ResourceLocation itemId, @Nullable ItemStack itemStack, Item item) {
        int result;
        String source = "vanilla";

        // 预读取 NBT tag 一次，避免后续各检测重复调用 hasTag/getTag
        CompoundTag tag = (itemStack != null && itemStack.hasTag()) ? itemStack.getTag() : null;

        if (tag != null) {

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

        result = getNoRarityDefault();
        return fireQueryEvent(itemStack, result, source);
    }



    // 触发 RarityQueryEvent 并返回最终稀有度
    private static int fireQueryEvent(@Nullable ItemStack itemStack, int rarity, String source) {
        RarityQueryEvent event = new RarityQueryEvent(
            itemStack != null ? itemStack : ItemStack.EMPTY, rarity, source);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getRarity();
    }



    // 检查 NBT 匹配稀有度
    private static Integer checkNbtRarity(@Nullable ItemStack itemStack) {
        if (itemStack != null && itemStack.hasTag()) {
            return org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher.getNbtMatchedRarity(itemStack);
        }
        return null;
    }

    // 获取物品的神化稀有度（公共入口，供外部直接调用）
    @Nullable
    public static Integer getDirectApotheosisRarity(@Nullable ItemStack itemStack) {
        return checkApotheosisRarity(itemStack);
    }

    // 检查物品是否有配置的稀有度（含神化 NBT 检测）
    public static boolean hasConfiguredRarity(@Nullable Item item, @Nullable ItemStack itemStack) {
        if (item == null) {
            return false;
        }


        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            return false;
        }

        if (ITEM_RARITY_MAP.containsKey(itemId)) {
            return true;
        }

        if (itemStack != null && itemStack.hasTag()) {
            Integer apothRarity = getDirectApotheosisRarity(itemStack);
            if (apothRarity != null) {
                return true;
            }
        }

        return false;
    }



    // 检查神化模组稀有度（不应缓存，依赖物品实时 NBT 数据）
    @Nullable
    private static Integer checkApotheosisRarity(@Nullable ItemStack itemStack) {
        if (!org.yanbwe.raritycore.config.ServerConfigManager.isCheckApotheosisRarity() || itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        return org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter.getMappedRarity(itemStack);
    }

    // 检查神化模组稀有度（使用已读取的 CompoundTag，避免重复 getTag()）
    @Nullable
    private static Integer checkApotheosisRarityWithTag(@Nullable ItemStack itemStack, @Nullable CompoundTag tag) {
        if (!org.yanbwe.raritycore.config.ServerConfigManager.isCheckApotheosisRarity() || itemStack == null || itemStack.isEmpty() || tag == null) {
            return null;
        }

        return org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter.calculateApotheosisRarityFromTag(tag);
    }



    // 检查 Iron's Spellbooks 稀有度
    @Nullable
    private static Integer checkIronSpellbooksRarity(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return null;
        }
        return org.yanbwe.raritycore.compat.ironsspellbooks.IronSpellbooksAdapter.getMappedRarity(itemStack);
    }



    // 检查原版稀有度
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



    // 映射原版稀有度到本模组稀有度等级
    private static Integer mapVanillaRarity(Rarity vanillaRarity) {
        if (vanillaRarity == Rarity.UNCOMMON) {
            return 3;
        } else if (vanillaRarity == Rarity.RARE) {
            return 4;
        } else if (vanillaRarity == Rarity.EPIC) {
            return 5;
        } else {
            return 1;
        }
    }


    // 获取物品稀有度映射
    public static java.util.Map<net.minecraft.resources.ResourceLocation, Integer> getItemRarityMap() {
        return ITEM_RARITY_MAP;
    }

    // 返回所有被解析为指定稀有度等级的物品
    public static java.util.List<Item> getItemsByRarity(int rarity) {
        java.util.List<Item> result = new java.util.ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (getRarity(item) == rarity) {
                result.add(item);
            }
        }
        return java.util.Collections.unmodifiableList(result);
    }



    // 返回所有被解析为指定稀有度等级的物品 ID
    public static java.util.List<ResourceLocation> getItemIdsByRarity(int rarity) {
        java.util.List<ResourceLocation> result = new java.util.ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (getRarity(item) == rarity) {
                result.add(ForgeRegistries.ITEMS.getKey(item));
            }
        }
        return java.util.Collections.unmodifiableList(result);
    }



    // 返回当前出现过的稀有度等级集合
    public static java.util.Set<Integer> getConfiguredRarities() {
        java.util.Set<Integer> result = new java.util.HashSet<>();
        result.addAll(ITEM_RARITY_MAP.values());
        result.addAll(AUTO_RARITY_MAP.values());
        result.add(getNoRarityDefault());
        return java.util.Collections.unmodifiableSet(result);
    }



    // 返回所有被解析为指定稀有度等级集合中任一等级的物品
    public static java.util.List<Item> getItemsByRarities(java.util.Set<Integer> rarities) {
        java.util.List<Item> result = new java.util.ArrayList<>();
        if (rarities == null || rarities.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (rarities.contains(getRarity(item))) {
                result.add(item);
            }
        }
        return java.util.Collections.unmodifiableList(result);
    }



    // 返回被解析为指定稀有度等级的物品数量
    public static int getRarityCount(int rarity) {
        int count = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (getRarity(item) == rarity) {
                count++;
            }
        }
        return count;
    }



    // 返回当前全部已解析稀有度等级的快照（显式配置与自动计算合并，含无稀有度默认等级）
    public static java.util.Map<ResourceLocation, Integer> getAllRarityEntries() {
        java.util.Map<ResourceLocation, Integer> result = new java.util.HashMap<>();
        result.putAll(AUTO_RARITY_MAP);
        result.putAll(ITEM_RARITY_MAP);
        return java.util.Collections.unmodifiableMap(result);
    }



    // 获取自动计算的稀有度映射
    public static java.util.Map<net.minecraft.resources.ResourceLocation, Integer> getAutoRarityMap() {
        return AUTO_RARITY_MAP;
    }

    // 应用来自服务端同步的自动稀有度映射（仅客户端）
    public static void applySyncedAutoRarity(java.util.Map<net.minecraft.resources.ResourceLocation, Integer> autoRarityMap) {
        AUTO_RARITY_MAP.clear();
        if (autoRarityMap != null) {
            AUTO_RARITY_MAP.putAll(autoRarityMap);
        }
    }



    // 同步稀有度数据到客户端
    public static void syncRarityToClients() {
        SyncManager.syncRarityToClients(ITEM_RARITY_MAP, AUTO_RARITY_MAP,
            org.yanbwe.raritycore.config.TagRarityConfigManager.getSyncedRules());
    }



    public static void syncRarityToClientsWithRetry() {
        SyncManager.syncRarityToClientsWithRetry(ITEM_RARITY_MAP, AUTO_RARITY_MAP,
            org.yanbwe.raritycore.config.TagRarityConfigManager.getSyncedRules());
    }



    // 同步增量变更到客户端
    public static void syncIncrementalChangesToClients() {
        SyncManager.syncIncrementalChangesToClients();
    }



    // 同步增量变更到客户端（带重试机制）
    public static void syncIncrementalChangesToClientsWithRetry() {
        SyncManager.syncIncrementalChangesToClientsWithRetry();
    }



    // 获取待处理的变更操作数量
    public static int getPendingChangeCount() {
        return SyncManager.getPendingChangeCount();
    }




}
