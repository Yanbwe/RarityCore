package org.yanbwe.raritycore.compat.ironsspellbooks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nullable;

/**
 * Iron's Spells 'n Spellbooks 兼容性适配器
 * 解析法术卷轴 NBT 中的法术等级，映射为本模组稀有度
 * NBT 路径：tag.irons_spellbooks:spell_container.data[0].level
 * 映射规则：level 1→1, 2→2, … 7→7, 8+→7（上限 7）
 */
public class IronSpellbooksAdapter {

    private static boolean isInitialized = false;

    private static final String SPELL_CONTAINER_KEY = "irons_spellbooks:spell_container";
    private static final String DATA_KEY = "data";
    private static final String LEVEL_KEY = "level";

    public static void init() {
        if (isInitialized) return;

        if (!ModList.get().isLoaded("irons_spellbooks")) {
            RarityCore.LOGGER.debug("Iron's Spellbooks not detected, skipping initialization");
            return;
        }

        isInitialized = true;
        RarityCore.LOGGER.info("Iron's Spellbooks compatibility adapter initialized");
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    /**
     * 获取法术卷轴映射后的稀有度等级
     * @param itemStack 物品栈
     * @return 映射后的稀有度等级 (1-7)，无数据返回 null
     */
    @Nullable
    public static Integer getMappedRarity(ItemStack itemStack) {
        if (!isInitialized || itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return null;
        }

        return calculateSpellbookRarity(itemStack);
    }

    @Nullable
    private static Integer calculateSpellbookRarity(ItemStack itemStack) {
        CompoundTag tag = itemStack.getTag();
        if (tag == null) return null;

        // 检查 spell_container 键
        if (!tag.contains(SPELL_CONTAINER_KEY, CompoundTag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag container = tag.getCompound(SPELL_CONTAINER_KEY);

        // 检查 data 数组
        if (!container.contains(DATA_KEY, Tag.TAG_LIST)) {
            return null;
        }

        ListTag dataList = container.getList(DATA_KEY, Tag.TAG_COMPOUND);
        if (dataList.isEmpty()) return null;

        // 获取第一个法术的数据
        CompoundTag firstSpell = dataList.getCompound(0);
        if (!firstSpell.contains(LEVEL_KEY, Tag.TAG_INT)) {
            return null;
        }

        int spellLevel = firstSpell.getInt(LEVEL_KEY);
        return mapSpellLevel(spellLevel);
    }

    /**
     * 法术等级 → 稀有度映射：直接映射法术等级为稀有度等级，不设上限
     */
    private static int mapSpellLevel(int spellLevel) {
        if (spellLevel < 1) return 1;
        return spellLevel;
    }

    public static void reset() {
        isInitialized = false;
    }
}
