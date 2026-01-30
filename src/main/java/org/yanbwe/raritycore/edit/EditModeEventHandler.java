package org.yanbwe.raritycore.edit;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.mixin.AbstractContainerScreenAccessor;

/**
 * 客户端编辑模式事件处理器
 * 处理键盘输入和鼠标点击事件来控制编辑模式
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = RarityCore.MODID)
public class EditModeEventHandler {
    
    @SubscribeEvent
    public static void onKeyInput(ScreenEvent.KeyPressed.Pre event) {
        // 检查是否按下 Ctrl + 数字键组合
        if (isCtrlPressed() && isNumberKey(event.getKeyCode())) {
            handleNumberKeyPress(event.getKeyCode());
            event.setCanceled(true); // 阻止默认按键行为
        }
    }
    
    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        // 只在编辑模式下处理鼠标点击
        if (!EditModeManager.isEditModeEnabled()) {
            return;
        }
        
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }
        
        // 获取鼠标位置
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        
        // 获取被点击的槽位
        Slot clickedSlot = getSlotUnderMouse(containerScreen, mouseX, mouseY);
        if (clickedSlot == null || !clickedSlot.hasItem()) {
            return;
        }
        
        ItemStack itemStack = clickedSlot.getItem();
        if (itemStack.isEmpty()) {
            return;
        }
        
        // 修改物品稀有度
        if (EditModeManager.modifyItemRarity(itemStack)) {
            // 显示反馈消息
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                int currentRarity = EditModeManager.getCurrentRarity();
                player.displayClientMessage(
                    Component.translatable("rarity.core.edit_mode_applied", currentRarity), 
                    true
                );
            }
        }
        
        // 阻止默认的鼠标点击行为
        event.setCanceled(true);
    }
    
    /**
     * 检查Ctrl键是否被按下
     */
    private static boolean isCtrlPressed() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || 
               InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
    
    /**
     * 检查是否为数字键
     */
    private static boolean isNumberKey(int keyCode) {
        return keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_7;
    }
    
    /**
     * 处理数字键按下事件
     */
    private static void handleNumberKeyPress(int keyCode) {
        if (!EditModeManager.isEditModeEnabled()) {
            return;
        }
        
        // 将键码转换为稀有度等级 (1-7)
        int rarity = keyCode - GLFW.GLFW_KEY_1 + 1;
        EditModeManager.setRarity(rarity);
        
        // 显示反馈消息
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                Component.translatable("rarity.core.edit_mode_rarity_selected", rarity), 
                true
            );
        }
    }
    
    /**
     * 获取鼠标位置下的槽位
     */
    private static Slot getSlotUnderMouse(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (screen instanceof AbstractContainerScreenAccessor accessor) {
            return accessor.getHoveredSlot();
        }
        
        // 备用方法：手动计算槽位
        try {
            // 获取屏幕左上角坐标
            int leftPos = ((AbstractContainerScreen<?>) screen).getGuiLeft();
            int topPos = ((AbstractContainerScreen<?>) screen).getGuiTop();
            
            // 计算相对坐标
            int relX = (int) (mouseX - leftPos);
            int relY = (int) (mouseY - topPos);
            
            // 遍历所有槽位寻找匹配的
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
    
    /**
     * 检查鼠标是否在槽位上方
     */
    private static boolean isMouseOverSlot(Slot slot, int mouseX, int mouseY) {
        return mouseX >= slot.x && mouseX < slot.x + 16 && 
               mouseY >= slot.y && mouseY < slot.y + 16;
    }
}