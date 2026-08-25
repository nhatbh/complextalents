package com.complextalents.impl.warrior.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

/**
 * Client-side data holder for active Warrior weapon path parry effects.
 */
public class ClientParryData {

    private static String pathName = "";
    private static String translationKey = "";
    private static int valueArg = 0;
    private static String icon = "";
    private static int color = 0xFFFFFFFF;
    private static int totalTicks = 0;
    private static long endGameTime = 0;

    public static synchronized void setParryEffect(String newPathName, String newTranslationKey, int newValueArg, int durationTicks, String newIcon, int newColor) {
        pathName = newPathName != null ? newPathName : "";
        translationKey = newTranslationKey != null ? newTranslationKey : "";
        valueArg = newValueArg;
        icon = newIcon != null ? newIcon : "";
        color = newColor;
        totalTicks = Math.max(1, durationTicks);

        long currentGameTime = getCurrentGameTime();
        endGameTime = currentGameTime + durationTicks;
    }

    public static synchronized boolean hasActiveEffect() {
        return getRemainingTicks() > 0;
    }

    public static synchronized int getRemainingTicks() {
        long current = getCurrentGameTime();
        long remaining = endGameTime - current;
        return (int) Math.max(0, remaining);
    }

    public static synchronized float getProgress() {
        if (totalTicks <= 0) return 0.0f;
        int remaining = getRemainingTicks();
        return Math.min(1.0f, Math.max(0.0f, (float) remaining / (float) totalTicks));
    }

    public static synchronized String getPathName() {
        return pathName;
    }

    public static synchronized String getDisplayText() {
        if (translationKey.isEmpty()) return "";
        if (valueArg > 0) {
            return I18n.get(translationKey, valueArg);
        }
        return I18n.get(translationKey);
    }

    public static synchronized String getTitleText() {
        return I18n.get("hud.complextalents.warrior.parry.title");
    }

    public static synchronized String getIcon() {
        return icon;
    }

    public static synchronized int getColor() {
        return color;
    }

    public static synchronized int getTotalTicks() {
        return totalTicks;
    }

    private static long getCurrentGameTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level.getGameTime();
        }
        return 0;
    }
}
