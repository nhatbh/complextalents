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

        // Build Ammo Table button (opens TACZ ammo assembly table)
        ResourceLocation ammoTableBlockId = ResourceLocation.fromNamespaceAndPath("tacz", "ammo_workbench");
        Button ammoTableBtn = Button.builder(Component.empty(),
                (btn) -> com.complextalents.network.PacketHandler.sendToServer(new com.complextalents.network.C2SOpenGunTablePacket(ammoTableBlockId)))
                .pos(xOffset + 438, yOffset + 5)
                .size(20, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Open Ammo Assembly Table")))
                .build();
        buttons.add(ammoTableBtn);

        // Build Gun Table button (opens TACZ gun smith table)
        ResourceLocation gunTableBlockId = ResourceLocation.fromNamespaceAndPath("tacz", "gun_smith_table");
        Button gunTableBtn = Button.builder(Component.empty(),
                (btn) -> com.complextalents.network.PacketHandler.sendToServer(new com.complextalents.network.C2SOpenGunTablePacket(gunTableBlockId)))
                .pos(xOffset + 464, yOffset + 5)
                .size(20, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Open Gun Table")))
                .build();
        buttons.add(gunTableBtn);

        ResourceLocation originId = ClientOriginData.getOriginId();
        boolean originAllowed = ClassCostMatrix.getGunMasteryCostMultiplier(originId) >= 0;

        int xPos = xOffset + 10;
        int yPos = yOffset + 35;
        int col = 0;
        int row = 0;

        for (GunType type : ARCHETYPES) {
            int cardX = xPos + (col * 240);
            int cardY = yPos + (row * 58);

            int realCurrentLevel = getRealLevel(type);
            int pendingPurchases = getPending(type);
            int currentLevel = getEffectiveLevel(type, realCurrentLevel, pendingPurchases);
            int maxLevel = GunMasteryManager.getInstance().getMaxLevel(type);

            double accumulated = getAccumulatedDamage(type);
            double requiredDamage = currentLevel < maxLevel ? GunMasteryManager.getInstance().getDamageRequiredForNextLevel(type, currentLevel) : 0;
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
                int nextTargetLevel = GunMasteryManager.getInstance().getNextLevel(type, currentLevel);
                int nextCost = getAdjustedGunCost(type, currentLevel);
                boolean canAfford = cart.canAfford(nextCost);

                int playerLevel = com.complextalents.leveling.client.ClientLevelingData.getLevel();
                int requiredPlayerLevel = GunMasteryManager.getInstance().getRequiredPlayerLevelForTier(nextTargetLevel);
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
                    tooltipComponent = Component.literal("\u00A7aUpgrade to Level " + nextTargetLevel + " (" + nextCost + " SP)");
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
        // Header background for workbench table access buttons
        guiGraphics.fill(xOffset + 10, yOffset - 5, xOffset + 490, yOffset + 28, 0xFF282C3D);
        guiGraphics.fill(xOffset + 11, yOffset - 4, xOffset + 489, yOffset + 27, 0xFF141724);

        int xPos = xOffset + 10;
        int yPos = yOffset + 35;
        int col = 0;
        int row = 0;

        for (GunType type : ARCHETYPES) {
            int cardX = xPos + (col * 240);
            int cardY = yPos + (row * 58);

            int realCurrentLevel = getRealLevel(type);
            int pendingPurchases = getPending(type);
            int level = getEffectiveLevel(type, realCurrentLevel, pendingPurchases);
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
        // Header title
        guiGraphics.drawString(font, "\u00A76\u00A7lGun Mastery & Equipment Tables", xOffset + 18, yOffset + 8, 0xFFFFAA00, false);

        // Render item icons for Ammo Table and Gun Table buttons
        net.minecraft.world.item.Item ammoItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("tacz", "workbench_a"));
        if (ammoItem != null && ammoItem != net.minecraft.world.item.Items.AIR) {
            net.minecraft.world.item.ItemStack ammoStack = new net.minecraft.world.item.ItemStack(ammoItem);
            ammoStack.getOrCreateTag().putString("BlockId", "tacz:ammo_workbench");
            guiGraphics.renderItem(ammoStack, xOffset + 440, yOffset + 7);
        }

        ResourceLocation gunBlockId = ResourceLocation.fromNamespaceAndPath("tacz", "gun_smith_table");
        net.minecraft.world.item.Item gunItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(gunBlockId);
        if (gunItem != null && gunItem != net.minecraft.world.item.Items.AIR) {
            guiGraphics.renderItem(new net.minecraft.world.item.ItemStack(gunItem), xOffset + 466, yOffset + 7);
        }

        int xPos = xOffset + 10;
        int yPos = yOffset + 35;

        int col = 0;
        int row = 0;

        for (GunType type : ARCHETYPES) {
            int cardX = xPos + (col * 240);
            int cardY = yPos + (row * 58);

            int realCurrentLevel = getRealLevel(type);
            int pendingPurchases = getPending(type);
            int currentLevel = getEffectiveLevel(type, realCurrentLevel, pendingPurchases);
            int maxLevel = GunMasteryManager.getInstance().getMaxLevel(type);

            double accumulated = getAccumulatedDamage(type);
            double requiredDamage = currentLevel < maxLevel ? GunMasteryManager.getInstance().getDamageRequiredForNextLevel(type, currentLevel) : 0;
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
            if (pendingPurchases > 0) lvlText += " \u00A7a(+" + (currentLevel - realCurrentLevel) + ")";
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
                String lockMsg = getUnlockLockMessage(type);
                guiGraphics.drawString(font, lockMsg, barX + 10, barY + 2, 0xFFFF5555, false);
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
        int originLevel = ClientOriginData.getOriginLevel();
        player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
            unlocked.set(GunMasteryManager.getInstance().canUnlockArchetype(type, data, originLevel));
        });
        return unlocked.get();
    }

    private String getUnlockLockMessage(GunType type) {
        if (type == GunType.PISTOL) return "";
        int pistolLvl = getRealLevel(GunType.PISTOL);
        if (pistolLvl < 5) return "\u00A7cReq. Pistol L.5";
        int originLevel = ClientOriginData.getOriginLevel();
        if (originLevel < 3) return "\u00A7cReq. Origin L.3";
        if (originLevel < 5) return "\u00A7cReq. Origin L.5";
        return "\u00A7cMax Slots (3/3)";
    }

    private int getPending(GunType type) {
        return cart.getAmount(UpgradeType.GUN, type);
    }

    private int getEffectiveLevel(GunType type, int realLevel, int pendingPurchases) {
        if (pendingPurchases <= 0) return realLevel;
        if (type != GunType.PISTOL && realLevel == 0) {
            return 4 + pendingPurchases;
        }
        return realLevel + pendingPurchases;
    }

    private java.util.Map<GunType, Integer> getEffectiveMasteryLevelsMap() {
        java.util.Map<GunType, Integer> map = new java.util.HashMap<>();
        player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
            map.putAll(data.getAllMasteryLevels());
        });
        for (GunType t : ARCHETYPES) {
            int realLvl = map.getOrDefault(t, 0);
            int pending = getPending(t);
            map.put(t, getEffectiveLevel(t, realLvl, pending));
        }
        return map;
    }

    private void adjust(GunType type, int delta) {
        int realLevel = getRealLevel(type);
        int currentPending = getPending(type);
        int maxLevel = GunMasteryManager.getInstance().getMaxLevel(type);

        int currentEffectiveLevel = getEffectiveLevel(type, realLevel, currentPending);

        if (delta > 0) {
            if (currentEffectiveLevel < maxLevel) {
                int cost = getAdjustedGunCost(type, currentEffectiveLevel);
                if (cart.canAfford(cost)) {
                    cart.modifyItem(UpgradeType.GUN, type, 1, cost);
                }
            }
        } else if (delta < 0 && currentPending > 0) {
            int levelBeforeThisStep = getEffectiveLevel(type, realLevel, currentPending - 1);
            int removedLevelCost = getAdjustedGunCost(type, levelBeforeThisStep);
            cart.modifyItem(UpgradeType.GUN, type, -1, -removedLevelCost);
        }
    }

    private int getAdjustedGunCost(GunType type, int currentLevel) {
        ResourceLocation originId = ClientOriginData.getOriginId();
        java.util.Map<GunType, Integer> effectiveMap = getEffectiveMasteryLevelsMap();
        return GunMasteryManager.getInstance().getSPCostForNextLevel(type, currentLevel, effectiveMap, originId);
    }

    private String getTierSymbol(GunType type, int level) {
        if (level <= 0) return "✧";
        if (level <= 4) return "✧";
        if (level <= 8) return "✦";
        if (level <= 12) return "❖";
        if (level <= 16) return "❂";
        return "⚜";
    }

    private String getTierSymbolFormatted(GunType type, int level) {
        int maxLevel = GunMasteryManager.getInstance().getMaxLevel(type);
        if (level >= maxLevel) {
            return "\u00A76\u00A7l⚜";
        }
        return getTierColor(type, level) + getTierSymbol(type, level);
    }

    private String getTierName(GunType type, int level) {
        if (level <= 0) return "Unlearned";
        if (level <= 4) return "Recruit";
        if (level <= 8) return "Trooper";
        if (level <= 12) return "Sergeant";
        if (level <= 16) return "Captain";
        return "General";
    }

    private String getTierColor(GunType type, int level) {
        if (level <= 0) return "\u00A78"; // Dark Gray
        if (level <= 4) return "\u00A7f"; // White
        if (level <= 8) return "\u00A7a"; // Green
        if (level <= 12) return "\u00A79"; // Blue
        if (level <= 16) return "\u00A75"; // Purple
        return "\u00A76";                 // Gold
    }

    private int getTierAccentColor(GunType type, int level) {
        if (level <= 0) return 0xFF4B5563;
        if (level <= 4) return 0xFF3B82F6; // Blue
        if (level <= 8) return 0xFF10B981; // Green
        if (level <= 12) return 0xFF6366F1; // Indigo
        if (level <= 16) return 0xFFA855F7; // Purple
        return 0xFFF59E0B;                 // Amber Gold
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

        String dmgName = switch (type) {
            case PISTOL -> "Pistol Dmg";
            case RIFLE -> "Rifle Dmg";
            case SNIPER -> "Sniper Dmg";
            case SHOTGUN -> "Shotgun Dmg";
            case SMG -> "SMG Dmg";
            case MG -> "MG Dmg";
            default -> "Gun Dmg";
        };

        StringBuilder sb = new StringBuilder();
        if (dmgPct > 0) {
            sb.append("\u00A7a+").append(dmgPct).append("% ").append(dmgName).append(" ");
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
