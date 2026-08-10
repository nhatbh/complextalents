package com.complextalents.targeting.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Manages the global smart cast toggle state for client targeting.
 */
public class SmartCastManager {

    private static boolean smartCastEnabled = true;

    public static boolean isSmartCastEnabled() {
        return smartCastEnabled;
    }

    public static void setSmartCastEnabled(boolean enabled) {
        smartCastEnabled = enabled;
    }

    public static void toggleSmartCast() {
        smartCastEnabled = !smartCastEnabled;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§7Smart Cast: " + (smartCastEnabled ? "§aON" : "§cOFF")),
                    true
            );
        }
    }
}
