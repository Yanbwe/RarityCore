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
    private static final int NBT_STRING_LENGTH_THRESHOLD = 50000;

    private static volatile long lastErrorTime = 0;
    private static volatile String lastErrorItem = "";
    private static final long ERROR_COOLDOWN = 5000;

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
     * 使用包含模式检测 NBT 数据中的稀有度信息
     * @param itemStack 物品栈
     * @return 计算得到的稀有度，如果没有则返回 null
     */
    private static Integer calculateApotheosisRarity(ItemStack itemStack) {
        if (!itemStack.hasTag()) {
            return null;
        }

        CompoundTag tag = itemStack.getTag();
        String tagString = tag.toString();

        if (tagString.length() > NBT_STRING_LENGTH_THRESHOLD) {
            String itemId = itemStack.getItem().toString();
            long currentTime = System.currentTimeMillis();
            if (!itemId.equals(lastErrorItem) ||
                (currentTime - lastErrorTime) > ERROR_COOLDOWN) {
                RarityCore.LOGGER.debug("Skipping apotheosis rarity check for item with oversized NBT: {} (NBT length: {})",
                    itemId, tagString.length());
                lastErrorItem = itemId;
                lastErrorTime = currentTime;
            }
            return null;
        }

        if (!tagString.contains(AFFIX_DATA_KEY)) {
            return null;
        }

        int affixDataStart = tagString.indexOf(AFFIX_DATA_KEY);
        int braceStart = tagString.indexOf("{", affixDataStart);
        int braceEnd = findMatchingBrace(tagString, braceStart);

        if (braceStart == -1 || braceEnd == -1) {
            return null;
        }

        String affixDataBlock = tagString.substring(braceStart, braceEnd + 1);
        return extractRarityFromBlock(affixDataBlock);
    }

    /**
     * 查找匹配的右括号
     */
    private static int findMatchingBrace(String str, int openBraceIndex) {
        if (str.charAt(openBraceIndex) != '{') {
            return -1;
        }

        int depth = 1;
        for (int i = openBraceIndex + 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 从 affix_data 块中提取 rarity 值
     */
    private static Integer extractRarityFromBlock(String block) {
        int rarityIndex = block.indexOf(RARITY_KEY);
        if (rarityIndex == -1) {
            return null;
        }

        int colonIndex = block.indexOf(":", rarityIndex);
        if (colonIndex == -1) {
            return null;
        }

        int quoteStart = block.indexOf("\"", colonIndex);
        if (quoteStart == -1) {
            return null;
        }

        int quoteEnd = block.indexOf("\"", quoteStart + 1);
        if (quoteEnd == -1) {
            return null;
        }

        String rarityString = block.substring(quoteStart + 1, quoteEnd);
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