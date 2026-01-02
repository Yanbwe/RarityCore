package org.yanbwe.raritycore.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

public class RarityRegistry {
    /**
     * 物品稀有度映射
     */
    public static final ConcurrentHashMap<ResourceLocation, Integer> ITEM_RARITY_MAP = new ConcurrentHashMap<>();

    /**
     * 注册物品的稀有度等级
     * 1普通，2稀有，3罕见，4史诗，5传说，6神话，7唯一
     * 不注册视为普通品质
     * @param item 要注册稀有度的物品
     * @param rarity 稀有度等级
     */
    public static void register(@Nullable Item item, int rarity) {
        if (item != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                ITEM_RARITY_MAP.put(itemId, rarity);
            }
        }
    }

    /**
     * 获取物品的稀有度等级
     * @param item 要查稀有度的物品
     * @return 物品的稀有度等级（1-7）
     */
    @Nullable
    public static Integer getRarity(@Nullable Item item) {
        if (item != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                return ITEM_RARITY_MAP.get(itemId);
            }
        }
        return null;
    }
}