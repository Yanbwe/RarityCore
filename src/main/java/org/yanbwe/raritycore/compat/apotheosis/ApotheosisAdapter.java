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

        CompoundTag tag = itemStack.getTag();

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
     * 根据神化稀有字符串映射到本模组稀有度
     * 使用包含模式匹配稀有度名称，按长度降序避免子串误匹配
     * @param rarityString 神化稀有度字符串，格式如 "apotheosis:mythic", "apotheosis:ancient" 等
     * @return 对应的本模组稀有度等级 (1-9)
     */
    private static Integer mapApotheosisRarityString(String rarityString) {
        if (rarityString == null || rarityString.isEmpty()) {
            return null;
        }

        String lowerRarity = rarityString.toLowerCase();

        if (lowerRarity.contains("esoteric")) {
            return 9;
        }
        if (lowerRarity.contains("heirloom")) {
            return 8;
        }
        if (lowerRarity.contains("artifact")) {
            return 7;
        }
        if (lowerRarity.contains("ancient")) {
            return 6;
        }
        if (lowerRarity.contains("mythic")) {
            return 5;
        }
        if (lowerRarity.contains("epic")) {
            return 4;
        }
        if (lowerRarity.contains("rare")) {
            return 3;
        }
        if (lowerRarity.contains("uncommon")) {
            return 2;
        }
        if (lowerRarity.contains("common")) {
            return 1;
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