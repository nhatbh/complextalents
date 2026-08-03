package com.complextalents.client;

import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginManager;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.skill.Skill;
import com.complextalents.skill.SkillRegistry;
import com.complextalents.stats.ScaledStat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OriginTabUI {
    private final UpgradeCart cart;

    public OriginTabUI(UpgradeCart cart) {
        this.cart = cart;
    }

    public List<Button> buildWidgets(Screen screen, int xOffset, int yOffset) {
        List<Button> buttons = new ArrayList<>();
        update();

        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) {
            return buttons;
        }

        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        if (origin == null) {
            return buttons;
        }

        // Origin upgrade button
        int originTarget = ClientOriginData.getOriginLevel() + getOriginPending();
        int nextOriginCost = OriginManager.getCostForNextLevel(originTarget);

        Button upgOriginBtn;
        if (originTarget >= origin.getMaxLevel()) {
            upgOriginBtn = Button.builder(Component.literal("MAX RANK ATTAINED"),
                    (btn) -> {})
                    .pos(xOffset + 10, yOffset + 140)
                    .size(220, 20)
                    .build();
            upgOriginBtn.active = false;
        } else if (cart.canAfford(nextOriginCost)) {
            upgOriginBtn = Button.builder(Component.literal("Add Rank (Cost: " + nextOriginCost + ")"),
                    (btn) -> adjustOrigin(1))
                    .pos(xOffset + 10, yOffset + 140)
                    .size(220, 20)
                    .build();
        } else {
            upgOriginBtn = Button.builder(Component.literal("Insufficient SP"),
                    (btn) -> {})
                    .pos(xOffset + 10, yOffset + 140)
                    .size(220, 20)
                    .build();
            upgOriginBtn.active = false;
        }
        buttons.add(upgOriginBtn);

        // Cancel origin button
        if (getOriginPending() > 0) {
            Button cancelOrigin = Button.builder(Component.literal("Cancel Upgrade(s)"),
                    (btn) -> adjustOrigin(-1))
                    .pos(xOffset + 10, yOffset + 165)
                    .size(220, 16)
                    .build();
            buttons.add(cancelOrigin);
        }

        // Skill upgrade button
        ResourceLocation activeSkillId = origin.getActiveSkillId();
        if (activeSkillId != null) {
            Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
            if (skill != null) {
                int skillTarget = getActiveSkillLevel() + getSkillPending();
                int nextSkillCost = OriginManager.getSkillCostForNextLevel(skillTarget);

                Button upgSkillBtn;
                if (skillTarget >= skill.getMaxLevel()) {
                    upgSkillBtn = Button.builder(Component.literal("SKILL FULLY MASTERED"),
                            (btn) -> {})
                            .pos(xOffset + 260, yOffset + 140)
                            .size(220, 20)
                            .build();
                    upgSkillBtn.active = false;
                } else if (cart.canAfford(nextSkillCost)) {
                    upgSkillBtn = Button.builder(Component.literal("Add Rank (Cost: " + nextSkillCost + ")"),
                            (btn) -> adjustSkill(1))
                            .pos(xOffset + 260, yOffset + 140)
                            .size(220, 20)
                            .build();
                } else {
                    upgSkillBtn = Button.builder(Component.literal("Insufficient SP"),
                            (btn) -> {})
                            .pos(xOffset + 260, yOffset + 140)
                            .size(220, 20)
                            .build();
                    upgSkillBtn.active = false;
                }
                buttons.add(upgSkillBtn);

                // Cancel skill button
                if (getSkillPending() > 0) {
                    Button cancelSkill = Button.builder(Component.literal("Cancel Upgrade(s)"),
                            (btn) -> adjustSkill(-1))
                            .pos(xOffset + 260, yOffset + 165)
                            .size(220, 16)
                            .build();
                    buttons.add(cancelSkill);
                }
            }
        }

        return buttons;
    }

    public void update() {
        // Update is handled by parent screen via cart callback
    }

    public void renderBackgrounds(GuiGraphics guiGraphics, int xOffset, int yOffset, int mouseX, int mouseY, float partialTick) {
        // Draw origin card background with gold accent border
        guiGraphics.fill(xOffset + 10, yOffset + 10, xOffset + 245, yOffset + 355, 0xFF3D321A);
        guiGraphics.fill(xOffset + 11, yOffset + 11, xOffset + 244, yOffset + 354, 0xFF171924);

        // Draw skill card background with magenta accent border
        guiGraphics.fill(xOffset + 255, yOffset + 10, xOffset + 490, yOffset + 355, 0xFF3A1A3D);
        guiGraphics.fill(xOffset + 256, yOffset + 11, xOffset + 489, yOffset + 354, 0xFF171924);
    }

    public void renderLabels(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, int xOffset, int yOffset, int mouseX, int mouseY) {
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) {
            guiGraphics.drawString(font, "§cNo active origin selected.", xOffset + 20, yOffset + 20, 0xFFCC0000, false);
            return;
        }

        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        if (origin == null) return;

        // Origin Area
        int originX = xOffset + 20;
        int originY = yOffset + 20;

        // Header tag
        guiGraphics.drawString(font, "§7[ CLASS ORIGIN ]", originX, originY, 0xFFAAAAAA, false);
        originY += 12;

        // Origin name
        String originName = "§6§l" + origin.getDisplayName().getString().toUpperCase();
        guiGraphics.drawString(font, originName, originX, originY, 0xFFFFAA00, false);

        // Current Rank Badge
        int currentOriginLevel = ClientOriginData.getOriginLevel();
        int pendingOriginLevel = getOriginPending();
        String rankBadge = "Rank " + currentOriginLevel + "/5";
        if (pendingOriginLevel > 0) {
            rankBadge += " §a(+" + pendingOriginLevel + ")";
        }
        guiGraphics.drawString(font, "§e" + rankBadge, originX + 130, originY, 0xFFFFD700, false);

        // Origin description (wrapped)
        originY += 18;
        String originDesc = origin.getDescription().getString();
        guiGraphics.drawWordWrap(font, Component.literal(originDesc), originX, originY, 210, 0xBBCCDDEE);

        // Rank Progress Pips (L1 - L5)
        originY += 45;
        guiGraphics.drawString(font, "§7Scaling Profile:", originX, originY, 0xFF888888, false);
        originY += 12;

        for (int i = 1; i <= 5; i++) {
            int activeTarget = currentOriginLevel + pendingOriginLevel;
            int pipX = originX + 70 + (i - 1) * 30;
            boolean isActive = (i <= activeTarget);
            boolean isPending = (i > currentOriginLevel && i <= activeTarget);

            int pipBg = isPending ? 0xFF2E4D2E : (isActive ? 0xFF5D4810 : 0xFF222533);
            int pipBorder = isPending ? 0xFF55FF55 : (isActive ? 0xFFFFD700 : 0xFF3D4258);
            guiGraphics.fill(pipX, originY - 2, pipX + 26, originY + 10, pipBorder);
            guiGraphics.fill(pipX + 1, originY - 1, pipX + 25, originY + 9, pipBg);

            String text = "L" + i;
            guiGraphics.drawString(font, (isActive ? "§f" : "§7") + text, pipX + 7, originY, 0xFFFFFF, false);
        }

        // Scaling table rows for origin
        Map<String, ScaledStat> originStats = new HashMap<>(origin.getScaledStats());
        originY += 18;
        int row = 0;
        String[] priority = {"Cooldown", "Cost"};
        for (String key : priority) {
            if (originStats.containsKey(key) && row < 5) {
                renderTableRow(guiGraphics, font, originX, originY + row * 13, originStats.get(key), true);
                row++;
            }
        }
        for (var entry : originStats.entrySet()) {
            if (row >= 5) break;
            if (entry.getKey().equals("Cooldown") || entry.getKey().equals("Cost")) continue;
            renderTableRow(guiGraphics, font, originX, originY + row * 13, entry.getValue(), true);
            row++;
        }

        // Skill Area
        ResourceLocation activeSkillId = origin.getActiveSkillId();
        if (activeSkillId != null) {
            Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
            if (skill != null) {
                int skillX = xOffset + 265;
                int skillY = yOffset + 20;

                // Header tag
                guiGraphics.drawString(font, "§7[ SIGNATURE ABILITY ]", skillX, skillY, 0xFFAAAAAA, false);
                skillY += 12;

                // Skill name
                String skillName = "§d§l" + skill.getDisplayName().getString().toUpperCase();
                guiGraphics.drawString(font, skillName, skillX, skillY, 0xFFFF00FF, false);

                // Skill Level Badge
                int currentSkillLevel = getActiveSkillLevel();
                int pendingSkillLevel = getSkillPending();
                String skillBadge = "Lvl " + currentSkillLevel + "/5";
                if (pendingSkillLevel > 0) {
                    skillBadge += " §a(+" + pendingSkillLevel + ")";
                }
                guiGraphics.drawString(font, "§b" + skillBadge, skillX + 130, skillY, 0xFF00E5FF, false);

                // Skill description
                skillY += 18;
                String skillDesc = skill.getDescription().getString();
                guiGraphics.drawWordWrap(font, Component.literal(skillDesc), skillX, skillY, 210, 0xBBCCDDEE);

                // Rank Progress Pips (L1 - L5)
                skillY += 45;
                guiGraphics.drawString(font, "§7Ability Scaling:", skillX, skillY, 0xFF888888, false);
                skillY += 12;

                for (int i = 1; i <= 5; i++) {
                    int activeTarget = currentSkillLevel + pendingSkillLevel;
                    int pipX = skillX + 70 + (i - 1) * 30;
                    boolean isActive = (i <= activeTarget);
                    boolean isPending = (i > currentSkillLevel && i <= activeTarget);

                    int pipBg = isPending ? 0xFF2E4D2E : (isActive ? 0xFF4A194A : 0xFF222533);
                    int pipBorder = isPending ? 0xFF55FF55 : (isActive ? 0xFFE040FB : 0xFF3D4258);
                    guiGraphics.fill(pipX, skillY - 2, pipX + 26, skillY + 10, pipBorder);
                    guiGraphics.fill(pipX + 1, skillY - 1, pipX + 25, skillY + 9, pipBg);

                    String text = "L" + i;
                    guiGraphics.drawString(font, (isActive ? "§f" : "§7") + text, pipX + 7, skillY, 0xFFFFFF, false);
                }

                // Scaling table rows for skill
                Map<String, ScaledStat> skillStats = new HashMap<>();
                double[] cds = new double[5];
                double[] costs = new double[5];
                for (int i = 1; i <= 5; i++) {
                    cds[i - 1] = skill.getActiveCooldown(i);
                    costs[i - 1] = skill.getResourceCost(i);
                }
                if (cds[0] > 0 || cds[4] > 0) skillStats.put("Cooldown", new ScaledStat("Cooldown", cds));
                if (skill.getResourceType() != null) skillStats.put("Cost", new ScaledStat("Cost", costs));
                skillStats.putAll(skill.getScaledStats());

                skillY += 18;
                row = 0;
                for (String key : priority) {
                    if (skillStats.containsKey(key) && row < 5) {
                        renderTableRow(guiGraphics, font, skillX, skillY + row * 13, skillStats.get(key), false);
                        row++;
                    }
                }
                for (var entry : skillStats.entrySet()) {
                    if (row >= 5) break;
                    if (entry.getKey().equals("Cooldown") || entry.getKey().equals("Cost")) continue;
                    renderTableRow(guiGraphics, font, skillX, skillY + row * 13, entry.getValue(), false);
                    row++;
                }
            }
        }
    }

    private void renderTableRow(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, int x, int y, ScaledStat stat, boolean isOrigin) {
        String label = stat.displayName().getString();
        if (label.length() > 11) label = label.substring(0, 9) + "..";
        guiGraphics.drawString(font, "§8" + label, x, y, 0xFF888888, false);

        double[] values = stat.values();
        for (int i = 1; i <= 5; i++) {
            int current = isOrigin ? ClientOriginData.getOriginLevel() + getOriginPending() : getActiveSkillLevel() + getSkillPending();
            String color = (i == current) ? "§f" : "§7";
            double v = values[Math.min(i - 1, values.length - 1)];
            String formattedVal = formatValue(v);
            guiGraphics.drawString(font, color + formattedVal, x + 70 + (i - 1) * 30, y, 0xFFFFFF, false);
        }
    }

    public void mouseScrolled(double scrollDelta) {
        // No scrolling needed for this tab
    }

    private String formatValue(double v) {
        if (v == (long) v) return String.format("%d", (long) v);
        if (v < 0.1) return String.format("%.3f", v);
        if (v < 1.0) return String.format("%.2f", v);
        return String.format("%.1f", v);
    }

    private int getOriginPending() {
        return cart.getAmount(UpgradeType.ORIGIN, "origin");
    }

    private int getSkillPending() {
        return cart.getAmount(UpgradeType.SKILL, "skill");
    }

    private void adjustOrigin(int delta) {
        int current = getOriginPending();
        int next = current + delta;
        if (next < 0 || (ClientOriginData.getOriginLevel() + next) > 5) return;

        if (delta > 0) {
            int lvl = ClientOriginData.getOriginLevel() + current;
            int cost = OriginManager.getCostForNextLevel(lvl);
            if (cart.canAfford(cost)) {
                cart.modifyItem(UpgradeType.ORIGIN, "origin", 1, cost);
            }
        } else if (delta < 0) {
            int lvl = ClientOriginData.getOriginLevel() + current - 1;
            int cost = OriginManager.getCostForNextLevel(lvl);
            cart.modifyItem(UpgradeType.ORIGIN, "origin", -1, -cost);
        }
    }

    private void adjustSkill(int delta) {
        int current = getSkillPending();
        int next = current + delta;
        int currentLevel = getActiveSkillLevel();
        if (next < 0 || (currentLevel + next) > 5) return;

        if (delta > 0) {
            int lvl = currentLevel + current;
            int cost = OriginManager.getSkillCostForNextLevel(lvl);
            if (cart.canAfford(cost)) {
                cart.modifyItem(UpgradeType.SKILL, "skill", 1, cost);
            }
        } else if (delta < 0) {
            int lvl = currentLevel + current - 1;
            int cost = OriginManager.getSkillCostForNextLevel(lvl);
            cart.modifyItem(UpgradeType.SKILL, "skill", -1, -cost);
        }
    }

    private int getActiveSkillLevel() {
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) return 0;
        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        if (origin == null) return 0;
        ResourceLocation skillId = origin.getActiveSkillId();
        if (skillId == null) return 0;
        
        int level = com.complextalents.skill.client.ClientSkillData.getSkillLevel(skillId);
        // Fail-safe: If origin is active but skill level is 0, default to 1 for origin skills
        return level > 0 ? level : 1;
    }
}
