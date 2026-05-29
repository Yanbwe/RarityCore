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

    // ---- Mouse Click ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings("null")
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!EditModeManager.isEditModeEnabled()) return;

        Screen screen = event.getScreen();

        // 如果点击在浮动面板区域内，处理面板交互
        if (!panelCollapsed && isInPanel((int) event.getMouseX(), (int) event.getMouseY())) {
            handlePanelClick(screen, (int) event.getMouseX(), (int) event.getMouseY());
            event.setCanceled(true);
            return;
        }

        // 否则处理物品槽点击
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

    // ---- GUI Overlay Render ----

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!EditModeManager.isEditModeEnabled()) return;

        Screen screen = event.getScreen();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;

        int x = 4, y = 4;
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
        if (panelCollapsed) return mouseX >= 4 && mouseX < 24 && mouseY >= 4 && mouseY < 18;
        return mouseX >= 4 && mouseX < 164 && mouseY >= 4 && mouseY < 90;
    }

    private static void handlePanelClick(Screen screen, int mouseX, int mouseY) {
        // 模式切换按钮区域: y=4~16 (line 2~14), x=4~124
        if (mouseY >= 4 && mouseY < 16 && mouseX >= 4 && mouseX < 124) {
            EditModeManager.EditMode newMode = EditModeManager.getCurrentMode() == EditModeManager.EditMode.FULLMATCH
                ? EditModeManager.EditMode.NORMAL : EditModeManager.EditMode.FULLMATCH;
            EditModeManager.setMode(newMode);
        }
        // 稀有度 - 按钮区域: x=8, y=16, w=20, h=10
        if (mouseX >= 8 && mouseX < 28 && mouseY >= 16 && mouseY < 28) {
            EditModeManager.previousRarity();
        }
        // 稀有度 + 按钮区域: x=124, y=16, w=20, h=10
        if (mouseX >= 124 && mouseX < 144 && mouseY >= 16 && mouseY < 28) {
            EditModeManager.nextRarity();
        }
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
