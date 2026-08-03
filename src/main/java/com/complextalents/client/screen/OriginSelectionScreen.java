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
    private static final int SCREEN_WIDTH = 360;
    private static final int SCREEN_HEIGHT = 320;

    private int currentIndex = 0;
    private List<Origin> origins = new ArrayList<>();

    private int screenX;
    private int screenY;

    private Button prevButton;
    private Button nextButton;
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

        int buttonY = screenY + SCREEN_HEIGHT - 28;

        // Previous button
        this.prevButton = this.addRenderableWidget(Button.builder(Component.literal("◀"),
                (btn) -> previousOrigin())
                .pos(screenX + 10, buttonY)
                .size(32, 20)
                .build());

        // Next button
        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal("▶"),
                (btn) -> nextOrigin())
                .pos(screenX + SCREEN_WIDTH - 42, buttonY)
                .size(32, 20)
                .build());

        // Select button — centred
        this.selectButton = this.addRenderableWidget(Button.builder(Component.literal("✔ Select Origin"),
                (btn) -> selectCurrentOrigin())
                .pos(screenX + (SCREEN_WIDTH / 2) - 55, buttonY)
                .size(110, 20)
                .build());

        updateButtonStates();
    }

    private void updateButtonStates() {
        prevButton.active = currentIndex > 0;
        nextButton.active = currentIndex < origins.size() - 1;
    }

    private void previousOrigin() {
        if (currentIndex > 0) {
            currentIndex--;
            updateButtonStates();
        }
    }

    private void nextOrigin() {
        if (currentIndex < origins.size() - 1) {
            currentIndex++;
            updateButtonStates();
        }
    }

    private void selectCurrentOrigin() {
        if (currentIndex >= 0 && currentIndex < origins.size()) {
            Origin selected = origins.get(currentIndex);
            PacketHandler.sendToServer(new SelectOriginPacket(selected.getId()));
            this.onClose();
        }
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

        // Page indicator (top right badge)
        if (!origins.isEmpty()) {
            String pageInfo = (currentIndex + 1) + " / " + origins.size();
            int pw = this.font.width(pageInfo);
            g.fill(screenX + SCREEN_WIDTH - pw - 18, screenY + 5, screenX + SCREEN_WIDTH - 6, screenY + 23, 0xFF3D321A);
            g.drawString(this.font, "§e" + pageInfo, screenX + SCREEN_WIDTH - pw - 12, screenY + 9, 0xFFFFFF, false);
        }

        // Header separator line
        g.fill(screenX, screenY + 28, screenX + SCREEN_WIDTH, screenY + 29, 0xFF5D4A18);

        // Origin card
        if (currentIndex >= 0 && currentIndex < origins.size()) {
            renderOriginCard(g, mouseX, mouseY);
        }

        // Footer separator line
        g.fill(screenX + 6, screenY + SCREEN_HEIGHT - 35, screenX + SCREEN_WIDTH - 6, screenY + SCREEN_HEIGHT - 34,
                0xFF3D4258);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderOriginCard(GuiGraphics g, int mouseX, int mouseY) {
        Origin origin = origins.get(currentIndex);

        int cardX = screenX + 10;
        int cardY = screenY + 36;
        int cardWidth = SCREEN_WIDTH - 20;
        int cardHeight = SCREEN_HEIGHT - 78;

        // Card outer border & body background
        g.fill(cardX - 1, cardY - 1, cardX + cardWidth + 1, cardY + cardHeight + 1, 0xFF2A2E44);
        g.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xFF181B29);

        int cx = cardX + 12;
        int cy = cardY + 12;

        // Icon + Name Row
        boolean hasIcon = false;
        ResourceLocation activeSkillId = origin.getActiveSkillId();
        if (activeSkillId != null) {
            Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
            if (skill != null && skill.getIcon() != null) {
                // Icon box frame
                g.fill(cx - 2, cy - 2, cx + 38, cy + 38, 0xFFFFD700);
                g.fill(cx - 1, cy - 1, cx + 37, cy + 37, 0xFF141724);
                g.blit(skill.getIcon(), cx + 2, cy + 2, 0, 0, 32, 32, 32, 32);
                hasIcon = true;
            }
        }

        int nameX = hasIcon ? cx + 46 : cx;
        int nameY = cy + 2;
        String displayName = origin.getDisplayName().getString();
        g.drawString(this.font, "§6§l" + displayName.toUpperCase(), nameX, nameY, 0xFFFFAA00, false);

        // Origin class badge
        String badge = "CLASS: " + origin.getId().getPath().replace("_", " ").toUpperCase();
        g.drawString(this.font, "§7[ §e" + badge + " §7]", nameX, nameY + 14, 0xFFFFFF, false);

        // Divider
        int divY = cy + 44;
        g.fill(cardX + 8, divY, cardX + cardWidth - 8, divY + 1, 0xFF2A2E44);

        // Description
        int descY = divY + 8;
        Component description = origin.getDescription();
        if (description != null) {
            g.drawWordWrap(this.font, description, cx, descY, cardWidth - 24, 0xBBCCDDEE);
        }

        // Signature Ability Card Container
        if (activeSkillId != null) {
            Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
            if (skill != null) {
                int skillBoxY = descY + 68;
                int boxWidth = cardWidth - 24;

                // Skill header label
                g.drawString(this.font, "§d✦ SIGNATURE ABILITY ✦", cx + 8, skillBoxY + 6, 0xFFE040FB, false);
                g.drawString(this.font, "§b§l" + skill.getDisplayName().getString(), cx + 8, skillBoxY + 18, 0xFF00E5FF,
                        false);

                // Skill description
                String scalingText = skill.getDescription().getString();
                g.drawWordWrap(this.font, Component.literal(scalingText), cx + 8, skillBoxY + 32, boxWidth - 16,
                        0xAABBBCCC);
            }
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
