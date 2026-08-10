package com.complextalents.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Utility for robustly checking keyboard modifier states (Ctrl, Shift) during client gameplay and GUI screens.
 */
public class KeyHelper {

    public static boolean isCtrlDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return Screen.hasControlDown();
        }
        long window = mc.getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)
                || Screen.hasControlDown();
    }

    public static boolean isShiftDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return Screen.hasShiftDown();
        }
        long window = mc.getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)
                || Screen.hasShiftDown()
                || (mc.options != null && mc.options.keyShift.isDown());
    }
}
