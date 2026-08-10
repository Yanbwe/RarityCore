package org.yanbwe.raritycore.registry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.compat.modularshoot.ModularShootAdapter;

/**
 * 组件稀有度读取器
 * 从物品的 DataComponents.CUSTOM_DATA 中读取 "raritycore" 复合标签，
 * 解析由外部工具或模组写入的稀有度控制字段。
 *
 * <p>读取路径：stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("raritycore")</p>
 *
 * <p>支持的可选字段：Level、Color、Tooltips、Renderer、NameColor、TextureBorder。
 * Level 为 0 或不存在时视为无效，返回 null 以回退到常规稀有度查询链。</p>
 *
 * <p>优先级：Component 稀有度凌驾所有其他稀有度来源（最高优先级）。</p>
 */
public class ComponentRarityReader {

    /** raritycore 复合标签在 CUSTOM_DATA 中的键名 */
    private static final String RARITYCORE_KEY = "raritycore";

    /** 稀有度等级的 NBT 键 */
    private static final String KEY_LEVEL = "Level";
    /** 颜色字符串的 NBT 键（#RRGGBB 格式） */
    private static final String KEY_COLOR = "Color";
    /** Tooltips 显示的 NBT 键 */
    private static final String KEY_TOOLTIPS = "Tooltips";
    /** 渲染器类型的 NBT 键 */
    private static final String KEY_RENDERER = "Renderer";
    /** 名称颜色的 NBT 键（#RRGGBB 格式） */
    private static final String KEY_NAME_COLOR = "NameColor";
    /** 纹理边框路径的 NBT 键 */
    private static final String KEY_TEXTURE_BORDER = "TextureBorder";

    /**
     * 组件稀有度数据记录
     * 包含从物品 DataComponent 中解析出的所有可选字段。
     *
     * @param level 稀有度等级（1-7），null 或 0 表示无效
     * @param color 颜色字符串（#RRGGBB），可选
     * @param tooltips Tooltips 设置字符串，可选
     * @param renderer 渲染器类型，可选
     * @param nameColor 名称颜色字符串（#RRGGBB），可选
     * @param textureBorder 纹理边框路径，可选
     */
    public record ComponentRarityData(
        @Nullable Integer level,
        @Nullable String color,
        @Nullable String tooltips,
        @Nullable String renderer,
        @Nullable String nameColor,
        @Nullable String textureBorder
    ) {
        /**
         * 检查 Level 是否有效（非 null 且大于 0）
         * @return Level 有效时返回 true
         */
        public boolean hasValidLevel() {
            return level != null && level > 0;
        }

        /** 空的组件稀有度数据，表示无组件稀有度 */
        public static final ComponentRarityData EMPTY = new ComponentRarityData(
            null, null, null, null, null, null
        );
    }

    /**
     * 从物品栈读取完整的组件稀有度数据
     * 读取路径：stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("raritycore")
     *
     * @param stack 物品栈，不可为 null 但可以为空
     * @return 组件稀有度数据，无数据时返回 null
     */
    @Nullable
    public static ComponentRarityData read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        // 半开判定：仅 ModularShoot 提供的枪械/插件物品参与组件稀有度读取，
        // 无关物品 O(1) 短路跳过（避免 copyTag 全量 NBT 复制的热路径开销）
        if (!ModularShootAdapter.isRelevant(stack)) {
            return null;
        }

        try {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null) {
                return null;
            }

            CompoundTag tag = customData.copyTag();
            if (!tag.contains(RARITYCORE_KEY, Tag.TAG_COMPOUND)) {
                return null;
            }

            CompoundTag rarityTag = tag.getCompound(RARITYCORE_KEY);
            if (rarityTag.isEmpty()) {
                return null;
            }

            // 读取 Level（必须有效，否则整体视为无组件稀有度）
            Integer level = readIntField(rarityTag, KEY_LEVEL);

            // 读取全部可选字段
            String color = readStringField(rarityTag, KEY_COLOR);
            String tooltips = readTooltipsField(rarityTag);
            String renderer = readStringField(rarityTag, KEY_RENDERER);
            String nameColor = readStringField(rarityTag, KEY_NAME_COLOR);
            String textureBorder = readStringField(rarityTag, KEY_TEXTURE_BORDER);

            return new ComponentRarityData(level, color, tooltips, renderer, nameColor, textureBorder);

        } catch (Exception e) {
            RarityCore.LOGGER.debug("读取组件稀有度数据时出错: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从物品栈读取组件稀有度等级（仅 Level 字段）
     * Level=0 或无 Level 时返回 null，调用方回退到常规查询。
     *
     * @param stack 物品栈
     * @return 稀有度等级，无效时返回 null
     */
    @Nullable
    public static Integer readLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        // 半开判定：仅 ModularShoot 提供的枪械/插件物品参与组件稀有度读取，
        // 无关物品 O(1) 短路跳过（避免 copyTag 全量 NBT 复制的热路径开销）
        if (!ModularShootAdapter.isRelevant(stack)) {
            return null;
        }

        try {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null) {
                return null;
            }

            CompoundTag tag = customData.copyTag();
            if (!tag.contains(RARITYCORE_KEY, Tag.TAG_COMPOUND)) {
                return null;
            }

            CompoundTag rarityTag = tag.getCompound(RARITYCORE_KEY);
            Integer level = readIntField(rarityTag, KEY_LEVEL);

            // Level=0 或无 Level 时返回 null（回退到常规查询）
            return (level != null && level > 0) ? level : null;

        } catch (Exception e) {
            RarityCore.LOGGER.debug("读取组件稀有度等级时出错: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查物品栈是否包含有效的组件稀有度数据
     *
     * @param stack 物品栈
     * @return 包含有效组件稀有度数据时返回 true
     */
    public static boolean hasComponentRarity(ItemStack stack) {
        return readLevel(stack) != null;
    }

    // ==================== 内部解析辅助方法 ====================

    /**
     * 从复合标签中读取整数字段
     * @param tag 复合标签
     * @param key 键名
     * @return 整数值，不存在时返回 null
     */
    @Nullable
    private static Integer readIntField(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_INT)) {
            return tag.getInt(key);
        }
        return null;
    }

    /**
     * 从复合标签中读取字符串字段
     * @param tag 复合标签
     * @param key 键名
     * @return 字符串值，不存在时返回 null
     */
    @Nullable
    private static String readStringField(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_STRING)) {
            return tag.getString(key);
        }
        return null;
    }

    /**
     * 从复合标签中灵活读取 Tooltips 字段
     * 支持 String 和 Boolean 两种写入格式。
     * @param tag 复合标签
     * @return Tooltips 字符串值，不存在时返回 null
     */
    @Nullable
    private static String readTooltipsField(CompoundTag tag) {
        if (tag.contains(KEY_TOOLTIPS, Tag.TAG_STRING)) {
            return tag.getString(KEY_TOOLTIPS);
        }
        if (tag.contains(KEY_TOOLTIPS, Tag.TAG_BYTE)) {
            return String.valueOf(tag.getBoolean(KEY_TOOLTIPS));
        }
        return null;
    }
}
