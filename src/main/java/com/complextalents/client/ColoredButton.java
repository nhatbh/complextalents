package com.complextalents.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class ColoredButton extends Button {
    private final int backgroundColor;

    public ColoredButton(int x, int y, int width, int height, Component component, OnPress onPress, int backgroundColor) {
        super(x, y, width, height, component, onPress, Button.DEFAULT_NARRATION);
        this.backgroundColor = backgroundColor;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw background
        int bgColor = this.active ? this.backgroundColor : (this.backgroundColor & 0xFF888888);
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), bgColor);

        // Draw border
        guiGraphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.getWidth() - 1, this.getY() + this.getHeight() - 1, 0xFF000000);

        // Draw text
        int textColor = this.active ? 0xFFFFFF : 0xFFA0A0A0;
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.getWidth() / 2, this.getY() + (this.getHeight() - 8) / 2, textColor);
    }
}
