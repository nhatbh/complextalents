package com.complextalents.client.screen;

import com.complextalents.client.SpellTabUI;
import com.complextalents.client.StatsTabUI;
import com.complextalents.client.UpgradeCart;
import com.complextalents.client.WeaponTabUI;
import com.complextalents.leveling.client.ClientLevelingData;
import com.complextalents.network.FinalizePlayerUpgradesPacket;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.UpgradeData;
import com.complextalents.stats.StatType;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerProgressionScreen extends Screen {
    private static final int SCREEN_WIDTH = 520;
    private static final int SCREEN_HEIGHT = 480;
    private static final int CONTENT_HEIGHT = 360;

    private final Player player;
    private final UpgradeCart cart;

    private final StatsTabUI statsTab;
    private final SpellTabUI spellTab;
    private final WeaponTabUI weaponTab;

    private int currentTab = 0; // 0=Stats, 1=Spells, 2=Weapons
    private Button[] tabButtons = new Button[3];
    private List<Button> contentButtons = new ArrayList<>();
    private Button finalizeButton;

    private int screenX;
    private int screenY;

    public PlayerProgressionScreen(Player player) {
        super(Component.literal("Character Progression"));
        this.player = player;
        this.cart = new UpgradeCart(player);
        this.cart.setOnChangeCallback(this::onCartChanged);

        this.statsTab = new StatsTabUI(cart);
        this.spellTab = new SpellTabUI(cart);
        this.weaponTab = new WeaponTabUI(cart);
    }

    @Override
    protected void init() {
        super.init();

        // Center screen
        this.screenX = (this.width - SCREEN_WIDTH) / 2;
        this.screenY = (this.height - SCREEN_HEIGHT) / 2;

        // Clear previous widgets
        this.clearWidgets();
        this.contentButtons.clear();

        // Create tab buttons
        String[] tabNames = {"Stats", "Spells", "Weapons"};
        for (int i = 0; i < 3; i++) {
            final int tabIndex = i;
            this.tabButtons[i] = this.addRenderableWidget(Button.builder(Component.literal(tabNames[i]),
                    (btn) -> selectTab(tabIndex))
                    .pos(screenX + 10 + (i * 130), screenY + 35)
                    .size(120, 20)
                    .build());
            updateTabButtonStyle(i);
        }

        // Create finalize button
        this.finalizeButton = this.addRenderableWidget(Button.builder(Component.literal("Finalize"),
                (btn) -> this.finalizeUpgrades())
                .pos(screenX + 390, screenY + 440)
                .size(110, 20)
                .build());
        updateFinalizeButtonStyle();

        // Build content for current tab
        rebuildContent();
    }

    private void selectTab(int tabIndex) {
        if (currentTab != tabIndex) {
            currentTab = tabIndex;
            rebuildContent();
            for (int i = 0; i < 3; i++) {
                updateTabButtonStyle(i);
            }
        }
    }

    private void rebuildContent() {
        // Remove old content buttons
        for (Button btn : contentButtons) {
            this.removeWidget(btn);
        }
        contentButtons.clear();

        // Build new content based on current tab
        switch (currentTab) {
            case 0:
                contentButtons.addAll(statsTab.buildWidgets(this, screenX + 10, screenY + 65));
                break;
            case 1:
                contentButtons.addAll(spellTab.buildWidgets(this, screenX + 10, screenY + 65));
                break;
            case 2:
                contentButtons.addAll(weaponTab.buildWidgets(this, screenX + 10, screenY + 65));
                break;
        }

        // Add all content buttons to renderable widgets
        for (Button btn : contentButtons) {
            this.addRenderableWidget(btn);
        }
    }

    private void onCartChanged() {
        // Update current tab
        switch (currentTab) {
            case 0 -> statsTab.update();
            case 1 -> spellTab.update();
            case 2 -> weaponTab.update();
        }
        rebuildContent();
        updateFinalizeButtonStyle();
    }

    private void updateTabButtonStyle(int index) {
        if (tabButtons[index] != null) {
            tabButtons[index].active = true;
            // Active tab gets different styling - could add message change here if needed
        }
    }

    private void updateFinalizeButtonStyle() {
        if (finalizeButton != null) {
            int totalCost = cart.getTotalCost();
            finalizeButton.active = totalCost > 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Update is handled by cart callback, but we can add periodic refreshes if needed
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw background
        guiGraphics.fill(0, 0, this.width, this.height, 0xBB000000);
        guiGraphics.fill(screenX, screenY, screenX + SCREEN_WIDTH, screenY + SCREEN_HEIGHT, 0xBB8B8B8B);
        guiGraphics.fill(screenX + 1, screenY + 1, screenX + SCREEN_WIDTH - 1, screenY + SCREEN_HEIGHT - 1, 0xBB000000);

        // Render header
        renderHeader(guiGraphics, mouseX, mouseY, partialTick);

        // Render content area background
        guiGraphics.fill(screenX + 10, screenY + 60, screenX + SCREEN_WIDTH - 10, screenY + 430, 0xBB111111);

        // Render current tab content (backgrounds)
        switch (currentTab) {
            case 0 -> statsTab.renderBackgrounds(guiGraphics, screenX + 10, screenY + 65, mouseX, mouseY, partialTick);
            case 1 -> spellTab.renderBackgrounds(guiGraphics, screenX + 10, screenY + 65, mouseX, mouseY, partialTick);
            case 2 -> weaponTab.renderBackgrounds(guiGraphics, screenX + 10, screenY + 65, mouseX, mouseY, partialTick);
        }

        // Render current tab content (text/labels) - before widgets so they don't appear on top
        switch (currentTab) {
            case 0 -> statsTab.renderLabels(guiGraphics, this.font, screenX + 10, screenY + 65, mouseX, mouseY);
            case 1 -> spellTab.renderLabels(guiGraphics, this.font, screenX + 10, screenY + 65, mouseX, mouseY);
            case 2 -> weaponTab.renderLabels(guiGraphics, this.font, screenX + 10, screenY + 65, mouseX, mouseY);
        }

        // Render widgets (buttons, etc.) - after labels so buttons appear on top
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render labels on top
        renderFooter(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Title
        guiGraphics.drawString(this.font, "Character Progression", screenX + 10, screenY + 10, 0xFFFFFF, false);

        // SP Display
        long remainingSP = cart.getRemainingSP();
        String spText = "SP: " + remainingSP;
        int spWidth = this.font.width(spText);
        guiGraphics.drawString(this.font, spText, screenX + SCREEN_WIDTH - 10 - spWidth, screenY + 10, 0xFFFFAA, false);

        // Separator line
        guiGraphics.fill(screenX, screenY + 30, screenX + SCREEN_WIDTH, screenY + 31, 0xFF444444);
    }

    private void renderFooter(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int totalCost = cart.getTotalCost();
        if (totalCost > 0) {
            String costText = "To Consume: " + totalCost + " SP";
            guiGraphics.drawString(this.font, costText, screenX + 10, screenY + 440, 0xFFCC4444, false);
        }

        // Separator line
        guiGraphics.fill(screenX, screenY + 435, screenX + SCREEN_WIDTH, screenY + 436, 0xFF444444);
    }

    private void finalizeUpgrades() {
        if (cart.getTotalCost() == 0) return;

        Map<String, Integer> statsUpgrades = new HashMap<>();
        Map<String, Integer> weaponUpgrades = new HashMap<>();
        List<UpgradeData.MasteryUpgrade> spellMasteryUpgrades = new ArrayList<>();
        List<UpgradeData.SpellPurchase> spellPurchases = new ArrayList<>();
        int originUpgrades = 0;
        int originSkillUpgrades = 0;

        for (com.complextalents.client.UpgradeItem item : cart.getItems()) {
            switch (item.getType()) {
                case STAT:
                    statsUpgrades.put(((StatType) item.getContent()).name(), item.getAmount());
                    break;
                case WEAPON:
                    weaponUpgrades.put(((IWeaponMasteryData.WeaponPath) item.getContent()).name(), item.getAmount());
                    break;
                case SPELL_MASTERY: {
                    String uniqueId = (String) item.getContent();
                    String[] parts = uniqueId.substring(2).split("@");
                    spellMasteryUpgrades.add(new UpgradeData.MasteryUpgrade(
                            net.minecraft.resources.ResourceLocation.parse(parts[0]),
                            Integer.parseInt(parts[1])));
                    break;
                }
                case SPELL_PURCHASE: {
                    String uniqueId = (String) item.getContent();
                    String[] parts = uniqueId.substring(2).split("@");
                    spellPurchases.add(new UpgradeData.SpellPurchase(
                            net.minecraft.resources.ResourceLocation.parse(parts[0]),
                            Integer.parseInt(parts[1])));
                    break;
                }
                case ORIGIN:
                    originUpgrades = item.getAmount();
                    break;
                case SKILL:
                    originSkillUpgrades = item.getAmount();
                    break;
            }
        }

        PacketHandler.sendToServer(new FinalizePlayerUpgradesPacket(
                statsUpgrades,
                weaponUpgrades,
                spellMasteryUpgrades,
                spellPurchases,
                originUpgrades,
                originSkillUpgrades
        ));

        this.onClose();
    }

    @Override
    public void onClose() {
        super.onClose();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
