package org.yanbwe.raritycore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.edit.EditModeManager;
import org.yanbwe.raritycore.util.InputHelper;
import org.yanbwe.raritycore.util.RarityColorUtil;

/**
 * 编辑模式 GUI 覆盖层。
 *
 * <p>编辑模式激活后，在所有 GUI 左上角叠加浮动面板，
 * 显示当前编辑模式（Normal/FullMatch）、当前稀有度等级及各项参数。
 * 面板上的按钮可交互（模式切换、稀有度调整），
 * 鼠标事件不会穿透到下层 GUI。</p>
 *
 * <h3>交互</h3>
 * <ul>
 *   <li>点击模式名 → 切换 Normal / FullMatch</li>
 *   <li>点击 [+]/[-] → 调整稀有度</li>
 *   <li>点击参数名 → 切换布尔参数（autoReload / stringContains）</li>
 *   <li>Ctrl+H → 折叠/展开面板</li>
 * </ul>
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = RarityCore.MODID)
public class EditModeOverlay {

    private static boolean collapsed = false;

    private static final int PANEL_X = 4;
    private static final int PANEL_Y = 4;
    private static final int PANEL_WIDTH = 175;
    private static final int COLLAPSED_WIDTH = 65;
    private static final int LINE_HEIGHT = 12;
    private static final int PADDING = 4;
    private static final int ARROW_WIDTH = 12;

    private static final int BG_COLOR = 0xDD1E1E1E;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HIGHLIGHT_COLOR = 0xFF55FF55;
    private static final int DIM_COLOR = 0xFFAAAAAA;
    private static final int BUTTON_HOVER_COLOR = 0xFF777777;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!EditModeManager.isEditModeEnabled()) return;

        GuiGraphicsExtractor gui = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;

        if (collapsed) {
            renderCollapsed(gui, font);
        } else if (EditModeManager.isFullMatchMode()) {
            renderFullMatchExpanded(gui, font);
        } else {
            renderNormalExpanded(gui, font);
        }
    }

    private static void renderCollapsed(GuiGraphicsExtractor gui, Font font) {
        int panelHeight = LINE_HEIGHT + PADDING * 2;
        int width = COLLAPSED_WIDTH;

        gui.fill(PANEL_X, PANEL_Y, PANEL_X + width, PANEL_Y + panelHeight, BG_COLOR);
        drawBorder(gui, PANEL_X, PANEL_Y, width, panelHeight);

        String modeAbbr = EditModeManager.isFullMatchMode() ? "F" : "N";
        String text = "Edit: " + modeAbbr;
        gui.text(font, text, PANEL_X + PADDING, PANEL_Y + PADDING, TEXT_COLOR);

        String tip = "\u25C6";
        gui.text(font, tip, PANEL_X + width - PADDING - font.width(tip),
            PANEL_Y + PADDING, DIM_COLOR);
    }

    private static void renderNormalExpanded(GuiGraphicsExtractor gui, Font font) {
        int lineCount = 3;
        int panelHeight = lineCount * LINE_HEIGHT + PADDING * 2 + (lineCount - 1) * 2;

        gui.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + panelHeight, BG_COLOR);
        drawBorder(gui, PANEL_X, PANEL_Y, PANEL_WIDTH, panelHeight);

        int y = PANEL_Y + PADDING;

        String modeText = "Mode: [Normal] \u25C0\u25B6";
        gui.text(font, modeText, PANEL_X + PADDING, y, HIGHLIGHT_COLOR);
        y += LINE_HEIGHT + 2;

        int rarity = EditModeManager.getCurrentRarity();
        int rarityColor = getRarityColorForDisplay(rarity);
        String rarityText = "Rarity: " + rarity;
        gui.text(font, rarityText, PANEL_X + PADDING, y, rarityColor);

        int minusX = PANEL_X + PADDING + font.width(rarityText) + 6;
        gui.fill(minusX, y - 1, minusX + ARROW_WIDTH, y + LINE_HEIGHT, rarity > 0 ? BUTTON_HOVER_COLOR : DIM_COLOR);
        gui.text(font, "-", minusX + 3, y, rarity > 0 ? TEXT_COLOR : DIM_COLOR);

        int plusX = minusX + ARROW_WIDTH + 2;
        gui.fill(plusX, y - 1, plusX + ARROW_WIDTH, y + LINE_HEIGHT, BUTTON_HOVER_COLOR);
        gui.text(font, "+", plusX + 3, y, TEXT_COLOR);
        y += LINE_HEIGHT + 2;

        gui.text(font, "[Ctrl+H] Collapse", PANEL_X + PADDING, y, DIM_COLOR);
    }

    private static void renderFullMatchExpanded(GuiGraphicsExtractor gui, Font font) {
        int lineCount = 6;
        int panelHeight = lineCount * LINE_HEIGHT + PADDING * 2 + (lineCount - 1) * 2;

        gui.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + panelHeight, BG_COLOR);
        drawBorder(gui, PANEL_X, PANEL_Y, PANEL_WIDTH, panelHeight);

        int y = PANEL_Y + PADDING;

        String modeText = "Mode: [FullMatch] \u25C0\u25B6";
        gui.text(font, modeText, PANEL_X + PADDING, y, HIGHLIGHT_COLOR);
        y += LINE_HEIGHT + 2;

        int rarity = EditModeManager.getCurrentRarity();
        int rarityColor = getRarityColorForDisplay(rarity);
        String rarityText = "Rarity: " + rarity;
        gui.text(font, rarityText, PANEL_X + PADDING, y, rarityColor);

        int minusX = PANEL_X + PADDING + font.width(rarityText) + 6;
        gui.fill(minusX, y - 1, minusX + ARROW_WIDTH, y + LINE_HEIGHT, rarity > 0 ? BUTTON_HOVER_COLOR : DIM_COLOR);
        gui.text(font, "-", minusX + 3, y, rarity > 0 ? TEXT_COLOR : DIM_COLOR);

        int plusX = minusX + ARROW_WIDTH + 2;
        gui.fill(plusX, y - 1, plusX + ARROW_WIDTH, y + LINE_HEIGHT, BUTTON_HOVER_COLOR);
        gui.text(font, "+", plusX + 3, y, TEXT_COLOR);
        y += LINE_HEIGHT + 2;

        boolean autoReload = EditModeManager.isAutoReload();
        String arText = "AutoReload: " + (autoReload ? "ON" : "OFF");
        gui.text(font, arText, PANEL_X + PADDING, y, autoReload ? HIGHLIGHT_COLOR : DIM_COLOR);
        y += LINE_HEIGHT + 2;

        String ignore = EditModeManager.getIgnoreComponents();
        String ignoreText = "Ignore: " + (ignore.isEmpty() ? "(none)" : truncate(ignore, 25));
        gui.text(font, ignoreText, PANEL_X + PADDING, y, DIM_COLOR);
        y += LINE_HEIGHT + 2;

        boolean strContains = EditModeManager.isStringContains();
        String scText = "StrContains: " + (strContains ? "ON" : "OFF");
        gui.text(font, scText, PANEL_X + PADDING, y, strContains ? HIGHLIGHT_COLOR : DIM_COLOR);
        y += LINE_HEIGHT + 2;

        gui.text(font, "[Ctrl+H] Collapse", PANEL_X + PADDING, y, DIM_COLOR);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    @SuppressWarnings("null")
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!EditModeManager.isEditModeEnabled()) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        if (collapsed) {
            if (isInCollapsedPanel(mouseX, mouseY)) {
                collapsed = false;
                event.setCanceled(true);
            }
            return;
        }

        int lineCount = EditModeManager.isFullMatchMode() ? 6 : 3;
        int panelHeight = lineCount * LINE_HEIGHT + PADDING * 2 + (lineCount - 1) * 2;

        if (mouseX < PANEL_X || mouseX > PANEL_X + PANEL_WIDTH ||
            mouseY < PANEL_Y || mouseY > PANEL_Y + panelHeight) {
            return;
        }

        event.setCanceled(true);

        int relY = (int) (mouseY - PANEL_Y - PADDING);
        int lineIndex = relY / (LINE_HEIGHT + 2);
        int relX = (int) (mouseX - PANEL_X - PADDING);

        if (EditModeManager.isFullMatchMode()) {
            handleFullMatchClick(lineIndex, relX);
        } else {
            handleNormalClick(lineIndex, relX);
        }
    }

    private static void handleNormalClick(int lineIndex, int relX) {
        Font font = Minecraft.getInstance().font;
        switch (lineIndex) {
            case 0:
                EditModeManager.setCurrentMode(EditModeManager.EditMode.FULLMATCH);
                break;
            case 1: {
                String rarityText = "Rarity: " + EditModeManager.getCurrentRarity();
                int minusX = font.width(rarityText) + 6;
                int plusX = minusX + ARROW_WIDTH + 2;

                if (relX >= minusX && relX < minusX + ARROW_WIDTH) {
                    if (EditModeManager.getCurrentRarity() > 0) {
                        EditModeManager.setRarity(EditModeManager.getCurrentRarity() - 1);
                    }
                } else if (relX >= plusX && relX < plusX + ARROW_WIDTH) {
                    EditModeManager.setRarity(EditModeManager.getCurrentRarity() + 1);
                }
                break;
            }
            default:
                break;
        }
    }

    private static void handleFullMatchClick(int lineIndex, int relX) {
        Font font = Minecraft.getInstance().font;
        switch (lineIndex) {
            case 0:
                EditModeManager.setCurrentMode(EditModeManager.EditMode.NORMAL);
                break;
            case 1: {
                String rarityText = "Rarity: " + EditModeManager.getCurrentRarity();
                int minusX = font.width(rarityText) + 6;
                int plusX = minusX + ARROW_WIDTH + 2;

                if (relX >= minusX && relX < minusX + ARROW_WIDTH) {
                    if (EditModeManager.getCurrentRarity() > 0) {
                        EditModeManager.setRarity(EditModeManager.getCurrentRarity() - 1);
                    }
                } else if (relX >= plusX && relX < plusX + ARROW_WIDTH) {
                    EditModeManager.setRarity(EditModeManager.getCurrentRarity() + 1);
                }
                break;
            }
            case 2:
                EditModeManager.setAutoReload(!EditModeManager.isAutoReload());
                break;
            case 4:
                EditModeManager.setStringContains(!EditModeManager.isStringContains());
                break;
            default:
                break;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKeyPress(ScreenEvent.KeyPressed.Pre event) {
        if (!EditModeManager.isEditModeEnabled()) return;

        if (event.getKeyCode() == GLFW.GLFW_KEY_H && InputHelper.isCtrlPressed()) {
            collapsed = !collapsed;
            event.setCanceled(true);
        }
    }

    private static void drawBorder(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
        gui.fill(x, y, x + width, y + 1, BORDER_COLOR);
        gui.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR);
        gui.fill(x, y, x + 1, y + height, BORDER_COLOR);
        gui.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR);
    }

    private static boolean isInCollapsedPanel(double mouseX, double mouseY) {
        int panelHeight = LINE_HEIGHT + PADDING * 2;
        return mouseX >= PANEL_X && mouseX <= PANEL_X + COLLAPSED_WIDTH &&
               mouseY >= PANEL_Y && mouseY <= PANEL_Y + panelHeight;
    }

    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    private static int getRarityColorForDisplay(int rarity) {
        if (rarity < 1) {
            return RarityColorUtil.getRarityArgbColor(1);
        }
        if (rarity <= 7) {
            return RarityColorUtil.getRarityArgbColor(rarity);
        }
        return RarityColorUtil.getRarityArgbColor(7);
    }

}
