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

        // O(1) 键查找：先检查是否存在 affix_data 键，避免对无神化数据的物品做全量序列化
        if (!tag.contains(AFFIX_DATA_KEY)) {
            return null;
        }

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
     * 注意:使用匹配键名前导字符(逗号或花括号)的方式定位rarity键,
     * 避免匹配到其他键名(如some_rarity_data)或值中偶然包含"rarity"的情况
     */
    private static Integer extractRarityFromBlock(String block) {
        // 循环查找所有"rarity"出现位置,确保匹配的是键名(前有','或'{'后有':')
        int searchStart = 0;
        int rarityIndex = -1;
        
        while (searchStart < block.length()) {
            int idx = block.indexOf(RARITY_KEY, searchStart);
            if (idx == -1) {
                break;
            }
            
            // 检查是否为键名:前一个字符是','或'{' (或是字符串开头)
            int keyEnd = idx + RARITY_KEY.length();
            boolean isKeyPrefix = (idx == 0 || block.charAt(idx - 1) == ',' || block.charAt(idx - 1) == '{');
            boolean isKeySuffix = (keyEnd < block.length() && block.charAt(keyEnd) == ':');
            
            if (isKeyPrefix && isKeySuffix) {
                rarityIndex = idx;
                break;
            }
            
            // 继续向后查找
            searchStart = keyEnd;
        }
        
        if (rarityIndex == -1) {
            return null;
        }

        int colonIndex = rarityIndex + RARITY_KEY.length(); // ':'的位置
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