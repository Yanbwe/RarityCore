package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.DualCacheManager;

/**
 * NBT匹配缓存 — 委托至 DualCacheManager 统一管理
 * 
 * 所有缓存读写操作均通过 DualCacheManager 的共享 nbtCache (Guava Cache) 完成。
 * 本类仅保留缓存键生成逻辑和未命中时的回退计算（calculateWithoutCache）。
 * 
 * 缓存键格式: itemId|nbt:{hashCode}  （与 DualCacheManager.generateNbtKey 一致）
 */
public class SimpleNbtCache {

    /**
     * 重新初始化NBT缓存
     * 委托至 DualCacheManager 清空共享的 NBT 缓存实例
     */
    public static void reinitializeCache() {
        DualCacheManager.invalidateAllNbtCache();
        RarityCore.LOGGER.info("NBT cache reinitialized (delegated to DualCacheManager)");
    }

    /**
     * 生成NBT缓存键
     * 使用物品ID + NBT数据的 CompoundTag.hashCode() 作为缓存键。
     * 当物品没有NBT匹配规则时返回空字符串（跳过缓存）。
     * @param stack 物品栈
     * @return 缓存键字符串，不可缓存时返回空字符串
     */
    public static String generateNbtCacheKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return "";
        }

        if (!NbtRarityMatcher.hasRulesForItem(itemId)) {
            return "";
        }

        StringBuilder key = new StringBuilder(itemId.toString());

        if (stack.hasTag() && stack.getTag() != null) {
            CompoundTag tag = stack.getTag();
            // 使用 CompoundTag.hashCode() 替代 MD5 哈希
            // hashCode() 遍历 NBT 树使用原始类型操作，无需全量字符串序列化
            int nbtHash = tag.hashCode();
            key.append("|nbt:").append(nbtHash);
        }

        return key.toString();
    }

    /**
     * 获取缓存的稀有度值
     * 先查共享 NBT 缓存，未命中则调用 calculateWithoutCache 计算并回填。
     * @param stack 物品堆
     * @return 缓存的稀有度，如果未匹配到规则则返回 null
     */
    public static Integer getCachedRarity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        String cacheKey = generateNbtCacheKey(stack);
        if (cacheKey.isEmpty()) {
            return null;
        }

        // 查询共享 NBT 缓存（委托至 DualCacheManager）
        Integer result = DualCacheManager.getCachedNbtRarity(cacheKey);
        if (result != null) {
            // -1 是"无匹配"标记，返回 null
            return result != -1 ? result : null;
        }

        // 缓存未命中 — 执行实际匹配计算
        result = NbtRarityMatcher.calculateWithoutCache(stack);
        if (result != null) {
            DualCacheManager.cacheNbtRarity(cacheKey, result);
            return result;
        } else {
            // 存入 -1 标记"无匹配"，避免重复计算
            DualCacheManager.cacheNbtRarity(cacheKey, -1);
            return null;
        }
    }

    /**
     * 手动添加缓存条目
     * @param stack 物品堆
     * @param rarity 稀有度值（null 将存入 -1 作为"无匹配"标记）
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
            DualCacheManager.cacheNbtRarity(cacheKey, rarity);
        } else {
            DualCacheManager.cacheNbtRarity(cacheKey, -1);
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
            DualCacheManager.invalidateNbtCacheEntry(cacheKey);
        }
    }

    /**
     * 清空所有缓存
     */
    public static void invalidateAll() {
        DualCacheManager.invalidateAllNbtCache();
    }

    /**
     * 获取缓存统计信息
     * @return 缓存条目数（来自共享 NBT 缓存）
     */
    public static long getCacheSize() {
        return DualCacheManager.getNbtCacheSize();
    }
}
