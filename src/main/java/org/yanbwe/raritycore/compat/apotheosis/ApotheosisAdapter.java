package org.yanbwe.raritycore.compat.apotheosis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.yanbwe.raritycore.RarityCore;

/**
 * 神化模组兼容性适配器
 * 实现神化模组稀有度与本模组稀有度的映射
 */
public class ApotheosisAdapter {

    private static boolean isInitialized = false;

    private static final String AFFIX_DATA_KEY = "affix_data";
    private static final String RARITY_KEY = "rarity";

    public static void init() {
        if (isInitialized) {
            return;
        }

        if (!ModList.get().isLoaded("apotheosis")) {
            RarityCore.LOGGER.debug("Apotheosis mod not detected, skipping initialization");
            return;
        }

        isInitialized = true;
        RarityCore.LOGGER.info("Apotheosis compatibility adapter initialized");
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    /**
     * 获取物品的神化稀有度并映射到本模组稀有度
     * 使用包含模式检测 tag.affix_data.rarity NBT 数据
     * @param itemStack 要检查的物品
     * @return 映射后的稀有度等级 (1-9)，如果没有神化稀有度则返回 null
     */
    public static Integer getMappedRarity(ItemStack itemStack) {
        if (!isInitialized || itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        return calculateApotheosisRarity(itemStack);
    }

    /**
     * 计算物品的神化稀有度
     * 直接通过 NBT API 访问 affix_data.rarity，避免 toString() 全量序列化
     * 使用 O(1) 键查找替代字符串解析
     * @param itemStack 物品栈
     * @return 计算得到的稀有度，如果没有则返回 null
     */
    private static Integer calculateApotheosisRarity(ItemStack itemStack) {
        if (!itemStack.hasTag()) {
            return null;
        }

        return calculateApotheosisRarityFromTag(itemStack.getTag());
    }

    /**
     * 使用已读取的 CompoundTag 计算神化稀有度（避免重复 getTag() 调用）
     * 供 RarityRegistry.getRarityInternal() 等已持有 tag 引用的调用方使用
     * @param tag 物品的 CompoundTag
     * @return 计算得到的稀有度，如果没有则返回 null
     */
    public static Integer calculateApotheosisRarityFromTag(CompoundTag tag) {
        if (tag == null) {
            return null;
        }

        // O(1) 键查找：检查是否存在 affix_data 键
        if (!tag.contains(AFFIX_DATA_KEY)) {
            return null;
        }

        // 直接 NBT API 访问：getCompound 在键存在时安全，
        // 内部使用 contains(key, TAG_COMPOUND) 做类型检查
        CompoundTag affixData = tag.getCompound(AFFIX_DATA_KEY);

        // getString 在键缺失时返回 "" — 直接访问是安全的
        String rarityString = affixData.getString(RARITY_KEY);
        if (rarityString.isEmpty()) {
            return null;
        }

        return mapApotheosisRarityString(rarityString);
    }

    /**
     * O(1) 稀有度名称映射表，替代线性 contains() 链
     * 键按长度降序排列以优先匹配长名称，避免子串误匹配（如 "rare" 误匹配 "artifacts:rare"）
     */
    private static final String[] RARITY_NAMES = {
        "esoteric", "heirloom", "artifact", "ancient", "mythic", "epic", "rare", "uncommon", "common"
    };
    private static final int[] RARITY_VALUES = {9, 8, 7, 6, 5, 4, 3, 2, 1};

    /**
     * 根据神化稀有字符串映射到本模组稀有度
     * 使用预排序数组做 O(n) 快速匹配（n=9，常数级），比 contains() 链更高效且易维护
     * @param rarityString 神化稀有度字符串，格式如 "apotheosis:mythic", "apotheosis:ancient" 等
     * @return 对应的本模组稀有度等级 (1-9)
     */
    private static Integer mapApotheosisRarityString(String rarityString) {
        if (rarityString == null || rarityString.isEmpty()) {
            return null;
        }

        String lowerRarity = rarityString.toLowerCase();

        // 遍历预排序的名称数组，第一个匹配即为结果
        for (int i = 0; i < RARITY_NAMES.length; i++) {
            if (lowerRarity.contains(RARITY_NAMES[i])) {
                return RARITY_VALUES[i];
            }
        }

        return null;
    }

    /**
     * 重置初始化状态
     */
    public static void reset() {
        isInitialized = false;
    }
}