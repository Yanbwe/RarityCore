package org.yanbwe.raritycore.nbtmatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.config.ServerConfigManager;

import javax.annotation.Nullable;

/**
 * NBT 稀有度控制处理器
 * 检测物品 NBT 中的 raritycore 标签，提供最高优先级的稀有度覆盖
 * 由 server.json 中的 enableNbtRarityControl 开关控制（默认关闭）
 */
public class NbtRarityControlHandler {

    private static final String DATA_KEY = "raritycore";
    private static final String LEVEL_KEY = "Level";
    private static final String COLOR_KEY = "Color";
    private static final String TOOLTIPS_KEY = "Tooltips";
    private static final String RENDERER_KEY = "Renderer";
    private static final String NAME_COLOR_KEY = "NameColor";
    private static final String TEXTURE_BORDER_KEY = "TextureBorder";

    /**
     * NBT 稀有度控制数据
     */
    public static class NbtRarityData {
        public final int level;
        @Nullable public final String color;
        public final boolean tooltips;
        public final boolean renderer;
        public final boolean nameColor;
        @Nullable public final String textureBorder;

        NbtRarityData(int level, @Nullable String color, boolean tooltips,
                      boolean renderer, boolean nameColor, @Nullable String textureBorder) {
            this.level = level;
            this.color = color;
            this.tooltips = tooltips;
            this.renderer = renderer;
            this.nameColor = nameColor;
            this.textureBorder = textureBorder;
        }

        public static final NbtRarityData EMPTY = new NbtRarityData(0, null, true, true, true, null);
    }

    /**
     * 获取物品的 NBT 稀有度等级（最高优先级）
     * @param itemStack 物品栈
     * @return 稀有度等级，>0 表示有效覆盖，0 表示无 NBT 控制数据
     */
    public static int getNbtControlRarity(@Nullable ItemStack itemStack) {
        if (!ServerConfigManager.isEnableNbtRarityControl()) {
            return 0;
        }
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return 0;
        }

        CompoundTag tag = itemStack.getTag();
        if (tag == null || !tag.contains(DATA_KEY, CompoundTag.TAG_COMPOUND)) {
            return 0;
        }

        CompoundTag data = tag.getCompound(DATA_KEY);
        if (!data.contains(LEVEL_KEY, CompoundTag.TAG_INT)) {
            return 0;
        }

        return data.getInt(LEVEL_KEY);
    }

    /**
     * 获取完整的 NBT 稀有度控制数据
     */
    public static NbtRarityData getNbtControlData(@Nullable ItemStack itemStack) {
        if (!ServerConfigManager.isEnableNbtRarityControl()) {
            return NbtRarityData.EMPTY;
        }
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return NbtRarityData.EMPTY;
        }

        CompoundTag tag = itemStack.getTag();
        if (tag == null || !tag.contains(DATA_KEY, CompoundTag.TAG_COMPOUND)) {
            return NbtRarityData.EMPTY;
        }

        CompoundTag data = tag.getCompound(DATA_KEY);
        int level = data.contains(LEVEL_KEY, CompoundTag.TAG_INT) ? data.getInt(LEVEL_KEY) : 0;
        String color = data.contains(COLOR_KEY, CompoundTag.TAG_STRING) ? data.getString(COLOR_KEY) : null;
        boolean tooltips = !data.contains(TOOLTIPS_KEY) || data.getBoolean(TOOLTIPS_KEY);
        boolean renderer = !data.contains(RENDERER_KEY) || data.getBoolean(RENDERER_KEY);
        boolean nameColor = !data.contains(NAME_COLOR_KEY) || data.getBoolean(NAME_COLOR_KEY);
        String textureBorder = data.contains(TEXTURE_BORDER_KEY, CompoundTag.TAG_STRING)
            ? data.getString(TEXTURE_BORDER_KEY) : null;

        return new NbtRarityData(level, color, tooltips, renderer, nameColor, textureBorder);
    }
}
