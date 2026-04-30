package org.yanbwe.raritycore.nbtmatching;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;

import java.util.concurrent.TimeUnit;

/**
 * 简化版NBT匹配缓存
 * 提供高效的物品稀有度缓存机制,支持动态容量调整
 * 缓存键格式: itemId|nbt:hash:xxx 或 itemId|nbt:原始NBT字符串
 */
public class SimpleNbtCache {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static Cache<String, Integer> itemCache;
    private static final int BASE_CACHE_SIZE = 100;
    private static final int MAX_CACHE_SIZE = 2000;
    private static final int NBT_HASH_TRUNCATE_LENGTH = 4096;

    private static volatile long lastKeyErrorTime = 0;
    private static volatile String lastKeyErrorItem = "";
    private static final long KEY_ERROR_COOLDOWN = 5000;

    static {
        initializeCache();
    }

    private static void initializeCache() {
        int dynamicSize = calculateDynamicCacheSize();
        itemCache = CacheBuilder.newBuilder()
            .maximumSize(dynamicSize)
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();

        RarityCore.LOGGER.info("NBT cache initialized with capacity: {}", dynamicSize);
    }

    private static int calculateDynamicCacheSize() {
        int ruleCount = NbtRarityMatcher.getRuleCount();
        int calculatedSize = BASE_CACHE_SIZE + (ruleCount * 10);
        return Math.min(calculatedSize, MAX_CACHE_SIZE);
    }

    public static void reinitializeCache() {
        itemCache.invalidateAll();
        initializeCache();
        RarityCore.LOGGER.info("NBT cache reinitialized");
    }

    /**
     * 生成NBT缓存键
     * 使用物品ID + NBT数据的哈希值作为缓存键
     * 当物品没有NBT匹配规则时返回空字符串
     * @param stack 物品栈
     * @return 缓存键字符串
     */
    public static String generateNbtCacheKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            RarityCore.LOGGER.debug("[NBT缓存] 物品为空,生成空缓存键");
            return "";
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            RarityCore.LOGGER.debug("[NBT缓存] 无法获取物品ID,生成空缓存键");
            return "";
        }

        if (!NbtRarityMatcher.hasRulesForItem(itemId)) {
            RarityCore.LOGGER.debug("[NBT缓存] 物品 {} 没有NBT匹配规则,生成空缓存键", itemId);
            return "";
        }

        RarityCore.LOGGER.debug("[NBT缓存] 物品 {} 拥有NBT规则,开始生成缓存键", itemId);

        StringBuilder key = new StringBuilder(itemId.toString());

        if (stack.hasTag() && stack.getTag() != null) {
            CompoundTag tag = stack.getTag();
            try {
                String nbtString = tag.toString();
                byte[] hashInput;

                if (nbtString.length() > NBT_HASH_TRUNCATE_LENGTH) {
                    hashInput = nbtString.substring(0, NBT_HASH_TRUNCATE_LENGTH).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    hashInput = nbtString.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }

                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] hash = md.digest(hashInput);
                StringBuilder hexString = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    hexString.append(HEX_CHARS[(b >> 4) & 0xF]);
                    hexString.append(HEX_CHARS[b & 0xF]);
                }
                key.append("|nbt:hash:").append(hexString.toString());
                if (nbtString.length() > NBT_HASH_TRUNCATE_LENGTH) {
                    key.append(":truncated");
                }
            } catch (Exception e) {
                String currentItemId = itemId.toString();
                long currentTime = System.currentTimeMillis();
                if (!currentItemId.equals(lastKeyErrorItem) ||
                    (currentTime - lastKeyErrorTime) > KEY_ERROR_COOLDOWN) {
                    RarityCore.LOGGER.debug("Error generating NBT cache key for item: {}", currentItemId);
                    lastKeyErrorItem = currentItemId;
                    lastKeyErrorTime = currentTime;
                }
                return "";
            }
        }

        return key.toString();
    }

    /**
     * 获取缓存的稀有度值
     * @param stack 物品堆
     * @return 缓存的稀有度,如果未匹配到规则则返回 null
     */
    public static Integer getCachedRarity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            RarityCore.LOGGER.debug("[NBT缓存] getCachedRarity: 物品为空");
            return null;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null && RarityCore.LOGGER.isDebugEnabled()) {
            RarityCore.LOGGER.debug("[NBT缓存] getCachedRarity: 物品ID={}, hasTag={}, tag={}", 
                itemId, stack.hasTag(), stack.hasTag() ? stack.getTag().toString() : "null");
        }

        String cacheKey = generateNbtCacheKey(stack);
        if (cacheKey.isEmpty()) {
            RarityCore.LOGGER.debug("[NBT缓存] getCachedRarity: 缓存键为空,跳过缓存");
            return null;
        }

        RarityCore.LOGGER.debug("[NBT缓存] getCachedRarity: 缓存键={}", cacheKey);

        Integer result = itemCache.getIfPresent(cacheKey);
        if (result != null) {
            RarityCore.LOGGER.debug("[NBT缓存] 缓存命中: {}", result != -1 ? result : "无匹配");
            return result != -1 ? result : null;
        }

        RarityCore.LOGGER.debug("[NBT缓存] 缓存未命中,开始计算稀有度");

        result = NbtRarityMatcher.calculateWithoutCache(stack);
        if (result != null) {
            RarityCore.LOGGER.debug("[NBT缓存] 计算结果: 稀有度={}, 写入缓存", result);
            itemCache.put(cacheKey, result);
            return result;
        } else {
            RarityCore.LOGGER.debug("[NBT缓存] 无匹配结果,写入-1到缓存");
            itemCache.put(cacheKey, -1);
            return null;
        }
    }

    /**
     * 手动添加缓存条目
     * @param stack 物品堆
     * @param rarity 稀有度值
     */
    public static void put(ItemStack stack, Integer rarity) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        String cacheKey = generateNbtCacheKey(stack);
        if (cacheKey.isEmpty()) {
            return;
        }

        if (rarity != null) {
            itemCache.put(cacheKey, rarity);
        } else {
            itemCache.put(cacheKey, -1);
        }
    }

    /**
     * 清除指定物品的缓存
     * @param stack 物品堆
     */
    public static void invalidate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        String cacheKey = generateNbtCacheKey(stack);
        if (!cacheKey.isEmpty()) {
            itemCache.invalidate(cacheKey);
        }
    }

    /**
     * 清空所有缓存
     */
    public static void invalidateAll() {
        itemCache.invalidateAll();
    }

    /**
     * 获取缓存统计信息
     * @return 缓存大小
     */
    public static long getCacheSize() {
        return itemCache.size();
    }
}