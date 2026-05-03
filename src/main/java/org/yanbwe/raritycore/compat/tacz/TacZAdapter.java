package org.yanbwe.raritycore.compat.tacz;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nullable;

/**
 * TacZ 模组兼容性适配器
 * TacZ 的枪械/配件/子弹共用物品 ID，通过 NBT 标签区分
 * 编辑时改为写入 NBT 匹配配置文件
 *
 * NBT 路径：
 * - 枪械：tag.GunId
 * - 配件：tag.AttachmentId
 * - 子弹：tag.AmmoId
 */
public class TacZAdapter {

    private static boolean isInitialized = false;

    private static final String GUN_ID_KEY = "GunId";
    private static final String ATTACHMENT_ID_KEY = "AttachmentId";
    private static final String AMMO_ID_KEY = "AmmoId";

    public enum TacZItemType {
        GUN, ATTACHMENT, AMMO, NONE
    }

    public static void init() {
        if (isInitialized) return;

        if (!ModList.get().isLoaded("tacz")) {
            RarityCore.LOGGER.debug("TacZ not detected, skipping initialization");
            return;
        }

        isInitialized = true;
        RarityCore.LOGGER.info("TacZ compatibility adapter initialized");
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    /**
     * 判断物品是否为 TacZ 类型
     */
    public static TacZItemType getTacZItemType(ItemStack itemStack) {
        if (!isInitialized || itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return TacZItemType.NONE;
        }

        CompoundTag tag = itemStack.getTag();
        if (tag == null) return TacZItemType.NONE;

        if (tag.contains(GUN_ID_KEY)) return TacZItemType.GUN;
        if (tag.contains(ATTACHMENT_ID_KEY)) return TacZItemType.ATTACHMENT;
        if (tag.contains(AMMO_ID_KEY)) return TacZItemType.AMMO;
        return TacZItemType.NONE;
    }

    /**
     * 获取 TacZ 物品的区分 NBT 路径
     */
    @Nullable
    public static String getTacZNbtPath(ItemStack itemStack) {
        TacZItemType type = getTacZItemType(itemStack);
        return switch (type) {
            case GUN -> GUN_ID_KEY;
            case ATTACHMENT -> ATTACHMENT_ID_KEY;
            case AMMO -> AMMO_ID_KEY;
            default -> null;
        };
    }

    /**
     * 获取 TacZ 物品的区分 NBT 值
     */
    @Nullable
    public static String getTacZNbtValue(ItemStack itemStack) {
        CompoundTag tag = itemStack.getTag();
        if (tag == null) return null;

        TacZItemType type = getTacZItemType(itemStack);
        return switch (type) {
            case GUN -> tag.getString(GUN_ID_KEY);
            case ATTACHMENT -> tag.getString(ATTACHMENT_ID_KEY);
            case AMMO -> tag.getString(AMMO_ID_KEY);
            default -> null;
        };
    }

    public static void reset() {
        isInitialized = false;
    }
}
