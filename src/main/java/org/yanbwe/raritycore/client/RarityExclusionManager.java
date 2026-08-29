package org.yanbwe.raritycore.client;

public class RarityExclusionManager {
    
    private static final ThreadLocal<Boolean> RENDERING_TOOLTIP_ITEM = 
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 抑制标志：在自定义 GUI 框架（如 FTB Library ItemIcon）接管边框渲染期间，
     * 阻止 GuiGraphicsMixin 在 renderItemDecorations 内重复绘制边框
     */
    private static final ThreadLocal<Boolean> SUPPRESS_BORDER_RENDER = 
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void setRenderingTooltipItem(boolean rendering) {
        RENDERING_TOOLTIP_ITEM.set(rendering);
    }

    public static boolean isRenderingTooltipItem() {
        return Boolean.TRUE.equals(RENDERING_TOOLTIP_ITEM.get());
    }

    public static void clear() {
        RENDERING_TOOLTIP_ITEM.remove();
        SUPPRESS_BORDER_RENDER.remove();
    }

    public static void setSuppressBorderRender(boolean suppress) {
        SUPPRESS_BORDER_RENDER.set(suppress);
    }

    public static boolean isSuppressBorderRender() {
        return Boolean.TRUE.equals(SUPPRESS_BORDER_RENDER.get());
    }
}