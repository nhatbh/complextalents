package com.complextalents.client.screen;

import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.origin.network.SelectOriginPacket;
import com.complextalents.network.PacketHandler;
import com.complextalents.skill.Skill;
import com.complextalents.skill.SkillRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class OriginSelectionScreen extends Screen {
    private static final int SCREEN_WIDTH = 520;
    private static final int SCREEN_HEIGHT = 320;

    private int selectedIndex = 0;
    private final List<Origin> origins = new ArrayList<>();

    private int screenX;
    private int screenY;

    private Button selectButton;

    public OriginSelectionScreen() {
        super(Component.literal("Select Your Origin"));
        this.origins.addAll(OriginRegistry.getInstance().getAllOrigins());
    }

    @Override
    protected void init() {
        super.init();

        this.screenX = (this.width - SCREEN_WIDTH) / 2;
        this.screenY = (this.height - SCREEN_HEIGHT) / 2;

        // Sort origins alphabetically by name to make the grid layout stable
        this.origins.clear();
        this.origins.addAll(OriginRegistry.getInstance().getAllOrigins());
        this.origins.sort(java.util.Comparator.comparing(o -> o.getDisplayName().getString()));

        // Add Lock In button at the bottom right
        this.selectButton = this.addRenderableWidget(Button.builder(Component.literal("✔ Lock In"),
                (btn) -> selectCurrentOrigin())
                .pos(screenX + SCREEN_WIDTH - 110 - 12, screenY + SCREEN_HEIGHT - 32)
                .size(110, 20)
                .build());

        updateButtonStates();
    }

    private void updateButtonStates() {
        if (selectButton != null) {
            selectButton.active = selectedIndex >= 0 && selectedIndex < origins.size();
        }
    }

    private void selectCurrentOrigin() {
        if (selectedIndex >= 0 && selectedIndex < origins.size()) {
            Origin selected = origins.get(selectedIndex);
            PacketHandler.sendToServer(new SelectOriginPacket(selected.getId()));
            this.onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int startX = screenX + 22;
        int startY = screenY + 67;

        for (int i = 0; i < origins.size(); i++) {
            int col = i % 3;
            int row = i / 3;
            int slotX = startX + col * 58;
            int slotY = startY + row * 52;

            if (mouseX >= slotX && mouseX <= slotX + 40 && mouseY >= slotY && mouseY <= slotY + 40) {
                this.selectedIndex = i;
                this.updateButtonStates();
                // Play click sound
                if (minecraft != null) {
                    minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim background
        g.fill(0, 0, this.width, this.height, 0xD005070A);

        // Panel outer border glow (gold theme)
        g.fill(screenX - 2, screenY - 2, screenX + SCREEN_WIDTH + 2, screenY + SCREEN_HEIGHT + 2, 0xFFFFD700);
        g.fill(screenX - 1, screenY - 1, screenX + SCREEN_WIDTH + 1, screenY + SCREEN_HEIGHT + 1, 0xFF3D321A);

        // Panel body fill
        g.fill(screenX, screenY, screenX + SCREEN_WIDTH, screenY + SCREEN_HEIGHT, 0xF0121522);

        // Header bar
        g.fill(screenX, screenY, screenX + SCREEN_WIDTH, screenY + 28, 0xFF241D0D);

        // Header text centred
        String title = "✦  SELECT YOUR ORIGIN  ✦";
        int titleW = this.font.width(title);
        g.drawString(this.font, "§6§l" + title, screenX + (SCREEN_WIDTH - titleW) / 2, screenY + 9, 0xFFFFD700, false);

        // Header separator line
        g.fill(screenX, screenY + 28, screenX + SCREEN_WIDTH, screenY + 29, 0xFF5D4A18);

        // Left Panel Content: Grid Header
        String gridTitle = "ORIGINS";
        g.drawString(this.font, "§e§l" + gridTitle, screenX + 16, screenY + 44, 0xFFFFAA00, false);

        // Draw left container frame
        int gridFrameX = screenX + 10;
        int gridFrameY = screenY + 58;
        int gridFrameW = 180;
        int gridFrameH = 214;
        g.fill(gridFrameX - 1, gridFrameY - 1, gridFrameX + gridFrameW + 1, gridFrameY + gridFrameH + 1, 0xFF2A2E44);
        g.fill(gridFrameX, gridFrameY, gridFrameX + gridFrameW, gridFrameY + gridFrameH, 0xFF0E101A);

        // Draw grid slots
        int startX = screenX + 22;
        int startY = screenY + 67;
        int slotHoveredIndex = -1;

        for (int i = 0; i < origins.size(); i++) {
            int col = i % 3;
            int row = i / 3;
            int slotX = startX + col * 58;
            int slotY = startY + row * 52;

            boolean isSelected = (i == selectedIndex);
            boolean isHovered = (mouseX >= slotX && mouseX <= slotX + 40 && mouseY >= slotY && mouseY <= slotY + 40);
            if (isHovered) {
                slotHoveredIndex = i;
            }

            // Draw slot border & background
            int borderColor = isSelected ? 0xFFFFD700 : (isHovered ? 0xFFAAAAAA : 0xFF3D4258);
            g.fill(slotX - 1, slotY - 1, slotX + 41, slotY + 41, borderColor);
            g.fill(slotX, slotY, slotX + 40, slotY + 40, 0xFF141724);

            // Draw origin icon or letter fallback
            Origin origin = origins.get(i);
            ResourceLocation activeSkillId = origin.getActiveSkillId();
            boolean hasIcon = false;
            if (activeSkillId != null) {
                Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
                if (skill != null && skill.getIcon() != null) {
                    g.blit(skill.getIcon(), slotX + 4, slotY + 4, 0, 0, 32, 32, 32, 32);
                    hasIcon = true;
                }
            }

            if (!hasIcon) {
                String displayName = origin.getDisplayName().getString();
                String firstChar = displayName.isEmpty() ? "?" : displayName.substring(0, 1).toUpperCase();
                int charW = this.font.width(firstChar);
                g.drawString(this.font, "§6" + firstChar, slotX + (40 - charW) / 2, slotY + (40 - 9) / 2, 0xFFFFD700, false);
            }
        }

        // Right Panel Content: Details of selected origin
        if (selectedIndex >= 0 && selectedIndex < origins.size()) {
            renderOriginDetails(g, mouseX, mouseY);
        }

        // Render standard widgets (Lock In button)
        super.render(g, mouseX, mouseY, partialTick);

        // Render hover tooltip over grid slots
        if (slotHoveredIndex >= 0 && slotHoveredIndex < origins.size()) {
            Origin hoveredOrigin = origins.get(slotHoveredIndex);
            g.renderTooltip(this.font, hoveredOrigin.getDisplayName(), mouseX, mouseY);
        }
    }

    private void renderOriginDetails(GuiGraphics g, int mouseX, int mouseY) {
        Origin origin = origins.get(selectedIndex);

        int panelX = screenX + 200;
        int panelY = screenY + 36;
        int panelWidth = 310;
        int panelHeight = 236;

        // Card outer border & body background
        g.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF2A2E44);
        g.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF181B29);

        int cx = panelX + 14;
        int cy = panelY + 12;

        // Name
        String displayName = origin.getDisplayName().getString();
        g.drawString(this.font, "§6§l" + displayName.toUpperCase(), cx, cy, 0xFFFFAA00, false);

        // Class badge
        String badge = "CLASS: " + origin.getId().getPath().replace("_", " ").toUpperCase();
        g.drawString(this.font, "§7[ §e" + badge + " §7]", cx, cy + 14, 0xFFFFFF, false);

        // Divider
        int divY = cy + 28;
        g.fill(panelX + 8, divY, panelX + panelWidth - 8, divY + 1, 0xFF2A2E44);

        // Description (wrapped)
        int descY = divY + 8;
        Component description = origin.getDescription();
        if (description != null) {
            g.pose().pushPose();
            g.pose().translate((float) cx, (float) descY, 0.0f);
            g.pose().scale(0.85f, 0.85f, 1.0f);
            g.drawWordWrap(this.font, description, 0, 0, (int) ((panelWidth - 28) / 0.85f), 0xBBCCDDEE);
            g.pose().popPose();
        }

        // Signature Ability Box
        ResourceLocation activeSkillId = origin.getActiveSkillId();
        if (activeSkillId != null) {
            Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
            if (skill != null) {
                int skillBoxY = panelY + 120;
                int boxWidth = panelWidth - 28;

                // Frame for skill details
                g.fill(cx - 4, skillBoxY, cx + boxWidth + 4, skillBoxY + 1, 0xFF2D3247);

                // Skill header label
                g.drawString(this.font, "§d✦ SIGNATURE ABILITY ✦", cx, skillBoxY + 8, 0xFFE040FB, false);
                g.drawString(this.font, "§b§l" + skill.getDisplayName().getString(), cx, skillBoxY + 20, 0xFF00E5FF, false);

                // Skill description (wrapped)
                String scalingText = skill.getDescription().getString();
                g.pose().pushPose();
                g.pose().translate((float) cx, (float) (skillBoxY + 34), 0.0f);
                g.pose().scale(0.85f, 0.85f, 1.0f);
                g.drawWordWrap(this.font, Component.literal(scalingText), 0, 0, (int) (boxWidth / 0.85f), 0xAABBBCCC);
                g.pose().popPose();
            }
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        if (minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

