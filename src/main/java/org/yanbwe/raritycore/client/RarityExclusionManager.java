package org.yanbwe.raritycore.client;

public class RarityExclusionManager {
    private static final ThreadLocal<Boolean> RENDERING_TOOLTIP_ITEM = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void setRenderingTooltipItem(boolean rendering) {
        RENDERING_TOOLTIP_ITEM.set(rendering);
    }

    public static boolean isRenderingTooltipItem() {
        return Boolean.TRUE.equals(RENDERING_TOOLTIP_ITEM.get());
    }

    public static void clear() {
        RENDERING_TOOLTIP_ITEM.remove();
    }
}