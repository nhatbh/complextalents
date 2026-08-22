package com.complextalents.client;

import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import com.complextalents.stats.capability.GeneralStatsDataProvider;
import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginManager;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.skill.Skill;
import com.complextalents.skill.SkillRegistry;
import com.complextalents.skill.client.ClientSkillData;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class StatsTabUI {
    private final Player player;
    private final UpgradeCart cart;
    private int currentPage = 0;
    private int totalPages = 0;
    private static final int STAT_ENTRY_HEIGHT = 35;
    private static final int STATS_PER_ROW = 1;
    private static final int STAT_ENTRY_WIDTH = 145;
    private static final int STAT_CARD_WIDTH = 150;
    private static final int UPGRADE_CARD_WIDTH = 330;
    private static final int UPGRADE_CARD_HEIGHT = 140;

    // Store rendering data
    private List<StatEntryData> statEntries = new ArrayList<>();

    public static class StatEntryData {
        public StatType type;
        public int row;
        public int col;
        public Button addBtn;
        public Button removeBtn;
        public int realRank;
        public int pendingRank;
        public int costPerRank;
    }

    public StatsTabUI(UpgradeCart cart) {
        this.cart = cart;
        this.player = cart.getPlayer();
    }

    public List<Button> buildWidgets(Screen screen, int xOffset, int yOffset) {
        List<Button> buttons = new ArrayList<>();
        update();

        // Build Refining Anvil button (opens Refining Anvil menu directly)
        Button anvilBtn = Button.builder(Component.empty(),
                (btn) -> com.complextalents.network.PacketHandler.sendToServer(new com.complextalents.network.C2SOpenRefiningAnvilPacket()))
                .pos(xOffset + 465, yOffset + 9)
                .size(20, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Open Refining Anvil")))
                .build();
        buttons.add(anvilBtn);

        // Stats on left side
        for (StatEntryData entry : statEntries) {
            int entryX = xOffset + 5;
            int entryY = yOffset + (entry.row * STAT_ENTRY_HEIGHT) + 5;

            if (entry.pendingRank > 0) {
                Button removeBtn = new ColoredButton(entryX + 115, entryY + 15, 14, 14,
                        Component.literal("-"), (btn) -> adjust(entry.type, -1), 0xFFCC4444);
                buttons.add(removeBtn);
            }

            Button addBtn = Button.builder(Component.literal("+"),
                    (btn) -> adjust(entry.type, 1))
                    .pos(entryX + 132, entryY + 15)
                    .size(14, 14)
                    .build();
            addBtn.active = cart.canAfford(entry.costPerRank);
            buttons.add(addBtn);

            entry.addBtn = addBtn;
        }

        // Origin upgrade card on right side
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId != null) {
            Origin origin = OriginRegistry.getInstance().getOrigin(originId);
            if (origin != null) {
                int originLevel = ClientOriginData.getOriginLevel();
                int originPending = cart.getAmount(UpgradeType.ORIGIN, "origin");
                int originTarget = originLevel + originPending;
                int originCost = OriginManager.getCostForNextLevel(originTarget);

                int rightX = xOffset + 160;
                int rightY = yOffset + 5;

                if (originTarget < origin.getMaxLevel()) {
                    if (originPending > 0) {
                        Button cancelOrigin = new ColoredButton(rightX + 285, rightY + 120, 14, 14,
                                Component.literal("-"), (btn) -> adjustOrigin(-1), 0xFFCC4444);
                        buttons.add(cancelOrigin);
                    }

                    Button addOrigin = Button.builder(Component.literal("+"),
                            (btn) -> adjustOrigin(1))
                            .pos(rightX + 301, rightY + 120)
                            .size(14, 14)
                            .build();
                    addOrigin.active = cart.canAfford(originCost);
                    buttons.add(addOrigin);
                } else if (originPending > 0) {
                    // Show rollback button when origin is at max level but has pending upgrades
                    Button cancelOrigin = new ColoredButton(rightX + 285, rightY + 120, 14, 14,
                            Component.literal("-"), (btn) -> adjustOrigin(-1), 0xFFCC4444);
                    buttons.add(cancelOrigin);
                }
            }
        }

        // Skill upgrade card on right side
        ResourceLocation originId2 = ClientOriginData.getOriginId();
        if (originId2 != null) {
            Origin origin = OriginRegistry.getInstance().getOrigin(originId2);
            if (origin != null) {
                ResourceLocation activeSkillId = origin.getActiveSkillId();
                if (activeSkillId != null) {
                    Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
                    if (skill != null) {
                        int skillLevel = getActiveSkillLevel();
                        int skillPending = cart.getAmount(UpgradeType.SKILL, activeSkillId);
                        int skillTarget = skillLevel + skillPending;
                        int skillCost = OriginManager.getSkillCostForNextLevel(skillTarget);

                        int rightX = xOffset + 160;
                        int rightY = yOffset + 155;

                        if (skillTarget < skill.getMaxLevel()) {
                            if (skillPending > 0) {
                                Button cancelSkill = new ColoredButton(rightX + 285, rightY + 120, 14, 14,
                                        Component.literal("-"), (btn) -> adjustSkill(-1), 0xFFCC4444);
                                buttons.add(cancelSkill);
                            }

                            Button addSkill = Button.builder(Component.literal("+"),
                                    (btn) -> adjustSkill(1))
                                    .pos(rightX + 301, rightY + 120)
                                    .size(14, 14)
                                    .build();
                            addSkill.active = cart.canAfford(skillCost);
                            buttons.add(addSkill);
                        } else if (skillPending > 0) {
                            // Show rollback button when skill is at max level but has pending upgrades
                            Button cancelSkill = new ColoredButton(rightX + 285, rightY + 120, 14, 14,
                                    Component.literal("-"), (btn) -> adjustSkill(-1), 0xFFCC4444);
                            buttons.add(cancelSkill);
                        }
                    }
                }
            }
        }

        return buttons;
    }

    public void update() {
        statEntries.clear();

        int row = 0;
        int col = 0;

        ResourceLocation originId = com.complextalents.origin.client.ClientOriginData.getOriginId();
        List<StatType> upgradableStats = Origin.DEFAULT_UPGRADABLE_STATS;
        if (originId != null) {
            Origin origin = OriginRegistry.getInstance().getOrigin(originId);
            if (origin != null) {
                upgradableStats = origin.getUpgradableStats();
            }
        }

        for (StatType type : upgradableStats) {
            int realCurrentRank = com.complextalents.stats.client.ClientStatsData.getStatRank(type);
            int pendingPurchases = cart.getAmount(UpgradeType.STAT, type);

            int costPerRank = ClassCostMatrix.getCost(originId, type);

            StatEntryData entry = new StatEntryData();
            entry.type = type;
            entry.row = row;
            entry.col = col;
            entry.realRank = realCurrentRank;
            entry.pendingRank = pendingPurchases;
            entry.costPerRank = costPerRank;

            statEntries.add(entry);

            col++;
            if (col >= STATS_PER_ROW) {
                col = 0;
                row++;
            }
        }
    }


    public void renderBackgrounds(GuiGraphics guiGraphics, int xOffset, int yOffset, int mouseX, int mouseY, float partialTick) {
        // Stat entries on left side — compact card rows
        for (StatEntryData entry : statEntries) {
            int entryX = xOffset + 5;
            int entryY = yOffset + (entry.row * STAT_ENTRY_HEIGHT) + 5;

            guiGraphics.fill(entryX, entryY, entryX + 150, entryY + 30, 0xFF2A2E44);
            guiGraphics.fill(entryX + 1, entryY + 1, entryX + 149, entryY + 29, 0xFF141724);
        }

        // Origin upgrade card on right side
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId != null) {
            Origin origin = OriginRegistry.getInstance().getOrigin(originId);
            if (origin != null) {
                int rightX = xOffset + 160;
                int rightY = yOffset + 5;
                guiGraphics.fill(rightX, rightY, rightX + 330, rightY + 140, 0xFF3D321A);
                guiGraphics.fill(rightX + 1, rightY + 1, rightX + 329, rightY + 139, 0xFF171924);
            }
        }

        // Skill upgrade card on right side
        ResourceLocation originId2 = ClientOriginData.getOriginId();
        if (originId2 != null) {
            Origin origin = OriginRegistry.getInstance().getOrigin(originId2);
            if (origin != null) {
                ResourceLocation activeSkillId = origin.getActiveSkillId();
                if (activeSkillId != null) {
                    Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
                    if (skill != null) {
                        int rightX = xOffset + 160;
                        int rightY = yOffset + 155;
                        guiGraphics.fill(rightX, rightY, rightX + 330, rightY + 140, 0xFF3A1A3D);
                        guiGraphics.fill(rightX + 1, rightY + 1, rightX + 329, rightY + 139, 0xFF171924);
                    }
                }
            }
        }
    }

    public void renderLabels(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, int xOffset, int yOffset, int mouseX, int mouseY) {
        // Render Refining Anvil icon on top of anvil button
        net.minecraft.world.item.ItemStack anvilStack = new net.minecraft.world.item.ItemStack(com.complextalents.item.ModItems.REFINING_ANVIL.get());
        guiGraphics.renderItem(anvilStack, xOffset + 467, yOffset + 11);

        // Render stat entries on left
        player.getCapability(GeneralStatsDataProvider.STATS_DATA).ifPresent(data -> {
            for (StatEntryData entry : statEntries) {
                int entryX = xOffset + 5;
                int entryY = yOffset + (entry.row * STAT_ENTRY_HEIGHT) + 5;

                // Stat Name
                String nameText = entry.type.getDisplayName();
                guiGraphics.drawString(font, "§f" + nameText, entryX + 4, entryY + 3, 0xFFFFFF, false);

                // Yield per rank info
                double yieldPerRank = entry.type.getYieldPerRank();
                String yieldLabel;
                if (entry.type == StatType.MAGIC_EFFECTIVENESS) {
                    yieldLabel = formatNumber(yieldPerRank * 100.0) + "/rank";
                } else if (yieldPerRank < 1.0) {
                    yieldLabel = String.format("%.0f%%", yieldPerRank * 100) + "/rank";
                } else {
                    yieldLabel = formatNumber(yieldPerRank) + "/rank";
                }
                guiGraphics.drawString(font, "§8" + yieldLabel, entryX + 4, entryY + 14, 0xFF888888, false);

                // Cost
                guiGraphics.drawString(font, "§e" + entry.costPerRank + "SP", entryX + 65, entryY + 14, 0xFFFFAA00, false);

                // Accumulated stat value on the right side
                double currentValue = entry.realRank * yieldPerRank;
                double pendingDiff = entry.pendingRank * yieldPerRank;

                String currentFormatted;
                String diffFormatted;
                if (entry.type == StatType.MAGIC_EFFECTIVENESS) {
                    currentFormatted = formatNumber(currentValue * 100.0);
                    diffFormatted = formatNumber(pendingDiff * 100.0);
                } else {
                    currentFormatted = yieldPerRank < 1.0 ? formatPercent(currentValue) : formatNumber(currentValue);
                    diffFormatted = yieldPerRank < 1.0 ? formatPercent(pendingDiff) : formatNumber(pendingDiff);
                }

                String valueStr;
                if (entry.pendingRank > 0) {
                    valueStr = "§a" + currentFormatted + " §b+" + diffFormatted;
                } else {
                    valueStr = "§a" + currentFormatted;
                }


                int valueWidth = font.width(valueStr);
                guiGraphics.drawString(font, valueStr, entryX + 145 - valueWidth, entryY + 3, 0xFFFFFF, false);
            }
        });

        // Render origin upgrade card on right
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId != null) {
            Origin origin = OriginRegistry.getInstance().getOrigin(originId);
            if (origin != null) {
                int originLevel = ClientOriginData.getOriginLevel();
                int originPending = cart.getAmount(UpgradeType.ORIGIN, "origin");
                int originTarget = originLevel + originPending;

                int rightX = xOffset + 160;
                int rightY = yOffset + 5;

                // Draw origin icon
                ResourceLocation activeSkillId = origin.getActiveSkillId();
                if (activeSkillId != null) {
                    Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
                    if (skill != null && skill.getIcon() != null) {
                        guiGraphics.blit(skill.getIcon(), rightX + 5, rightY + 3, 0, 0, 16, 16, 16, 16);
                    }
                }

                // Title
                Component originDisplayName = origin.getDisplayName();
                guiGraphics.drawString(font, "§6§l" + originDisplayName.getString(), rightX + 25, rightY + 3, 0xFFFFAA00, false);

                // Level pips
                int maxLevel = origin.getMaxLevel();
                drawLevelIndicators(guiGraphics, rightX + 25, rightY + 14, maxLevel, originLevel, originPending);

                // Description
                Component description = origin.getDescription();
                if (description != null) {
                    guiGraphics.drawWordWrap(font, description, rightX + 5, rightY + 28, 290, 0xCCCCCC);
                }

                // Cost and stat changes
                if (originTarget < origin.getMaxLevel()) {
                    int originCost = OriginManager.getCostForNextLevel(originTarget);
                    guiGraphics.drawString(font, "§e" + originCost + " SP", rightX + 5, rightY + 122, 0xFFFFAA00, false);
                    if (originPending > 0) {
                        String statChanges = generateOriginStatChanges(origin, originLevel, originTarget);
                        if (!statChanges.isEmpty()) {
                            guiGraphics.drawString(font, statChanges, rightX + 5, rightY + 112, 0xFFAAAA, false);
                        }
                    }
                } else if (originPending > 0) {
                    int originCost = OriginManager.getCostForNextLevel(originTarget);
                    guiGraphics.drawString(font, "§e" + originCost + " SP", rightX + 5, rightY + 122, 0xFFFFAA00, false);
                }
            }
        }

        // Render skill upgrade card on right
        ResourceLocation originId2 = ClientOriginData.getOriginId();
        if (originId2 != null) {
            Origin origin = OriginRegistry.getInstance().getOrigin(originId2);
            if (origin != null) {
                ResourceLocation activeSkillId = origin.getActiveSkillId();
                if (activeSkillId != null) {
                    Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
                    if (skill != null) {
                        int skillLevel = getActiveSkillLevel();
                        int skillPending = cart.getAmount(UpgradeType.SKILL, activeSkillId);
                        int skillTarget = skillLevel + skillPending;

                        int rightX = xOffset + 160;
                        int rightY = yOffset + 155;

                        // Draw skill icon
                        if (skill.getIcon() != null) {
                            guiGraphics.blit(skill.getIcon(), rightX + 5, rightY + 3, 0, 0, 16, 16, 16, 16);
                        }

                        // Title
                        Component skillDisplayName = skill.getDisplayName();
                        guiGraphics.drawString(font, "§d§l" + skillDisplayName.getString(), rightX + 25, rightY + 3, 0xFFE040FB, false);

                        // Level pips
                        int maxLevel = skill.getMaxLevel();
                        drawLevelIndicators(guiGraphics, rightX + 25, rightY + 14, maxLevel, skillLevel, skillPending);

                        // Scaling description
                        String scalingText = generateSkillScalingDescription(skill, skillTarget);
                        guiGraphics.drawWordWrap(font, Component.literal(scalingText), rightX + 5, rightY + 28, 290, 0xCCCCCC);

                        // Cost and stat changes
                        if (skillTarget < skill.getMaxLevel()) {
                            int skillCost = OriginManager.getSkillCostForNextLevel(skillTarget);
                            guiGraphics.drawString(font, "§e" + skillCost + " SP", rightX + 5, rightY + 122, 0xFFFFAA00, false);
                            if (skillPending > 0) {
                                String statChanges = generateSkillStatChanges(skill, skillLevel, skillTarget);
                                if (!statChanges.isEmpty()) {
                                    guiGraphics.drawString(font, statChanges, rightX + 5, rightY + 112, 0xFFAAAA, false);
                                }
                            }
                        } else if (skillPending > 0) {
                            int skillCost = OriginManager.getSkillCostForNextLevel(skillTarget);
                            guiGraphics.drawString(font, "§e" + skillCost + " SP", rightX + 5, rightY + 122, 0xFFFFAA00, false);
                        }
                    }
                }
            }
        }
    }


    private void adjust(StatType type, int delta) {
        ResourceLocation originId = com.complextalents.origin.client.ClientOriginData.getOriginId();
        int cost = ClassCostMatrix.getCost(originId, type);

        if (delta > 0) {
            if (cart.canAfford(cost)) {
                cart.modifyItem(UpgradeType.STAT, type, 1, cost);
            }
        } else if (delta < 0) {
            cart.modifyItem(UpgradeType.STAT, type, -1, -cost);
        }
    }

    private void adjustOrigin(int delta) {
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) return;

        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        if (origin == null) return;

        int originLevel = ClientOriginData.getOriginLevel();
        int originPending = cart.getAmount(UpgradeType.ORIGIN, "origin");
        int originTarget = originLevel + originPending;

        if (delta > 0) {
            if (originTarget < origin.getMaxLevel()) {
                int cost = OriginManager.getCostForNextLevel(originTarget);
                if (cart.canAfford(cost)) {
                    cart.modifyItem(UpgradeType.ORIGIN, "origin", 1, cost);
                }
            }
        } else if (delta < 0) {
            if (originPending > 0) {
                int cost = OriginManager.getCostForNextLevel(originTarget - 1);
                cart.modifyItem(UpgradeType.ORIGIN, "origin", -1, -cost);
            }
        }
    }

    private void adjustSkill(int delta) {
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) return;

        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        if (origin == null) return;

        ResourceLocation activeSkillId = origin.getActiveSkillId();
        if (activeSkillId == null) return;

        Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
        if (skill == null) return;

        int skillLevel = getActiveSkillLevel();
        int skillPending = cart.getAmount(UpgradeType.SKILL, activeSkillId);
        int skillTarget = skillLevel + skillPending;

        if (delta > 0) {
            if (skillTarget < skill.getMaxLevel()) {
                int cost = OriginManager.getSkillCostForNextLevel(skillTarget);
                if (cart.canAfford(cost)) {
                    cart.modifyItem(UpgradeType.SKILL, activeSkillId, 1, cost);
                }
            }
        } else if (delta < 0) {
            if (skillPending > 0) {
                int cost = OriginManager.getSkillCostForNextLevel(skillTarget - 1);
                cart.modifyItem(UpgradeType.SKILL, activeSkillId, -1, -cost);
            }
        }
    }

    private int getActiveSkillLevel() {
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) return 0;

        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        if (origin == null) return 0;

        ResourceLocation activeSkillId = origin.getActiveSkillId();
        if (activeSkillId == null) return 0;

        return ClientSkillData.getSkillLevel(activeSkillId);
    }

    private String generateSkillScalingDescription(Skill skill, int skillTarget) {
        return skill.getDescription().getString();
    }

    private String generateOriginStatChanges(Origin origin, int currentLevel, int nextLevel) {
        // Get the first scaled stat from the origin and show its change
        var scaledStats = origin.getScaledStats();
        if (scaledStats.isEmpty()) {
            return "";
        }

        // Get the first stat entry
        var firstStat = scaledStats.values().stream().findFirst().orElse(null);
        if (firstStat == null) {
            return "";
        }

        double currentVal = firstStat.getValue(currentLevel);
        double nextVal = firstStat.getValue(nextLevel);

        String displayName = firstStat.displayName().getString();
        if (currentVal == nextVal) {
            return "";
        }

        // Format based on the type of number (percentage or absolute)
        String format = (currentVal > 0 && nextVal > 0 && nextVal < 1) ? "§8" + displayName + ": " + formatPercent(currentVal) + " -> " + formatPercent(nextVal)
                : "§8" + displayName + ": " + formatNumber(currentVal) + " -> " + formatNumber(nextVal);
        return format;
    }

    private String generateSkillStatChanges(Skill skill, int currentLevel, int nextLevel) {
        // Get the first scaled stat from the skill and show its change
        var scaledStats = skill.getScaledStats();
        if (scaledStats.isEmpty()) {
            return "";
        }

        // Get the first stat entry
        var firstStat = scaledStats.values().stream().findFirst().orElse(null);
        if (firstStat == null) {
            return "";
        }

        double currentVal = firstStat.getValue(currentLevel);
        double nextVal = firstStat.getValue(nextLevel);

        String displayName = firstStat.displayName().getString();
        if (currentVal == nextVal) {
            return "";
        }

        // Format based on the type of number (percentage or absolute)
        String format = (currentVal > 0 && nextVal > 0 && nextVal < 1) ? "§8" + displayName + ": " + formatPercent(currentVal) + " -> " + formatPercent(nextVal)
                : "§8" + displayName + ": " + formatNumber(currentVal) + " -> " + formatNumber(nextVal);
        return format;
    }

    private String formatPercent(double value) {
        return String.format("%.0f%%", value * 100);
    }

    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.format("%d", (long) value);
        } else {
            return String.format("%.3f", value);
        }
    }

    private void drawLevelIndicators(GuiGraphics guiGraphics, int x, int y, int maxLevel, int currentLevel, int pendingLevel) {
        int targetLevel = currentLevel + pendingLevel;
        int rectWidth = 6;
        int rectHeight = 6;
        int spacing = 2;

        for (int i = 0; i < maxLevel; i++) {
            int rectX = x + (i * (rectWidth + spacing));
            int rectY = y;

            if (i < currentLevel) {
                // Learned level - filled with gold yellow
                guiGraphics.fill(rectX, rectY, rectX + rectWidth, rectY + rectHeight, 0xFFFFAA00);
            } else if (i < targetLevel) {
                // Preview level - filled with white
                guiGraphics.fill(rectX, rectY, rectX + rectWidth, rectY + rectHeight, 0xFFFFFFFF);
            } else {
                // Unlearned level - outlined only
                guiGraphics.fill(rectX, rectY, rectX + rectWidth, rectY + 1, 0xFF888888);
                guiGraphics.fill(rectX, rectY, rectX + 1, rectY + rectHeight, 0xFF888888);
                guiGraphics.fill(rectX + rectWidth - 1, rectY, rectX + rectWidth, rectY + rectHeight, 0xFF888888);
                guiGraphics.fill(rectX, rectY + rectHeight - 1, rectX + rectWidth, rectY + rectHeight, 0xFF888888);
            }
        }
    }
}
