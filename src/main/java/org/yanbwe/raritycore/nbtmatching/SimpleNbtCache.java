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

    private static Cache<String, Integer> itemCache;
    private static final int BASE_CACHE_SIZE = 100;
    private static final int MAX_CACHE_SIZE = 2000;

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
     * @param stack 物品栈
     * @return 缓存键字符串
     */
    public static String generateNbtCacheKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return "";
        }

        StringBuilder key = new StringBuilder(itemId.toString());

        if (stack.hasTag() && stack.getTag() != null) {
            CompoundTag tag = stack.getTag();
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] hash = md.digest(tag.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    hexString.append(String.format("%02x", b));
                }
                key.append("|nbt:hash:").append(hexString.toString());
            } catch (Exception e) {
                key.append("|nbt:").append(tag.toString());
            }
        }

        return key.toString();
    }

    /**
     * 获取缓存的稀有度值
     * @param stack 物品堆
     * @return 缓存的稀有度,如果未缓存或未匹配到规则则返回 null
     */
    public static Integer getCachedRarity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        String cacheKey = generateNbtCacheKey(stack);
        if (cacheKey.isEmpty()) {
            return null;
        }

        Integer result = itemCache.getIfPresent(cacheKey);
        if (result != null) {
            return result != -1 ? result : null;
        }

        return null;
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