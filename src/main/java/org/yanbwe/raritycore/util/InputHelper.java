package org.yanbwe.raritycore.util;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 输入辅助工具类。
 * <p>提供通用的键盘状态检测方法，避免在多个事件处理器中重复实现。</p>
 */
public final class InputHelper {

    private InputHelper() {
        // 工具类，禁止实例化
    }

    /**
     * 检查左 Ctrl 或右 Ctrl 是否当前被按下。
     * @return 如果任一 Ctrl 键处于按下状态则返回 true
     */
    public static boolean isCtrlPressed() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
