package org.yanbwe.raritycore.compat.colortooltips;

import net.neoforged.fml.ModList;
import org.yanbwe.raritycore.RarityCore;

/**
 * colortooltips 兼容性适配器。
 * <p>
 * 当 colortooltips 模组已加载时，由该模组接管物品稀有度的工具提示渲染。
 * 本适配器仅承担检测职责，检测结果由 {@code RarityTooltipHandler}
 * 在工具提示事件入口处读取并直接返回，避免两条渲染管线互相叠加。
 * </p>
 */
public class ColorTooltipsCompat {

    /** 目标模组 ID */
    public static final String COLOR_TOOLTIPS_MOD_ID = "colortooltips";

    /** 缓存的加载状态：null 表示尚未执行检测 */
    private static volatile Boolean loaded = null;

    private ColorTooltipsCompat() {}

    /**
     * 执行 colortooltips 模组加载检测并缓存结果。
     * <p>本方法线程安全，可由 {@code CompatibilityManager}
     * 在通用初始化阶段显式调用；未显式调用时，
     * {@link #isLoaded()} 在首次访问时也会触发懒加载。</p>
     */
    public static synchronized void init() {
        if (loaded != null) {
            return;
        }
        try {
            loaded = ModList.get().isLoaded(COLOR_TOOLTIPS_MOD_ID);
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to detect colortooltips mod: {}", e.getMessage());
            loaded = false;
            return;
        }
        if (loaded) {
            RarityCore.LOGGER.info("colortooltips detected, RarityCore tooltip insertion will be disabled to avoid duplicate rendering");
        } else {
            RarityCore.LOGGER.debug("colortooltips not found, tooltip insertion kept enabled");
        }
    }

    /**
     * 检查 colortooltips 模组是否已加载。
     * <p>首次调用时执行检测，结果缓存在静态字段中，
     * 避免在每帧 (60fps) 工具提示事件中重复访问 ModList。</p>
     *
     * @return colortooltips 模组已加载返回 true
     */
    public static boolean isLoaded() {
        if (loaded == null) {
            init();
        }
        return loaded;
    }

    /**
     * 重置检测缓存（用于热重载或测试）。
     */
    public static void reset() {
        loaded = null;
    }
}
