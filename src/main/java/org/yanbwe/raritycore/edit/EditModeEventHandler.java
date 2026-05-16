package org.yanbwe.raritycore.edit;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.mixin.AbstractContainerScreenAccessor;

/**
 * 客户端编辑模式事件处理器。
 * <p>负责：</p>
 * <ul>
 *   <li>Ctrl+数字键设置当前稀有度等级</li>
 *   <li>Ctrl+0 切换删除模式</li>
 *   <li>在容器界面中点击物品槽修改稀有度（Normal/FullMatch）</li>
 * </ul>
 * <p>GUI 覆盖层渲染由 {@link org.yanbwe.raritycore.client.EditModeOverlay} 处理。</p>
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = RarityCore.MODID)
public class EditModeEventHandler {

    // ============================================================
    //  键盘事件：Ctrl+数字 稀有度 / Ctrl+0 删除
    // ============================================================

    /**
     * 处理键盘快捷键：
     * <ul>
     *   <li>Ctrl+1~7 — 设置当前稀有度（仅编辑模式）</li>
     *   <li>Ctrl+0 — 切换删除模式（仅编辑模式）</li>
     * </ul>
     * <p>Ctrl+H 折叠由 {@link org.yanbwe.raritycore.client.EditModeOverlay} 处理。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(ScreenEvent.KeyPressed.Pre event) {
        // 以下快捷键仅在编辑模式下生效
        if (!EditModeManager.isEditModeEnabled() || !isCtrlPressed()) {
            return;
        }

        // Ctrl+1~7：设置稀有度
        if (isNumberKey(event.getKeyCode())) {
            int rarity = event.getKeyCode() - GLFW.GLFW_KEY_1 + 1;
            EditModeManager.setRarity(rarity);
            EditModeManager.setDeleteMode(false);
            event.setCanceled(true);

            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendOverlayMessage(
                        Component.translatable("rarity.core.edit_mode_rarity_selected", rarity));
            }
            return;
        }

        // Ctrl+0：切换删除模式
        if (event.getKeyCode() == GLFW.GLFW_KEY_0) {
            boolean newDeleteMode = EditModeManager.toggleDeleteMode();
            event.setCanceled(true);

            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendOverlayMessage(Component.translatable(
                        newDeleteMode ? "rarity.core.edit_mode_delete_mode_enabled"
                                : "rarity.core.edit_mode_delete_mode_disabled"));
            }
        }
    }

    // ============================================================
    //  鼠标点击：Normal / FullMatch 物品稀有度修改
    // ============================================================

    /**
     * 在容器界面中点击物品槽以修改其稀有度。
     * <p>根据 {@link EditModeManager#getEditMode()} 自动分发到 Normal 或 FullMatch 处理器。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings("null")
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!EditModeManager.isEditModeEnabled()) {
            return;
        }

        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        Slot clickedSlot = getSlotUnderMouse(containerScreen, mouseX, mouseY);
        if (clickedSlot == null || !clickedSlot.hasItem()) {
            return;
        }

        ItemStack itemStack = clickedSlot.getItem();
        if (itemStack.isEmpty()) {
            return;
        }

        // modifyItemRarity 内部根据 EditModeManager.getEditMode() 自动分派
        if (EditModeManager.modifyItemRarity(itemStack)) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                int currentRarity = EditModeManager.getCurrentRarity();
                player.sendOverlayMessage(
                        Component.translatable("rarity.core.edit_mode_applied", currentRarity));
            }
        }

        event.setCanceled(true);
    }

    // ============================================================
    //  辅助方法
    // ============================================================

    /** 检查左/右 Ctrl 是否按下 */
    private static boolean isCtrlPressed() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    /** 检查键码是否为数字键 1~7 */
    private static boolean isNumberKey(int keyCode) {
        return keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_7;
    }

    /**
     * 获取鼠标指针下方的物品槽位。
     * <p>优先使用 Mixin Accessor，失败时回退到手动坐标计算。</p>
     */
    private static Slot getSlotUnderMouse(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (screen instanceof AbstractContainerScreenAccessor accessor) {
            return accessor.getHoveredSlot();
        }

        // 回退：手动遍历所有槽位
        try {
            int leftPos = screen.getGuiLeft();
            int topPos = screen.getGuiTop();
            int relX = (int) (mouseX - leftPos);
            int relY = (int) (mouseY - topPos);

            for (Slot slot : screen.getMenu().slots) {
                if (isMouseOverSlot(slot, relX, relY)) {
                    return slot;
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to get slot under mouse", e);
        }

        return null;
    }

    /** 判断鼠标坐标是否落在槽位矩形内 */
    private static boolean isMouseOverSlot(Slot slot, int mouseX, int mouseY) {
        return mouseX >= slot.x && mouseX < slot.x + 16
                && mouseY >= slot.y && mouseY < slot.y + 16;
    }
}
