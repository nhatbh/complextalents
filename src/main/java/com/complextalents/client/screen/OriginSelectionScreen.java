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
    private static final int SCREEN_HEIGHT = 480;

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

        // Previous button
        this.prevButton = this.addRenderableWidget(Button.builder(Component.literal("< Previous"),
                (btn) -> previousOrigin())
                .pos(screenX + 20, screenY + 440)
                .size(100, 20)
                .build());

        // Next button
        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal("Next >"),
                (btn) -> nextOrigin())
                .pos(screenX + 400, screenY + 440)
                .size(100, 20)
                .build());

        // Select button
        this.selectButton = this.addRenderableWidget(Button.builder(Component.literal("Select"),
                (btn) -> selectCurrentOrigin())
                .pos(screenX + 220, screenY + 440)
                .size(80, 20)
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw background
        guiGraphics.fill(0, 0, this.width, this.height, 0xBB000000);
        guiGraphics.fill(screenX, screenY, screenX + SCREEN_WIDTH, screenY + SCREEN_HEIGHT, 0xBB8B8B8B);
        guiGraphics.fill(screenX + 1, screenY + 1, screenX + SCREEN_WIDTH - 1, screenY + SCREEN_HEIGHT - 1, 0xBB000000);

        // Render title
        guiGraphics.drawString(this.font, "§l§6Select Your Origin", screenX + 10, screenY + 10, 0xFFFFFF, false);

        // Render current origin
        if (currentIndex >= 0 && currentIndex < origins.size()) {
            renderOriginCard(guiGraphics, mouseX, mouseY);
        }

        // Render pagination info
        String pageInfo = (currentIndex + 1) + " / " + origins.size();
        int pageWidth = this.font.width(pageInfo);
        guiGraphics.drawString(this.font, pageInfo, screenX + SCREEN_WIDTH - 10 - pageWidth, screenY + 440, 0xFFFFAA, false);

        // Render widgets
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderOriginCard(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Origin origin = origins.get(currentIndex);

        int cardX = screenX + 10;
        int cardY = screenY + 35;
        int cardWidth = 500;
        int cardHeight = 390;

        // Draw card background
        guiGraphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xFF333333);
        guiGraphics.fill(cardX + 1, cardY + 1, cardX + cardWidth - 1, cardY + cardHeight - 1, 0xFF222222);

        int contentX = cardX + 10;
        int contentY = cardY + 10;

        // Draw origin icon
        ResourceLocation activeSkillId = origin.getActiveSkillId();
        if (activeSkillId != null) {
            Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
            if (skill != null && skill.getIcon() != null) {
                guiGraphics.blit(skill.getIcon(), contentX, contentY, 0, 0, 32, 32, 32, 32);
            }
        }

        // Draw origin name
        Component originDisplayName = origin.getDisplayName();
        guiGraphics.drawString(this.font, "§l§6" + originDisplayName.getString(), contentX + 40, contentY, 0xFFFFFF, false);

        // Draw description
        Component description = origin.getDescription();
        if (description != null) {
            guiGraphics.drawWordWrap(this.font, description, contentX, contentY + 40, cardWidth - 20, 0xCCCCCC);
        }

        // Draw skill section (if available)
        if (activeSkillId != null) {
            Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
            if (skill != null) {
                int skillSectionY = contentY + 130;

                // Skill title
                Component skillDisplayName = skill.getDisplayName();
                guiGraphics.drawString(this.font, "§l§b" + skillDisplayName.getString(), contentX, skillSectionY, 0xFFFFFF, false);

                // Skill description/scaling
                String scalingText = generateSkillScalingDescription(skill, 1);
                guiGraphics.drawWordWrap(this.font, Component.literal(scalingText), contentX, skillSectionY + 15, cardWidth - 20, 0xCCCCCC);
            }
        }
    }

    private String generateSkillScalingDescription(Skill skill, int skillLevel) {
        return skill.getDescription().getString();
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
