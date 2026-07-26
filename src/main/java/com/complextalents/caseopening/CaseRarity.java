package com.complextalents.caseopening;

import net.minecraft.network.chat.Component;

public enum CaseRarity {
    MIL_SPEC("Mil-Spec Grade", 0x4B69FF, 0.9f),
    RESTRICTED("Restricted", 0x8847FF, 1.0f),
    CLASSIFIED("Classified", 0xD32CE6, 1.1f),
    COVERT("Covert", 0xEB4B4B, 1.2f),
    SPECIAL("★ Special Item ★", 0xFFD700, 1.3f);

    private final String displayName;
    private final int colorHex;
    private final float soundPitch;

    CaseRarity(String displayName, int colorHex, float soundPitch) {
        this.displayName = displayName;
        this.colorHex = colorHex;
        this.soundPitch = soundPitch;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColorHex() {
        return colorHex;
    }

    public float getSoundPitch() {
        return soundPitch;
    }

    public Component getFormattedComponent() {
        return Component.literal(displayName).withStyle(style -> style.withColor(colorHex));
    }
}
