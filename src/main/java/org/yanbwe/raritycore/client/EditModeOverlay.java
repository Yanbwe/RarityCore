package org.yanbwe.raritycore.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.edit.EditModeManager;
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
 *   <li>按住左键拖动面板空白区域 → 移动面板位置（重启复位）</li>
 * </ul>
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = RarityCore.MODID)
public class EditModeOverlay {

    /** 面板是否折叠 */
    private static boolean collapsed = false;

    // ──────────── 面板位置（内存态，不持久化） ────────────

    /** 面板左上角 X（默认左上角 4,4，拖动后改变，重启复位） */
    private static int panelX = 4;
    private static int panelY = 4;

    // ──────────── 拖动状态 ────────────

    /** 拖动判定阈值（px），位移超过该值视为拖动而非点击 */
    private static final int DRAG_THRESHOLD = 4;
    /** 按下时鼠标位置（用于阈值判定） */
    private static int pressMouseX = 0;
    private static int pressMouseY = 0;
    /** 按下时是否位于面板内 */
    private static boolean pressedInPanel = false;
    /** 是否正在拖动 */
    private static boolean dragging = false;

    // ──────────── 布局常量 ────────────

    private static final int PANEL_WIDTH = 175;
    private static final int COLLAPSED_WIDTH = 65;
    private static final int LINE_HEIGHT = 12;
    private static final int PADDING = 4;
    private static final int ARROW_WIDTH = 12;

    // ──────────── 颜色 ────────────

    private static final int BG_COLOR = 0xDD1E1E1E;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HIGHLIGHT_COLOR = 0xFF55FF55;
    private static final int DIM_COLOR = 0xFFAAAAAA;
    private static final int BUTTON_HOVER_COLOR = 0xFF777777;

    // ──────────── 事件：渲染 ────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!EditModeManager.isEditModeEnabled()) return;

        clampToScreen(); // 状态变化（折叠/展开/模式切换）后确保面板仍在屏幕内

        GuiGraphics gui = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;

        if (collapsed) {
            renderCollapsed(gui, font);
        } else if (EditModeManager.isFullMatchMode()) {
            renderFullMatchExpanded(gui, font);
        } else {
            renderNormalExpanded(gui, font);
        }
    }

    // ──────────── 渲染：折叠状态 ────────────

    private static void renderCollapsed(GuiGraphics gui, Font font) {
        int panelHeight = LINE_HEIGHT + PADDING * 2;
        int width = COLLAPSED_WIDTH;

        // 背景
        gui.fill(panelX, panelY, panelX + width, panelY + panelHeight, BG_COLOR);
        // 边框
        drawBorder(gui, panelX, panelY, width, panelHeight);

        // 文本：Edit: N 或 Edit: F
        String modeAbbr = EditModeManager.isFullMatchMode() ? "F" : "N";
        String text = "Edit: " + modeAbbr;
        gui.drawString(font, text, panelX + PADDING, panelY + PADDING, TEXT_COLOR);

        // 提示：点击展开
        String tip = "\u25C6"; // ◆
        gui.drawString(font, tip, panelX + width - PADDING - font.width(tip),
            panelY + PADDING, DIM_COLOR);
    }

    // ──────────── 渲染：展开 — Normal 模式 ────────────

    private static void renderNormalExpanded(GuiGraphics gui, Font font) {
        int lineCount = 3; // 模式、稀有度、折叠提示
        int panelHeight = lineCount * LINE_HEIGHT + PADDING * 2 + (lineCount - 1) * 2;

        // 背景
        gui.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, BG_COLOR);
        drawBorder(gui, panelX, panelY, PANEL_WIDTH, panelHeight);

        int y = panelY + PADDING;

        // 行1：当前模式（可点击切换）
        String modeText = "Mode: [Normal] \u25C0\u25B6";
        gui.drawString(font, modeText, panelX + PADDING, y, HIGHLIGHT_COLOR);
        y += LINE_HEIGHT + 2;

        // 行2：稀有度 +/- 按钮
        int rarity = EditModeManager.getCurrentRarity();
        int rarityColor = getRarityColorForDisplay(rarity);
        String rarityText = "Rarity: " + rarity;
        gui.drawString(font, rarityText, panelX + PADDING, y, rarityColor);

        // [-] 按钮
        int minusX = panelX + PADDING + font.width(rarityText) + 6;
        gui.fill(minusX, y - 1, minusX + ARROW_WIDTH, y + LINE_HEIGHT, rarity > 0 ? BUTTON_HOVER_COLOR : DIM_COLOR);
        gui.drawString(font, "-", minusX + 3, y, rarity > 0 ? TEXT_COLOR : DIM_COLOR);

        // [+] 按钮
        int plusX = minusX + ARROW_WIDTH + 2;
        gui.fill(plusX, y - 1, plusX + ARROW_WIDTH, y + LINE_HEIGHT, BUTTON_HOVER_COLOR);
        gui.drawString(font, "+", plusX + 3, y, TEXT_COLOR);
        y += LINE_HEIGHT + 2;

        // 行3：折叠提示
        gui.drawString(font, "[Ctrl+H] Collapse", panelX + PADDING, y, DIM_COLOR);
    }

    // ──────────── 渲染：展开 — FullMatch 模式 ────────────

    private static void renderFullMatchExpanded(GuiGraphics gui, Font font) {
        int lineCount = 6; // 模式、稀有度、autoReload、ignore、stringContains、折叠提示
        int panelHeight = lineCount * LINE_HEIGHT + PADDING * 2 + (lineCount - 1) * 2;

        gui.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, BG_COLOR);
        drawBorder(gui, panelX, panelY, PANEL_WIDTH, panelHeight);

        int y = panelY + PADDING;

        // 行1：模式（可切换）
        String modeText = "Mode: [FullMatch] \u25C0\u25B6";
        gui.drawString(font, modeText, panelX + PADDING, y, HIGHLIGHT_COLOR);
        y += LINE_HEIGHT + 2;

        // 行2：稀有度
        int rarity = EditModeManager.getCurrentRarity();
        int rarityColor = getRarityColorForDisplay(rarity);
        String rarityText = "Rarity: " + rarity;
        gui.drawString(font, rarityText, panelX + PADDING, y, rarityColor);

        int minusX = panelX + PADDING + font.width(rarityText) + 6;
        gui.fill(minusX, y - 1, minusX + ARROW_WIDTH, y + LINE_HEIGHT, rarity > 0 ? BUTTON_HOVER_COLOR : DIM_COLOR);
        gui.drawString(font, "-", minusX + 3, y, rarity > 0 ? TEXT_COLOR : DIM_COLOR);

        int plusX = minusX + ARROW_WIDTH + 2;
        gui.fill(plusX, y - 1, plusX + ARROW_WIDTH, y + LINE_HEIGHT, BUTTON_HOVER_COLOR);
        gui.drawString(font, "+", plusX + 3, y, TEXT_COLOR);
        y += LINE_HEIGHT + 2;

        // 行3：autoReload（可切换）
        boolean autoReload = EditModeManager.isAutoReload();
        String arText = "AutoReload: " + (autoReload ? "ON" : "OFF");
        gui.drawString(font, arText, panelX + PADDING, y, autoReload ? HIGHLIGHT_COLOR : DIM_COLOR);
        y += LINE_HEIGHT + 2;

        // 行4：ignore components（显示值）
        String ignore = EditModeManager.getIgnoreComponents();
        String ignoreText = "Ignore: " + (ignore.isEmpty() ? "(none)" : truncate(ignore, 25));
        gui.drawString(font, ignoreText, panelX + PADDING, y, DIM_COLOR);
        y += LINE_HEIGHT + 2;

        // 行5：stringContains（可切换）
        boolean strContains = EditModeManager.isStringContains();
        String scText = "StrContains: " + (strContains ? "ON" : "OFF");
        gui.drawString(font, scText, panelX + PADDING, y, strContains ? HIGHLIGHT_COLOR : DIM_COLOR);
        y += LINE_HEIGHT + 2;

        // 行6：折叠提示
        gui.drawString(font, "[Ctrl+H] Collapse", panelX + PADDING, y, DIM_COLOR);
    }

    // ──────────── 鼠标交互：按下/拖动/释放 ────────────

    /**
     * 鼠标按下：左键在面板内（折叠或展开）时记录按下点与起点位置，
     * 并拦截事件防止穿透下层 GUI。按钮逻辑延迟到释放时执行，
     * 以区分"点击"与"拖动"。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    @SuppressWarnings("null")
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!EditModeManager.isEditModeEnabled()) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        boolean inPanel = collapsed ? isInCollapsedPanel(mouseX, mouseY) : isInPanel(mouseX, mouseY);
        if (!inPanel) {
            // 面板外按下：复位拖动状态（防御：避免 GUI 关闭时释放事件未送达导致残留）
            pressedInPanel = false;
            dragging = false;
            return;
        }

        pressMouseX = (int) mouseX;
        pressMouseY = (int) mouseY;
        pressedInPanel = true;
        dragging = false;
        event.setCanceled(true);
    }

    /**
     * 鼠标拖动：位移超过阈值后进入拖动状态，
     * 用 dragX/dragY 增量更新面板位置并钳制在屏幕内。
     * 拖动开始后鼠标移出面板仍继续拖动（已抓取）。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!EditModeManager.isEditModeEnabled()) return;
        if (event.getMouseButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (!pressedInPanel) return; // 未在面板内按下，不响应拖动

        if (!dragging) {
            // 尚未进入拖动：位移未超阈值前不响应
            int dx = (int) Math.abs(event.getMouseX() - pressMouseX);
            int dy = (int) Math.abs(event.getMouseY() - pressMouseY);
            if (dx < DRAG_THRESHOLD && dy < DRAG_THRESHOLD) return;
            dragging = true;
        }

        panelX += (int) event.getDragX();
        panelY += (int) event.getDragY();
        clampToScreen();
        event.setCanceled(true);
    }

    /**
     * 鼠标释放：若未发生拖动则执行按钮点击
     * （模式切换、+/− 稀有度、参数切换、折叠展开）；
     * 若已拖动则仅结束拖动。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    @SuppressWarnings("null")
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!EditModeManager.isEditModeEnabled()) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (!pressedInPanel) return; // 按下不在面板内，不处理

        pressedInPanel = false;

        if (dragging) {
            dragging = false;
            event.setCanceled(true);
            return;
        }

        // 未拖动 → 视为点击，执行原按钮逻辑
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        if (collapsed) {
            // 点击折叠面板 → 展开
            if (isInCollapsedPanel(mouseX, mouseY)) {
                collapsed = false;
                event.setCanceled(true);
            }
            return;
        }

        if (!isInPanel(mouseX, mouseY)) return;
        event.setCanceled(true);

        // 计算被点击的行（与原 onMouseClick 一致，仅 panelX/panelY 可变化）
        int relY = (int) (mouseY - panelY - PADDING);
        int lineIndex = relY / (LINE_HEIGHT + 2);
        int relX = (int) (mouseX - panelX - PADDING);

        if (EditModeManager.isFullMatchMode()) {
            handleFullMatchClick(lineIndex, relX);
        } else {
            handleNormalClick(lineIndex, relX);
        }
    }

    /**
     * 处理 Normal 模式下的点击。
     * 行布局：
     *   0: 模式切换
     *   1: 稀有度 [-] [+]
     *   2: 折叠提示（仅显示）
     */
    private static void handleNormalClick(int lineIndex, int relX) {
        Font font = Minecraft.getInstance().font;
        switch (lineIndex) {
            case 0:
                // 切换模式
                EditModeManager.setCurrentMode(
                    EditModeManager.EditMode.FULLMATCH);
                break;
            case 1: {
                // 稀有度调整
                String rarityText = "Rarity: " + EditModeManager.getCurrentRarity();
                int minusX = font.width(rarityText) + 6;
                int plusX = minusX + ARROW_WIDTH + 2;

                if (relX >= minusX && relX < minusX + ARROW_WIDTH) {
                    // [-] 按钮
                    if (EditModeManager.getCurrentRarity() > 0) {
                        EditModeManager.setRarity(EditModeManager.getCurrentRarity() - 1);
                    }
                } else if (relX >= plusX && relX < plusX + ARROW_WIDTH) {
                    // [+] 按钮
                    EditModeManager.setRarity(EditModeManager.getCurrentRarity() + 1);
                }
                break;
            }
            default:
                break; // 其他行无交互
        }
    }

    /**
     * 处理 FullMatch 模式下的点击。
     * 行布局：
     *   0: 模式切换
     *   1: 稀有度 [-] [+]
     *   2: autoReload 切换
     *   3: ignore（仅显示）
     *   4: stringContains 切换
     *   5: 折叠提示（仅显示）
     */
    private static void handleFullMatchClick(int lineIndex, int relX) {
        Font font = Minecraft.getInstance().font;
        switch (lineIndex) {
            case 0:
                // 切换模式
                EditModeManager.setCurrentMode(EditModeManager.EditMode.NORMAL);
                break;
            case 1: {
                // 稀有度调整
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
                // 切换 autoReload
                EditModeManager.setAutoReload(!EditModeManager.isAutoReload());
                break;
            case 4:
                // 切换 stringContains
                EditModeManager.setStringContains(!EditModeManager.isStringContains());
                break;
            default:
                break;
        }
    }

    // ──────────── 键盘处理：Ctrl+H 折叠 ────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKeyPress(ScreenEvent.KeyPressed.Pre event) {
        if (!EditModeManager.isEditModeEnabled()) return;

        if (event.getKeyCode() == GLFW.GLFW_KEY_H && isCtrlPressed()) {
            collapsed = !collapsed;
            event.setCanceled(true);
        }
    }

    // ──────────── 辅助方法 ────────────

    /**
     * 获取当前面板宽度（折叠/展开不同）。
     */
    private static int getPanelWidth() {
        return collapsed ? COLLAPSED_WIDTH : PANEL_WIDTH;
    }

    /**
     * 获取当前面板高度（按折叠/模式行数计算）。
     */
    private static int getPanelHeight() {
        int lineCount = collapsed ? 1 : (EditModeManager.isFullMatchMode() ? 6 : 3);
        return lineCount * LINE_HEIGHT + PADDING * 2 + (lineCount - 1) * 2;
    }

    /**
     * 判断鼠标是否在展开面板范围内。
     */
    private static boolean isInPanel(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + getPanelWidth() &&
               mouseY >= panelY && mouseY <= panelY + getPanelHeight();
    }

    /**
     * 将面板位置钳制在屏幕内（按当前折叠/展开尺寸）。
     */
    private static void clampToScreen() {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        panelX = Math.max(0, Math.min(panelX, screenW - getPanelWidth()));
        panelY = Math.max(0, Math.min(panelY, screenH - getPanelHeight()));
    }

    /**
     * 绘制面板边框。
     */
    private static void drawBorder(GuiGraphics gui, int x, int y, int width, int height) {
        gui.fill(x, y, x + width, y + 1, BORDER_COLOR);           // 上
        gui.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR); // 下
        gui.fill(x, y, x + 1, y + height, BORDER_COLOR);           // 左
        gui.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR); // 右
    }

    /**
     * 检查鼠标是否在折叠面板范围内。
     */
    private static boolean isInCollapsedPanel(double mouseX, double mouseY) {
        int panelHeight = LINE_HEIGHT + PADDING * 2;
        return mouseX >= panelX && mouseX <= panelX + COLLAPSED_WIDTH &&
               mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    /**
     * 截断字符串到指定长度。
     */
    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    /**
     * 获取编辑模式叠加层中稀有度显示所用的 ARGB 颜色。
     *
     * <p>优先从 {@link RarityStyleConfigManager} 获取该等级的自定义颜色，
     * 如果配置中未定义该等级，则回退到 {@link RarityColorUtil} 的默认色。</p>
     *
     * @param rarity 当前稀有度等级（可能 &gt;7）
     * @return ARGB 颜色值
     */
    private static int getRarityColorForDisplay(int rarity) {
        if (rarity < 1) {
            return RarityColorUtil.getRarityArgbColor(1);
        }
        // 1-7: 使用 RarityColorUtil 的标准颜色
        if (rarity <= 7) {
            return RarityColorUtil.getRarityArgbColor(rarity);
        }
        // >7: 优先使用 RarityStyleConfigManager 的自定义颜色
        RarityStyleConfigManager styleConfig = RarityStyleConfigManager.getInstance();
        if (!styleConfig.getConfiguredLevels().isEmpty()) {
            return styleConfig.resolveColor(rarity);
        }
        // 无配置时，使用 7 的颜色作为合理回退
        return RarityColorUtil.getRarityArgbColor(7);
    }

    /**
     * 检查 Ctrl 键是否按下。
     */
    private static boolean isCtrlPressed() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) ||
               InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
