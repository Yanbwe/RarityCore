package org.yanbwe.raritycore.registry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.ServerConfigManager;

import javax.annotation.Nullable;

/**
 * Component 稀有度解析器
 * <p>
 * 从 ItemStack 的 {@link DataComponents#CUSTOM_DATA} 中读取 {@code "raritycore"} 子标签,
 * 实现基于 Data Component 的稀有度控制。
 * <p>
 * Component 稀有度优先级为所有稀有度来源中的最高级，
 * 一旦有效则凌驾于 Tag 稀有度、ID 映射、自动稀有度等一切其他来源。
 */
public class ComponentRarityResolver {

    private static final String RARITYCORE_TAG = "raritycore";
    private static final String TAG_LEVEL = "Level";
    private static final String TAG_COLOR = "Color";
    private static final String TAG_TOOLTIPS = "Tooltips";
    private static final String TAG_RENDERER = "Renderer";
    private static final String TAG_NAME_COLOR = "NameColor";
    private static final String TAG_TEXTURE_BORDER = "TextureBorder";

    /**
     * Component 稀有度数据记录
     * <p>
     * 每个字段均可选 — 若 ItemStack 的 NBT 中未提供对应字段, 则包装类型为 {@code null}、
     * 原始类型为默认值 (level=0)。
     * 仅当 {@code level > 0} 时视为有效稀有度覆盖。
     *
     * @param level         稀有度等级, 1~7 有效, <=0 或缺失表示无稀有度覆盖
     * @param color         颜色字符串, 如 {@code "#FF5555"}
     * @param tooltips      是否显示工具提示
     * @param renderer      是否渲染物品槽边框
     * @param nameColor     是否修改物品名称颜色
     * @param textureBorder 纹理边框资源路径
     */
    public record ComponentRarityData(
        int level,
        @Nullable String color,
        @Nullable Boolean tooltips,
        @Nullable Boolean renderer,
        @Nullable Boolean nameColor,
        @Nullable String textureBorder
    ) {}

    /**
     * 解析 ItemStack 的 Component 稀有度数据
     * <p>
     * 执行流程:
     * <ol>
     *   <li>检查 {@link ServerConfigManager#isEnableComponentRarityControl()} 开关 (false→null)</li>
     *   <li>检查 ItemStack 非空且非空堆 (null/empty→null)</li>
     *   <li>读取 {@link DataComponents#CUSTOM_DATA} 组件 (无数据→null)</li>
     *   <li>获取 {@code "raritycore"} 子标签 (无标签→null)</li>
     *   <li>解析 Level, 若 ≤0 或无 →null</li>
     *   <li>解析剩余可选字段, 缺失则保留为 null</li>
     * </ol>
     *
     * @param stack 要解析的物品堆
     * @return 解析到的 ComponentRarityData, 无有效数据时返回 {@code null}
     */
    @Nullable
    public static ComponentRarityData resolveComponentRarity(@Nullable ItemStack stack) {
        // 开关检查: 若未启用则立即短路
        if (!ServerConfigManager.isEnableComponentRarityControl()) {
            return null;
        }

        // 空物品检查
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        // 获取 CUSTOM_DATA 组件
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty()) {
            return null;
        }

        // 获取 raritycore 子标签
        CompoundTag rootTag = customData.copyTag();
        if (!rootTag.contains(RARITYCORE_TAG)) {
            return null;
        }

        CompoundTag rarityTag = rootTag.getCompound(RARITYCORE_TAG).orElse(null);
        if (rarityTag == null || rarityTag.isEmpty()) {
            return null;
        }

        // 解析 Level — 必须 >0 才视为有效稀有度覆盖
        int level = rarityTag.getIntOr(TAG_LEVEL, 0);
        if (level <= 0) {
            return null;
        }

        // 解析可选字段: 未提供时保留为 null
        String color = rarityTag.contains(TAG_COLOR)
            ? rarityTag.getStringOr(TAG_COLOR, null)
            : null;

        Boolean tooltips = rarityTag.contains(TAG_TOOLTIPS)
            ? rarityTag.getBooleanOr(TAG_TOOLTIPS, false)
            : null;

        Boolean renderer = rarityTag.contains(TAG_RENDERER)
            ? rarityTag.getBooleanOr(TAG_RENDERER, false)
            : null;

        Boolean nameColor = rarityTag.contains(TAG_NAME_COLOR)
            ? rarityTag.getBooleanOr(TAG_NAME_COLOR, false)
            : null;

        String textureBorder = rarityTag.contains(TAG_TEXTURE_BORDER)
            ? rarityTag.getStringOr(TAG_TEXTURE_BORDER, null)
            : null;

        RarityCore.LOGGER.debug(
            "ComponentRarityResolver: raritycore tag parsed — Level={} Color={} Tooltips={} Renderer={} NameColor={} TextureBorder={}",
            level, color, tooltips, renderer, nameColor, textureBorder
        );

        return new ComponentRarityData(level, color, tooltips, renderer, nameColor, textureBorder);
    }
}
