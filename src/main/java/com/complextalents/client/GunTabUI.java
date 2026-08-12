package com.complextalents.client;

import com.complextalents.gunmastery.GunMasteryManager;
import com.complextalents.gunmastery.capability.IGunMasteryData;
import com.complextalents.gunmastery.capability.GunMasteryDataProvider;
import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.tacz.GunType;
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

public class GunTabUI {

    private static final GunType[] ARCHETYPES = new GunType[]{
            GunType.PISTOL, GunType.SNIPER, GunType.RIFLE,
            GunType.SHOTGUN, GunType.SMG, GunType.MG
    };

    private final Player player;
    private final UpgradeCart cart;

    public GunTabUI(UpgradeCart cart) {
        this.cart = cart;
        this.player = cart.getPlayer();
    }

    public List<Button> buildWidgets(Screen screen, int xOffset, int yOffset) {
        List<Button> buttons = new ArrayList<>();

        ResourceLocation originId = ClientOriginData.getOriginId();
        boolean originAllowed = ClassCostMatrix.getGunMasteryCostMultiplier(originId) >= 0;

        int xPos = xOffset + 10;
        int yPos = yOffset + 10;
        int col = 0;
        int row = 0;

        for (GunType type : ARCHETYPES) {
            int cardX = xPos + (col * 240);
            int cardY = yPos + (row * 58);

            int realCurrentLevel = getRealLevel(type);
            int pendingPurchases = getPending(type);
            int currentLevel = realCurrentLevel + pendingPurchases;
            int maxLevel = GunMasteryManager.getInstance().getMaxLevel(type);

            double accumulated = getAccumulatedDamage(type);
            double requiredDamage = currentLevel < maxLevel ? GunMasteryManager.getInstance().getDamageRequiredForNextLevel(currentLevel) : 0;
            boolean isDamageUnlocked = currentLevel < maxLevel && accumulated >= requiredDamage;

            boolean archetypeUnlocked = isArchetypeUnlocked(type);

            int minusX = cardX + 165;
            int plusX = cardX + 195;
            int btnY = cardY + 31;
            int minusW = 26;
            int plusW = 28;

            // Minus Button
            Button minusBtn = Button.builder(Component.literal("-"), (btn) -> adjust(type, -1))
                    .pos(minusX, btnY)
                    .size(minusW, 16)
                    .build();
            minusBtn.active = pendingPurchases > 0;
            buttons.add(minusBtn);

            // Plus Button
            Button upgradeBtn;
            if (currentLevel >= maxLevel) {
                upgradeBtn = Button.builder(Component.literal("MAX"), (btn) -> {})
                        .pos(plusX, btnY)
                        .size(plusW, 16)
                        .build();
                upgradeBtn.active = false;
            } else {
                int nextCost = getAdjustedGunCost(type, currentLevel);
                boolean canAfford = cart.canAfford(nextCost);

                int playerLevel = com.complextalents.leveling.client.ClientLevelingData.getLevel();
                int requiredPlayerLevel = GunMasteryManager.getInstance().getRequiredPlayerLevelForTier(currentLevel + 1);
                boolean isLevelUnlocked = playerLevel >= requiredPlayerLevel;

                Component tooltipComponent;
                if (!archetypeUnlocked) {
                    tooltipComponent = Component.literal("\u00A7cRequires Pistol Level 5 (Trooper Rank)");

                } else if (!isLevelUnlocked) {
                    tooltipComponent = Component.literal("\u00A7cRequires Player Level " + requiredPlayerLevel);
                } else if (!isDamageUnlocked) {
                    tooltipComponent = Component.literal("\u00A7cRequires " + (int) requiredDamage + " Accumulated Damage");
                } else if (!canAfford) {
                    tooltipComponent = Component.literal("\u00A7cRequires " + nextCost + " SP");
                } else {
                    tooltipComponent = Component.literal("\u00A7aUpgrade to Level " + (currentLevel + 1) + " (" + nextCost + " SP)");
                }

                if (originAllowed && archetypeUnlocked && isDamageUnlocked && canAfford && isLevelUnlocked) {
                    upgradeBtn = Button.builder(Component.literal("+"), (btn) -> adjust(type, 1))
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

    public void renderBackgrounds(GuiGraphics guiGraphics, int xOffset, int yOffset, int mouseX, int mouseY, float partialTick) {
        int xPos = xOffset + 10;
        int yPos = yOffset + 10;
        int col = 0;
        int row = 0;

        for (GunType type : ARCHETYPES) {
            int cardX = xPos + (col * 240);
            int cardY = yPos + (row * 58);

            int level = getRealLevel(type) + getPending(type);
            int accentColor = getTierAccentColor(type, level);

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
        int xPos = xOffset + 10;
        int yPos = yOffset + 10;

        int col = 0;
        int row = 0;

        for (GunType type : ARCHETYPES) {
            int cardX = xPos + (col * 240);
            int cardY = yPos + (row * 58);

            int realCurrentLevel = getRealLevel(type);
            int pendingPurchases = getPending(type);
            int currentLevel = realCurrentLevel + pendingPurchases;
            int maxLevel = GunMasteryManager.getInstance().getMaxLevel(type);

            double accumulated = getAccumulatedDamage(type);
            double requiredDamage = currentLevel < maxLevel ? GunMasteryManager.getInstance().getDamageRequiredForNextLevel(currentLevel) : 0;
            int nextCost = currentLevel < maxLevel ? getAdjustedGunCost(type, currentLevel) : 0;

            boolean archetypeUnlocked = isArchetypeUnlocked(type);
            String tierName = getTierName(type, currentLevel);
            String tierColor = getTierColor(type, currentLevel);
            String symbolFormatted = getTierSymbolFormatted(type, currentLevel);

            // Row 1: Type Title + Tier + Level + Top-Right Progression Icon
            String typeName = type.getDisplayName();
            guiGraphics.drawString(font, "\u00A76\u00A7l" + typeName, cardX + 7, cardY + 3, 0xFFFFAA00, false);

            if (archetypeUnlocked) {
                guiGraphics.drawString(font, tierColor + "[" + tierName + "]", cardX + 95, cardY + 3, 0xFFFFFF, false);
            } else {
                guiGraphics.drawString(font, "\u00A7c[LOCKED]", cardX + 95, cardY + 3, 0xFF5555, false);
            }

            String lvlText = "\u00A77L." + realCurrentLevel + "/" + maxLevel;
            if (pendingPurchases > 0) lvlText += " \u00A7a(+" + pendingPurchases + ")";
            guiGraphics.drawString(font, lvlText, cardX + 155, cardY + 3, 0xFF888888, false);

            // Top-Right Corner Progression Icon Symbol
            guiGraphics.drawString(font, symbolFormatted, cardX + 216, cardY + 3, 0xFFFFFF, false);

            // Row 2: Stat Gain Summary
            String statLine = formatStatGainSummary(type, currentLevel);
            guiGraphics.drawString(font, statLine, cardX + 7, cardY + 16, 0xFFCCCCCC, false);

            // Row 3: Progress Bar + Cost
            int barX = cardX + 7;
            int barY = cardY + 33;
            int barW = 120;
            int barH = 12;

            if (currentLevel >= maxLevel) {
                guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF0E1A18);
                guiGraphics.drawString(font, "\u00A7a\u2714 MASTERED", barX + 22, barY + 2, 0xFF00FF88, false);
            } else if (!archetypeUnlocked) {
                guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF1C0C0C);
                guiGraphics.drawString(font, "\u00A7cReq. Pistol L.5", barX + 10, barY + 2, 0xFFFF5555, false);
            } else {
                double pct = requiredDamage > 0 ? Math.min(accumulated / requiredDamage, 1.0) : 1.0;

                // Progress Frame
                guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF0C101C);
                if (pct > 0) {
                    int fillW = Math.max(2, (int) (barW * pct));
                    int fillColor = getTierAccentColor(type, currentLevel);
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

    private int getRealLevel(GunType type) {
        AtomicInteger level = new AtomicInteger(0);
        player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
            level.set(data.getMasteryLevel(type));
        });
        return level.get();
    }

    private double getAccumulatedDamage(GunType type) {
        AtomicReference<Double> dmg = new AtomicReference<>(0.0);
        player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
            dmg.set(data.getAccumulatedDamage(type));
        });
        return dmg.get();
    }

    private boolean isArchetypeUnlocked(GunType type) {
        if (type == GunType.PISTOL) return true;
        AtomicReference<Boolean> unlocked = new AtomicReference<>(false);
        player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
            unlocked.set(GunMasteryManager.getInstance().canUnlockArchetype(type, data));
        });
        return unlocked.get();
    }

    private int getPending(GunType type) {
        return cart.getAmount(UpgradeType.GUN, type);
    }

    private void adjust(GunType type, int delta) {
        int realLevel = getRealLevel(type);
        int current = getPending(type);
        int maxLevel = GunMasteryManager.getInstance().getMaxLevel(type);

        if (delta > 0) {
            int nextLevel = realLevel + current;
            if (nextLevel < maxLevel) {
                int cost = getAdjustedGunCost(type, nextLevel);
                if (cart.canAfford(cost)) {
                    cart.modifyItem(UpgradeType.GUN, type, 1, cost);
                }
            }
        } else if (delta < 0 && current > 0) {
            int removedLevelCost = getAdjustedGunCost(type, realLevel + current - 1);
            cart.modifyItem(UpgradeType.GUN, type, -1, -removedLevelCost);
        }
    }

    private int getAdjustedGunCost(GunType type, int currentLevel) {
        ResourceLocation originId = ClientOriginData.getOriginId();
        AtomicInteger cost = new AtomicInteger(0);
        player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
            cost.set(GunMasteryManager.getInstance().getSPCostForNextLevel(type, currentLevel, data, originId));
        });
        return cost.get();
    }

    private String getTierSymbol(GunType type, int level) {
        if (type == GunType.PISTOL) {
            if (level < 5) return "✧";
            if (level < 9) return "✦";
            if (level < 13) return "❖";
            return "❂";
        } else {
            if (level < 6) return "✦";
            if (level < 11) return "❖";
            if (level < 16) return "❂";
            return "⚜";
        }
    }

    private String getTierSymbolFormatted(GunType type, int level) {
        int maxLevel = GunMasteryManager.getInstance().getMaxLevel(type);
        if (level >= maxLevel) {
            return "\u00A76\u00A7l⚜";
        }
        return getTierColor(type, level) + getTierSymbol(type, level);
    }

    private String getTierName(GunType type, int level) {
        if (type == GunType.PISTOL) {
            if (level < 5) return "Recruit";
            if (level < 9) return "Trooper";
            if (level < 13) return "Sergeant";
            return "Captain";
        } else {
            if (level < 6) return "Trooper";
            if (level < 11) return "Sergeant";
            if (level < 16) return "Captain";
            return "General";
        }
    }

    private String getTierColor(GunType type, int level) {
        if (type == GunType.PISTOL) {
            if (level < 5) return "\u00A77";
            if (level < 9) return "\u00A7a";
            if (level < 13) return "\u00A79";
            return "\u00A7d";
        } else {
            if (level < 6) return "\u00A7a";
            if (level < 11) return "\u00A79";
            if (level < 16) return "\u00A7d";
            return "\u00A76";
        }
    }

    private int getTierAccentColor(GunType type, int level) {
        if (type == GunType.PISTOL) {
            if (level < 5) return 0xFF888888;
            if (level < 9) return 0xFF55FF55;
            if (level < 13) return 0xFF5555FF;
            return 0xFFFF55FF;
        } else {
            if (level < 6) return 0xFF55FF55;
            if (level < 11) return 0xFF5555FF;
            if (level < 16) return 0xFFFF55FF;
            return 0xFFFFAA00;
        }
    }

    private String formatStatGainSummary(GunType type, int level) {
        if (level <= 0) return "\u00A78No mastery bonuses yet";

        double dmgBonus = GunMasteryManager.getInstance().getStatBonus(type, com.complextalents.tacz.GunAttributeType.GUN_DAMAGE, level);
        double primaryBonus = 0.0;
        String primaryName = "";
        double secondaryBonus = 0.0;
        String secondaryName = "";

        switch (type) {
            case PISTOL -> {
                primaryBonus = GunMasteryManager.getInstance().getStatBonus(type, com.complextalents.tacz.GunAttributeType.RELOAD_SPEED, level);
                primaryName = "Reload Speed";
            }
            case SNIPER -> {
                primaryBonus = GunMasteryManager.getInstance().getStatBonus(type, com.complextalents.tacz.GunAttributeType.HEADSHOT_MULTIPLIER, level);
                primaryName = "Headshot Dmg";
                secondaryBonus = GunMasteryManager.getInstance().getStatBonus(type, com.complextalents.tacz.GunAttributeType.PIERCE_MULTIPLIER, level);
                secondaryName = "Pierce";
            }
            case RIFLE -> {
                primaryBonus = GunMasteryManager.getInstance().getStatBonus(type, com.complextalents.tacz.GunAttributeType.FORTITUDE, level);
                primaryName = "Fortitude";
            }
            case SHOTGUN -> {
                primaryBonus = GunMasteryManager.getInstance().getStatBonus(type, com.complextalents.tacz.GunAttributeType.HIP_FIRE_DAMAGE, level);
                primaryName = "Hipfire Dmg";
                secondaryBonus = GunMasteryManager.getInstance().getStatBonus(type, com.complextalents.tacz.GunAttributeType.AMMO_SAVE_CHANCE, level);
                secondaryName = "Ammo Save";
            }
            case SMG -> {
                primaryBonus = GunMasteryManager.getInstance().getStatBonus(type, com.complextalents.tacz.GunAttributeType.RPM_MULTIPLIER, level);
                primaryName = "Fire Rate";
            }
            case MG -> {
                primaryBonus = GunMasteryManager.getInstance().getStatBonus(type, com.complextalents.tacz.GunAttributeType.MAGAZINE_CAPACITY, level);
                primaryName = "Mag Capacity";
            }
        }

        int dmgPct = (int) Math.round(dmgBonus * 100);
        int priPct = (int) Math.round(primaryBonus * 100);
        int secPct = (int) Math.round(secondaryBonus * 100);

        StringBuilder sb = new StringBuilder();
        if (dmgPct > 0) {
            sb.append("\u00A7a+").append(dmgPct).append("% Dmg ");
        }
        if (priPct > 0) {
            if (sb.length() > 0) sb.append("\u00A77| ");
            sb.append("\u00A7b+").append(priPct).append("% ").append(primaryName).append(" ");
        }
        if (secPct > 0) {
            if (sb.length() > 0) sb.append("\u00A77| ");
            sb.append("\u00A7d+").append(secPct).append("% ").append(secondaryName);
        }
        return sb.toString().trim();
    }

}
