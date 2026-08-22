package com.complextalents.client;

import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class WeaponTabUI {

    private final Player player;
    private final UpgradeCart cart;

    public WeaponTabUI(UpgradeCart cart) {
        this.cart = cart;
        this.player = cart.getPlayer();
    }

    public List<Button> buildWidgets(Screen screen, int xOffset, int yOffset) {
        List<Button> buttons = new ArrayList<>();

        IWeaponMasteryData.WeaponPath[] allPaths = IWeaponMasteryData.WeaponPath.values();

        int xPos = xOffset + 10;
        int yPos = yOffset + 10;
        int col = 0;
        int row = 0;

        for (int i = 0; i < allPaths.length; i++) {
            IWeaponMasteryData.WeaponPath path = allPaths[i];
            int cardX = xPos + (col * 240);
            int cardY = yPos + (row * 58);

            int realCurrentLevel = getRealLevel(path);
            int pendingPurchases = getPending(path);
            int currentLevel = realCurrentLevel + pendingPurchases;

            double accumulated = getAccumulatedDamage(path);
            double requiredDamage = currentLevel < 15 ? WeaponMasteryManager.getInstance().getDamageRequiredForNextLevel(currentLevel) : 0;
            boolean isDamageUnlocked = currentLevel < 15 && accumulated >= requiredDamage;

            int minusX = cardX + 165;
            int plusX = cardX + 195;
            int btnY = cardY + 31;
            int minusW = 26;
            int plusW = 28;

            // Minus Button
            Button minusBtn = Button.builder(Component.literal("-"), (btn) -> adjust(path, -1))
                    .pos(minusX, btnY)
                    .size(minusW, 16)
                    .build();
            minusBtn.active = pendingPurchases > 0;
            buttons.add(minusBtn);

            // Plus Button
            Button upgradeBtn;
            if (currentLevel >= 15) {
                upgradeBtn = Button.builder(Component.literal("MAX"), (btn) -> {})
                        .pos(plusX, btnY)
                        .size(plusW, 16)
                        .build();
                upgradeBtn.active = false;
            } else {
                int nextCost = getAdjustedWeaponCost(currentLevel);
                boolean canAfford = cart.canAfford(nextCost);

                int playerLevel = com.complextalents.leveling.client.ClientLevelingData.getLevel();
                int requiredPlayerLevel = WeaponMasteryManager.getInstance().getRequiredPlayerLevelForTier(currentLevel + 1);
                boolean isLevelUnlocked = playerLevel >= requiredPlayerLevel;

                Component tooltipComponent;
                if (!isLevelUnlocked) {
                    tooltipComponent = Component.literal("\u00A7cRequires Player Level " + requiredPlayerLevel);
                } else if (!isDamageUnlocked) {
                    tooltipComponent = Component.literal("\u00A7cRequires " + (int) requiredDamage + " Accumulated Damage");
                } else if (!canAfford) {
                    tooltipComponent = Component.literal("\u00A7cRequires " + nextCost + " SP");
                } else {
                    tooltipComponent = Component.literal("\u00A7aUpgrade to Level " + (currentLevel + 1) + " (" + nextCost + " SP)");
                }

                if (isDamageUnlocked && canAfford && isLevelUnlocked) {
                    upgradeBtn = Button.builder(Component.literal("+"), (btn) -> adjust(path, 1))
                            .pos(plusX, btnY)
                            .size(plusW, 16)
                            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tooltipComponent))
                            .build();
                    upgradeBtn.active = true;
                } else {
                    upgradeBtn = Button.builder(Component.literal("+"), (btn) -> {})
                            .pos(plusX, btnY)
                            .size(plusW, 16)
                            .tooltip(net.minecraft.client.gui.components.Tooltip.create(tooltipComponent))
                            .build();
                    upgradeBtn.active = false;
                }
            }
            buttons.add(upgradeBtn);

            col++;
            if (col >= 2) {
                col = 0;
                row++;
            }
        }

        return buttons;
    }

    public void update() {
    }

    public void render(GuiGraphics guiGraphics, int xOffset, int yOffset, int mouseX, int mouseY, float partialTick) {
        renderBackgrounds(guiGraphics, xOffset, yOffset, mouseX, mouseY, partialTick);
        renderLabels(guiGraphics, Minecraft.getInstance().font, xOffset, yOffset, mouseX, mouseY);
    }

    public void renderBackgrounds(GuiGraphics guiGraphics, int xOffset, int yOffset, int mouseX, int mouseY, float partialTick) {
        IWeaponMasteryData.WeaponPath[] allPaths = IWeaponMasteryData.WeaponPath.values();

        int xPos = xOffset + 10;
        int yPos = yOffset + 10;
        int col = 0;
        int row = 0;

        for (int i = 0; i < allPaths.length; i++) {
            int cardX = xPos + (col * 240);
            int cardY = yPos + (row * 58);

            IWeaponMasteryData.WeaponPath path = allPaths[i];
            int level = getRealLevel(path) + getPending(path);
            int accentColor = getTierAccentColor(level);

            // Left 3px accent strip
            guiGraphics.fill(cardX, cardY, cardX + 3, cardY + 52, accentColor);
            // Glass inner fill
            guiGraphics.fill(cardX + 3, cardY, cardX + 230, cardY + 52, 0xF0121624);

            col++;
            if (col >= 2) {
                col = 0;
                row++;
            }
        }
    }

    public void renderLabels(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, int xOffset, int yOffset, int mouseX, int mouseY) {
        IWeaponMasteryData.WeaponPath[] allPaths = IWeaponMasteryData.WeaponPath.values();

        int xPos = xOffset + 10;
        int yPos = yOffset + 10;
        int col = 0;
        int row = 0;

        for (int i = 0; i < allPaths.length; i++) {
            IWeaponMasteryData.WeaponPath path = allPaths[i];
            int cardX = xPos + (col * 240);
            int cardY = yPos + (row * 58);

            int realCurrentLevel = getRealLevel(path);
            int pendingPurchases = getPending(path);
            int currentLevel = realCurrentLevel + pendingPurchases;

            double accumulated = getAccumulatedDamage(path);
            double requiredDamage = currentLevel < 15 ? WeaponMasteryManager.getInstance().getDamageRequiredForNextLevel(currentLevel) : 0;
            int nextCost = currentLevel < 15 ? getAdjustedWeaponCost(currentLevel) : 0;

            String tierName = getTierName(currentLevel);
            String tierColor = getTierColor(currentLevel);
            String symbolFormatted = getTierSymbolFormatted(currentLevel);

            // Row 1: Path Title + Tier + Level + Top-Right Progression Icon
            guiGraphics.drawString(font, "\u00A7b\u00A7l" + path.getDisplayName(), cardX + 7, cardY + 3, 0xFF00E5FF, false);
            guiGraphics.drawString(font, tierColor + "[" + tierName + "]", cardX + 95, cardY + 3, 0xFFFFFF, false);

            String lvlText = "\u00A77L." + realCurrentLevel + "/15";
            if (pendingPurchases > 0) lvlText += " \u00A7a(+" + pendingPurchases + ")";
            guiGraphics.drawString(font, lvlText, cardX + 155, cardY + 3, 0xFF888888, false);

            // Top-Right Corner Progression Icon Symbol
            guiGraphics.drawString(font, symbolFormatted, cardX + 216, cardY + 3, 0xFFFFFF, false);

            // Row 2: Stat Gain Summary
            String statLine = formatStatGainSummary(path, realCurrentLevel, pendingPurchases);
            guiGraphics.drawString(font, statLine, cardX + 7, cardY + 16, 0xFFCCCCCC, false);

            // Row 3: Progress Bar + Cost
            int barX = cardX + 7;
            int barY = cardY + 33;
            int barW = 120;
            int barH = 12;

            if (currentLevel >= 15) {
                guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF0E1A18);
                guiGraphics.drawString(font, "\u00A7a\u2714 MASTERED", barX + 22, barY + 2, 0xFF00FF88, false);
            } else {
                double pct = requiredDamage > 0 ? Math.min(accumulated / requiredDamage, 1.0) : 1.0;

                // Progress Frame
                guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF0C101C);
                if (pct > 0) {
                    int fillW = Math.max(2, (int) (barW * pct));
                    int fillColor = getTierAccentColor(currentLevel);
                    guiGraphics.fill(barX, barY, barX + fillW, barY + barH, fillColor);
                }

                String pctStr = (int) (pct * 100) + "%";
                int pctW = font.width(pctStr);
                guiGraphics.drawString(font, "\u00A7f" + pctStr, barX + (barW - pctW) / 2, barY + 2, 0xFFFFFF, false);

                // Cost Label next to progress bar
                guiGraphics.drawString(font, "\u00A7e" + nextCost + " SP", cardX + 134, barY + 2, 0xFFFFAA00, false);
            }

            col++;
            if (col >= 2) {
                col = 0;
                row++;
            }
        }
    }

    private int getRealLevel(IWeaponMasteryData.WeaponPath path) {
        AtomicInteger level = new AtomicInteger(0);
        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
            level.set(data.getMasteryLevel(path));
        });
        return level.get();
    }

    private double getAccumulatedDamage(IWeaponMasteryData.WeaponPath path) {
        AtomicReference<Double> dmg = new AtomicReference<>(0.0);
        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
            dmg.set(data.getAccumulatedDamage(path));
        });
        return dmg.get();
    }

    private int getPending(IWeaponMasteryData.WeaponPath path) {
        return cart.getAmount(UpgradeType.WEAPON, path);
    }

    private void adjust(IWeaponMasteryData.WeaponPath path, int delta) {
        int realLevel = getRealLevel(path);
        int current = getPending(path);

        if (delta > 0) {
            int nextLevel = realLevel + current;
            if (nextLevel < 15) {
                int cost = getAdjustedWeaponCost(nextLevel);
                if (cart.canAfford(cost)) {
                    cart.modifyItem(UpgradeType.WEAPON, path, 1, cost);
                }
            }
        } else if (delta < 0 && current > 0) {
            int removedLevelCost = getAdjustedWeaponCost(realLevel + current - 1);
            cart.modifyItem(UpgradeType.WEAPON, path, -1, -removedLevelCost);
        }
    }

    private int getAdjustedWeaponCost(int currentLevel) {
        ResourceLocation originId = ClientOriginData.getOriginId();
        return WeaponMasteryManager.getInstance().getSPCostForNextLevel(currentLevel, originId);
    }

    private String getTierSymbol(int level) {
        if (level < 2) return "✧";
        if (level < 5) return "✦";
        if (level < 9) return "❖";
        if (level < 14) return "❂";
        return "⚜";
    }

    private String getTierSymbolFormatted(int level) {
        if (level >= 15) {
            return "\u00A76\u00A7l⚜";
        }
        return getTierColor(level) + getTierSymbol(level);
    }

    private String getTierName(int level) {
        if (level < 2) return "Novice";
        if (level < 5) return "Apprentice";
        if (level < 9) return "Adept";
        if (level < 14) return "Expert";
        return "Master";
    }

    private String getTierColor(int level) {
        if (level < 2) return "\u00A7f";
        if (level < 5) return "\u00A7a";
        if (level < 9) return "\u00A79";
        if (level < 14) return "\u00A75";
        return "\u00A76";
    }

    private int getTierAccentColor(int level) {
        if (level < 2) return 0xFF3B82F6; // Cyan blue
        if (level < 5) return 0xFF10B981; // Emerald green
        if (level < 9) return 0xFF6366F1; // Indigo
        if (level < 14) return 0xFFA855F7; // Purple
        return 0xFFF59E0B; // Amber gold
    }

    private String formatStatGainSummary(IWeaponMasteryData.WeaponPath path, int baseLevel, int pending) {
        int targetLevel = baseLevel + pending;
        int baseRanks = WeaponMasteryData.getRanksCompleted(baseLevel);
        int targetRanks = WeaponMasteryData.getRanksCompleted(targetLevel);

        return switch (path.name()) {
            case "BLADEMASTER" -> {
                double baseAd = (baseLevel * 1.5) + (baseLevel >= 15 ? 5.0 : 0);
                double targetAd = (targetLevel * 1.5) + (targetLevel >= 15 ? 5.0 : 0);
                double baseAs = (baseRanks * 4.0) + (baseLevel >= 15 ? 15.0 : 0);
                double targetAs = (targetRanks * 4.0) + (targetLevel >= 15 ? 15.0 : 0);
                double baseMs = (baseRanks * 3.0) + (baseLevel >= 15 ? 10.0 : 0);
                double targetMs = (targetRanks * 3.0) + (targetLevel >= 15 ? 10.0 : 0);
                yield fmtStat("AD", baseAd, targetAd, false) + " " +
                        fmtStat("AS", baseAs, targetAs, true) + " " +
                        fmtStat("MS", baseMs, targetMs, true);
            }
            case "COLOSSUS" -> {
                double baseAd = (baseLevel * 6.0) + (baseLevel >= 15 ? 25.0 : 0);
                double targetAd = (targetLevel * 6.0) + (targetLevel >= 15 ? 25.0 : 0);
                double baseHp = (baseRanks * 4.0) + (baseLevel >= 15 ? 10.0 : 0);
                double targetHp = (targetRanks * 4.0) + (targetLevel >= 15 ? 10.0 : 0);
                double baseSw = (baseRanks * 6.0) + (baseLevel >= 15 ? 15.0 : 0);
                double targetSw = (targetRanks * 6.0) + (targetLevel >= 15 ? 15.0 : 0);
                yield fmtStat("AD", baseAd, targetAd, true) + " " +
                        fmtStat("HP", baseHp, targetHp, false) + " " +
                        fmtStat("Sweep", baseSw, targetSw, true);
            }
            case "VANGUARD" -> {
                double baseAd = (baseLevel * 4.0) + (baseLevel >= 15 ? 20.0 : 0);
                double targetAd = (targetLevel * 4.0) + (targetLevel >= 15 ? 20.0 : 0);
                double baseMs = (baseLevel * 2.5) + (baseLevel >= 15 ? 10.0 : 0);
                double targetMs = (targetLevel * 2.5) + (targetLevel >= 15 ? 10.0 : 0);
                double baseHp = (baseRanks * 4.0);
                double targetHp = (targetRanks * 4.0);
                yield fmtStat("AD", baseAd, targetAd, true) + " " +
                        fmtStat("MS", baseMs, targetMs, true) + " " +
                        fmtStat("HP", baseHp, targetHp, false);
            }
            case "REAPER" -> {
                double baseCrit = (baseLevel * 3.0) + (baseLevel >= 15 ? 15.0 : 0);
                double targetCrit = (targetLevel * 3.0) + (targetLevel >= 15 ? 15.0 : 0);
                double baseCd = (baseRanks * 12.0) + (baseLevel >= 15 ? 35.0 : 0);
                double targetCd = (targetRanks * 12.0) + (targetLevel >= 15 ? 35.0 : 0);
                double baseAd = (baseRanks * 1.0);
                double targetAd = (targetRanks * 1.0);
                yield fmtStat("Crit", baseCrit, targetCrit, true) + " " +
                        fmtStat("CritD", baseCd, targetCd, true) + " " +
                        fmtStat("AD", baseAd, targetAd, false);
            }
            case "JUGGERNAUT" -> {
                double baseAp = (baseLevel * 3.0) + (baseLevel >= 15 ? 20.0 : 0);
                double targetAp = (targetLevel * 3.0) + (targetLevel >= 15 ? 20.0 : 0);
                double baseAd = (baseRanks * 6.0);
                double targetAd = (targetRanks * 6.0);
                double baseKb = (baseRanks * 12.0) + (baseLevel >= 15 ? 25.0 : 0);
                double targetKb = (targetRanks * 12.0) + (targetLevel >= 15 ? 25.0 : 0);
                yield fmtStat("ArPen", baseAp, targetAp, false) + " " +
                        fmtStat("AD", baseAd, targetAd, true) + " " +
                        fmtStat("KB", baseKb, targetKb, true);
            }
            case "BRAWLER" -> {
                double baseAd = (baseLevel * 1.0) + (baseLevel >= 15 ? 6.0 : 0);
                double targetAd = (targetLevel * 1.0) + (targetLevel >= 15 ? 6.0 : 0);
                double baseAs = (baseRanks * 4.0) + (baseLevel >= 15 ? 20.0 : 0);
                double targetAs = (targetRanks * 4.0) + (targetLevel >= 15 ? 20.0 : 0);
                double baseMs = (baseRanks * 3.0) + (baseLevel >= 15 ? 15.0 : 0);
                double targetMs = (targetRanks * 3.0) + (targetLevel >= 15 ? 15.0 : 0);
                yield fmtStat("AD", baseAd, targetAd, false) + " " +
                        fmtStat("AS", baseAs, targetAs, true) + " " +
                        fmtStat("MS", baseMs, targetMs, true);
            }
            default -> "";
        };
    }

    private String fmtStat(String name, double cur, double target, boolean isPct) {
        String suffix = isPct ? "%" : "";
        if (target > cur) {
            double diff = target - cur;
            return "\u00A77" + name + ":\u00A7f" + fmtVal(cur) + suffix + "\u00A7a+" + fmtVal(diff) + suffix;
        }
        return "\u00A77" + name + ":\u00A7f" + fmtVal(cur) + suffix;
    }

    private String fmtVal(double val) {
        if (val == (long) val) {
            return String.format("%d", (long) val);
        }
        return String.format("%.1f", val);
    }
}
