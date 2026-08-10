package org.yanbwe.raritycore.edit;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.mixin.AbstractContainerScreenAccessor;

/**
 * 客户端编辑模式事件处理器
 * 处理键盘输入、鼠标点击和 GUI 覆盖层渲染
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = RarityCore.MODID)
public class EditModeEventHandler {

    private static boolean panelCollapsed = false;

    // ---- 面板位置（内存态，不持久化）----
    private static int panelX = 4;
    private static int panelY = 4;

    // ---- 拖动状态 ----
    private static final int DRAG_THRESHOLD = 4; // px
    private static int pressMouseX = 0;
    private static int pressMouseY = 0;
    private static boolean pressedInPanel = false;
    private static boolean dragging = false;

    // 编辑点击防抖：避免快速连点产生大量网络请求
    private static long lastEditClickTime = 0;
    private static final long EDIT_CLICK_COOLDOWN_MS = 200;

    // ---- Key Input ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(ScreenEvent.KeyPressed.Pre event) {
        if (isCtrlPressed()) {
            if (isNumberKey(event.getKeyCode())) {
                handleNumberKeyPress(event.getKeyCode());
                event.setCanceled(true);
            } else if (event.getKeyCode() == GLFW.GLFW_KEY_0) {
                handleZeroKeyPress();
                event.setCanceled(true);
            } else if (event.getKeyCode() == GLFW.GLFW_KEY_H) {
                // Ctrl+H 切换面板折叠
                panelCollapsed = !panelCollapsed;
                event.setCanceled(true);
            }
        }
    }

    // ---- Mouse Interaction: Press / Drag / Release ----

    /**
     * 鼠标按下：左键在面板内时记录按下点并拦截事件（按钮逻辑延迟到释放时执行，
     * 以区分"点击"与"拖动"）；面板外则保留原物品槽编辑逻辑。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings("null")
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!EditModeManager.isEditModeEnabled()) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        Screen screen = event.getScreen();
        int mouseX = (int) event.getMouseX();
        int mouseY = (int) event.getMouseY();

        // 左键按下在面板内 → 记录起点并拦截，按钮逻辑延迟到释放时执行
        if (isInPanel(mouseX, mouseY)) {
            pressMouseX = mouseX;
            pressMouseY = mouseY;
            pressedInPanel = true;
            dragging = false;
            event.setCanceled(true);
            return;
        }

        // 否则处理物品槽点击（原逻辑原样保留）
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

        Slot clickedSlot = getSlotUnderMouse(containerScreen, event.getMouseX(), event.getMouseY());
        if (clickedSlot == null || !clickedSlot.hasItem()) return;

        ItemStack itemStack = clickedSlot.getItem();
        if (itemStack.isEmpty()) return;

        // 防抖：200ms 内的重复点击忽略，避免快速连点产生大量网络请求
        long now = System.currentTimeMillis();
        if (now - lastEditClickTime < EDIT_CLICK_COOLDOWN_MS) return;
        lastEditClickTime = now;

        // 判断是否单人游戏：单人游戏中编辑操作直接在本机执行，多人则通过数据包发送到服务端
        boolean isSingleplayer = Minecraft.getInstance().getSingleplayerServer() != null;
        if (EditModeManager.modifyItemRarity(itemStack, isSingleplayer)) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(
                    Component.translatable("rarity.core.edit_mode_applied", EditModeManager.getCurrentRarity()),
                    true);
            }
        }
        event.setCanceled(true);
    }

    /**
     * 鼠标拖动：位移超过阈值后进入拖动状态，
     * 用 dragX/dragY 增量更新面板位置并钳制在屏幕内。
     * 拖动开始后鼠标移出面板仍继续拖动（已抓取）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
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
     * 鼠标释放：若未发生拖动则执行面板按钮点击；若已拖动则仅结束拖动。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings("null")
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!EditModeManager.isEditModeEnabled()) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (!pressedInPanel) return;

        pressedInPanel = false;
        int mouseX = (int) event.getMouseX();
        int mouseY = (int) event.getMouseY();

        if (dragging) {
            dragging = false;
            event.setCanceled(true);
            return;
        }

        // 未拖动 → 视为点击，执行原面板按钮逻辑（相对坐标）
        if (!isInPanel(mouseX, mouseY)) return;
        event.setCanceled(true);
        handlePanelClick(event.getScreen(), mouseX, mouseY);
    }

    // ---- GUI Overlay Render ----

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!EditModeManager.isEditModeEnabled()) return;

        Screen screen = event.getScreen();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;

        int x = panelX, y = panelY;
        int bgColor = 0xCC000000;
        int textColor = 0xFFFFFFFF;
        int highlightColor = 0xFF00FF00;
        int btnColor = 0xFFFFFF55; // 按钮颜色

        if (panelCollapsed) {
            // 折叠状态：小按钮
            graphics.fill(x, y, x + 20, y + 14, bgColor);
            graphics.drawString(font, "E", x + 6, y + 3, highlightColor);
            return;
        }

        // 展开状态：完整面板
        int panelWidth = 160;
        boolean isFullMatch = EditModeManager.getCurrentMode() == EditModeManager.EditMode.FULLMATCH;
        int panelHeight = isFullMatch ? 106 : 66;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, bgColor);

        int line = y + 2;

        // 模式切换按钮
        String modeStr = isFullMatch ? "FullMatch" : "Normal";
        int modeBtnW = font.width("[Mode: " + modeStr + "]");
        graphics.drawString(font, "[Mode: " + modeStr + "]", x + 4, line, btnColor);
        line += 12;

        // 稀有度 +/- 按钮
        graphics.drawString(font, "[ - ]", x + 4, line, 0xFFFF5555);
        String rarityText = "Rarity: " + EditModeManager.getCurrentRarity();
        graphics.drawString(font, rarityText, x + 28, line, highlightColor);
        graphics.drawString(font, "[ + ]", x + 120, line, 0xFF55FF55);
        line += 12;

        // FullMatch 额外参数
        if (EditModeManager.getCurrentMode() == EditModeManager.EditMode.FULLMATCH) {
            String autoReloadStr = "AutoReload: " + EditModeManager.isAutoReload();
            graphics.drawString(font, autoReloadStr, x + 4, line, textColor);
            line += 12;

            String strContainsStr = "StrContains: " + EditModeManager.isStringContains();
            graphics.drawString(font, strContainsStr, x + 4, line, textColor);
            line += 12;

            String ignoreStr = "Ignore: " + (EditModeManager.getIgnoreTags().isEmpty() ? "(none)" : EditModeManager.getIgnoreTags());
            graphics.drawString(font, truncate(font, ignoreStr, panelWidth - 8), x + 4, line, 0xFFAAAAAA);
            line += 12;
        }

        // 折叠提示
        graphics.drawString(font, "[Ctrl+H fold]", x + 4, y + panelHeight - 10, 0xFF888888);
    }

    // ---- Panel Click Handling ----

    private static boolean isInPanel(int mouseX, int mouseY) {
        if (panelCollapsed) return mouseX >= panelX && mouseX < panelX + 20 && mouseY >= panelY && mouseY < panelY + 14;
        int panelHeight = EditModeManager.getCurrentMode() == EditModeManager.EditMode.FULLMATCH ? 106 : 66;
        return mouseX >= panelX && mouseX < panelX + 160 && mouseY >= panelY && mouseY < panelY + panelHeight;
    }

    private static void handlePanelClick(Screen screen, int mouseX, int mouseY) {
        int rx = mouseX - panelX;
        int ry = mouseY - panelY;
        // 模式切换按钮区域: 相对 y=0~12, x=0~120
        if (ry >= 0 && ry < 12 && rx >= 0 && rx < 120) {
            EditModeManager.EditMode newMode = EditModeManager.getCurrentMode() == EditModeManager.EditMode.FULLMATCH
                ? EditModeManager.EditMode.NORMAL : EditModeManager.EditMode.FULLMATCH;
            EditModeManager.setMode(newMode);
        }
        // 稀有度 - 按钮区域: 相对 x=4~24, y=12~24
        if (rx >= 4 && rx < 24 && ry >= 12 && ry < 24) {
            EditModeManager.previousRarity();
        }
        // 稀有度 + 按钮区域: 相对 x=120~140, y=12~24
        if (rx >= 120 && rx < 140 && ry >= 12 && ry < 24) {
            EditModeManager.nextRarity();
        }
    }

    /**
     * 将面板位置钳制在屏幕内（按当前折叠/展开尺寸）。
     */
    private static void clampToScreen() {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int panelWidth = panelCollapsed ? 20 : 160;
        int panelHeight = panelCollapsed ? 14
            : (EditModeManager.getCurrentMode() == EditModeManager.EditMode.FULLMATCH ? 106 : 66);
        panelX = Math.max(0, Math.min(panelX, screenW - panelWidth));
        panelY = Math.max(0, Math.min(panelY, screenH - panelHeight));
    }

    // ---- Key Handlers ----

    private static boolean isCtrlPressed() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) ||
               InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean isNumberKey(int keyCode) {
        return keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_7;
    }

    @SuppressWarnings("null")
    private static void handleNumberKeyPress(int keyCode) {
        if (!EditModeManager.isEditModeEnabled()) return;
        int rarity = keyCode - GLFW.GLFW_KEY_1 + 1;
        EditModeManager.setRarity(rarity);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                Component.translatable("rarity.core.edit_mode_rarity_selected", rarity), true);
        }
    }

    @SuppressWarnings("null")
    private static void handleZeroKeyPress() {
        if (!EditModeManager.isEditModeEnabled()) return;
        EditModeManager.setRarity(0);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                Component.translatable("rarity.core.edit_mode_no_rarity"), true);
        }
    }

    // ---- Slot Detection ----

    private static Slot getSlotUnderMouse(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (screen instanceof AbstractContainerScreenAccessor accessor) {
            return accessor.getHoveredSlot();
        }
        try {
            int leftPos = screen.getGuiLeft();
            int topPos = screen.getGuiTop();
            int relX = (int) (mouseX - leftPos);
            int relY = (int) (mouseY - topPos);
            for (Slot slot : screen.getMenu().slots) {
                if (isMouseOverSlot(slot, relX, relY)) return slot;
            }
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to get slot under mouse", e);
        }
        return null;
    }

    private static boolean isMouseOverSlot(Slot slot, int mouseX, int mouseY) {
        return mouseX >= slot.x && mouseX < slot.x + 16 &&
               mouseY >= slot.y && mouseY < slot.y + 16;
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        while (!text.isEmpty() && font.width(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
