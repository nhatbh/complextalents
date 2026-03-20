package com.complextalents.client;

import com.complextalents.origin.OriginManager;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class WeaponTabUI {
    private final Player player;
    private final UpgradeCart cart;
    private int currentPage = 0;
    private int totalPages = 0;
    private static final int WEAPONS_PER_PAGE = 3;

    public WeaponTabUI(UpgradeCart cart) {
        this.player = cart.getPlayer();
        this.cart = cart;
    }

    public List<Button> buildWidgets(Screen screen, int xOffset, int yOffset) {
        List<Button> buttons = new ArrayList<>();
        update();

        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
            IWeaponMasteryData.WeaponPath[] allPaths = IWeaponMasteryData.WeaponPath.values();

            int xPos = xOffset + 10;
            int yPos = yOffset + 10;

            int col = 0;
            int row = 0;
            for (int i = 0; i < allPaths.length; i++) {
                IWeaponMasteryData.WeaponPath path = allPaths[i];
                int realCurrentLevel = data.getMasteryLevel(path);
                int pendingPurchases = getPending(path);
                int currentLevel = realCurrentLevel + pendingPurchases;

                double accumulated = data.getAccumulatedDamage(path);
                double requiredDamage = currentLevel < 25 ? WeaponMasteryManager.getInstance().getDamageRequiredForNextLevel(currentLevel) : 0;
                int nextCost = currentLevel < 25 ? getAdjustedWeaponCost(currentLevel) : 0;

                int btnX = xPos + (col * 240);
                int btnY = yPos + (row * 80) + 45;

                // Always show minus button if there are pending purchases
                if (pendingPurchases > 0) {
                    Button cancelBtn = new ColoredButton(btnX + 170, btnY, 20, 20,
                            Component.literal("-"), (btn) -> adjust(path, -1), 0xFFCC4444);
                    buttons.add(cancelBtn);
                }

                // Show plus button or max reached message
                if (currentLevel >= 25) {
                    if (pendingPurchases == 0) {
                        // Only show message if no pending upgrades
                        Button maxBtn = Button.builder(Component.literal("MAX"),
                                (btn) -> {})
                                .pos(btnX + 120, btnY)
                                .size(45, 20)
                                .build();
                        maxBtn.active = false;
                        buttons.add(maxBtn);
                    }
                } else {
                    Button upgradeBtn;
                    if (accumulated >= requiredDamage && cart.canAfford(nextCost)) {
                        upgradeBtn = Button.builder(Component.literal("+"),
                                (btn) -> adjust(path, 1))
                                .pos(btnX + 195, btnY)
                                .size(20, 20)
                                .build();
                    } else {
                        upgradeBtn = Button.builder(Component.literal("+"),
                                (btn) -> {})
                                .pos(btnX + 195, btnY)
                                .size(20, 20)
                                .build();
                        upgradeBtn.active = false;
                    }
                    buttons.add(upgradeBtn);
                }

                col++;
                if (col >= 2) {
                    col = 0;
                    row++;
                }
            }
        });

        return buttons;
    }

    public void update() {
        // Update handled via cart callback
    }

    public void renderBackgrounds(GuiGraphics guiGraphics, int xOffset, int yOffset, int mouseX, int mouseY, float partialTick) {
        // Draw weapon path card backgrounds in 2-column grid
        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
            IWeaponMasteryData.WeaponPath[] allPaths = IWeaponMasteryData.WeaponPath.values();

            int xPos = xOffset + 10;
            int yPos = yOffset + 10;
            int col = 0;
            int row = 0;

            for (int i = 0; i < allPaths.length; i++) {
                IWeaponMasteryData.WeaponPath path = allPaths[i];
                int cardX = xPos + (col * 240);
                int cardY = yPos + (row * 80);

                guiGraphics.fill(cardX, cardY, cardX + 230, cardY + 75, 0xFF333333);
                guiGraphics.fill(cardX + 1, cardY + 1, cardX + 229, cardY + 74, 0xFF222222);

                col++;
                if (col >= 2) {
                    col = 0;
                    row++;
                }
            }
        });
    }

    public void renderLabels(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, int xOffset, int yOffset, int mouseX, int mouseY) {
        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
            IWeaponMasteryData.WeaponPath[] allPaths = IWeaponMasteryData.WeaponPath.values();

            int xPos = xOffset + 10;
            int yPos = yOffset + 10;
            int col = 0;
            int row = 0;

            for (int i = 0; i < allPaths.length; i++) {
                IWeaponMasteryData.WeaponPath path = allPaths[i];
                int cardX = xPos + 5 + (col * 240);
                int cardY = yPos + 5 + (row * 80);

                int realCurrentLevel = data.getMasteryLevel(path);
                int pendingPurchases = getPending(path);
                int currentLevel = realCurrentLevel + pendingPurchases;

                double accumulated = data.getAccumulatedDamage(path);
                double requiredDamage = currentLevel < 25 ? WeaponMasteryManager.getInstance().getDamageRequiredForNextLevel(currentLevel) : 0;
                int nextCost = currentLevel < 25 ? getAdjustedWeaponCost(currentLevel) : 0;

                String tierName = getTierName(currentLevel);
                String tierColor = getTierColor(currentLevel);

                // Path name
                guiGraphics.drawString(font, "§l" + path.name(), cardX, cardY, 0xFFFFFF, false);

                // Tier and level
                String tierText;
                if (pendingPurchases > 0) {
                    tierText = tierColor + tierName + " §7(L." + realCurrentLevel + ") §a(+" + pendingPurchases + ")";
                } else {
                    tierText = tierColor + tierName + " §7(L." + realCurrentLevel + "/25)";
                }
                guiGraphics.drawString(font, tierText, cardX, cardY + 10, 0xFFFFFF, false);

                if (currentLevel >= 25) {
                    guiGraphics.drawString(font, "§aMaximum Reached", cardX, cardY + 25, 0xFF00AA00, false);
                } else {
                    // Progress bar
                    double pct = (accumulated / requiredDamage);
                    if (pct > 1.0) pct = 1.0;

                    int barWidth = 220;
                    guiGraphics.fill(cardX, cardY + 28, cardX + barWidth, cardY + 38, 0xFF222222);
                    guiGraphics.fill(cardX + 1, cardY + 29, cardX + 1 + (int)(barWidth * pct) - 1, cardY + 37, 0xFF22AA22);
                    guiGraphics.drawString(font, "§f" + (int)(pct * 100) + "%", cardX + 2, cardY + 30, 0xFFFFFF, false);

                    // Cost
                    guiGraphics.drawString(font, "§7Cost: §e" + nextCost + " SP", cardX, cardY + 40, 0xFFAAAA, false);
                }

                col++;
                if (col >= 2) {
                    col = 0;
                    row++;
                }
            }

        });
    }


    private int getPending(IWeaponMasteryData.WeaponPath path) {
        return cart.getAmount(UpgradeType.WEAPON, path);
    }

    private void adjust(IWeaponMasteryData.WeaponPath path, int delta) {
        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
            if (delta > 0) {
                int realLevel = data.getMasteryLevel(path);
                int current = getPending(path);
                int targetLevel = realLevel + current;

                // Add one level if conditions are met
                if (targetLevel < 25) {
                    int nextLevelCost = getAdjustedWeaponCost(targetLevel);
                    double damage = data.getAccumulatedDamage(path);
                    double req = WeaponMasteryManager.getInstance().getDamageRequiredForNextLevel(targetLevel);

                    if (cart.canAfford(nextLevelCost) && damage >= req) {
                        cart.modifyItem(UpgradeType.WEAPON, path, 1, nextLevelCost);
                    }
                }
            } else if (delta < 0) {
                int current = getPending(path);
                if (current > 0) {
                    int realLevel = data.getMasteryLevel(path);
                    int removedLevelCost = getAdjustedWeaponCost(realLevel + current - 1);
                    cart.modifyItem(UpgradeType.WEAPON, path, -1, -removedLevelCost);
                }
            }
        });
    }

    private String getTierName(int level) {
        if (level < 5) return "Novice";
        if (level < 10) return "Apprentice";
        if (level < 15) return "Adept";
        if (level < 20) return "Expert";
        return "Master";
    }

    private String getTierColor(int level) {
        if (level < 5) return "§f";
        if (level < 10) return "§a";
        if (level < 15) return "§9";
        if (level < 20) return "§5";
        return "§6";
    }

    /**
     * Get the weapon mastery cost adjusted by the player's origin mastery cost multiplier.
     */
    private int getAdjustedWeaponCost(int currentLevel) {
        // Get origin from cached client data
        ResourceLocation originId = com.complextalents.origin.client.ClientOriginData.getOriginId();

        if (originId != null) {
            return WeaponMasteryManager.getInstance().getSPCostForNextLevel(currentLevel, originId);
        }
        return WeaponMasteryManager.getInstance().getSPCostForNextLevel(currentLevel);
    }
}
