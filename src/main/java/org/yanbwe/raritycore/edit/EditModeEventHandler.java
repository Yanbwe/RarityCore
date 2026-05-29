package org.yanbwe.raritycore.edit;

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
import org.yanbwe.raritycore.util.InputHelper;

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
        if (!EditModeManager.isEditModeEnabled() || !InputHelper.isCtrlPressed()) {
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
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!EditModeManager.isEditModeEnabled()) {
            return;
        }

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        // 避免与 EditModeOverlay 面板区域冲突：面板内点击由 Overlay 独占处理
        if (isInOverlayPanel(mouseX, mouseY)) {
            return;
        }

        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        Slot clickedSlot = getSlotUnderMouse(containerScreen, mouseX, mouseY);
        if (clickedSlot == null || !clickedSlot.hasItem()) {
            return;
        }

        ItemStack itemStack = clickedSlot.getItem();
        if (itemStack.isEmpty()) {
            return;
        }

        // modifyItemRarity 内部根据 EditModeManager.getEditMode() 自动分派
        // 判断是否单人游戏：单人游戏中编辑操作直接在本机执行，多人则通过数据包发送到服务端
        boolean isSingleplayer = Minecraft.getInstance().getSingleplayerServer() != null;
        if (EditModeManager.modifyItemRarity(itemStack, isSingleplayer)) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                int currentRarity = EditModeManager.getCurrentRarity();
                player.sendOverlayMessage(
                        Component.translatable("rarity.core.edit_mode_applied", currentRarity));
            }
            event.setCanceled(true);
        }
    }

    // ============================================================
    //  辅助方法
    // ============================================================

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
            return accessor.raritycore$getHoveredSlot();
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

    /** 判断鼠标坐标是否落在槽位矩形内（槽位渲染大小为 18x18，含边框） */
    private static boolean isMouseOverSlot(Slot slot, int mouseX, int mouseY) {
        return mouseX >= slot.x && mouseX < slot.x + 18
                && mouseY >= slot.y && mouseY < slot.y + 18;
    }

    /**
     * 检查鼠标是否落在 EditModeOverlay 面板区域内。
     * <p>面板位于屏幕左上角 (4,4)，最大尺寸 175×94（FullMatch 展开模式）。
     * 面板内点击由 {@link org.yanbwe.raritycore.client.EditModeOverlay} 独占处理。</p>
     */
    private static boolean isInOverlayPanel(double mouseX, double mouseY) {
        return mouseX >= 4 && mouseX < 4 + 175
                && mouseY >= 4 && mouseY < 4 + 94;
    }
}
